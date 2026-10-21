package com.example.dengbaoevidence.data

import java.io.File
import java.util.Locale

object BrowserImageCompatibility {
  fun needsJpegPreview(file: File): Boolean {
    if (file.extension.lowercase(Locale.ROOT) in setOf("heic", "heif", "hif")) return true
    val header = ByteArray(64)
    val count = file.inputStream().use { stream ->
      var size = 0
      while (size < header.size) {
        val read = stream.read(header, size, header.size - size)
        if (read < 0) break
        size += read
      }
      size
    }
    if (count < 12 || String(header, 4, 4, Charsets.US_ASCII) != "ftyp") return false
    return (8 until count - 3 step 4).filter { it != 12 }.any { offset ->
      String(header, offset, 4, Charsets.US_ASCII) in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif")
    }
  }
}
