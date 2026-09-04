package com.example.dengbaoevidence.ui.main

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.dengbaoevidence.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun projectListTitleIsVisible() {
    assertTrue(composeTestRule.onAllNodesWithText("现场取证").fetchSemanticsNodes().isNotEmpty())
  }
}
