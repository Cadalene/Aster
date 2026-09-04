package com.example.dengbaoevidence.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPackageExporterTest {
  @Test
  fun exportsOfflineViewerDataOriginalAndThumbnail() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val testDirectory = File(context.cacheDir, "project_package_test_${System.nanoTime()}").apply { mkdirs() }
    try {
      val source = File(testDirectory, "source.png")
      Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.rgb(35, 91, 143))
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
      }
      val hash = sha256(source.readBytes())
      val item = EvidenceItemWithPhotos(
        item = EvidenceItemEntity(
          id = 30,
          deviceId = 20,
          name = "正面照",
          displayOrder = 0,
          isKeyScreenshot = true,
          wordOrder = 0,
          excludedFromProgress = false,
          isTemporary = false,
          isCompliant = true,
          isNotApplicable = true,
          note = "设备运行正常",
          updatedAt = 1_700_000_002_000,
        ),
        photos = listOf(
          PhotoEntity(
            id = 40,
            evidenceItemId = 30,
            path = source.absolutePath,
            createdAt = 1_700_000_003_000,
            sortOrder = 0,
            sha256 = hash,
          ),
        ),
      )
      val data = ProjectExportData(
        project = ProjectEntity(id = 10, name = "测试项目", createdAt = 1_700_000_000_000),
        devices = listOf(
          DeviceWithItems(
            device = DeviceEntity(
              id = 20,
              projectId = 10,
              name = "防火墙01",
              typeName = "防火墙",
              createdAt = 1_700_000_001_000,
            ),
            items = listOf(item),
          ),
        ),
      )
      val destination = File(testDirectory, "测试项目_导出包.zip")

      val summary = ProjectPackageExporter.export(context, data, destination)

      assertEquals(1, summary.deviceCount)
      assertEquals(1, summary.itemCount)
      assertEquals(1, summary.photoCount)
      ZipFile(destination).use { zip ->
        assertNotNull(zip.getEntry("index.html"))
        assertNotNull(zip.getEntry("data.js"))
        assertNotNull(zip.getEntry("manifest.json"))
        val originalPath = "originals/防火墙/防火墙01/防火墙01_正面照_01.png"
        val thumbnailPath = "thumbnails/photo_40_1.jpg"
        assertArrayEquals(source.readBytes(), zip.getInputStream(zip.getEntry(originalPath)).use { it.readBytes() })
        assertNotNull(BitmapFactory.decodeStream(zip.getInputStream(zip.getEntry(thumbnailPath))))
        val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
        assertEquals(hash, manifest.getJSONArray("photos").getJSONObject(0).getString("sha256"))
        val dataScript = zip.getInputStream(zip.getEntry("data.js")).bufferedReader().use { it.readText() }
        val exportedData = JSONObject(dataScript.removePrefix("window.EVIDENCE_EXPORT = ").trim().removeSuffix(";"))
        assertEquals("测试项目", exportedData.getJSONObject("project").getString("name"))
        assertTrue(
          exportedData.getJSONArray("devices").getJSONObject(0)
            .getJSONArray("items").getJSONObject(0)
            .getBoolean("isNotApplicable"),
        )
        assertEquals(
          originalPath,
          exportedData.getJSONArray("devices").getJSONObject(0)
            .getJSONArray("items").getJSONObject(0)
            .getJSONArray("photos").getJSONObject(0)
            .getString("originalPath"),
        )
      }
    } finally {
      testDirectory.deleteRecursively()
    }
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }
}
