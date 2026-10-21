package com.example.dengbaoevidence.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BrowserPreviewExportTest {
  private fun data(file: File) = ProjectExportData(ProjectEntity(1, "Preview", 123), listOf(
    DeviceWithItems(DeviceEntity(2, 1, "Device", "Type", 123), listOf(
      EvidenceItemWithPhotos(EvidenceItemEntity(3, 2, "Policy", 0, true, 0, false, false, updatedAt = 123, verificationMethod = "show policy"),
        listOf(PhotoEntity(4, 3, file.path, 123, 0, ""))),
    )),
  ))

  @Test fun writesSeparateBrowserJpegKeepsOriginalAndThumbnailAndFailsExplicitlyOnDecodeError() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val directory = File(context.cacheDir, "browser_preview_test_${System.nanoTime()}").apply { mkdirs() }
    try {
      // A PNG with a HEIC suffix exercises preview routing independently of device HEIC codecs.
      val source = File(directory, "source.heic")
      Bitmap.createBitmap(3600, 1800, Bitmap.Config.ARGB_8888).also { image ->
        image.eraseColor(Color.BLUE)
        source.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
      }
      val before = source.readBytes()
      val destination = File(directory, "project.zip")
      ProjectPackageExporter.export(context, data(source), destination)
      ZipFile(destination).use { zip ->
        val json = JSONObject(zip.getInputStream(zip.getEntry("data.js")).bufferedReader().use { it.readText() }.removePrefix("window.EVIDENCE_EXPORT = ").trim().removeSuffix(";"))
        val item = json.getJSONArray("devices").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        assertEquals("show policy", item.getString("verificationMethod"))
        val photo = item.getJSONArray("photos").getJSONObject(0)
        assertArrayEquals(before, zip.getInputStream(zip.getEntry(photo.getString("originalPath"))).use { it.readBytes() })
        assertNotEquals(photo.getString("originalPath"), photo.getString("browserPath"))
        val preview = requireNotNull(BitmapFactory.decodeStream(zip.getInputStream(zip.getEntry(photo.getString("browserPath")))))
        assertTrue(maxOf(preview.width, preview.height) <= 3200)
        preview.recycle()
        val thumbnail = requireNotNull(BitmapFactory.decodeStream(zip.getInputStream(zip.getEntry(photo.getString("thumbnailPath")))))
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 720)
        thumbnail.recycle()
      }
      assertArrayEquals(before, source.readBytes())
      val broken = File(directory, "broken.heic").apply { writeText("not an image") }
      val failed = File(directory, "failed.zip")
      val error = runCatching { ProjectPackageExporter.export(context, data(broken), failed) }.exceptionOrNull()
      assertTrue(error?.message.orEmpty().contains("HEIC"))
      assertFalse(failed.exists())
      assertEquals("not an image", broken.readText())
    } finally { directory.deleteRecursively() }
  }
}
