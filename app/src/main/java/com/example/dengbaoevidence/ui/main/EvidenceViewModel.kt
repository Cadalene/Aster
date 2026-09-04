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

enum class SaveStatus {
  IDLE,
  SAVING,
  SAVED,
  FAILED,
}

class EvidenceViewModel(private val repository: DataRepository) : ViewModel() {
  val projects: StateFlow<List<ProjectSummary>> =
    repository.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val deviceTypes: StateFlow<List<DeviceTypeWithItems>> =
    repository.observeDeviceTypes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _saveStatuses = MutableStateFlow<Map<Long, SaveStatus>>(emptyMap())
  val saveStatuses: StateFlow<Map<Long, SaveStatus>> = _saveStatuses.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

  fun exportDeviceTypeTemplates(context: Context, destination: Uri, onFinished: (Boolean) -> Unit) {
    viewModelScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val json = TemplateTransfer.encode(repository.getDeviceTypesSnapshot())
          requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "无法写入所选文件" }
            .bufferedWriter(Charsets.UTF_8)
            .use { it.write(json) }
        }
      }.onSuccess {
        _errorMessage.value = "设备类型模板已导出"
        onFinished(true)
      }.onFailure {
        runCatching { context.contentResolver.delete(destination, null, null) }
        showError("设备类型模板导出失败", it)
        onFinished(false)
      }
    }
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

  fun deleteDevice(deviceId: Long, onFinished: (Boolean) -> Unit) {
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
    destination: Uri,
    onFinished: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          val temporaryDocument = File(context.cacheDir, "word_export_${UUID.randomUUID()}.docx")
          try {
            val summary = WordExporter.export(context, repository.getProjectExportData(projectId), temporaryDocument)
            requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "无法写入所选文件" }.use { output ->
              temporaryDocument.inputStream().buffered().use { input -> input.copyTo(output) }
            }
            summary
          } catch (throwable: Throwable) {
            runCatching { context.contentResolver.delete(destination, null, null) }
            throw throwable
          } finally {
            temporaryDocument.delete()
          }
        }
      }
      result
        .onSuccess { summary ->
          _errorMessage.value = if (summary.photoCount == 0) {
            "Word 已导出，当前项目没有可插入的关键截图"
          } else {
            "Word 已导出：${summary.deviceCount} 台设备，${summary.photoCount} 张图片"
          }
          onFinished(true)
        }
        .onFailure {
          showError("Word 导出失败", it)
          onFinished(false)
        }
    }
  }

  fun exportProjectPackage(
    projectId: Long,
    context: Context,
    destination: Uri,
    onFinished: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          val temporaryPackage = File(context.cacheDir, "project_export_${UUID.randomUUID()}.zip")
          try {
            val summary = ProjectPackageExporter.export(
              context = context,
              data = repository.getProjectExportData(projectId),
              destination = temporaryPackage,
            )
            requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "无法写入所选文件" }.use { output ->
              temporaryPackage.inputStream().buffered().use { input -> input.copyTo(output) }
            }
            summary
          } catch (throwable: Throwable) {
            runCatching { context.contentResolver.delete(destination, null, null) }
            throw throwable
          } finally {
            temporaryPackage.delete()
          }
        }
      }
      result
        .onSuccess { summary ->
          _errorMessage.value = "项目浏览包已导出：${summary.deviceCount} 台设备，${summary.photoCount} 张照片"
          onFinished(true)
        }
        .onFailure {
          showError("项目浏览包导出失败", it)
          onFinished(false)
        }
    }
  }

  fun clearError() {
    _errorMessage.value = null
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
