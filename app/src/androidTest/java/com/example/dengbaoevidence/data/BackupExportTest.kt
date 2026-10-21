package com.example.dengbaoevidence.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class BackupExportTest {
  @Test
  fun unknownDocumentProviderReportsInternalSpaceButNotDestinationCapacity() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = BackupStorage.inspect(context, Uri.parse("content://unknown.provider/tree/backup"))
    val preview = storage.preview(BackupSpaceEstimate(1024, 1200, 1))
    assertTrue(preview.availableInternalBytes > 0)
    assertTrue(preview.copyUsesInternalStorage)
    assertNull(preview.availableDestinationBytes)
    assertEquals(2520, preview.requiredInternalBytes)
  }

  @Test
  fun completeBackupKeepsOriginalInBrowserOnlyAndIncludesValidWordAndTemplates() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val directory = File(context.cacheDir, "backup_export_test_${System.nanoTime()}").apply { mkdirs() }
    try {
      val source = File(directory, "source.png")
      Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.rgb(35, 91, 143))
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
      }
      val original = source.readBytes()
      val hash = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
      val data = ProjectExportData(
        ProjectEntity(1, "测试项目", 1),
        listOf(DeviceWithItems(DeviceEntity(2, 1, "防火墙01", "防火墙", 1), listOf(EvidenceItemWithPhotos(
          EvidenceItemEntity(3, 2, "正面照", 0, true, 0, false, false, updatedAt = 1),
          listOf(PhotoEntity(4, 3, source.path, 1, 0, hash)),
        )))),
      )
      val date = "20261007"
      val templates = TemplateTransfer.encode(emptyList())
      val estimate = BackupSpaceEstimator.estimate(listOf(data), templates.toByteArray(Charsets.UTF_8).size.toLong())
      val destination = File(directory, "backup.zip")
      val working = File(directory, "work")
      BackupArchiveWriter.export(
        listOf(data), templates, date, destination, working,
        exportPackage = { project, file, progress -> ProjectPackageExporter.export(context, project, file, progress) },
        exportWord = { project, file, progress -> WordExporter.export(context, project, file, progress) },
        onProgress = { _, _ -> },
      )
      assertTrue(destination.length() <= estimate.archiveBytes)
      assertFalse(working.exists())
      assertArrayEquals(original, source.readBytes())
      ZipFile(destination).use { outer ->
        assertEquals(3, outer.size())
        assertFalse(outer.entries().asSequence().any { it.name.contains("原始照片") || it.name.contains("originals/") })
        val template = outer.getEntry("设备类型模板_$date.json")
        assertNotNull(template)
        assertEquals(templates, outer.getInputStream(template).bufferedReader(Charsets.UTF_8).use { it.readText() })
        fun extract(name: String, target: File) {
          outer.getInputStream(outer.getEntry(name)).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        val browserFile = File(directory, "browser.zip")
        extract("测试项目_项目1/测试项目_浏览包_$date.zip", browserFile)
        ZipFile(browserFile).use { browser ->
          assertNotNull(browser.getEntry("index.html"))
          val photo = browser.entries().asSequence().single { it.name.startsWith("originals/") }
          assertArrayEquals(original, browser.getInputStream(photo).use { it.readBytes() })
        }
        val wordFile = File(directory, "word.docx")
        extract("测试项目_项目1/测试项目_关键设备截图_$date.docx", wordFile)
        ZipFile(wordFile).use { word ->
          assertNotNull(word.getEntry("word/document.xml"))
          val image = word.entries().asSequence().single { it.name.startsWith("word/media/") }
          assertArrayEquals(original, word.getInputStream(image).use { it.readBytes() })
        }
      }
    } finally {
      directory.deleteRecursively()
    }
  }
}
