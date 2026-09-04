package com.example.dengbaoevidence.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dengbaoevidence.data.DeviceTypeWithItems
import com.example.dengbaoevidence.data.TemplateItemEntity
import com.example.dengbaoevidence.theme.LocalEvidenceColors
import kotlinx.coroutines.launch

private val TemplatePageBackground: Color
  @Composable get() = MaterialTheme.colorScheme.background
private val TemplateCardShape: Shape
  @Composable get() = MaterialTheme.shapes.large
private val TemplateNeutralSoft: Color
  @Composable get() = LocalEvidenceColors.current.neutralSoft
private val TemplateKeySoft: Color
  @Composable get() = LocalEvidenceColors.current.keyScreenshotSoft
private val TemplateKey: Color
  @Composable get() = LocalEvidenceColors.current.keyScreenshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagementScreen(
  viewModel: EvidenceViewModel,
  onOpenTemplate: (Long) -> Unit,
  onOpenEvidence: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val templates by viewModel.deviceTypes.collectAsStateWithLifecycle()
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
  var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }
  val displayedTemplates = remember { mutableStateListOf<DeviceTypeWithItems>() }
  val ordinaryTemplates = templates.filterNot { it.template.isSystemTemplate }
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val edgeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
  val edgeScrollStep = with(LocalDensity.current) { 24.dp.toPx() }
  var draggedTemplateId by remember { mutableStateOf<Long?>(null) }
  var draggedTemplateOffset by remember { mutableStateOf(0f) }
  var dragOrderChanged by remember { mutableStateOf(false) }

  fun resetDisplayedTemplates() {
    displayedTemplates.clear()
    displayedTemplates.addAll(templates)
  }

  LaunchedEffect(templates) {
    if (draggedTemplateId == null) resetDisplayedTemplates()
  }

  fun dragTemplate(templateId: Long, deltaY: Float) {
    if (draggedTemplateId != templateId) return
    draggedTemplateOffset += deltaY
    val layoutInfo = listState.layoutInfo
    val currentInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == templateId } ?: return
    val draggedCenter = currentInfo.offset + currentInfo.size / 2f + draggedTemplateOffset
    val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
      val targetId = candidate.key as? Long
      targetId != null && targetId != templateId &&
        displayedTemplates.firstOrNull { it.template.id == targetId }?.template?.isSystemTemplate != true &&
        draggedCenter >= candidate.offset && draggedCenter <= candidate.offset + candidate.size
    }
    if (targetInfo != null) {
      val targetId = targetInfo.key as Long
      val fromIndex = displayedTemplates.indexOfFirst { it.template.id == templateId }
      val toIndex = displayedTemplates.indexOfFirst { it.template.id == targetId }
      if (fromIndex >= 0 && toIndex >= 0) {
        displayedTemplates.add(toIndex, displayedTemplates.removeAt(fromIndex))
        draggedTemplateOffset += (currentInfo.offset - targetInfo.offset).toFloat()
        dragOrderChanged = true
      }
    }
    val scrollAmount = when {
      draggedCenter < layoutInfo.viewportStartOffset + edgeThreshold -> -edgeScrollStep
      draggedCenter > layoutInfo.viewportEndOffset - edgeThreshold -> edgeScrollStep
      else -> 0f
    }
    if (scrollAmount != 0f) coroutineScope.launch { listState.scrollBy(scrollAmount) }
  }

  fun finishTemplateDragging(save: Boolean) {
    val shouldSave = save && dragOrderChanged
    draggedTemplateId = null
    draggedTemplateOffset = 0f
    dragOrderChanged = false
    if (shouldSave) {
      viewModel.reorderDeviceTypes(displayedTemplates.map { it.template.id })
    } else if (!save) {
      resetDisplayedTemplates()
    }
  }

  Scaffold(
    containerColor = TemplatePageBackground,
    topBar = {
      TopAppBar(
        title = { Text("设备类型模板", style = MaterialTheme.typography.titleLarge) },
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
        Icon(Icons.Default.Add, contentDescription = "新增设备类型")
      }
    },
    bottomBar = {
      RootBottomBar(
        selected = RootTab.TEMPLATES,
        onOpenEvidence = onOpenEvidence,
        onOpenTemplates = {},
      )
    },
  ) { padding ->
    if (templates.isEmpty()) {
      TemplateEmptyState(
        title = "还没有设备类型模板",
        supporting = "点击右下角新增一个模板",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        itemsIndexed(displayedTemplates, key = { _, template -> template.template.id }) { index, template ->
          TemplateRow(
            template = template,
            position = index,
            isDragging = draggedTemplateId == template.template.id,
            dragOffset = if (draggedTemplateId == template.template.id) draggedTemplateOffset else 0f,
            onClick = { onOpenTemplate(template.template.id) },
            onRename = { if (!template.template.isSystemTemplate) renameTarget = template.template.id },
            onDelete = { if (!template.template.isSystemTemplate) deleteTarget = template.template.id },
            onDragStart = {
              draggedTemplateId = template.template.id
              draggedTemplateOffset = 0f
              dragOrderChanged = false
            },
            onDrag = { dragTemplate(template.template.id, it) },
            onDragEnd = { finishTemplateDragging(save = true) },
            onDragCancel = { finishTemplateDragging(save = false) },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    TemplateCreateDialog(
      title = "新增设备类型",
      templates = ordinaryTemplates,
      confirmText = "创建",
      onDismiss = { showCreateDialog = false },
      onConfirm = { name, copyFromTemplateId ->
        showCreateDialog = false
        viewModel.createDeviceType(name, copyFromTemplateId) { onOpenTemplate(it) }
      },
    )
  }

  val renameTemplate = templates.firstOrNull { it.template.id == renameTarget }
  if (renameTemplate != null) {
    TemplateNameDialog(
      title = "修改设备类型",
      initialName = renameTemplate.template.name,
      confirmText = "保存",
      onDismiss = { renameTarget = null },
      onConfirm = { name ->
        renameTarget = null
        viewModel.renameDeviceType(renameTemplate.template.id, name)
      },
    )
  }

  val deleteTemplate = templates.firstOrNull { it.template.id == deleteTarget }
  if (deleteTemplate != null) {
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("删除设备类型？") },
      text = { Text("将删除“${deleteTemplate.template.name}”模板。已创建的设备不会受到影响。") },
      confirmButton = {
        TextButton(
          onClick = {
            deleteTarget = null
            viewModel.deleteDeviceType(deleteTemplate.template.id) {}
          },
        ) { Text("删除") }
      },
      dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
      containerColor = TemplatePageBackground,
      shape = TemplateCardShape,
    )
  }
}

@Composable
private fun TemplateRow(
  template: DeviceTypeWithItems,
  position: Int,
  isDragging: Boolean,
  dragOffset: Float,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onDragStart: () -> Unit,
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
  onDragCancel: () -> Unit,
) {
  Card(
    modifier =
      Modifier
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = dragOffset },
    shape = TemplateCardShape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "${position + 1}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(28.dp),
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(template.template.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
          "${template.items.size} 个取证项",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (template.template.isSystemTemplate) {
        Icon(Icons.Default.Lock, contentDescription = "固定模板", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 14.dp))
      } else {
        IconButton(onClick = onRename) {
          Icon(Icons.Default.Edit, contentDescription = "修改设备类型")
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "删除设备类型")
        }
      }
      if (!template.template.isSystemTemplate) {
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .pointerInput(template.template.id) {
                detectDragGesturesAfterLongPress(
                  onDragStart = { onDragStart() },
                  onDragEnd = onDragEnd,
                  onDragCancel = onDragCancel,
                  onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                  },
                )
              },
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.DragHandle, contentDescription = "长按拖动设备类型排序")
        }
      } else {
        Spacer(Modifier.width(48.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCreateDialog(
  title: String,
  templates: List<DeviceTypeWithItems>,
  confirmText: String,
  onDismiss: () -> Unit,
  onConfirm: (String, Long?) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var copyFromTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("设备类型名称") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text("复制现有模板（可选）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
          modifier = Modifier.fillMaxWidth().clickable { copyFromTemplateId = null }.padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = copyFromTemplateId == null, onClick = { copyFromTemplateId = null })
          Text("空白模板（仅含“其他”）")
        }
        templates.forEach { template ->
          Row(
            modifier = Modifier.fillMaxWidth().clickable { copyFromTemplateId = template.template.id }.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = copyFromTemplateId == template.template.id,
              onClick = { copyFromTemplateId = template.template.id },
            )
            Text(template.template.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim(), copyFromTemplateId) }, enabled = name.isNotBlank()) { Text(confirmText) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    containerColor = TemplatePageBackground,
    shape = TemplateCardShape,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
  templateId: Long,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
) {
  val templates by viewModel.deviceTypes.collectAsStateWithLifecycle()
  val template = templates.firstOrNull { it.template.id == templateId }
  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var editingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
  var deleteItemId by rememberSaveable { mutableStateOf<Long?>(null) }
  val displayedItems = remember(templateId) { mutableStateListOf<TemplateItemEntity>() }
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val edgeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
  val edgeScrollStep = with(LocalDensity.current) { 24.dp.toPx() }
  var draggedItemId by remember { mutableStateOf<Long?>(null) }
  var draggedItemOffset by remember { mutableStateOf(0f) }
  var dragOrderChanged by remember { mutableStateOf(false) }

  fun orderedTemplateItems(): List<TemplateItemEntity> =
    template?.items.orEmpty().sortedWith(
      compareBy<TemplateItemEntity> { it.name == "其他" }.thenBy { it.displayOrder },
    )

  fun resetDisplayedItems() {
    displayedItems.clear()
    displayedItems.addAll(orderedTemplateItems())
  }

  LaunchedEffect(template?.items) {
    if (draggedItemId == null) resetDisplayedItems()
  }

  fun dragItem(itemId: Long, deltaY: Float) {
    if (draggedItemId != itemId) return
    draggedItemOffset += deltaY
    val layoutInfo = listState.layoutInfo
    val currentInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemId } ?: return
    val draggedCenter = currentInfo.offset + currentInfo.size / 2f + draggedItemOffset
    val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
      val targetId = candidate.key as? Long
      targetId != null && targetId != itemId &&
        draggedCenter >= candidate.offset && draggedCenter <= candidate.offset + candidate.size
    }
    if (targetInfo != null) {
      val targetId = targetInfo.key as Long
      val fromIndex = displayedItems.indexOfFirst { it.id == itemId }
      val toIndex = displayedItems.indexOfFirst { it.id == targetId }
      if (fromIndex >= 0 && toIndex >= 0 && displayedItems[toIndex].name != "其他") {
        displayedItems.add(toIndex, displayedItems.removeAt(fromIndex))
        draggedItemOffset += (currentInfo.offset - targetInfo.offset).toFloat()
        dragOrderChanged = true
      }
    }
    val scrollAmount = when {
      draggedCenter < layoutInfo.viewportStartOffset + edgeThreshold -> -edgeScrollStep
      draggedCenter > layoutInfo.viewportEndOffset - edgeThreshold -> edgeScrollStep
      else -> 0f
    }
    if (scrollAmount != 0f) coroutineScope.launch { listState.scrollBy(scrollAmount) }
  }

  fun finishDragging(save: Boolean) {
    val shouldSave = save && dragOrderChanged
    draggedItemId = null
    draggedItemOffset = 0f
    dragOrderChanged = false
    if (shouldSave) {
      val normalized = displayedItems.mapIndexed { index, item -> item.copy(displayOrder = index) }
      displayedItems.clear()
      displayedItems.addAll(normalized)
      viewModel.reorderTemplateItems(templateId, normalized.map { it.id })
    } else if (!save) {
      resetDisplayedItems()
    }
  }

  Scaffold(
    containerColor = TemplatePageBackground,
    topBar = {
      TopAppBar(
        title = {
          Text(
            template?.template?.name ?: "模板",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = { TemplateBackButton(onBack) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { if (template != null) showAddDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "新增取证项")
      }
    },
  ) { padding ->
    if (template == null) {
      TemplateEmptyState(
        title = "模板不存在",
        supporting = "请返回模板列表重新选择",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item(key = "template_info") {
          Text(
            "取证项模板",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 2.dp),
          )
          Text(
            "新增设备时会复制当前模板；之后修改模板不会影响已有设备。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
          )
        }
        itemsIndexed(displayedItems, key = { _, item -> item.id }) { index, item ->
          TemplateItemRow(
            item = item,
            position = index,
            isDragging = draggedItemId == item.id,
            dragOffset = if (draggedItemId == item.id) draggedItemOffset else 0f,
            onEdit = { if (item.name != "其他") editingItemId = item.id },
            onDelete = { if (item.name != "其他") deleteItemId = item.id },
            onDragStart = {
              draggedItemId = item.id
              draggedItemOffset = 0f
              dragOrderChanged = false
            },
            onDrag = { dragItem(item.id, it) },
            onDragEnd = { finishDragging(save = true) },
            onDragCancel = { finishDragging(save = false) },
          )
        }
      }
    }
  }

  if (showAddDialog && template != null) {
    TemplateNameDialog(
      title = "新增取证项",
      label = "取证项名称",
      confirmText = "添加",
      onDismiss = { showAddDialog = false },
      onConfirm = { name ->
        showAddDialog = false
        viewModel.addTemplateItem(template.template.id, name) {}
      },
    )
  }

  val editingItem = template?.items?.firstOrNull { it.id == editingItemId }
  if (editingItem != null) {
    TemplateItemDialog(
      item = editingItem,
      onDismiss = { editingItemId = null },
      onConfirm = { updated ->
        editingItemId = null
        viewModel.updateTemplateItem(updated)
      },
    )
  }

  val deleteItem = template?.items?.firstOrNull { it.id == deleteItemId }
  if (deleteItem != null) {
    AlertDialog(
      onDismissRequest = { deleteItemId = null },
      title = { Text("删除取证项？") },
      text = { Text("删除后，新创建的设备不会再包含“${deleteItem.name}”。已有设备中的取证项不受影响。") },
      confirmButton = {
        TextButton(
          onClick = {
            deleteItemId = null
            viewModel.deleteTemplateItem(deleteItem.id) {}
          },
        ) { Text("删除") }
      },
      dismissButton = { TextButton(onClick = { deleteItemId = null }) { Text("取消") } },
      containerColor = TemplatePageBackground,
      shape = TemplateCardShape,
    )
  }
}

@Composable
private fun TemplateItemRow(
  item: TemplateItemEntity,
  position: Int,
  isDragging: Boolean,
  dragOffset: Float,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDragStart: () -> Unit,
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
  onDragCancel: () -> Unit,
) {
  val isReserved = item.name == "其他"
  Card(
    modifier =
      Modifier
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = dragOffset },
    shape = TemplateCardShape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 11.dp, bottom = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "${position + 1}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(28.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(item.name, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (item.isKeyScreenshot) {
            Surface(color = TemplateKeySoft, shape = RoundedCornerShape(4.dp)) {
              Text("关键截图", color = TemplateKey, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
          }
          item.wordOrder?.let {
            Text("Word 第 ${it + 1} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (isReserved) {
            Text("不计入完成度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (isReserved) {
        Icon(Icons.Default.Lock, contentDescription = "保留项", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 14.dp))
      } else {
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = "编辑取证项")
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "删除取证项")
        }
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                  onDragStart = { onDragStart() },
                  onDragEnd = onDragEnd,
                  onDragCancel = onDragCancel,
                  onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                  },
                )
              },
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.DragHandle, contentDescription = "长按拖动排序")
        }
      }
    }
  }
}

@Composable
private fun TemplateItemDialog(
  item: TemplateItemEntity,
  onDismiss: () -> Unit,
  onConfirm: (TemplateItemEntity) -> Unit,
) {
  var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
  var isKeyScreenshot by rememberSaveable(item.id) { mutableStateOf(item.isKeyScreenshot) }
  var wordOrder by rememberSaveable(item.id) { mutableStateOf(item.wordOrder?.let { (it + 1).toString() } ?: "") }
  val wordOrderValue = wordOrder.toIntOrNull()
  val valid = name.isNotBlank() &&
    (wordOrder.isBlank() || (wordOrderValue != null && wordOrderValue > 0))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("编辑取证项") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("取证项名称") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isKeyScreenshot, onCheckedChange = { isKeyScreenshot = it })
          Text("属于关键设备截图项")
        }
        OutlinedTextField(
          value = wordOrder,
          onValueChange = { wordOrder = it.filter(Char::isDigit) },
          label = { Text("Word 排列顺序（从 1 开始，可留空）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(
            item.copy(
              name = name.trim(),
              isKeyScreenshot = isKeyScreenshot,
              wordOrder = if (isKeyScreenshot && wordOrderValue != null) wordOrderValue - 1 else null,
            ),
          )
        },
        enabled = valid,
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    containerColor = TemplatePageBackground,
    shape = TemplateCardShape,
  )
}

@Composable
private fun TemplateNameDialog(
  title: String,
  confirmText: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  initialName: String = "",
  label: String = "设备类型名称",
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
    containerColor = TemplatePageBackground,
    shape = TemplateCardShape,
  )
}

@Composable
private fun TemplateEmptyState(title: String, supporting: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(horizontal = 32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(color = TemplateNeutralSoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(70.dp)) {
      Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(42.dp))
      }
    }
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun TemplateBackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
  }
}
