package com.example.dengbaoevidence.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFileIoTest {
  @get:Rule val files = TemporaryFolder()

  @Test fun repeatedExportUsesUniqueNameWithoutChangingExistingNames() {
    val names = setOf("project_20260930.zip", "project_20260930_2.zip")
    assertEquals("project_20260930_3.zip", ExportFileIo.uniqueName("project_20260930.zip", names))
    assertEquals(2, names.size)
    assertEquals("FILE_2.json", ExportFileIo.uniqueName("FILE.json", setOf("file.json")))
    assertEquals("fresh.docx", ExportFileIo.uniqueName("fresh.docx", names))
  }

  @Test fun safeNamesCannotEscapeTheirArchiveDirectory() {
    assertEquals("A_B", ExportFileIo.safeName(" A/B "))
    assertEquals("A_B", ExportFileIo.safeName("A\\B"))
    assertEquals("项目", ExportFileIo.safeName(".."))
    assertFalse(ExportFileIo.safeName("A\nB").contains('\n'))
    assertTrue(ExportFileIo.dateSuffix().matches(Regex("\\d{8}")))
  }

  @Test fun reportsCompleteOnlyAfterOutputAndVerificationStreamsClose() {
    val source = files.newFile().apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
    val bytes = ByteArrayOutputStream()
    var outputClosed = false
    var inputClosed = false
    val progress = mutableListOf<Int>()
    ExportFileIo.copyAndVerify(source,
      openOutput = { object : OutputStream() {
        override fun write(value: Int) = bytes.write(value)
        override fun write(buffer: ByteArray, offset: Int, length: Int) = bytes.write(buffer, offset, length)
        override fun close() { outputClosed = true }
      } },
      openInput = {
        assertTrue(outputClosed)
        object : ByteArrayInputStream(bytes.toByteArray()) {
          override fun close() { inputClosed = true }
        }
      },
      onProgress = { percent, _ ->
        progress += percent
        if (percent == 100) { assertTrue(outputClosed); assertTrue(inputClosed) }
      },
    )
    assertArrayEquals(source.readBytes(), bytes.toByteArray())
    assertEquals(100, progress.last())
    assertTrue(progress.zipWithNext().all { (left, right) -> left <= right })
  }

  @Test fun destinationCorruptionFailsWithoutReportingCompleteOrChangingSource() {
    val original = byteArrayOf(1, 2, 3, 4)
    val source = files.newFile().apply { writeBytes(original) }
    val progress = mutableListOf<Int>()
    assertThrows(IllegalStateException::class.java) {
      ExportFileIo.copyAndVerify(source, { ByteArrayOutputStream() }, { ByteArrayInputStream(byteArrayOf(1, 2, 3, 5)) }) { percent, _ -> progress += percent }
    }
    assertFalse(100 in progress)
    assertArrayEquals(original, source.readBytes())
  }

  @Test fun truncatedReadAndFailedWriteNeverReportComplete() {
    val source = files.newFile().apply { writeText("original-data") }
    val progress = mutableListOf<Int>()
    assertThrows(IllegalStateException::class.java) {
      ExportFileIo.copyAndVerify(source, { ByteArrayOutputStream() }, { ByteArrayInputStream(byteArrayOf(1)) }) { percent, _ -> progress += percent }
    }
    assertThrows(IOException::class.java) {
      ExportFileIo.copyAndVerify(source, { object : OutputStream() {
        override fun write(value: Int) { throw IOException("Storage full") }
      } }, { source.inputStream() }) { percent, _ -> progress += percent }
    }
    assertFalse(100 in progress)
    assertEquals("original-data", source.readText())
  }

  @Test fun zipValidationRejectsMissingOrUnexpectedEntries() {
    val archive = files.newFile()
    ZipOutputStream(archive.outputStream()).use {
      it.putNextEntry(ZipEntry("templates.json"))
      it.write("{}".toByteArray())
      it.closeEntry()
    }
    ExportFileIo.validateZip(archive, setOf("templates.json"))
    assertThrows(IllegalStateException::class.java) { ExportFileIo.validateZip(archive, setOf("templates.json", "missing.zip")) }
    assertThrows(IllegalStateException::class.java) { ExportFileIo.validateZip(archive, emptySet()) }
  }

  @Test fun spaceDropDuringCopyStopsBeforeCompleteAndLeavesSourceUnchanged() {
    val original = ByteArray(200_000) { 7 }
    val source = files.newFile().apply { writeBytes(original) }
    val progress = mutableListOf<Int>()
    var written = 0
    assertThrows(IOException::class.java) {
      ExportFileIo.copyAndVerify(source, { ByteArrayOutputStream() }, { source.inputStream() },
        beforeWrite = { count -> written += count; if (written > 64 * 1024) throw IOException("Insufficient space") },
        onProgress = { percent, _ -> progress += percent },
      )
    }
    assertFalse(100 in progress)
    assertArrayEquals(original, source.readBytes())
  }
}
