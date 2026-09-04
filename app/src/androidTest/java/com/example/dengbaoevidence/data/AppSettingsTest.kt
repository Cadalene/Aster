package com.example.dengbaoevidence.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dengbaoevidence.theme.AppAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
  @Test
  fun remembersAppearanceAndDefaultsToPaperBlue() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    preferences.edit().remove("appearance").commit()
    try {
      assertEquals(AppAppearance.PAPER_BLUE, AppSettings.appearance(context))

      AppSettings.setAppearance(context, AppAppearance.PAPER_BLUE)

      assertEquals(AppAppearance.PAPER_BLUE, AppSettings.appearance(context))

      preferences.edit().putString("appearance", "unknown").commit()
      assertEquals(AppAppearance.PAPER_BLUE, AppSettings.appearance(context))
    } finally {
      preferences.edit().remove("appearance").commit()
    }
  }

  @Test
  fun remembersExpandedDeviceTypesPerProject() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firstProjectId = System.nanoTime()
    val secondProjectId = firstProjectId + 1
    try {
      AppSettings.setDeviceTypeExpanded(context, firstProjectId, "防火墙", true)
      AppSettings.setDeviceTypeExpanded(context, firstProjectId, "堡垒机", true)
      AppSettings.setDeviceTypeExpanded(context, firstProjectId, "防火墙", false)

      assertEquals(setOf("堡垒机"), AppSettings.expandedDeviceTypes(context, firstProjectId))
      assertTrue(AppSettings.expandedDeviceTypes(context, secondProjectId).isEmpty())
    } finally {
      AppSettings.clearProjectUiState(context, firstProjectId)
      AppSettings.clearProjectUiState(context, secondProjectId)
    }
  }

  @Test
  fun remembersProjectListScrollPositionPerProject() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val projectId = System.nanoTime()
    try {
      AppSettings.setProjectListScrollPosition(context, projectId, 12, 48)
      assertEquals(ProjectListScrollPosition(12, 48), AppSettings.projectListScrollPosition(context, projectId))
    } finally {
      AppSettings.clearProjectUiState(context, projectId)
    }
  }
}
