package com.example.dengbaoevidence.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveWriterTest {
  @get:Rule val files = TemporaryFolder()
  private val date = "20260930"

  private fun project(id: Long, name: String) = ProjectExportData(ProjectEntity(id, name, id), emptyList())
  private fun writeExport(data: ProjectExportData, target: File, progress: (Int, String) -> Unit) {
    target.writeText("project-${data.project.id}")
    progress(50, "generating")
    progress(100, "generated")
  }

  @Test fun preservesHistoricalDuplicatesAndSanitizedNameCollisionsWithDatedExports() {
    val destination = files.newFile("backup.zip")
    val working = File(files.root, "work")
    val projects = listOf(project(1, "Same"), project(2, "Same"), project(3, "A/B"), project(4, "A\\B"))
    val progress = mutableListOf<Int>()
    BackupArchiveWriter.export(projects, "{}", date, destination, working,
      exportPackage = { data, target, callback ->
        assertEquals(listOf("p_${data.project.id}"), working.list()?.toList())
        writeExport(data, target, callback)
      }, exportWord = ::writeExport, onProgress = { value, _ -> progress += value },
    )
    assertFalse(working.exists())
    ZipFile(destination).use { zip ->
      val names = zip.entries().asSequence().map { it.name }.toList()
      assertEquals(9, names.size)
      assertEquals(9, names.distinct().size)
      assertTrue(names.all { it.contains(date) })
      assertTrue(names.any { it.startsWith("Same_项目1/") })
      assertTrue(names.any { it.startsWith("Same_项目2/") })
      assertTrue(names.any { it.startsWith("A_B_项目3/") })
      assertTrue(names.any { it.startsWith("A_B_项目4/") })
    }
    assertTrue(progress.zipWithNext().all { (left, right) -> left <= right })
    assertEquals(100, progress.last())
  }

  @Test fun failedProjectCleansOnlyNewTemporaryDataAndPreservesOriginalsAndOldExport() {
    val original = files.newFile("original.jpg").apply { writeText("original") }
    val oldExport = files.newFile("old-backup.zip").apply { writeText("old-backup") }
    val destination = File(files.root, "new-backup.zip")
    val working = File(files.root, "work")
    assertThrows(IOException::class.java) {
      BackupArchiveWriter.export(listOf(project(1, "One"), project(2, "Two")), "{}", date, destination, working,
        exportPackage = ::writeExport,
        exportWord = { data, target, callback ->
          if (data.project.id == 2L) throw IOException("Storage full")
          writeExport(data, target, callback)
        }, onProgress = { _, _ -> },
      )
    }
    assertFalse(destination.exists())
    assertFalse(working.exists())
    assertEquals("original", original.readText())
    assertEquals("old-backup", oldExport.readText())
  }

  @Test fun emptyProjectListStillBacksUpTemplates() {
    val destination = File(files.root, "backup.zip")
    BackupArchiveWriter.export(emptyList(), "template-content", date, destination, File(files.root, "work"), ::writeExport, ::writeExport) { _, _ -> }
    ZipFile(destination).use { zip ->
      assertEquals(1, zip.size())
      assertEquals("template-content", zip.getInputStream(zip.getEntry("设备类型模板_$date.json")).reader().use { it.readText() })
    }
  }

  @Test fun projectPackageFailureStopsBackupAndRemovesTemporaryFiles() {
    val item = EvidenceItemWithPhotos(
      EvidenceItemEntity(1, 1, "item", 0, false, null, false, false, updatedAt = 1),
      listOf(PhotoEntity(1, 1, File(files.root, "missing.jpg").path, 1, 0, "")),
    )
    val data = ProjectExportData(ProjectEntity(1, "project", 1), listOf(DeviceWithItems(DeviceEntity(1, 1, "device", "type", 1), listOf(item))))
    val destination = File(files.root, "backup.zip")
    val work = File(files.root, "work")
    assertThrows(IllegalStateException::class.java) {
      BackupArchiveWriter.export(listOf(data), "{}", date, destination, work,
        exportPackage = { project, _, _ ->
          project.devices.flatMap { it.items }.flatMap { it.photos }.forEach { check(File(it.path).isFile) }
        }, exportWord = ::writeExport, onProgress = { _, _ -> },
      )
    }
    assertFalse(destination.exists())
    assertFalse(work.exists())
  }

  @Test fun originalsRemainInNestedBrowserPackageWithoutSeparateOriginalPhotoDirectory() {
    val original = files.newFile("photo.jpg").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
    val devices = (1L..2L).map { id ->
      DeviceWithItems(DeviceEntity(id, 1, "same", "type", id), listOf(EvidenceItemWithPhotos(
        EvidenceItemEntity(id, id, "item", 0, false, null, false, false, updatedAt = id),
        listOf(PhotoEntity(id, id, original.path, id, 0, "")),
      )))
    }
    val destination = File(files.root, "backup.zip")
    BackupArchiveWriter.export(listOf(ProjectExportData(ProjectEntity(1, "project", 1), devices)), "{}", date, destination, File(files.root, "work"),
      exportPackage = { data, file, _ ->
        ZipOutputStream(file.outputStream()).use { zip ->
          data.devices.forEach { device ->
            zip.putNextEntry(ZipEntry("originals/type/device_${device.device.id}/photo.jpg"))
            zip.write(original.readBytes())
            zip.closeEntry()
          }
        }
      }, exportWord = ::writeExport, onProgress = { _, _ -> },
    )
    ZipFile(destination).use { zip ->
      assertEquals(3, zip.size())
      assertFalse(zip.entries().asSequence().any { it.name.contains("原始照片") || it.name.contains("originals/") })
      val packageEntry = zip.entries().asSequence().single { it.name.endsWith(".zip") }
      val extracted = files.newFile("browser.zip")
      zip.getInputStream(packageEntry).use { input -> extracted.outputStream().use { input.copyTo(it) } }
      ZipFile(extracted).use { browser ->
        assertEquals(2, browser.size())
        browser.entries().asSequence().forEach { assertArrayEquals(original.readBytes(), browser.getInputStream(it).use { input -> input.readBytes() }) }
      }
    }
  }

  @Test fun insufficientSpaceBeforeProjectDoesNotStartExportsAndCleansPartialBackup() {
    val destination = File(files.root, "backup.zip")
    val working = File(files.root, "work")
    var exported = false
    assertThrows(IOException::class.java) {
      BackupArchiveWriter.export(listOf(project(1, "project")), "{}", date, destination, working,
        exportPackage = { _, _, _ -> exported = true }, exportWord = ::writeExport,
        beforeProject = { throw IOException("Insufficient space") }, onProgress = { _, _ -> },
      )
    }
    assertFalse(exported)
    assertFalse(destination.exists())
    assertFalse(working.exists())
  }

  @Test fun spaceDropDuringArchiveWritePreservesOriginalsAndOldBackup() {
    val original = files.newFile("photo.jpg").apply { writeText("original") }
    val oldBackup = files.newFile("old.zip").apply { writeText("old") }
    val destination = File(files.root, "new.zip")
    val work = File(files.root, "work")
    var bytes = 0
    assertThrows(IOException::class.java) {
      BackupArchiveWriter.export(listOf(project(1, "project")), "{}", date, destination, work, ::writeExport, ::writeExport,
        beforeWrite = { count -> bytes += count; if (bytes > 100) throw IOException("Insufficient space") },
        onProgress = { _, _ -> },
      )
    }
    assertFalse(destination.exists())
    assertFalse(work.exists())
    assertEquals("original", original.readText())
    assertEquals("old", oldBackup.readText())
  }
}
