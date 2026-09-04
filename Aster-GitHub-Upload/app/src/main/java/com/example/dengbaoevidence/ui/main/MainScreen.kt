package com.example.dengbaoevidence.ui.main

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import com.example.dengbaoevidence.data.AppSettings
import com.example.dengbaoevidence.data.DeviceTypeWithItems
import com.example.dengbaoevidence.data.DeviceWithItems
import com.example.dengbaoevidence.data.EvidenceConclusion
import com.example.dengbaoevidence.data.EvidenceItemWithPhotos
import com.example.dengbaoevidence.data.ProjectSummary
import com.example.dengbaoevidence.data.conclusion
import com.example.dengbaoevidence.theme.LocalEvidenceColors
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

private val PageBackground: Color
  @Composable get() = MaterialTheme.colorScheme.background
private val Success: Color
  @Composable get() = LocalEvidenceColors.current.success
private val SuccessSoft: Color
  @Composable get() = LocalEvidenceColors.current.successSoft
private val Danger: Color
  @Composable get() = LocalEvidenceColors.current.danger
private val DangerSoft: Color
  @Composable get() = LocalEvidenceColors.current.dangerSoft
private val Warning: Color
  @Composable get() = LocalEvidenceColors.current.warning
private val WarningSoft: Color
  @Composable get() = LocalEvidenceColors.current.warningSoft
private val NeutralSoft: Color
  @Composable get() = LocalEvidenceColors.current.neutralSoft
private val CardShape: Shape
  @Composable get() = MaterialTheme.shapes.large

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
  viewModel: EvidenceViewModel,
  onOpenProject: (Long) -> Unit,
  onOpenTemplates: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val projects by viewModel.projects.collectAsStateWithLifecycle()
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    containerColor = PageBackground,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("我的项目", style = MaterialTheme.typography.titleLarge) },
        actions = {
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showCreateDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "新建项目")
      }
    },
    bottomBar = {
      RootBottomBar(
        selected = RootTab.EVIDENCE,
        onOpenEvidence = {},
        onOpenTemplates = onOpenTemplates,
      )
    },
  ) { padding ->
    if (projects.isEmpty()) {
      EmptyState(
        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(42.dp)) },
        title = "还没有项目",
        supporting = "点击右下角创建本次测评项目",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
            Text("现场取证", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("把每次测评的照片和记录整理在一起", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        items(projects, key = { it.project.id }) { project ->
          ProjectRow(project = project, onClick = { onOpenProject(project.project.id) })
        }
      }
    }
  }

  if (showCreateDialog) {
    NameDialog(
      title = "新建项目",
      label = "项目名称",
      confirmText = "创建",
      onDismiss = { showCreateDialog = false },
      onConfirm = { name ->
        showCreateDialog = false
        viewModel.createProject(name, onOpenProject)
      },
    )
  }
}

@Composable
private fun ProjectRow(project: ProjectSummary, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    shape = CardShape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(NeutralSoft),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.project.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(5.dp))
          Text("${formatTime(project.project.createdAt)} · ${project.deviceCount} 台设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
  projectId: Long,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
  onOpenDevice: (Long) -> Unit,
) {
  val project by viewModel.observeProject(projectId).collectAsStateWithLifecycle(initialValue = null)
  val devices by viewModel.observeDevices(projectId).collectAsStateWithLifecycle(initialValue = emptyList())
  val templates by viewModel.deviceTypes.collectAsStateWithLifecycle()
  val selectableTemplates = templates.filterNot { it.template.isSystemTemplate }
  val context = LocalContext.current
  val deviceListState = rememberLazyListState()
  val restoreScrollPosition = remember(projectId) { mutableStateOf(false) }
  var expandedTypes by remember(projectId) {
    mutableStateOf(AppSettings.expandedDeviceTypes(context, projectId))
  }
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var showRenameDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteExportReminder by rememberSaveable { mutableStateOf(false) }
  var showDeleteFinalDialog by rememberSaveable { mutableStateOf(false) }
  var deletingProject by rememberSaveable(projectId) { mutableStateOf(false) }
  var exportingWord by rememberSaveable(projectId) { mutableStateOf(false) }
  var exportingPackage by rememberSaveable(projectId) { mutableStateOf(false) }
  var pendingDefaultExport by remember { mutableStateOf<DefaultExportTarget?>(null) }

  LaunchedEffect(projectId, devices.size, expandedTypes) {
    if (!restoreScrollPosition.value && devices.isNotEmpty()) {
      val saved = AppSettings.projectListScrollPosition(context, projectId)
      deviceListState.scrollToItem(saved.index, saved.offset)
      restoreScrollPosition.value = true
    }
  }

  LaunchedEffect(projectId) {
    snapshotFlow { deviceListState.firstVisibleItemIndex to deviceListState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        if (restoreScrollPosition.value) {
          AppSettings.setProjectListScrollPosition(context, projectId, index, offset)
        }
      }
  }

  fun openDeviceFromProject(deviceId: Long) {
    AppSettings.setProjectListScrollPosition(
      context,
      projectId,
      deviceListState.firstVisibleItemIndex,
      deviceListState.firstVisibleItemScrollOffset,
    )
    onOpenDevice(deviceId)
  }
  val createWordDocument = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument(DOCX_MIME_TYPE),
  ) { destination ->
    destination?.let {
      exportingWord = true
      viewModel.exportKeyScreenshotsWord(projectId, context, it) { exportingWord = false }
    }
  }
  val createProjectPackage = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument(ZIP_MIME_TYPE),
  ) { destination ->
    destination?.let {
      exportingPackage = true
      viewModel.exportProjectPackage(projectId, context, it) { exportingPackage = false }
    }
  }
  fun exportProjectPackageToDefault() {
    project?.let {
      val fileName = projectPackageFileName(it.name)
      val destination = createFileInDefaultTree(
        context = context,
        treeUri = AppSettings.defaultProjectPackageExportTreeUri(context),
        mimeType = ZIP_MIME_TYPE,
        fileName = fileName,
      )
      if (destination == null) {
        createProjectPackage.launch(fileName)
      } else {
        exportingPackage = true
        viewModel.exportProjectPackage(projectId, context, destination) { exportingPackage = false }
      }
    }
  }
  fun requestProjectPackageExport() {
    project?.let {
      if (AppSettings.defaultProjectPackageExportTreeUri(context) == null) {
        createProjectPackage.launch(projectPackageFileName(it.name))
      } else {
        pendingDefaultExport = DefaultExportTarget.PROJECT_PACKAGE
      }
    }
  }
  fun exportWordDocumentToDefault() {
    project?.let {
      val fileName = wordDocumentFileName(it.name)
      val destination = createFileInDefaultTree(
        context = context,
        treeUri = AppSettings.defaultWordExportTreeUri(context),
        mimeType = DOCX_MIME_TYPE,
        fileName = fileName,
      )
      if (destination == null) {
        createWordDocument.launch(fileName)
      } else {
        exportingWord = true
        viewModel.exportKeyScreenshotsWord(projectId, context, destination) { exportingWord = false }
      }
    }
  }
  fun requestWordDocumentExport() {
    project?.let {
      if (AppSettings.defaultWordExportTreeUri(context) == null) {
        createWordDocument.launch(wordDocumentFileName(it.name))
      } else {
        pendingDefaultExport = DefaultExportTarget.WORD
      }
    }
  }
  val grouped = devices.groupBy { it.device.typeName }
  val exportBusy = exportingWord || exportingPackage

  Scaffold(
    containerColor = PageBackground,
    topBar = {
      TopAppBar(
        title = {
          Text(
            project?.name ?: "项目",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
          IconButton(onClick = { showRenameDialog = true }, enabled = project != null && !exportBusy) {
            Icon(Icons.Default.Edit, contentDescription = "修改项目名称")
          }
          IconButton(onClick = { showDeleteExportReminder = true }, enabled = project != null && !exportBusy) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "删除项目", tint = Danger)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showCreateDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "添加设备")
      }
    },
  ) { padding ->
    LazyColumn(
      state = deviceListState,
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
          ProjectExportActions(
            packageExporting = exportingPackage,
            wordExporting = exportingWord,
            enabled = project != null,
            onExportPackage = ::requestProjectPackageExport,
            onExportWord = ::requestWordDocumentExport,
          )
        }
        if (devices.isEmpty()) {
          item {
            EmptyState(
              icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(42.dp)) },
              title = "还没有设备",
              supporting = "添加设备后会自动复制对应的取证项模板",
              modifier = Modifier.fillMaxWidth().height(300.dp),
            )
          }
        }
        grouped.forEach { (typeName, typeDevices) ->
          val expanded = typeName in expandedTypes
          item(key = "header_$typeName") {
            DeviceGroupHeader(
              typeName = typeName,
              devices = typeDevices,
              expanded = expanded,
              onToggle = {
                expandedTypes = if (expanded) expandedTypes - typeName else expandedTypes + typeName
                AppSettings.setDeviceTypeExpanded(context, projectId, typeName, !expanded)
              },
            )
          }
          if (expanded) {
            items(typeDevices, key = { it.device.id }) { device ->
              DeviceRow(device = device, onClick = { openDeviceFromProject(device.device.id) })
            }
          }
        }
    }
  }

  if (showCreateDialog) {
    CreateDeviceDialog(
      templates = selectableTemplates,
      onDismiss = { showCreateDialog = false },
      onConfirm = { name, templateId ->
        showCreateDialog = false
        viewModel.createDevice(projectId, name, templateId, onOpenDevice)
      },
    )
  }

  if (showRenameDialog) {
    project?.let { current ->
      NameDialog(
        title = "修改项目名称",
        label = "项目名称",
        confirmText = "保存",
        initialName = current.name,
        onDismiss = { showRenameDialog = false },
        onConfirm = { name ->
          showRenameDialog = false
          viewModel.renameProject(projectId, name)
        },
      )
    }
  }

  if (showDeleteExportReminder) {
    val photoCount = devices.sumOf { device -> device.items.sumOf { item -> item.photos.size } }
    AlertDialog(
      onDismissRequest = { showDeleteExportReminder = false },
      title = { Text("删除项目前先确认") },
      text = {
        Text(
          "删除项目会同时删除原始取证照片。请先确认已经导出“项目浏览包 ZIP”完成留存；浏览包的 originals 目录包含全部原图。当前项目共有 $photoCount 张原始照片。",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          showDeleteExportReminder = false
          showDeleteFinalDialog = true
        }) { Text("已导出，继续") }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteExportReminder = false }) { Text("取消，先去导出") }
      },
      containerColor = PageBackground,
      shape = CardShape,
    )
  }

  if (showDeleteFinalDialog) {
    val itemCount = devices.sumOf { device -> device.items.size }
    val photoCount = devices.sumOf { device -> device.items.sumOf { item -> item.photos.size } }
    DeleteConfirmationDialog(
      title = "确认永久删除项目？",
      message = "此操作不可撤销，将永久删除项目、${devices.size} 台设备、$itemCount 个取证项、$photoCount 张原始照片，以及备注和符合性记录。请确认继续删除。",
      deleting = deletingProject,
      onDismiss = { showDeleteFinalDialog = false },
      onConfirm = {
        deletingProject = true
        viewModel.deleteProject(projectId) { deleted ->
          deletingProject = false
          if (deleted) {
            showDeleteFinalDialog = false
            AppSettings.clearProjectUiState(context, projectId)
            onBack()
          }
        }
      },
    )
  }

  pendingDefaultExport?.let { target ->
    val exportName = if (target == DefaultExportTarget.PROJECT_PACKAGE) "项目浏览包 ZIP" else "关键设备截图 Word"
    AlertDialog(
      onDismissRequest = { pendingDefaultExport = null },
      title = { Text("选择导出位置") },
      text = { Text("已为${exportName}设置默认目录，是否导出到该目录？") },
      confirmButton = {
        TextButton(onClick = {
          pendingDefaultExport = null
          when (target) {
            DefaultExportTarget.PROJECT_PACKAGE -> exportProjectPackageToDefault()
            DefaultExportTarget.WORD -> exportWordDocumentToDefault()
          }
        }) { Text("导出到默认目录") }
      },
      dismissButton = {
        Row {
          TextButton(onClick = { pendingDefaultExport = null }) { Text("取消") }
          TextButton(onClick = {
            pendingDefaultExport = null
            project?.let { current ->
              when (target) {
                DefaultExportTarget.PROJECT_PACKAGE -> createProjectPackage.launch(projectPackageFileName(current.name))
                DefaultExportTarget.WORD -> createWordDocument.launch(wordDocumentFileName(current.name))
              }
            }
          }) { Text("选择其他位置") }
        }
      },
      containerColor = PageBackground,
      shape = CardShape,
    )
  }
}

@Composable
private fun ProjectExportActions(
  packageExporting: Boolean,
  wordExporting: Boolean,
  enabled: Boolean,
  onExportPackage: () -> Unit,
  onExportWord: () -> Unit,
) {
  val busy = packageExporting || wordExporting
  Column(
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Text("导出项目文件", fontWeight = FontWeight.SemiBold)
    Button(
      onClick = onExportPackage,
      enabled = enabled && !busy,
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (packageExporting) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
      } else {
        Icon(Icons.Default.Folder, contentDescription = null)
      }
      Spacer(Modifier.width(8.dp))
      Text("导出项目浏览包 ZIP")
    }
    Text(
      "浏览包内的 originals 目录保存全部原图，thumbnails 目录仅用于网页预览。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = onExportWord,
      enabled = enabled && !busy,
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (wordExporting) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
      }
      Spacer(Modifier.width(8.dp))
      Text("导出关键设备截图 Word")
    }
  }
  Spacer(Modifier.height(10.dp))
}

@Composable
private fun DeviceGroupHeader(
  typeName: String,
  devices: List<DeviceWithItems>,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  val incomplete = devices.count { it.missingKeyScreenshotCount > 0 }
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (expanded) "折叠" else "展开",
      )
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(typeName, fontWeight = FontWeight.SemiBold)
        Text("${devices.size} 台设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (incomplete > 0) {
        StatusBadge(text = "$incomplete 台缺关键截图", foreground = Danger, background = DangerSoft)
      } else {
        StatusBadge(text = "关键截图完整", foreground = Success, background = SuccessSoft)
      }
    }
  }
  HorizontalDivider(color = LocalEvidenceColors.current.divider)
}

@Composable
private fun DeviceRow(device: DeviceWithItems, onClick: () -> Unit) {
  val progress = if (device.totalCount == 0) 0f else device.recordedCount.toFloat() / device.totalCount
  Row(
    modifier = Modifier.fillMaxWidth().background(PageBackground).clickable(onClick = onClick).padding(18.dp, 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(device.device.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (device.missingKeyScreenshotCount > 0) {
          Icon(Icons.Default.Error, contentDescription = "缺少关键截图", tint = Danger, modifier = Modifier.size(18.dp))
        }
      }
      Spacer(Modifier.height(7.dp))
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = if (progress >= 1f) Success else MaterialTheme.colorScheme.primary,
        trackColor = LocalEvidenceColors.current.progressTrack,
      )
      Spacer(Modifier.height(5.dp))
      Text(
        "已记录 ${device.recordedCount}/${device.totalCount}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.width(12.dp))
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
  deviceId: Long,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
  onOpenItem: (Long) -> Unit,
  onCapture: (Long) -> Unit,
) {
  val device by viewModel.observeDevice(deviceId).collectAsStateWithLifecycle(initialValue = null)
  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var showRenameDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var deletingDevice by rememberSaveable(deviceId) { mutableStateOf(false) }

  Scaffold(
    containerColor = PageBackground,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              device?.device?.name ?: "设备",
              style = MaterialTheme.typography.titleLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            device?.let {
              Text(it.device.typeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
          IconButton(onClick = { showRenameDialog = true }, enabled = device != null) {
            Icon(Icons.Default.Edit, contentDescription = "修改设备名称")
          }
          IconButton(onClick = { showDeleteDialog = true }, enabled = device != null) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "删除设备", tint = Danger)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showAddDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "临时添加取证项")
      }
    },
  ) { padding ->
    val current = device
    if (current == null) {
      Box(Modifier.fillMaxSize().padding(padding))
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 96.dp),
      ) {
        item { DeviceProgressHeader(current) }
        items(current.orderedItems, key = { it.item.id }) { evidence ->
          EvidenceRow(
            evidence = evidence,
            onClick = { onOpenItem(evidence.item.id) },
            onCapture = { onCapture(evidence.item.id) },
          )
        }
      }
    }
  }

  if (showAddDialog) {
    NameDialog(
      title = "临时添加取证项",
      label = "取证项名称",
      confirmText = "添加",
      onDismiss = { showAddDialog = false },
      onConfirm = { name ->
        showAddDialog = false
        viewModel.addTemporaryItem(deviceId, name, onOpenItem)
      },
    )
  }

  if (showRenameDialog) {
    device?.let { current ->
      NameDialog(
        title = "修改设备名称",
        label = "设备名称",
        confirmText = "保存",
        initialName = current.device.name,
        onDismiss = { showRenameDialog = false },
        onConfirm = { name ->
          showRenameDialog = false
          viewModel.renameDevice(deviceId, name)
        },
      )
    }
  }

  if (showDeleteDialog) {
    device?.let { current ->
      val photoCount = current.items.sumOf { item -> item.photos.size }
      DeleteConfirmationDialog(
        title = "删除这台设备？",
        message = "将删除 ${current.items.size} 个取证项和 $photoCount 张原始照片。此操作无法撤销。",
        deleting = deletingDevice,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
          deletingDevice = true
          viewModel.deleteDevice(deviceId) { deleted ->
            deletingDevice = false
            if (deleted) {
              showDeleteDialog = false
              onBack()
            }
          }
        },
      )
    }
  }
}

@Composable
private fun DeleteConfirmationDialog(
  title: String,
  message: String,
  deleting: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!deleting) onDismiss() },
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = !deleting) {
        if (deleting) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text("删除", color = Danger)
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !deleting) { Text("取消") }
    },
    containerColor = PageBackground,
    shape = CardShape,
  )
}

@Composable
private fun DeviceProgressHeader(device: DeviceWithItems) {
  val progress = if (device.totalCount == 0) 0f else device.recordedCount.toFloat() / device.totalCount
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("取证进度", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
      Text("${device.recordedCount}/${device.totalCount}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(9.dp))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
      color = if (progress >= 1f) Success else MaterialTheme.colorScheme.primary,
      trackColor = LocalEvidenceColors.current.progressTrackSecondary,
    )
    if (device.missingKeyScreenshotCount > 0) {
      Spacer(Modifier.height(9.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Error, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("还有 ${device.missingKeyScreenshotCount} 个关键截图项为空", style = MaterialTheme.typography.bodySmall, color = Danger)
      }
    }
  }
  Spacer(Modifier.height(10.dp))
}

@Composable
private fun EvidenceRow(evidence: EvidenceItemWithPhotos, onClick: () -> Unit, onCapture: () -> Unit) {
  val statusText: String
  val statusColor: Color
  val statusBackground: Color
  when {
    !evidence.isRecorded -> {
      statusText = "未记录"
      statusColor = Warning
      statusBackground = WarningSoft
    }
    evidence.item.isNotApplicable -> {
      statusText = "已记录·不适用"
      statusColor = Success
      statusBackground = SuccessSoft
    }
    evidence.item.isCompliant -> {
      statusText = "已记录·符合"
      statusColor = Success
      statusBackground = SuccessSoft
    }
    else -> {
      statusText = "已记录·不符合"
      statusColor = Danger
      statusBackground = DangerSoft
    }
  }
  Card(
    onClick = onClick,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
    shape = CardShape,
    colors = CardDefaults.cardColors(containerColor = statusBackground),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(statusColor),
      )
      Spacer(Modifier.width(11.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(evidence.item.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
          if (evidence.item.isKeyScreenshot) {
            StatusBadge("关键", Warning, WarningSoft)
          } else if (evidence.item.isTemporary) {
            StatusBadge("临时", MaterialTheme.colorScheme.onSurfaceVariant, NeutralSoft)
          }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          StatusBadge(statusText, Color.White, statusColor)
          if (evidence.photos.isNotEmpty()) {
            Text(" · ${evidence.photos.size} 张照片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (evidence.item.note.isNotBlank()) {
            Text(" · 有备注", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      IconButton(onClick = onCapture, modifier = Modifier.size(42.dp)) {
        Icon(Icons.Default.CameraAlt, contentDescription = "拍摄${evidence.item.name}")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceItemScreen(
  itemId: Long,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
  onCapture: () -> Unit,
  onOpenPhoto: (Int) -> Unit,
) {
  val evidence by viewModel.observeEvidenceItem(itemId).collectAsStateWithLifecycle(initialValue = null)
  val statuses by viewModel.saveStatuses.collectAsStateWithLifecycle()
  val current = evidence

  if (current == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("取证项", style = MaterialTheme.typography.titleLarge) },
          navigationIcon = { BackButton(onBack) },
        )
      },
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding))
    }
    return
  }

  var note by remember(current.item.id) { mutableStateOf(current.item.note) }
  val latestNote by rememberUpdatedState(note)
  val context = LocalContext.current
  var isImporting by remember(current.item.id) { mutableStateOf(false) }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
    if (uris.isNotEmpty() && !isImporting) {
      isImporting = true
      viewModel.importPhotos(itemId, context.applicationContext, uris) { isImporting = false }
    }
  }
  fun openGallery() {
    if (!isImporting) importLauncher.launch("image/*")
  }
  BackHandler { onBack() }

  Scaffold(
    containerColor = PageBackground,
    topBar = {
      TopAppBar(
        title = {
          Text(
            current.item.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
          SaveStatusLabel(status = statuses[itemId] ?: SaveStatus.IDLE, onRetry = { viewModel.updateNote(itemId, latestNote) })
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
    ) {
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text("核查结论", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(9.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          listOf(
            EvidenceConclusion.COMPLIANT to "符合",
            EvidenceConclusion.NON_COMPLIANT to "不符合",
            EvidenceConclusion.NOT_APPLICABLE to "不适用",
          ).forEachIndexed { index, (conclusion, label) ->
            SegmentedButton(
              selected = current.item.conclusion == conclusion,
              onClick = { viewModel.updateConclusion(itemId, conclusion) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
              label = { Text(label) },
              icon = {
                if (current.item.conclusion == conclusion) {
                  Icon(
                    when (conclusion) {
                      EvidenceConclusion.COMPLIANT -> Icons.Default.CheckCircle
                      EvidenceConclusion.NON_COMPLIANT -> Icons.Default.Error
                      EvidenceConclusion.NOT_APPLICABLE -> Icons.Default.RemoveCircleOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                }
              },
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("取证照片", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
          Text("${current.photos.size} 张", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        if (current.photos.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxWidth().height(112.dp).border(1.dp, LocalEvidenceColors.current.photoBorder, CardShape),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(onClick = onCapture, enabled = !isImporting) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("拍摄照片")
              }
              TextButton(onClick = ::openGallery, enabled = !isImporting) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("从相册导入")
              }
            }
          }
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(current.photos.sortedBy { it.sortOrder }, key = { _, photo -> photo.id }) { index, photo ->
              Box(
                modifier = Modifier
                  .size(width = 142.dp, height = 106.dp)
                  .clip(CardShape)
                  .background(NeutralSoft)
                  .clickable { onOpenPhoto(index) },
              ) {
                AsyncImage(
                  model = File(photo.path),
                  contentDescription = "第 ${index + 1} 张取证照片",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
                Surface(
                  color = Color.Black.copy(alpha = 0.66f),
                  shape = RoundedCornerShape(bottomEnd = 5.dp),
                ) {
                  Text("${index + 1}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
              }
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = ::openGallery, enabled = !isImporting) {
              Icon(Icons.Default.Image, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("从相册导入")
            }
            TextButton(onClick = onCapture, enabled = !isImporting) {
              Icon(Icons.Default.CameraAlt, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("继续拍摄")
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(19.dp))
          Spacer(Modifier.width(7.dp))
          Text("文字备注", fontWeight = FontWeight.SemiBold)
          Text("（选填）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = note,
          onValueChange = {
            note = it
            viewModel.updateNote(itemId, it)
          },
          modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) viewModel.flushNote(itemId) },
          minLines = 4,
          maxLines = 10,
          placeholder = { Text("记录无法拍照的核查过程或补充说明") },
          supportingText = { Text("自动保存") },
          shape = CardShape,
        )
      }
    }
  }
}

@Composable
private fun SaveStatusLabel(status: SaveStatus, onRetry: () -> Unit) {
  when (status) {
    SaveStatus.IDLE -> Unit
    SaveStatus.SAVING -> Text("保存中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp))
    SaveStatus.SAVED -> Text("已保存", style = MaterialTheme.typography.labelMedium, color = Success, modifier = Modifier.padding(end = 14.dp))
    SaveStatus.FAILED -> TextButton(onClick = onRetry) { Text("保存失败，重试", color = Danger) }
  }
}

@Composable
private fun StatusBadge(text: String, foreground: Color, background: Color) {
  Surface(color = background, shape = RoundedCornerShape(4.dp)) {
    Text(
      text = text,
      color = foreground,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun EmptyState(
  icon: @Composable () -> Unit,
  title: String,
  supporting: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(color = NeutralSoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(70.dp)) {
      Box(contentAlignment = Alignment.Center) { icon() }
    }
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
  }
}

@Composable
private fun NameDialog(
  title: String,
  label: String,
  confirmText: String,
  initialName: String = "",
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by rememberSaveable(title, initialName) { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmText) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    containerColor = PageBackground,
    shape = CardShape,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDeviceDialog(
  templates: List<DeviceTypeWithItems>,
  onDismiss: () -> Unit,
  onConfirm: (String, Long) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var selectedId by rememberSaveable(templates) { mutableStateOf(templates.firstOrNull()?.template?.id) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("添加设备") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("设备名称") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("设备类型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (templates.isEmpty()) {
          Text("正在加载设备类型…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          Column(
            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
          ) {
            templates.forEach { template ->
              Row(
                modifier = Modifier.fillMaxWidth().clickable { selectedId = template.template.id }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = selectedId == template.template.id,
                  onClick = { selectedId = template.template.id },
                )
                Spacer(Modifier.width(6.dp))
                Text(template.template.name)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { selectedId?.let { onConfirm(name.trim(), it) } },
        enabled = name.isNotBlank() && selectedId != null,
      ) { Text("添加") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    containerColor = PageBackground,
    shape = CardShape,
  )
}

private fun formatTime(timestamp: Long): String =
  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))

private fun wordDocumentFileName(projectName: String): String {
  val safeName = projectName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "项目" }
  return "${safeName}_关键设备截图.docx"
}

private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val ZIP_MIME_TYPE = "application/zip"

private fun createFileInDefaultTree(
  context: Context,
  treeUri: Uri?,
  mimeType: String,
  fileName: String,
): Uri? = treeUri?.let { uri ->
  runCatching {
    DocumentFile.fromTreeUri(context, uri)
      ?.takeIf { directory -> directory.isDirectory && directory.canWrite() }
      ?.createFile(mimeType, fileName)
      ?.uri
  }.getOrNull()
}

private fun projectPackageFileName(projectName: String): String {
  val safeName = projectName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "项目" }
  return "${safeName}_导出包.zip"
}

private enum class DefaultExportTarget { PROJECT_PACKAGE, WORD }
