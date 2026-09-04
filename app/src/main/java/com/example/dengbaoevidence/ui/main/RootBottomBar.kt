package com.example.dengbaoevidence.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.dengbaoevidence.theme.LocalEvidenceColors

enum class RootTab {
  EVIDENCE,
  TEMPLATES,
}

@Composable
fun RootBottomBar(
  selected: RootTab?,
  onOpenEvidence: () -> Unit,
  onOpenTemplates: () -> Unit,
) {
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
  ) {
    NavigationBarItem(
      selected = selected == RootTab.EVIDENCE,
      onClick = onOpenEvidence,
      icon = { Icon(Icons.Default.Folder, contentDescription = null) },
      label = { Text("现场取证") },
      colors = pinterestNavigationColors(),
    )
    NavigationBarItem(
      selected = selected == RootTab.TEMPLATES,
      onClick = onOpenTemplates,
      icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
      label = { Text("设备类型模板") },
      colors = pinterestNavigationColors(),
    )
  }
}

@Composable
private fun pinterestNavigationColors() = NavigationBarItemDefaults.colors(
  selectedIconColor = MaterialTheme.colorScheme.onPrimary,
  selectedTextColor = LocalEvidenceColors.current.navigationSelected,
  indicatorColor = LocalEvidenceColors.current.navigationSelected,
  unselectedIconColor = LocalEvidenceColors.current.navigationUnselectedIcon,
  unselectedTextColor = LocalEvidenceColors.current.navigationUnselectedText,
)
