package com.example.dengbaoevidence

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.dengbaoevidence.ui.main.CameraScreen
import com.example.dengbaoevidence.ui.main.DeviceScreen
import com.example.dengbaoevidence.ui.main.EvidenceItemScreen
import com.example.dengbaoevidence.ui.main.EvidenceViewModel
import com.example.dengbaoevidence.ui.main.ProjectScreen
import com.example.dengbaoevidence.ui.main.ProjectsScreen
import com.example.dengbaoevidence.ui.main.PhotoViewerScreen
import com.example.dengbaoevidence.ui.main.TemplateEditorScreen
import com.example.dengbaoevidence.ui.main.TemplateManagementScreen
import com.example.dengbaoevidence.ui.main.SettingsScreen
import com.example.dengbaoevidence.theme.AppAppearance

@Composable
fun MainNavigation(
  appearance: AppAppearance,
  onAppearanceChange: (AppAppearance) -> Unit,
) {
  val application = LocalContext.current.applicationContext as EvidenceApplication
  val activity = LocalActivity.current
  val evidenceViewModel: EvidenceViewModel = viewModel { EvidenceViewModel(application.repository) }
  val backStack = rememberNavBackStack(Projects)
  val snackbarHostState = remember { SnackbarHostState() }
  val errorMessage by evidenceViewModel.errorMessage.collectAsStateWithLifecycle()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  fun openProjectsRoot() {
    while (backStack.size > 1) backStack.removeLastOrNull()
    if (backStack.firstOrNull() != Projects) {
      backStack.removeLastOrNull()
      backStack.add(Projects)
    }
  }

  fun openTemplatesRoot() {
    while (backStack.size > 1) backStack.removeLastOrNull()
    if (backStack.firstOrNull() != TemplateManagement) {
      backStack.removeLastOrNull()
      backStack.add(TemplateManagement)
    }
  }

  fun handleBack() {
    if (backStack.size > 1) {
      backStack.removeLastOrNull()
    } else {
      activity?.finish()
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) evidenceViewModel.flushAllNotes()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let {
      snackbarHostState.showSnackbar(it)
      evidenceViewModel.clearError()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
      backStack = backStack,
      onBack = ::handleBack,
      modifier = Modifier.fillMaxSize(),
      transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
      popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
      predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
      entryProvider =
        entryProvider {
          entry<Projects> {
            ProjectsScreen(
              viewModel = evidenceViewModel,
              onOpenProject = { backStack.add(ProjectDetails(it)) },
              onOpenTemplates = ::openTemplatesRoot,
              onOpenSettings = { backStack.add(AppSettings) },
            )
          }
          entry<TemplateManagement> {
            TemplateManagementScreen(
              viewModel = evidenceViewModel,
              onOpenTemplate = { backStack.add(TemplateEditor(it)) },
              onOpenEvidence = ::openProjectsRoot,
              onOpenSettings = { backStack.add(AppSettings) },
            )
          }
          entry<AppSettings> {
            SettingsScreen(
              viewModel = evidenceViewModel,
              appearance = appearance,
              onAppearanceChange = onAppearanceChange,
              onBack = { backStack.removeLastOrNull() },
              onOpenEvidence = ::openProjectsRoot,
              onOpenTemplates = ::openTemplatesRoot,
            )
          }
          entry<TemplateEditor> { destination ->
            TemplateEditorScreen(
              templateId = destination.templateId,
              viewModel = evidenceViewModel,
              onBack = { backStack.removeLastOrNull() },
            )
          }
          entry<ProjectDetails> { destination ->
            ProjectScreen(
              projectId = destination.projectId,
              viewModel = evidenceViewModel,
              onBack = { backStack.removeLastOrNull() },
              onOpenDevice = { backStack.add(DeviceDetails(it)) },
            )
          }
          entry<DeviceDetails> { destination ->
            DeviceScreen(
              deviceId = destination.deviceId,
              viewModel = evidenceViewModel,
              onBack = { backStack.removeLastOrNull() },
              onOpenItem = { backStack.add(EvidenceDetails(it)) },
              onCapture = { backStack.add(CameraCapture(it)) },
            )
          }
          entry<EvidenceDetails> { destination ->
            EvidenceItemScreen(
              itemId = destination.itemId,
              viewModel = evidenceViewModel,
              onBack = {
                evidenceViewModel.flushNote(destination.itemId)
                backStack.removeLastOrNull()
              },
              onCapture = { backStack.add(CameraCapture(destination.itemId)) },
              onOpenPhoto = { index -> backStack.add(PhotoViewer(destination.itemId, index)) },
            )
          }
          entry<CameraCapture> { destination ->
            CameraScreen(
              itemId = destination.itemId,
              viewModel = evidenceViewModel,
              onBack = { backStack.removeLastOrNull() },
              onPhotoSaved = { backStack.removeLastOrNull() },
            )
          }
          entry<PhotoViewer> { destination ->
            PhotoViewerScreen(
              itemId = destination.itemId,
              initialIndex = destination.initialIndex,
              viewModel = evidenceViewModel,
              onBack = { backStack.removeLastOrNull() },
            )
          }
        },
    )
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
    )
  }
}
