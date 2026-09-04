package com.example.dengbaoevidence.ui.main

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.dengbaoevidence.data.TemplateConflictStrategy
import com.example.dengbaoevidence.data.TemplateImportPreview
import com.example.dengbaoevidence.theme.AppAppearance
import com.example.dengbaoevidence.theme.LocalEvidenceColors
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
  var defaultExportUri by remember { mutableStateOf(AppSettings.defaultWordExportTreeUri(context)) }
  var defaultPackageUri by remember { mutableStateOf(AppSettings.defaultProjectPackageExportTreeUri(context)) }
  var exportingTemplates by remember { mutableStateOf(false) }
  var importingTemplates by remember { mutableStateOf(false) }
  var pendingTemplateImport by remember { mutableStateOf<PendingTemplateImport?>(null) }
  var conflictStrategy by remember { mutableStateOf(TemplateConflictStrategy.RENAME) }
  val originalPhotoDirectory = remember(context) { File(context.filesDir, "originals").absolutePath }
  val directoryPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
    AppSettings.setDefaultWordExportTreeUri(context, uri)
    defaultExportUri = uri
  }
  val packageDirectoryPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
    AppSettings.setDefaultProjectPackageExportTreeUri(context, uri)
    defaultPackageUri = uri
  }
  val templateExporter = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    exportingTemplates = true
    viewModel.exportDeviceTypeTemplates(context, uri) { exportingTemplates = false }
  }
  val templateImporter = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    importingTemplates = true
    viewModel.previewTemplateImport(context, uri) { preview ->
      importingTemplates = false
      if (preview != null) {
        conflictStrategy = TemplateConflictStrategy.RENAME
        pendingTemplateImport = PendingTemplateImport(uri, preview)
      }
    }
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
            val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            templateExporter.launch("等保取证_设备类型模板_$date.json")
          },
          enabled = !exportingTemplates && !importingTemplates,
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
                if (exportingTemplates) "正在导出…" else "将全部设备类型和取证项保存为 JSON 文件",
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
          enabled = !exportingTemplates && !importingTemplates,
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
          onClick = { directoryPicker.launch(defaultExportUri) },
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
              Text("默认 Word 导出目录", fontWeight = FontWeight.Medium)
              Text(
                text = defaultExportUri?.let(::displayDirectory) ?: "未设置，导出时选择保存位置",
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
        Card(
          onClick = { packageDirectoryPicker.launch(defaultPackageUri) },
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
              Text("默认项目浏览包导出目录", fontWeight = FontWeight.Medium)
              Text(
                text = defaultPackageUri?.let(::displayDirectory) ?: "未设置，导出时选择保存位置",
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
      if (defaultExportUri != null) {
        item {
          TextButton(onClick = {
            defaultExportUri?.let { uri ->
              if (uri != defaultPackageUri) {
                runCatching {
                  context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                  )
                }
              }
            }
            AppSettings.clearDefaultWordExportTreeUri(context)
            defaultExportUri = null
          }) {
            Text("清除 Word 默认目录")
          }
        }
      }
      if (defaultPackageUri != null) {
        item {
          TextButton(onClick = {
            defaultPackageUri?.let { uri ->
              if (uri != defaultExportUri) {
                runCatching {
                  context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                  )
                }
              }
            }
            AppSettings.clearDefaultProjectPackageExportTreeUri(context)
            defaultPackageUri = null
          }) {
            Text("清除项目浏览包默认目录")
          }
        }
      }
      item {
        Text(
          "原始取证照片保存在 App 私有目录以保证数据稳定；默认目录设置只影响 Word 和项目浏览包的保存位置。",
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

  pendingTemplateImport?.let { pending ->
    AlertDialog(
      onDismissRequest = { pendingTemplateImport = null },
      title = { Text("导入设备类型模板") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("文件包含 ${pending.preview.templateCount} 个设备类型。导入只修改模板库，不影响项目中已经创建的设备。")
          if (pending.preview.duplicateNames.isNotEmpty()) {
            Text(
              "发现 ${pending.preview.duplicateNames.size} 个同名模板：${pending.preview.duplicateNames.joinToString("、")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("同名模板处理方式", fontWeight = FontWeight.SemiBold)
            listOf(
              TemplateConflictStrategy.RENAME to "保留两份并自动改名",
              TemplateConflictStrategy.OVERWRITE to "覆盖同名模板",
              TemplateConflictStrategy.SKIP to "跳过同名模板",
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
      },
      confirmButton = {
        TextButton(onClick = {
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
