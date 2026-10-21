package com.example.dengbaoevidence.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile

object ExportFileIo {
  fun dateSuffix(): String = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

  fun safeName(value: String, fallback: String = "项目"): String = value
    .replace(Regex("[\\u0000-\\u001f\\u007f\\\\/:*?\"<>|]"), "_")
    .trim().trimEnd('.', ' ').take(80).ifBlank { fallback }

  fun uniqueName(preferred: String, existing: Set<String>): String {
    val names = existing.map { it.lowercase(Locale.ROOT) }.toSet()
    val base = preferred.substringBeforeLast('.', preferred)
    val extension = preferred.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var candidate = preferred
    var suffix = 2
    while (candidate.lowercase(Locale.ROOT) in names) candidate = "${base}_${suffix++}$extension"
    return candidate
  }

  // Read the destination back: a successful stream close alone does not prove a complete export.
  fun copyAndVerify(
    source: File,
    openOutput: () -> OutputStream,
    openInput: () -> InputStream,
    beforeWrite: (Int) -> Unit = {},
    onProgress: (Int, String) -> Unit,
  ) {
    val expectedSize = source.length()
    check(source.isFile && expectedSize > 0L) { "导出文件为空" }
    val expectedHash = MessageDigest.getInstance("SHA-256")
    onProgress(0, "正在写入保存位置")
    var lastProgress = -1
    openOutput().buffered().use { output ->
      source.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          beforeWrite(read)
          output.write(buffer, 0, read)
          expectedHash.update(buffer, 0, read)
          written += read
          val progress = (written.toDouble() / expectedSize * 60).toInt().coerceIn(0, 60)
          if (progress != lastProgress) {
            lastProgress = progress
            onProgress(progress, "正在写入保存位置")
          }
        }
        check(written == expectedSize) { "导出文件在写入期间发生变化" }
      }
    }
    onProgress(60, "正在回读校验文件")
    val actualHash = MessageDigest.getInstance("SHA-256")
    var checked = 0L
    openInput().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        actualHash.update(buffer, 0, read)
        checked += read
        val progress = (60 + checked.toDouble() / expectedSize * 39).toInt().coerceIn(60, 99)
        if (progress != lastProgress) {
          lastProgress = progress
          onProgress(progress, "正在回读校验文件")
        }
      }
    }
    check(checked == expectedSize && expectedHash.digest().contentEquals(actualHash.digest())) {
      "保存文件完整性校验失败，请重新导出"
    }
    onProgress(100, "保存和校验已完成")
  }

  fun checkedOutput(output: OutputStream, beforeWrite: (Int) -> Unit): OutputStream = object : OutputStream() {
    override fun write(value: Int) {
      beforeWrite(1)
      output.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
      beforeWrite(length)
      output.write(buffer, offset, length)
    }

    override fun flush() = output.flush()
    override fun close() = output.close()
  }

  fun validateZip(file: File, expectedEntries: Set<String>) {
    ZipFile(file).use { zip ->
      val entries = zip.entries().asSequence().toList()
      check(entries.map { it.name }.toSet() == expectedEntries && entries.size == expectedEntries.size) {
        "备份文件条目不完整"
      }
      entries.forEach { entry ->
        val crc = CRC32()
        var size = 0L
        zip.getInputStream(entry).buffered().use { input ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
            size += read
          }
        }
        check(size == entry.size && crc.value == entry.crc) { "备份条目校验失败：${entry.name}" }
      }
    }
  }
}
