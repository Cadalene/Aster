package com.example.dengbaoevidence.data

import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupArchiveWriter {
  fun export(
    projects: List<ProjectExportData>,
    templates: String,
    date: String,
    destination: File,
    workingDirectory: File,
    exportPackage: (ProjectExportData, File, (Int, String) -> Unit) -> Unit,
    exportWord: (ProjectExportData, File, (Int, String) -> Unit) -> Unit,
    beforeProject: (ProjectExportData) -> Unit = {},
    beforeWrite: (Int) -> Unit = {},
    onProgress: (Int, String) -> Unit,
  ) {
    check(projects.map { it.project.id }.distinct().size == projects.size) { "备份项目列表重复" }
    check(workingDirectory.mkdirs()) { "无法创建备份临时目录" }
    val entries = mutableSetOf<String>()
    try {
      ZipOutputStream(ExportFileIo.checkedOutput(destination.outputStream().buffered(), beforeWrite)).use { zip ->
        zip.setLevel(Deflater.NO_COMPRESSION)
        projects.forEachIndexed { index, data ->
          beforeProject(data)
          val projectDir = File(workingDirectory, "p_${data.project.id}")
          check(projectDir.mkdir()) { "无法创建项目临时目录" }
          val projectName = ExportFileIo.safeName(data.project.name)
          val archiveDir = "${projectName}_项目${data.project.id}"
          val packageFile = File(projectDir, "${projectName}_浏览包_$date.zip")
          val wordFile = File(projectDir, "${projectName}_关键设备截图_$date.docx")
          fun progress(value: Int, message: String) {
            val total = (index * 90.0 + value * .9) / projects.size.coerceAtLeast(1)
            onProgress(total.toInt(), "项目 ${index + 1}/${projects.size} · $message")
          }
          try {
            exportPackage(data, packageFile) { value, message -> progress(value * 40 / 100, message) }
            exportWord(data, wordFile) { value, message -> progress(40 + value * 30 / 100, message) }
            val files = listOf(packageFile to "$archiveDir/${packageFile.name}", wordFile to "$archiveDir/${wordFile.name}")
            val totalBytes = files.sumOf { (source, _) -> source.length() }.coerceAtLeast(1)
            var written = 0L
            var lastProgress = -1
            files.forEach { (source, entryName) ->
              check(source.isFile && source.length() > 0L) { "备份源文件不存在：${source.name}" }
              check(entries.add(entryName)) { "备份文件名称重复" }
              zip.putNextEntry(ZipEntry(entryName))
              try {
                source.inputStream().buffered().use { input ->
                  val buffer = ByteArray(64 * 1024)
                  while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    zip.write(buffer, 0, read)
                    written += read
                    val value = 70 + (written.toDouble() / totalBytes * 30).toInt().coerceIn(0, 30)
                    if (value != lastProgress) {
                      lastProgress = value
                      progress(value, "正在合并浏览包和 Word")
                    }
                  }
                }
              } finally {
                zip.closeEntry()
              }
            }
          } finally {
            // Release this project's ZIP and Word before starting the next project.
            check(projectDir.deleteRecursively()) { "无法释放当前项目临时文件，已停止备份" }
          }
        }
        val templateName = "设备类型模板_$date.json"
        entries.add(templateName)
        zip.putNextEntry(ZipEntry(templateName))
        zip.write(templates.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
      onProgress(90, "正在校验备份项目、模板和文件完整性")
      ExportFileIo.validateZip(destination, entries)
      onProgress(100, "备份已生成，准备保存")
    } catch (throwable: Throwable) {
      destination.delete()
      throw throwable
    } finally {
      workingDirectory.deleteRecursively()
    }
  }
}
