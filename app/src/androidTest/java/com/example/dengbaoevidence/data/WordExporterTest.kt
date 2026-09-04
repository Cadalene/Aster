package com.example.dengbaoevidence.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordExporterTest {
  @Test
  fun usesBlackFangSongAndRequestedHeadingSizes() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val testDirectory = File(context.cacheDir, "word_style_test_${System.nanoTime()}").apply { mkdirs() }
    try {
      val source = File(testDirectory, "source.png")
      Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.WHITE)
        source.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        bitmap.recycle()
      }
      val data = ProjectExportData(
        project = ProjectEntity(id = 1, name = "项目", createdAt = 1_700_000_000_000),
        devices = listOf(
          DeviceWithItems(
            device = DeviceEntity(id = 2, projectId = 1, name = "防火墙01", typeName = "防火墙", createdAt = 1_700_000_001_000),
            items = listOf(
              EvidenceItemWithPhotos(
                item = EvidenceItemEntity(
                  id = 3,
                  deviceId = 2,
                  name = "安全策略",
                  displayOrder = 0,
                  isKeyScreenshot = true,
                  wordOrder = 0,
                  excludedFromProgress = false,
                  isTemporary = false,
                  updatedAt = 1_700_000_002_000,
                ),
                photos = listOf(
                  PhotoEntity(
                    id = 4,
                    evidenceItemId = 3,
                    path = source.absolutePath,
                    createdAt = 1_700_000_003_000,
                    sortOrder = 0,
                    sha256 = "",
                  ),
                ),
              ),
            ),
          ),
        ),
      )
      val destination = File(testDirectory, "项目_关键设备截图.docx")

      val summary = WordExporter.export(context, data, destination)

      assertEquals(1, summary.photoCount)
      ZipFile(destination).use { zip ->
        val styles = zip.getInputStream(requireNotNull(zip.getEntry("word/styles.xml")))
          .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertHeadingStyle(styles, "Heading1", "44")
        assertHeadingStyle(styles, "Heading2", "36")
        assertHeadingStyle(styles, "Heading3", "32")
      }
    } finally {
      testDirectory.deleteRecursively()
    }
  }

  private fun assertHeadingStyle(styles: String, styleId: String, size: String) {
    val block = styles.substringAfter("styleId=\"$styleId\"").substringBefore("</w:style>")
    assertTrue(block.contains("仿宋"))
    assertTrue(block.contains("w:color w:val=\"000000\""))
    assertTrue(block.contains("w:sz w:val=\"$size\""))
    assertTrue(block.contains("w:szCs w:val=\"$size\""))
  }
}
