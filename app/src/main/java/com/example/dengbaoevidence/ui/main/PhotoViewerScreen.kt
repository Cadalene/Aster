package com.example.dengbaoevidence.ui.main

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat

@Composable
fun PhotoViewerScreen(
  itemId: Long,
  initialIndex: Int,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val evidence by viewModel.observeEvidenceItem(itemId).collectAsStateWithLifecycle(initialValue = null)
  val photos = evidence?.photos?.sortedBy { it.sortOrder }.orEmpty()
  var selectedIndex by rememberSaveable(itemId) { mutableIntStateOf(initialIndex) }
  val safeIndex = selectedIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
  val photo = photos.getOrNull(safeIndex)
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  var pendingDownloadPath by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
  var isDownloading by remember { mutableStateOf(false) }
  var isDeleting by remember { mutableStateOf(false) }
  var showDeleteConfirmation by rememberSaveable(itemId) { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  fun downloadPhoto(path: String) {
    if (isDownloading) return
    isDownloading = true
    coroutineScope.launch {
      val message = runCatching {
        savePhotoToGallery(context, File(path))
        "已保存到相册"
      }.getOrElse { "保存失败：${it.message ?: "请重试"}" }
      isDownloading = false
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  val storagePermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    val path = pendingDownloadPath
    pendingDownloadPath = null
    if (granted && path != null) {
      downloadPhoto(path)
    } else if (path != null) {
      Toast.makeText(context, "没有相册写入权限", Toast.LENGTH_SHORT).show()
    }
  }
  val transformState = rememberTransformableState { zoomChange, panChange, _ ->
    val updatedScale = (scale * zoomChange).coerceIn(1f, 5f)
    scale = updatedScale
    if (updatedScale > 1f) {
      offsetX += panChange.x
      offsetY += panChange.y
    } else {
      offsetX = 0f
      offsetY = 0f
    }
  }

  LaunchedEffect(photo?.id) {
    scale = 1f
    offsetX = 0f
    offsetY = 0f
  }
  BackHandler(onBack = onBack)

  fun deleteCurrentPhoto() {
    val photoToDelete = photo ?: return
    isDeleting = true
    showDeleteConfirmation = false
    viewModel.deletePhoto(photoToDelete.id) { deleted ->
      isDeleting = false
      if (deleted) {
        if (photos.size <= 1) {
          onBack()
        } else if (safeIndex == photos.lastIndex) {
          selectedIndex = safeIndex - 1
        }
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    if (photo != null) {
      AsyncImage(
        model = ImageRequest.Builder(context)
          .data(File(photo.path))
          .size(Size.ORIGINAL)
          .build(),
        contentDescription = "第 ${safeIndex + 1} 张取证照片",
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offsetX
            translationY = offsetY
          }
          .transformable(transformState)
          .pointerInput(photo.id) {
            detectTapGestures(
              onDoubleTap = {
                if (scale > 1f) {
                  scale = 1f
                  offsetX = 0f
                  offsetY = 0f
                } else {
                  scale = 2.5f
                }
              },
            )
          },
      )
    }

    Row(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .background(Color.Black.copy(alpha = 0.62f))
        .safeDrawingPadding()
        .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
      }
      Text(
        text = evidence?.item?.name ?: "取证照片",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = {
          val path = photo?.path ?: return@IconButton
          if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
          ) {
            pendingDownloadPath = path
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
          } else {
            downloadPhoto(path)
          }
        },
        enabled = photo != null && !isDownloading,
      ) {
        if (isDownloading) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp),
          )
        } else {
          Icon(Icons.Default.Download, contentDescription = "保存到相册", tint = Color.White)
        }
      }
      IconButton(
        onClick = { showDeleteConfirmation = true },
        enabled = photo != null && !isDownloading && !isDeleting,
      ) {
        Icon(Icons.Default.Delete, contentDescription = "删除照片", tint = Color.White)
      }
      if (photos.isNotEmpty()) {
        Text("${safeIndex + 1} / ${photos.size}", color = Color.White, modifier = Modifier.padding(end = 14.dp))
      }
    }

    if (photos.size > 1) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Color.Black.copy(alpha = 0.5f))
          .safeDrawingPadding()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { selectedIndex = safeIndex - 1 },
          enabled = safeIndex > 0,
        ) {
          Icon(Icons.Default.ChevronLeft, contentDescription = "上一张", tint = if (safeIndex > 0) Color.White else Color.Gray)
        }
        IconButton(
          onClick = { selectedIndex = safeIndex + 1 },
          enabled = safeIndex < photos.lastIndex,
        ) {
          Icon(Icons.Default.ChevronRight, contentDescription = "下一张", tint = if (safeIndex < photos.lastIndex) Color.White else Color.Gray)
        }
      }
    }
  }

  if (showDeleteConfirmation && photo != null) {
    AlertDialog(
      onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
      title = { Text("删除这张取证照片？") },
      text = { Text("删除后将同时移除 App 中的原始文件，无法在 App 内恢复。") },
      confirmButton = {
        TextButton(onClick = ::deleteCurrentPhoto, enabled = !isDeleting) {
          Text("删除")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeleting) {
          Text("取消")
        }
      },
    )
  }
}

private suspend fun savePhotoToGallery(context: Context, source: File) = withContext(Dispatchers.IO) {
  require(source.isFile && source.length() > 0L) { "原始照片不存在" }

  val extension = source.extension.ifBlank { "jpg" }.lowercase()
  val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
  val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, "等保取证_${System.currentTimeMillis()}.$extension")
    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/等保现场取证")
      put(MediaStore.Images.Media.IS_PENDING, 1)
    }
  }
  val resolver = context.contentResolver
  val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
  } else {
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  }
  val destination = requireNotNull(resolver.insert(collection, values)) { "无法创建相册文件" }

  try {
    requireNotNull(resolver.openOutputStream(destination)) { "无法打开相册文件" }.use { output ->
      source.inputStream().use { input -> input.copyTo(output) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      resolver.update(
        destination,
        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
        null,
        null,
      )
    }
  } catch (throwable: Throwable) {
    resolver.delete(destination, null, null)
    throw throwable
  }
}
