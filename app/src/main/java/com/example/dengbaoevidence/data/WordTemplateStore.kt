package com.example.dengbaoevidence.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File

object WordTemplateStore {
  private fun storage(context: Context) = AtomicFile(File(context.filesDir, "word-export-template.docx"))
  private fun exists(target: AtomicFile) = target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()

  @Synchronized fun name(context: Context): String? =
    if (exists(storage(context))) context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
      .getString("word_template_name", "Word 导出模板") else null

  @Synchronized fun import(context: Context, uri: Uri): String {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    } ?: "Word 导出模板.docx"
    require(name.endsWith(".docx", true)) { "只支持 DOCX 模板" }
    val staging = File.createTempFile("word_template_", ".docx", context.cacheDir)
    try {
      requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取模板" }.use { input ->
        staging.outputStream().use { output ->
          val buffer = ByteArray(8192)
          var size = 0L
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            require(size <= 20L * 1024 * 1024) { "模板文件请控制在 20 MB 以内" }
            output.write(buffer, 0, count)
          }
        }
      }
      DocxTemplateMerger.validate(staging)
      val target = storage(context)
      val stream = target.startWrite()
      try {
        staging.inputStream().use { it.copyTo(stream) }
        target.finishWrite(stream)
      } catch (error: Throwable) { target.failWrite(stream); throw error }
      context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("word_template_name", name).apply()
      return name
    } finally { staging.delete() }
  }

  @Synchronized fun remove(context: Context) { storage(context).delete() }

  @Synchronized fun snapshot(context: Context): File? {
    val target = storage(context)
    if (!exists(target)) return null
    val copy = File.createTempFile("word_template_snapshot_", ".docx", context.cacheDir)
    try {
      target.openRead().use { input -> copy.outputStream().use { input.copyTo(it) } }
      return copy
    } catch (error: Throwable) { copy.delete(); throw error }
  }
}
