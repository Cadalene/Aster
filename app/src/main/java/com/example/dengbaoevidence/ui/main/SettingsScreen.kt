package com.example.dengbaoevidence.ui.main

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dengbaoevidence.data.AppSettings
import com.example.dengbaoevidence.data.WordTemplateStore
import com.example.dengbaoevidence.data.BackupSpaceMonitor.Companion.formatBytes
import com.example.dengbaoevidence.data.persistExportDirectory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dengbaoevidence.data.TemplateConflictStrategy
import com.example.dengbaoevidence.data.TemplateImportPreview
import com.example.dengbaoevidence.theme.AppAppearance
import com.example.dengbaoevidence.theme.LocalEvidenceColors
import java.io.File

private val SettingsPageBackground: Color
  @Composable get() = MaterialTheme.colorScheme.background
private val SettingsCardShape: Shape
  @Composable get() = MaterialTheme.shapes.large

private data class PendingTemplateImport(val uri: Uri, val preview: TemplateImportPreview)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: EvidenceViewModel,
  appearance: AppAppearance,
  onAppearanceChange: (AppAppearance) -> Unit,
  onBack: () -> Unit,
  onOpenEvidence: () -> Unit,
  onOpenTemplates: () -> Unit,
) {
  val context = LocalContext.current
  val appVersion = remember(context) {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
  }
  var exportRootUri by remember { mutableStateOf(AppSettings.exportRootTreeUri(context)) }
  val exportState by viewModel.exportProgress.collectAsStateWithLifecycle()
  val backupPreflight by viewModel.backupPreflight.collectAsStateWithLifecycle()
  val backupSuccessMessage by viewModel.backupSuccessMessage.collectAsStateWithLifecycle()
  val wordTemplateBusy by viewModel.wordTemplateBusy.collectAsStateWithLifecycle()
  val wordTemplateRevision by viewModel.wordTemplateRevision.collectAsStateWithLifecycle()
  val wordTemplateName = remember(wordTemplateRevision) { WordTemplateStore.name(context) }
  var showRemoveWordTemplate by rememberSaveable { mutableStateOf(false) }
  val exportingTemplates = exportState?.kind == ExportKind.TEMPLATES
  val backingUp = exportState?.kind == ExportKind.BACKUP
  val exportBusy = exportState != null || backupPreflight != null || wordTemplateBusy
  val backupProgress = exportState?.percent ?: 0
  val backupMessage = exportState?.message.orEmpty()
  var importingTemplates by remember { mutableStateOf(false) }
  var pendingExport by rememberSaveable { mutableStateOf<SettingsExportTarget?>(null) }
  var pickerExport by rememberSaveable { mutableStateOf<SettingsExportTarget?>(null) }
  var pickerSetsDefault by rememberSaveable { mutableStateOf(true) }
  var pendingTemplateImport by remember { mutableStateOf<PendingTemplateImport?>(null) }
  var conflictStrategy by remember { mutableStateOf(TemplateConflictStrategy.SKIP) }
  val originalPhotoDirectory = remember(context) { File(context.filesDir, "originals").absolutePath }
  fun startExport(target: SettingsExportTarget, directory: Uri, useSubdirectory: Boolean) {
    when (target) {
      SettingsExportTarget.TEMPLATES -> viewModel.exportDeviceTypeTemplates(context, directory, useSubdirectory)
      SettingsExportTarget.BACKUP -> viewModel.prepareBackup(context, directory, useSubdirectory)
    }
  }
  val directoryPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    val target = pickerExport
    pickerExport = null
    if (uri != null) {
      val ready = runCatching {
        if (pickerSetsDefault) {
          persistExportDirectory(context, uri)
          exportRootUri = uri
        }
      }.isSuccess
      if (ready) {
        target?.let { startExport(it, uri, pickerSetsDefault) }
      } else {
        viewModel.reportExportLocationError()
      }
    }
  }
  val templateImporter = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    importingTemplates = true
    viewModel.previewTemplateImport(context, uri) { preview ->
      importingTemplates = false
      if (preview != null) {
        conflictStrategy = TemplateConflictStrategy.SKIP
        pendingTemplateImport = PendingTemplateImport(uri, preview)
      }
    }
  }

  val wordTemplatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { viewModel.importWordTemplate(context, it) }
  }

  Scaffold(
    containerColor = SettingsPageBackground,
    topBar = {
      TopAppBar(
        title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    bottomBar = {
      RootBottomBar(
        selected = null,
        onOpenEvidence = onOpenEvidence,
        onOpenTemplates = onOpenTemplates,
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text("设备类型模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }
      item {
        Card(
          onClick = {
            pendingExport = SettingsExportTarget.TEMPLATES
          },
          enabled = !exportBusy && !importingTemplates,
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text("导出设备类型模板", fontWeight = FontWeight.Medium)
              Text(
                if (exportingTemplates) "${exportState?.percent}% · ${exportState?.message}" else "将全部设备类型和取证项保存为 JSON 文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
          }
        }
      }
      item {
        Card(
          onClick = { templateImporter.launch(arrayOf("application/json", "text/plain")) },
          enabled = !exportBusy && !importingTemplates,
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text("导入设备类型模板", fontWeight = FontWeight.Medium)
              Text(
                if (importingTemplates) "正在校验模板文件…" else "导入前完整校验，不影响已创建设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
          }
        }
      }
      item {
        Text("主题色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column {
            AppearanceChoiceRow(
              appearance = AppAppearance.CLASSIC,
              selected = appearance == AppAppearance.CLASSIC,
              onClick = { onAppearanceChange(AppAppearance.CLASSIC) },
            )
            HorizontalDivider(color = LocalEvidenceColors.current.divider)
            AppearanceChoiceRow(
              appearance = AppAppearance.PAPER_BLUE,
              selected = appearance == AppAppearance.PAPER_BLUE,
              onClick = { onAppearanceChange(AppAppearance.PAPER_BLUE) },
            )
          }
        }
      }
      item {
        Text("文件存储", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Word 导出模板", fontWeight = FontWeight.Medium)
            Text(
              if (wordTemplateBusy) "正在校验并保存模板…" else wordTemplateName ?: "未设置，按默认格式生成",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
              TextButton(enabled = !exportBusy, onClick = {
                wordTemplatePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
              }) { Text(if (wordTemplateName == null) "导入模板" else "替换模板") }
              if (wordTemplateName != null) {
                TextButton(enabled = !exportBusy, onClick = { showRemoveWordTemplate = true }) { Text("移除模板") }
              }
            }
          }
        }
      }
      item {
        Card(
          onClick = {
            pendingExport = SettingsExportTarget.BACKUP
          },
          enabled = !exportBusy && !importingTemplates,
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(Modifier.weight(1f)) {
                Text("备份当前数据", fontWeight = FontWeight.Medium)
                Text("逐个项目导出浏览包、Word 和模板；原图保留在浏览包内", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (backingUp) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (backingUp) {
              LinearProgressIndicator(progress = { backupProgress / 100f }, modifier = Modifier.fillMaxWidth())
              Text("$backupProgress% · $backupMessage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
      item {
        Card(
          onClick = {
            pickerExport = null
            pickerSetsDefault = true
            directoryPicker.launch(exportRootUri)
          },
          enabled = !exportBusy,
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text("默认导出位置", fontWeight = FontWeight.Medium)
              Text(
                text = exportRootUri?.let(::displayDirectory) ?: "未设置，首次导出时选择保存位置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "选择目录")
          }
        }
      }
      item {
        Text(
          "自动创建子目录：Word关键截图、PC浏览包、设备类型模板、数据备份",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = SettingsCardShape,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text("App 原始图片数据目录", fontWeight = FontWeight.Medium)
              Text(
                text = originalPhotoDirectory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "这是 App 私有目录，普通文件管理器通常无法直接进入；请通过项目浏览包导出原图。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
              )
            }
          }
        }
      }
      item {
        Text(
          "原始取证照片保存在 App 私有目录；默认导出位置只影响后续导出文件的保存位置。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        Text(
          "版本 $appVersion",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  backupPreflight?.let { pending ->
    val space = pending.space
    AlertDialog(
      onDismissRequest = viewModel::cancelBackupPreparation,
      title = { Text(if (space == null) "正在检查备份空间" else "备份空间确认") },
      text = {
        Column(
          modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (space == null) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("正在统计项目和照片，检查手机可用空间…")
          } else {
            Text("项目数量：${space.estimate.projectCount}")
            Text("预计备份文件：约 ${formatBytes(space.estimate.archiveBytes)}")
            Text("建议手机空闲空间：至少 ${formatBytes(space.requiredInternalBytes)}")
            Text("当前手机可用空间：${formatBytes(space.availableInternalBytes)}")
            val destinationAvailable = space.availableDestinationBytes
            if (destinationAvailable == null) {
              Text(
                "无法读取保存位置容量；手机空间按本机保存保守估算。请确认保存位置有足够空间。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else if (!space.copyUsesInternalStorage) {
              Text("保存位置可用空间：${formatBytes(destinationAvailable)}")
            }
            if (space.internalShortageBytes > 0) {
              Text("手机空间不足，请至少再释放 ${formatBytes(space.internalShortageBytes)}。", color = LocalEvidenceColors.current.danger)
            }
            if (!space.copyUsesInternalStorage && space.destinationShortageBytes > 0) {
              Text("保存位置空间不足，请至少再释放 ${formatBytes(space.destinationShortageBytes)}。", color = LocalEvidenceColors.current.danger)
            }
            Text(
              "预计文件按原图总量的 1.2 倍估算；HEIC 浏览副本和自定义 Word 模板可能使实际大小更大。建议手机空闲空间按预计文件的 2.1 倍预留，备份期间持续检查空间，请保持 App 运行。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      confirmButton = {
        TextButton(enabled = space?.canStart == true, onClick = { viewModel.confirmBackup(context) }) { Text("开始备份") }
      },
      dismissButton = {
        Row {
          TextButton(onClick = viewModel::cancelBackupPreparation) { Text("取消") }
          if (space != null) {
            TextButton(onClick = { viewModel.prepareBackup(context, pending.directory, pending.useSubdirectory) }) { Text("重新检查") }
          }
        }
      },
      containerColor = SettingsPageBackground,
      shape = SettingsCardShape,
    )
  }

  if (showRemoveWordTemplate) {
    AlertDialog(
      onDismissRequest = { showRemoveWordTemplate = false },
      title = { Text("移除 Word 模板？") },
      text = { Text("后续导出将恢复默认格式，已导出的文档不受影响。") },
      confirmButton = {
        TextButton(enabled = !exportBusy, onClick = {
          showRemoveWordTemplate = false
          viewModel.removeWordTemplate(context)
        }) { Text("移除") }
      },
      dismissButton = { TextButton(onClick = { showRemoveWordTemplate = false }) { Text("取消") } },
      containerColor = SettingsPageBackground,
      shape = SettingsCardShape,
    )
  }

  backupSuccessMessage?.let { message ->
    AlertDialog(
      onDismissRequest = viewModel::clearBackupSuccessMessage,
      title = { Text("备份成功") },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = viewModel::clearBackupSuccessMessage) { Text("确认") }
      },
      containerColor = SettingsPageBackground,
      shape = SettingsCardShape,
    )
  }

  pendingTemplateImport?.let { pending ->
    AlertDialog(
      onDismissRequest = { pendingTemplateImport = null },
      title = { Text("导入设备类型模板") },
      text = {
        Column(
          modifier = Modifier.heightIn(max = 520.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("文件包含 ${pending.preview.templateCount} 个设备类型。导入只修改模板库，不影响项目中已经创建的设备。")
          if (pending.preview.duplicateNames.isNotEmpty()) {
            Column(
              modifier = Modifier.weight(1f, fill = false).heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (pending.preview.identicalNames.isNotEmpty()) {
                Text(
                  "内容完全一致，将自动跳过：${pending.preview.identicalNames.joinToString("、")}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (pending.preview.differenceDetails.isNotEmpty()) {
                Text("以下同名模板存在差异（默认跳过）：", fontWeight = FontWeight.SemiBold)
                pending.preview.differenceDetails.forEach { (name, details) ->
                  Text(
                    "$name：${details.take(3).joinToString("；")}${if (details.size > 3) "；…" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            if (pending.preview.differentNames.isNotEmpty()) {
              Text("有差异的同名模板处理方式", fontWeight = FontWeight.SemiBold)
              listOf(
                TemplateConflictStrategy.SKIP to "跳过同名模板（推荐）",
                TemplateConflictStrategy.OVERWRITE to "覆盖同名模板",
                TemplateConflictStrategy.RENAME to "保留两份并自动改名",
              ).forEach { (strategy, label) ->
                Row(
                  modifier = Modifier.fillMaxWidth().clickable { conflictStrategy = strategy },
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  RadioButton(selected = conflictStrategy == strategy, onClick = { conflictStrategy = strategy })
                  Text(label)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(enabled = !exportBusy, onClick = {
          pendingTemplateImport = null
          importingTemplates = true
          viewModel.importDeviceTypeTemplates(context, pending.uri, conflictStrategy) { importingTemplates = false }
        }) { Text("确认导入") }
      },
      dismissButton = { TextButton(onClick = { pendingTemplateImport = null }) { Text("取消") } },
      containerColor = SettingsPageBackground,
      shape = SettingsCardShape,
    )
  }

  pendingExport?.let { target ->
    val label = if (target == SettingsExportTarget.TEMPLATES) "设备类型模板" else "当前数据备份"
    AlertDialog(
      onDismissRequest = { pendingExport = null },
      title = { Text("选择保存位置") },
      text = { Text(if (exportRootUri == null) "尚未设置默认导出位置，请先选择一个位置。" else "是否将${label}保存到默认导出位置？") },
      confirmButton = {
        TextButton(onClick = {
          pendingExport = null
          val root = exportRootUri
          if (root == null) {
            pickerExport = target
            pickerSetsDefault = true
            directoryPicker.launch(null)
          } else {
            startExport(target, root, true)
          }
        }) { Text("保存到默认位置") }
      },
      dismissButton = {
        Row {
          TextButton(onClick = { pendingExport = null }) { Text("取消") }
          TextButton(onClick = {
            pendingExport = null
            pickerExport = target
            pickerSetsDefault = false
            directoryPicker.launch(null)
          }) { Text("选择其他位置") }
        }
      },
      containerColor = SettingsPageBackground,
      shape = SettingsCardShape,
    )
  }
}

@Composable
private fun AppearanceChoiceRow(
  appearance: AppAppearance,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Column(modifier = Modifier.padding(start = 4.dp)) {
      Text(appearance.title, fontWeight = FontWeight.Medium)
    }
  }
}

private fun displayDirectory(uri: Uri): String =
  runCatching { DocumentsContract.getTreeDocumentId(uri).replace(':', '/') }.getOrNull()
    ?: uri.toString()

private enum class SettingsExportTarget { TEMPLATES, BACKUP }
