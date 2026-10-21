package com.example.dengbaoevidence.ui.main

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dengbaoevidence.data.DataRepository
import com.example.dengbaoevidence.data.DeviceTypeWithItems
import com.example.dengbaoevidence.data.DeviceWithItems
import com.example.dengbaoevidence.data.EvidenceConclusion
import com.example.dengbaoevidence.data.EvidenceItemWithPhotos
import com.example.dengbaoevidence.data.PhotoRegistration
import com.example.dengbaoevidence.data.ProjectPackageExporter
import com.example.dengbaoevidence.data.ProjectEntity
import com.example.dengbaoevidence.data.ProjectSummary
import com.example.dengbaoevidence.data.TemplateItemEntity
import com.example.dengbaoevidence.data.TemplateConflictStrategy
import com.example.dengbaoevidence.data.TemplateImportPreview
import com.example.dengbaoevidence.data.TemplateTransfer
import com.example.dengbaoevidence.data.WordExporter
import com.example.dengbaoevidence.data.WordTemplateStore
import com.example.dengbaoevidence.data.BackupArchiveWriter
import com.example.dengbaoevidence.data.BackupSpaceEstimator
import com.example.dengbaoevidence.data.BackupSpacePreview
import com.example.dengbaoevidence.data.BackupStorage
import com.example.dengbaoevidence.data.BackupStorageGuard
import com.example.dengbaoevidence.data.ExportFileIo
import com.example.dengbaoevidence.data.createChildTree
import com.example.dengbaoevidence.data.createFileTree
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class SaveStatus {
  IDLE,
  SAVING,
  SAVED,
  FAILED,
}

enum class ExportKind { TEMPLATES, BACKUP, WORD, PROJECT_PACKAGE }

data class ExportProgress(val kind: ExportKind, val projectId: Long?, val percent: Int, val message: String)

data class BackupPreflight(val directory: Uri, val useSubdirectory: Boolean, val space: BackupSpacePreview? = null)

class EvidenceViewModel(private val repository: DataRepository) : ViewModel() {
  val projects: StateFlow<List<ProjectSummary>> =
    repository.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val deviceTypes: StateFlow<List<DeviceTypeWithItems>> =
    repository.observeDeviceTypes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _saveStatuses = MutableStateFlow<Map<Long, SaveStatus>>(emptyMap())
  val saveStatuses: StateFlow<Map<Long, SaveStatus>> = _saveStatuses.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
  val exportProgress: StateFlow<ExportProgress?> = _exportProgress.asStateFlow()

  private val _backupPreflight = MutableStateFlow<BackupPreflight?>(null)
  val backupPreflight: StateFlow<BackupPreflight?> = _backupPreflight.asStateFlow()
  private val _backupSuccessMessage = MutableStateFlow<String?>(null)
  val backupSuccessMessage: StateFlow<String?> = _backupSuccessMessage.asStateFlow()
  private var backupPreflightJob: Job? = null
  private val _wordTemplateBusy = MutableStateFlow(false)
  val wordTemplateBusy = _wordTemplateBusy.asStateFlow()
  private val _wordTemplateRevision = MutableStateFlow(0)
  val wordTemplateRevision = _wordTemplateRevision.asStateFlow()

  fun importWordTemplate(context: Context, uri: Uri) {
    changeWordTemplate { WordTemplateStore.import(context, uri) }
  }

  fun removeWordTemplate(context: Context) {
    changeWordTemplate { WordTemplateStore.remove(context) }
  }

  private fun changeWordTemplate(action: () -> Unit) {
    if (_wordTemplateBusy.value || _exportProgress.value != null || _backupPreflight.value != null) {
      _errorMessage.value = "请等待当前导出或模板操作完成"
      return
    }
    _wordTemplateBusy.value = true
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { action() }
        _wordTemplateRevision.value += 1
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        showError("Word 模板设置失败", error)
      }
      finally { _wordTemplateBusy.value = false }
    }
  }

  private val pendingNotes = mutableMapOf<Long, String>()
  private val noteJobs = mutableMapOf<Long, Job>()

  init {
    viewModelScope.launch {
      runCatching { repository.ensureDefaultTemplates() }
        .onFailure { showError("初始化设备模板失败", it) }
    }
  }

  fun observeProject(projectId: Long): Flow<ProjectEntity?> = repository.observeProject(projectId)

  fun observeDevices(projectId: Long): Flow<List<DeviceWithItems>> = repository.observeDevices(projectId)

  fun observeDevice(deviceId: Long): Flow<DeviceWithItems?> = repository.observeDevice(deviceId)

  fun observeEvidenceItem(itemId: Long): Flow<EvidenceItemWithPhotos?> = repository.observeEvidenceItem(itemId)

  fun createProject(name: String, onCreated: (Long) -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.createProject(name) }
        .onSuccess(onCreated)
        .onFailure { showError("创建项目失败", it) }
    }
  }

  fun renameProject(projectId: Long, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.renameProject(projectId, name) }
        .onFailure { showError("修改项目名称失败", it) }
    }
  }

  fun deleteProject(projectId: Long, onFinished: (Boolean) -> Unit) {
    if (rejectDeletionDuringExport(onFinished)) return
    viewModelScope.launch {
      runCatching { repository.deleteProject(projectId) }
        .onSuccess { result ->
          _errorMessage.value = deletionMessage("项目", result.photoCount, result.failedFileCount)
          onFinished(true)
        }
        .onFailure {
          showError("删除项目失败", it)
          onFinished(false)
        }
    }
  }

  fun createDeviceType(name: String, copyFromTemplateId: Long? = null, onCreated: (Long) -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.createDeviceType(name, copyFromTemplateId) }
        .onSuccess(onCreated)
        .onFailure { showError("新增设备类型失败", it) }
    }
  }

  fun renameDeviceType(templateId: Long, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.renameDeviceType(templateId, name) }
        .onFailure { showError("修改设备类型失败", it) }
    }
  }

  fun reorderDeviceTypes(orderedTemplateIds: List<Long>) {
    viewModelScope.launch {
      runCatching { repository.reorderDeviceTypes(orderedTemplateIds) }
        .onFailure { showError("调整设备类型顺序失败", it) }
    }
  }

  fun deleteDeviceType(templateId: Long, onDeleted: () -> Unit) {
    viewModelScope.launch {
      runCatching { repository.deleteDeviceType(templateId) }
        .onSuccess { onDeleted() }
        .onFailure { showError("删除设备类型失败", it) }
    }
  }

  fun addTemplateItem(templateId: Long, name: String, onCreated: () -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.addTemplateItem(templateId, name) }
        .onSuccess { onCreated() }
        .onFailure { showError("新增取证项失败", it) }
    }
  }

  fun updateTemplateItem(item: TemplateItemEntity) {
    viewModelScope.launch {
      runCatching {
        repository.updateTemplateItem(
          itemId = item.id,
          name = item.name,
          displayOrder = item.displayOrder,
          isKeyScreenshot = item.isKeyScreenshot,
          wordOrder = item.wordOrder,
          verificationMethod = item.verificationMethod,
        )
      }.onFailure { showError("修改取证项失败", it) }
    }
  }

  fun reorderTemplateItems(templateId: Long, orderedItemIds: List<Long>) {
    viewModelScope.launch {
      runCatching { repository.reorderTemplateItems(templateId, orderedItemIds) }
        .onFailure { showError("调整取证项顺序失败", it) }
    }
  }

  fun deleteTemplateItem(itemId: Long, onDeleted: () -> Unit) {
    viewModelScope.launch {
      runCatching { repository.deleteTemplateItem(itemId) }
        .onSuccess { onDeleted() }
        .onFailure { showError("删除取证项失败", it) }
    }
  }

  fun exportDeviceTypeTemplates(context: Context, directory: Uri, useSubdirectory: Boolean = true) {
    val date = ExportFileIo.dateSuffix()
    exportToDirectory(
      context, directory, if (useSubdirectory) "设备类型模板" else null,
      { "等保取证_设备类型模板_$date.json" }, "application/json", ExportKind.TEMPLATES,
      produce = { file, progress ->
        val json = TemplateTransfer.encode(repository.getDeviceTypesSnapshot())
        file.writeText(json, Charsets.UTF_8)
        TemplateTransfer.decode(file.readText(Charsets.UTF_8))
        progress(100, "模板已生成")
      },
      successMessage = { "设备类型模板已导出" },
    )
  }

  fun prepareBackup(context: Context, directory: Uri, useSubdirectory: Boolean = true) {
    if (_exportProgress.value != null || _wordTemplateBusy.value) {
      _errorMessage.value = "已有导出任务，请等待完成"
      return
    }
    backupPreflightJob?.cancel()
    val pending = BackupPreflight(directory, useSubdirectory)
    _backupPreflight.value = pending
    backupPreflightJob = viewModelScope.launch {
      try {
        val space = withContext(Dispatchers.IO) {
          val job = currentCoroutineContext()
          val (projects, deviceTypes) = repository.getBackupSnapshot()
          val templates = TemplateTransfer.encode(deviceTypes)
          val estimate = BackupSpaceEstimator.estimate(projects, templates.toByteArray(Charsets.UTF_8).size.toLong()) {
            job.ensureActive()
          }
          BackupStorage.inspect(context, directory).preview(estimate)
        }
        if (_backupPreflight.value === pending) _backupPreflight.value = pending.copy(space = space)
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        if (_backupPreflight.value === pending) {
          _backupPreflight.value = null
          showError("备份空间检查失败", throwable)
        }
      }
    }
  }

  fun cancelBackupPreparation() {
    backupPreflightJob?.cancel()
    _backupPreflight.value = null
  }

  fun confirmBackup(context: Context) {
    val pending = _backupPreflight.value ?: return
    if (pending.space?.canStart != true) return
    _backupPreflight.value = null
    backupAllData(context, pending.directory, pending.useSubdirectory)
  }

  private fun backupAllData(context: Context, directory: Uri, useSubdirectory: Boolean) {
    val date = ExportFileIo.dateSuffix()
    var storageGuard: BackupStorageGuard? = null
    exportToDirectory(
      context, directory, if (useSubdirectory) "数据备份" else null,
      { "Aster-数据备份-$date.zip" }, "application/zip", ExportKind.BACKUP,
      beforeSave = { file -> requireNotNull(storageGuard).beforeSave(file) },
      beforeWrite = { bytes -> requireNotNull(storageGuard).beforeDestinationWrite(bytes) },
      produce = { file, progress ->
        progress(0, "正在重新检查备份空间")
        val job = currentCoroutineContext()
        val (projects, deviceTypes) = repository.getBackupSnapshot()
        val templates = TemplateTransfer.encode(deviceTypes)
        val estimate = BackupSpaceEstimator.estimate(projects, templates.toByteArray(Charsets.UTF_8).size.toLong()) {
          job.ensureActive()
        }
        val guard = BackupStorage.inspect(context, directory).guard(estimate)
        storageGuard = guard
        val checkWrite: (Int) -> Unit = { bytes -> job.ensureActive(); guard.beforeInternalWrite(bytes) }
        BackupArchiveWriter.export(
          projects, templates, date, file, File(context.cacheDir, "backup_${UUID.randomUUID()}"),
          exportPackage = { data, target, callback -> ProjectPackageExporter.export(context, data, target, callback, checkWrite) },
          exportWord = { data, target, callback -> WordExporter.export(context, data, target, callback, checkWrite) },
          beforeProject = { job.ensureActive(); guard.beforeProject() },
          beforeWrite = checkWrite,
          onProgress = progress,
        )
      },
      successMessage = { "数据备份已完成" },
    )
  }

  fun previewTemplateImport(context: Context, source: Uri, onFinished: (TemplateImportPreview?) -> Unit) {
    viewModelScope.launch {
      runCatching {
        val packageData = withContext(Dispatchers.IO) { readTemplatePackage(context, source) }
        repository.previewTemplateImport(packageData)
      }.onSuccess(onFinished)
        .onFailure {
          showError("设备类型模板文件读取失败", it)
          onFinished(null)
        }
    }
  }

  fun importDeviceTypeTemplates(
    context: Context,
    source: Uri,
    conflictStrategy: TemplateConflictStrategy,
    onFinished: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      runCatching {
        val packageData = withContext(Dispatchers.IO) { readTemplatePackage(context, source) }
        repository.importDeviceTypes(packageData, conflictStrategy)
      }.onSuccess { result ->
        _errorMessage.value = buildString {
          append("设备类型模板导入完成")
          if (result.importedCount > 0) append("：新增 ${result.importedCount} 个")
          if (result.overwrittenCount > 0) append("，覆盖 ${result.overwrittenCount} 个")
          if (result.renamedCount > 0) append("（其中 ${result.renamedCount} 个自动改名）")
          if (result.skippedCount > 0) append("，跳过 ${result.skippedCount} 个")
        }
        onFinished(true)
      }.onFailure {
        showError("设备类型模板导入失败", it)
        onFinished(false)
      }
    }
  }

  fun createDevice(projectId: Long, name: String, templateId: Long, onCreated: (Long) -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.createDevice(projectId, name, templateId) }
        .onSuccess(onCreated)
        .onFailure { showError("创建设备失败", it) }
    }
  }

  fun addTemporaryItem(deviceId: Long, name: String, onCreated: (Long) -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.addTemporaryEvidenceItem(deviceId, name) }
        .onSuccess(onCreated)
        .onFailure { showError("新增取证项失败", it) }
    }
  }

  fun updateConclusion(itemId: Long, conclusion: EvidenceConclusion) {
    setStatus(itemId, SaveStatus.SAVING)
    viewModelScope.launch {
      runCatching { repository.updateConclusion(itemId, conclusion) }
        .onSuccess { setStatus(itemId, SaveStatus.SAVED) }
        .onFailure {
          setStatus(itemId, SaveStatus.FAILED)
          showError("核查结论保存失败", it)
        }
    }
  }

  fun updateNote(itemId: Long, note: String) {
    pendingNotes[itemId] = note
    noteJobs.remove(itemId)?.cancel()
    setStatus(itemId, SaveStatus.SAVING)
    noteJobs[itemId] = viewModelScope.launch {
      delay(NOTE_SAVE_DELAY_MS)
      persistNote(itemId, note)
    }
  }

  fun flushNote(itemId: Long) {
    val note = pendingNotes[itemId] ?: return
    noteJobs.remove(itemId)?.cancel()
    noteJobs[itemId] = viewModelScope.launch { persistNote(itemId, note) }
  }

  fun flushAllNotes() {
    pendingNotes.keys.toList().forEach(::flushNote)
  }

  fun registerCapturedPhoto(itemId: Long, file: File, onSaved: () -> Unit, onFailed: () -> Unit) {
    setStatus(itemId, SaveStatus.SAVING)
    viewModelScope.launch {
      runCatching {
        val hash = withContext(Dispatchers.IO) { sha256(file) }
        repository.registerPhoto(itemId, file, hash)
      }.onSuccess {
        setStatus(itemId, SaveStatus.SAVED)
        onSaved()
      }.onFailure {
        withContext(Dispatchers.IO) { file.delete() }
        setStatus(itemId, SaveStatus.FAILED)
        showError("照片保存失败，请重试", it)
        onFailed()
      }
    }
  }

  fun importPhotos(itemId: Long, context: Context, uris: List<Uri>, onFinished: () -> Unit) {
    if (uris.isEmpty()) return
    setStatus(itemId, SaveStatus.SAVING)
    viewModelScope.launch {
      val copiedFiles = mutableListOf<File>()
      runCatching {
        val files = withContext(Dispatchers.IO) {
          uris.map { uri ->
            copyImportedPhoto(context, itemId, uri).also(copiedFiles::add)
          }
        }
        val registrations = files.map { file ->
          PhotoRegistration(file, withContext(Dispatchers.IO) { sha256(file) })
        }
        repository.registerPhotos(itemId, registrations)
      }.onSuccess {
        setStatus(itemId, SaveStatus.SAVED)
      }.onFailure {
        withContext(Dispatchers.IO) { copiedFiles.forEach(File::delete) }
        setStatus(itemId, SaveStatus.FAILED)
        showError("照片导入失败，请重试", it)
      }
      onFinished()
    }
  }

  fun deletePhoto(photoId: Long, onFinished: (Boolean) -> Unit) {
    if (rejectDeletionDuringExport(onFinished)) return
    viewModelScope.launch {
      val deleted = runCatching { repository.deletePhoto(photoId) }
        .onFailure { showError("照片删除失败，请重试", it) }
        .isSuccess
      onFinished(deleted)
    }
  }

  fun renameDevice(deviceId: Long, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { repository.renameDevice(deviceId, name) }
        .onFailure { showError("修改设备名称失败", it) }
    }
  }

  fun setDeviceRequiresEvidence(deviceId: Long, requiresEvidence: Boolean) {
    viewModelScope.launch {
      runCatching { repository.setDeviceRequiresEvidence(deviceId, requiresEvidence) }
        .onFailure { showError("设备取证设置保存失败", it) }
    }
  }

  fun deleteDevice(deviceId: Long, onFinished: (Boolean) -> Unit) {
    if (rejectDeletionDuringExport(onFinished)) return
    viewModelScope.launch {
      runCatching { repository.deleteDevice(deviceId) }
        .onSuccess { result ->
          _errorMessage.value = deletionMessage("设备", result.photoCount, result.failedFileCount)
          onFinished(true)
        }
        .onFailure {
          showError("删除设备失败", it)
          onFinished(false)
        }
    }
  }

  fun exportKeyScreenshotsWord(
    projectId: Long,
    context: Context,
    directory: Uri,
    useSubdirectory: Boolean = true,
  ) {
    var name = "项目"
    val date = ExportFileIo.dateSuffix()
    exportToDirectory(
      context, directory, if (useSubdirectory) "Word关键截图" else null,
      { "${ExportFileIo.safeName(name)}_关键设备截图_$date.docx" },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ExportKind.WORD, projectId,
      produce = { file, progress ->
        val data = repository.getProjectExportData(projectId)
        name = data.project.name
        WordExporter.export(context, data, file, progress)
      },
      successMessage = { summary ->
        if (summary.photoCount == 0) {
          "Word 已导出，当前项目没有可插入的关键截图"
        } else {
          "Word 已导出：${summary.deviceCount} 台设备，${summary.photoCount} 张图片"
        }
      },
    )
  }

  fun exportProjectPackage(
    projectId: Long,
    context: Context,
    directory: Uri,
    useSubdirectory: Boolean = true,
  ) {
    var name = "项目"
    val date = ExportFileIo.dateSuffix()
    exportToDirectory(
      context, directory, if (useSubdirectory) "PC浏览包" else null,
      { "${ExportFileIo.safeName(name)}_浏览包_$date.zip" }, "application/zip", ExportKind.PROJECT_PACKAGE, projectId,
      produce = { file, progress ->
        val data = repository.getProjectExportData(projectId)
        name = data.project.name
        ProjectPackageExporter.export(context, data, file, progress)
      },
      successMessage = { summary -> "项目浏览包已导出：${summary.deviceCount} 台设备，${summary.photoCount} 张照片" },
    )
  }

  fun reportExportLocationError() {
    _errorMessage.value = "无法取得该目录的读写权限，请重新选择保存位置"
  }

  private fun rejectDeletionDuringExport(onFinished: (Boolean) -> Unit): Boolean {
    if (_exportProgress.value == null && _backupPreflight.value == null) return false
    _errorMessage.value = "正在检查备份或导出，为保护照片完整性，请完成或取消后再删除"
    onFinished(false)
    return true
  }

  private fun <T> exportToDirectory(
    context: Context,
    directory: Uri,
    childDirectory: String?,
    fileName: () -> String,
    mimeType: String,
    kind: ExportKind,
    projectId: Long? = null,
    beforeSave: (File) -> Unit = {},
    beforeWrite: (Int) -> Unit = {},
    produce: suspend (File, (Int, String) -> Unit) -> T,
    successMessage: (T) -> String,
  ) {
    if (_exportProgress.value != null || _backupPreflight.value != null || _wordTemplateBusy.value) {
      _errorMessage.value = "已有导出或备份确认任务，请完成或取消后重试"
      return
    }
    _exportProgress.value = ExportProgress(kind, projectId, 0, "正在准备导出")
    viewModelScope.launch {
      var destination: Uri? = null
      val temporaryFile = File(context.cacheDir, "export_${UUID.randomUUID()}.tmp")
      try {
        // Save pending edits before taking the export snapshot; never silently omit unsaved notes.
        pendingNotes.toMap().forEach { (itemId, note) ->
          noteJobs.remove(itemId)?.cancel()
          try {
            repository.updateNote(itemId, note)
          } catch (throwable: Throwable) {
            setStatus(itemId, SaveStatus.FAILED)
            throw throwable
          }
          if (pendingNotes[itemId] == note) {
            pendingNotes.remove(itemId)
            setStatus(itemId, SaveStatus.SAVED)
          }
        }
        val result = withContext(Dispatchers.IO) {
          val job = currentCoroutineContext()
          fun progress(percent: Int, message: String) {
            job.ensureActive()
            val previous = _exportProgress.value?.percent ?: 0
            _exportProgress.value = ExportProgress(kind, projectId, maxOf(previous, percent.coerceIn(0, 100)), message)
          }
          val summary = produce(temporaryFile) { value, message -> progress(value.coerceIn(0, 100) * 75 / 100, message) }
          beforeSave(temporaryFile)
          val targetDirectory = if (childDirectory == null) directory else {
            requireNotNull(createChildTree(context, directory, childDirectory)) { "无法创建导出子目录，请检查权限" }
          }
          destination = requireNotNull(createFileTree(context, targetDirectory, fileName(), mimeType)) { "无法创建新导出文件，请检查权限" }
          val target = requireNotNull(destination)
          ExportFileIo.copyAndVerify(
            temporaryFile,
            openOutput = { requireNotNull(context.contentResolver.openOutputStream(target, "wt")) { "无法写入保存位置" } },
            openInput = { requireNotNull(context.contentResolver.openInputStream(target)) { "无法回读保存文件" } },
            beforeWrite = beforeWrite,
            onProgress = { value, message -> progress(75 + value * 25 / 100, message) },
          )
          summary
        }
        val message = successMessage(result)
        if (kind == ExportKind.BACKUP) _backupSuccessMessage.value = message
        else _errorMessage.value = message
      } catch (throwable: Throwable) {
        val cleaned = withContext(NonCancellable + Dispatchers.IO) {
          destination?.let { uri ->
            runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }.getOrDefault(false)
          } ?: true
        }
        val suffix = if (cleaned) "" else "；未能清理本次不完整文件，请手动删除，不要作为有效备份使用"
        showError("导出失败", IllegalStateException("${throwable.message ?: "未知错误"}$suffix", throwable))
        if (throwable is CancellationException) throw throwable
      } finally {
        withContext(NonCancellable + Dispatchers.IO) { temporaryFile.delete() }
        _exportProgress.value = null
      }
    }
  }

  fun clearError() {
    _errorMessage.value = null
  }

  fun clearBackupSuccessMessage() {
    _backupSuccessMessage.value = null
  }

  private suspend fun persistNote(itemId: Long, note: String) {
    runCatching { repository.updateNote(itemId, note) }
      .onSuccess {
        if (pendingNotes[itemId] == note) {
          pendingNotes.remove(itemId)
          noteJobs.remove(itemId)
          setStatus(itemId, SaveStatus.SAVED)
        }
      }.onFailure {
        setStatus(itemId, SaveStatus.FAILED)
        showError("备注保存失败，请重试", it)
      }
  }

  private fun setStatus(itemId: Long, status: SaveStatus) {
    _saveStatuses.value = _saveStatuses.value + (itemId to status)
  }

  private fun showError(prefix: String, throwable: Throwable) {
    _errorMessage.value = "$prefix：${throwable.message ?: "未知错误"}"
  }

  private fun readTemplatePackage(context: Context, source: Uri) =
    requireNotNull(context.contentResolver.openInputStream(source)) { "无法读取所选文件" }
      .bufferedReader(Charsets.UTF_8)
      .use { reader ->
        val text = reader.readText()
        require(text.length <= MAX_TEMPLATE_FILE_CHARS) { "模板文件过大" }
        TemplateTransfer.decode(text)
      }

  private fun deletionMessage(target: String, photoCount: Int, failedFileCount: Int): String =
    if (failedFileCount == 0) {
      "${target}已删除，同时清理 $photoCount 张照片"
    } else {
      "${target}已删除，但有 $failedFileCount 个照片文件未能清理"
    }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }

  private companion object {
    const val MAX_TEMPLATE_FILE_CHARS = 5_000_000
    const val NOTE_SAVE_DELAY_MS = 350L
  }

  private fun copyImportedPhoto(context: Context, itemId: Long, uri: Uri): File {
    val resolver = context.contentResolver
    val extension = resolver.getType(uri)
      ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
      ?.takeIf(String::isNotBlank)
      ?: "jpg"
    val directory = File(context.filesDir, "originals/item_$itemId").apply { mkdirs() }
    val destination = File(directory, "IMG_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
    try {
      requireNotNull(resolver.openInputStream(uri)) { "无法读取相册照片" }.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
      }
      require(destination.isFile && destination.length() > 0L) { "相册照片为空" }
      return destination
    } catch (throwable: Throwable) {
      destination.delete()
      throw throwable
    }
  }
}
