package com.example.dengbaoevidence.data

import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserImageCompatibilityTest {
  @get:Rule val files = TemporaryFolder()

  @Test fun recognizesExtensionAndHeicHiddenBehindJpgExtension() {
    val heic = files.newFile("photo.HEIC")
    val locale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      assertTrue(BrowserImageCompatibility.needsJpegPreview(heic))
    } finally { Locale.setDefault(locale) }
    val disguised = files.newFile("camera.jpg").apply {
      writeBytes(byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mif1heic".toByteArray())
    }
    assertTrue(BrowserImageCompatibility.needsJpegPreview(disguised))
  }

  @Test fun leavesJpegEmptyAndOtherFormatsAlone() {
    val jpeg = files.newFile("photo.jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -32)) }
    assertFalse(BrowserImageCompatibility.needsJpegPreview(jpeg))
    assertFalse(BrowserImageCompatibility.needsJpegPreview(files.newFile("empty")))
    val other = files.newFile("video.bin").apply { writeBytes(byteArrayOf(0, 0, 0, 24) + "ftypmp42".toByteArray() + ByteArray(12)) }
    assertFalse(BrowserImageCompatibility.needsJpegPreview(other))
  }
}
