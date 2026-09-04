package com.example.dengbaoevidence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.dengbaoevidence.data.AppSettings
import com.example.dengbaoevidence.theme.AppAppearance
import com.example.dengbaoevidence.theme.DengBaoEvidenceTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      var appearance by remember { mutableStateOf(AppSettings.appearance(this@MainActivity)) }
      DengBaoEvidenceTheme(appearance = appearance, darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            appearance = appearance,
            onAppearanceChange = { selected: AppAppearance ->
              AppSettings.setAppearance(this@MainActivity, selected)
              appearance = selected
            },
          )
        }
      }
    }
  }
}
