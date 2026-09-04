package com.example.dengbaoevidence.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

@Composable
fun CameraScreen(
  itemId: Long,
  viewModel: EvidenceViewModel,
  onBack: () -> Unit,
  onPhotoSaved: () -> Unit,
) {
  val context = LocalContext.current
  var pendingPhotoPath by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
  var launchRequested by rememberSaveable(itemId) { mutableStateOf(false) }
  var captureState by remember(itemId) { mutableStateOf(NativeCaptureState.OPENING) }
  var errorMessage by remember(itemId) { mutableStateOf<String?>(null) }

  val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
    val photoFile = pendingPhotoPath?.let(::File)
    if (!captured) {
      photoFile?.delete()
      pendingPhotoPath = null
      onBack()
    } else if (photoFile == null || !photoFile.isFile || photoFile.length() == 0L) {
      photoFile?.delete()
      pendingPhotoPath = null
      captureState = NativeCaptureState.ERROR
      errorMessage = "系统相机没有返回有效照片，请重试"
    } else {
      captureState = NativeCaptureState.SAVING
      viewModel.registerCapturedPhoto(
        itemId = itemId,
        file = photoFile,
        onSaved = {
          pendingPhotoPath = null
          onPhotoSaved()
        },
        onFailed = {
          pendingPhotoPath = null
          captureState = NativeCaptureState.ERROR
          errorMessage = "照片保存失败，请重试"
        },
      )
    }
  }

  LaunchedEffect(itemId, launchRequested) {
    if (launchRequested) return@LaunchedEffect

    val photoFile = createPhotoFile(context, itemId)
    val photoUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      photoFile,
    )
    pendingPhotoPath = photoFile.absolutePath
    launchRequested = true
    captureState = NativeCaptureState.OPENING
    errorMessage = null

    runCatching { cameraLauncher.launch(photoUri) }
      .onFailure {
        photoFile.delete()
        pendingPhotoPath = null
        captureState = NativeCaptureState.ERROR
        errorMessage = "无法启动系统相机：${it.message ?: "请重试"}"
      }
  }

  BackHandler(enabled = captureState == NativeCaptureState.SAVING) {}

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .safeDrawingPadding(),
  ) {
    IconButton(
      onClick = {
        pendingPhotoPath?.let(::File)?.delete()
        pendingPhotoPath = null
        onBack()
      },
      enabled = captureState != NativeCaptureState.SAVING,
      modifier = Modifier.align(Alignment.TopStart),
    ) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }

    Column(
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (captureState == NativeCaptureState.ERROR) {
        Text(
          text = errorMessage ?: "拍摄失败，请重试",
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(
          onClick = {
            pendingPhotoPath?.let(::File)?.delete()
            pendingPhotoPath = null
            launchRequested = false
          },
        ) {
          Text("重新拍摄")
        }
      } else {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
          text = if (captureState == NativeCaptureState.SAVING) "正在保存照片" else "正在打开系统相机",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}

private enum class NativeCaptureState {
  OPENING,
  SAVING,
  ERROR,
}

private fun createPhotoFile(context: Context, itemId: Long): File {
  val directory = File(context.filesDir, "originals/item_$itemId").apply { mkdirs() }
  return File(directory, "IMG_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
}
