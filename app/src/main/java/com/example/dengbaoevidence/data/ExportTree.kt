package com.example.dengbaoevidence.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

fun persistExportDirectory(context: Context, uri: Uri) {
  val directory = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "无法读取保存目录" }
  check(directory.isDirectory && directory.canRead() && directory.canWrite()) { "目录没有读写权限" }
  context.contentResolver.takePersistableUriPermission(
    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
  )
  AppSettings.setExportRootTreeUri(context, uri)
}

fun createChildTree(context: Context, rootUri: Uri, childName: String): Uri? = runCatching {
  val root = DocumentFile.fromTreeUri(context, rootUri)?.takeIf { it.isDirectory && it.canWrite() }
    ?: return@runCatching null
  val existing = root.findFile(childName)
  check(existing == null || existing.isDirectory) { "同名文件占用了导出子目录" }
  (existing ?: root.createDirectory(childName))?.uri
}.getOrNull()

fun createFileTree(context: Context, treeUri: Uri, fileName: String, mimeType: String): Uri? = runCatching {
  val directory = DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory && it.canWrite() }
    ?: return@runCatching null
  val existingFiles = directory.listFiles()
  val name = ExportFileIo.uniqueName(fileName, existingFiles.mapNotNull { it.name }.toSet())
  val created = directory.createFile(mimeType, name) ?: return@runCatching null
  check(existingFiles.none { it.uri == created.uri }) { "存储提供器返回了已有文件，已取消写入" }
  created.uri
}.getOrNull()
