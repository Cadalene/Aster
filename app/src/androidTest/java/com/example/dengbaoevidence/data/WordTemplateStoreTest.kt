package com.example.dengbaoevidence.data

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class WordTemplateStoreTest {
  @Test fun importsCopiesRetainsOnFailedReplacementRecoversAndRemovesTemplate() {
    val app = ApplicationProvider.getApplicationContext<Context>()
    val directory = File(app.filesDir, "originals/word_template_test_${System.nanoTime()}").apply { mkdirs() }
    val prefix = directory.name
    val context = object : ContextWrapper(app) {
      override fun getFilesDir() = directory
      override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
      override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("${prefix}_$name", mode)
    }
    fun uri(file: File) = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    try {
      assertNull(WordTemplateStore.name(context))
      val source = File(directory, "source.docx")
      WordExporter.export(context, ProjectExportData(ProjectEntity(1, "Original template content", 123), emptyList()), source)
      val original = source.readBytes()
      WordTemplateStore.import(context, uri(source))
      val imported = requireNotNull(WordTemplateStore.snapshot(context))
      assertArrayEquals(original, imported.readBytes())
      imported.delete()
      val invalid = File(directory, "invalid.docx").apply { writeText("not a Word file") }
      assertTrue(runCatching { WordTemplateStore.import(context, uri(invalid)) }.isFailure)
      assertEquals("source.docx", WordTemplateStore.name(context))
      val stored = File(directory, "word-export-template.docx")
      assertArrayEquals(original, stored.readBytes())
      val appended = File(directory, "appended.docx")
      WordExporter.export(context, ProjectExportData(ProjectEntity(2, "Appended project", 456), emptyList()), appended)
      ZipFile(appended).use { zip ->
        val content = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().use { it.readText() }
        assertTrue(content.contains("Original template content"))
        assertTrue(content.contains("Appended project"))
      }
      assertArrayEquals(original, source.readBytes())
      assertTrue(stored.renameTo(File(stored.path + ".bak")))
      assertNotNull(WordTemplateStore.name(context))
      requireNotNull(WordTemplateStore.snapshot(context)).also { assertArrayEquals(original, it.readBytes()); it.delete() }
      WordTemplateStore.remove(context)
      assertNull(WordTemplateStore.snapshot(context))
      assertArrayEquals(original, source.readBytes())
      val restoredDefault = File(directory, "default.docx")
      WordExporter.export(context, ProjectExportData(ProjectEntity(3, "Default project", 789), emptyList()), restoredDefault)
      ZipFile(restoredDefault).use { zip ->
        val content = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().use { it.readText() }
        assertFalse(content.contains("Original template content"))
        assertTrue(content.contains("Default project"))
      }
    } finally {
      directory.deleteRecursively()
      app.deleteSharedPreferences("${prefix}_app_settings")
    }
  }
}
