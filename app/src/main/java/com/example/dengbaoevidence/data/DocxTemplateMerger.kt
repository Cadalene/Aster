package com.example.dengbaoevidence.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Appends our generated body without replacing the template's styles or section properties. */
object DocxTemplateMerger {
  private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
  private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
  private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
  private const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
  private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
  private const val DOCUMENT = "word/document.xml"
  private const val STYLES = "word/styles.xml"
  private const val RELS = "word/_rels/document.xml.rels"
  private const val TYPES = "[Content_Types].xml"
  private const val XML_LIMIT = 8 * 1024 * 1024

  fun validate(file: File) {
    ZipFile(file).use { zip ->
      val entries = zip.entries().asSequence().toList()
      require(entries.size <= 4096 && entries.map { it.name }.distinct().size == entries.size) { "模板文件结构无效" }
      var total = 0L
      entries.filterNot { it.isDirectory }.forEach { entry ->
        require(entry.size in 0..64L * 1024 * 1024) { "模板解压后过大" }
        total = Math.addExact(total, entry.size)
        require(total <= 64L * 1024 * 1024) { "模板解压后过大" }
      }
      require(entries.none { it.name.contains("vbaProject", true) || it.name.startsWith("_xmlsignatures/") }) { "不支持含宏或数字签名的模板" }
      val document = read(zip, DOCUMENT)
      require(document.getElementsByTagNameNS(W, "body").length == 1) { "模板缺少正文" }
      read(zip, STYLES)
      read(zip, RELS)
      val types = read(zip, TYPES)
      require(types.elements(CT, "Override").any {
        it.getAttribute("PartName") == "/$DOCUMENT" &&
          it.getAttribute("ContentType") == "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
      }) { "请选择普通 DOCX 文件" }
      if (zip.getEntry("word/settings.xml") != null) {
        require(read(zip, "word/settings.xml").elements(W, "documentProtection").none {
          it.getAttributeNS(W, "enforcement").lowercase() in setOf("1", "true", "on")
        }) { "不支持限制编辑的 Word 模板" }
      }
    }
  }

  fun append(template: File, generated: File, destination: File, beforeWrite: (Int) -> Unit = {}) {
    require(destination.canonicalFile != template.canonicalFile && destination.canonicalFile != generated.canonicalFile) { "输出文件不能覆盖模板或输入文件" }
    validate(template)
    try {
      destination.parentFile?.mkdirs()
      ZipFile(template).use { base -> ZipFile(generated).use { extra ->
        val document = read(base, DOCUMENT)
        val styles = read(base, STYLES)
        val rels = read(base, RELS)
        val types = read(base, TYPES)
        val generatedDocument = read(extra, DOCUMENT)
        val token = "Aster${UUID.randomUUID().toString().replace("-", "")}_"
        val styleIds = read(extra, STYLES).elements(W, "style").associate { it.getAttributeNS(W, "styleId") to token + it.getAttributeNS(W, "styleId") }
        read(extra, STYLES).elements(W, "style").forEach { style ->
          val copy = styles.importNode(style, true) as Element
          copy.setAttributeNS(W, "w:styleId", styleIds.getValue(style.getAttributeNS(W, "styleId")))
          copy.removeAttributeNS(W, "default")
          (listOf(copy) + copy.descendants()).forEach { element ->
            if (element.localName in listOf("basedOn", "next", "link")) {
              styleIds[element.getAttributeNS(W, "val")]?.let { element.setAttributeNS(W, "w:val", it) }
            }
            if (element.namespaceURI == W && element.localName == "name") {
              element.setAttributeNS(W, "w:val", token + element.getAttributeNS(W, "val"))
            }
          }
          styles.documentElement.appendChild(copy)
        }
        val media = linkedMapOf<String, String>()
        val relationshipIds = mutableMapOf<String, String>()
        read(extra, RELS).elements(REL, "Relationship").filter { it.getAttribute("Type") == "$R/image" }.forEach { relation ->
          val oldTarget = relation.getAttribute("Target")
          require(oldTarget.startsWith("media/") && !oldTarget.contains("..")) { "生成图片路径无效" }
          val newTarget = "media/$token${oldTarget.substringAfterLast('/')}"
          media["word/$oldTarget"] = "word/$newTarget"
          val id = token + relation.getAttribute("Id")
          relationshipIds[relation.getAttribute("Id")] = id
          val copy = rels.importNode(relation, true) as Element
          copy.setAttribute("Id", id)
          copy.setAttribute("Target", newTarget)
          rels.documentElement.appendChild(copy)
          val contentType = types.createElementNS(CT, "Override")
          contentType.setAttribute("PartName", "/word/$newTarget")
          contentType.setAttribute("ContentType", when (newTarget.substringAfterLast('.')) { "png" -> "image/png"; "gif" -> "image/gif"; else -> "image/jpeg" })
          types.documentElement.appendChild(contentType)
        }
        val body = document.elements(W, "body").single()
        val section = body.children().lastOrNull { it.namespaceURI == W && it.localName == "sectPr" }
        val page = section?.descendants()?.firstOrNull { it.localName == "pgSz" }
        val margin = section?.descendants()?.firstOrNull { it.localName == "pgMar" }
        fun number(element: Element?, name: String, fallback: Long) = element?.getAttributeNS(W, name)?.toLongOrNull() ?: fallback
        val width = (number(page, "w", 11906) - number(margin, "left", 1440) - number(margin, "right", 1440) - number(margin, "gutter", 0)).coerceAtLeast(1) * 635
        val height = (number(page, "h", 16838) - number(margin, "top", 1440) - number(margin, "bottom", 1440) - 240).coerceAtLeast(1) * 635
        var drawingId = base.entries().asSequence().filter { it.name.startsWith("word/") && it.name.endsWith(".xml") }.flatMap { entry ->
          read(base, entry.name).elements(WP, "docPr").asSequence().mapNotNull { it.getAttribute("id").toLongOrNull() }
        }.maxOrNull() ?: 0L
        generatedDocument.elements(W, "body").single().children().filterNot { it.localName == "sectPr" }.forEach { node ->
          val copy = document.importNode(node, true) as Element
          if (copy.localName == "p" && copy.getElementsByTagNameNS(W, "pStyle").length == 0 && "Normal" in styleIds) {
            val properties = copy.children().firstOrNull { it.localName == "pPr" }
              ?: document.createElementNS(W, "w:pPr").also { copy.insertBefore(it, copy.firstChild) }
            properties.insertBefore(document.createElementNS(W, "w:pStyle").also {
              it.setAttributeNS(W, "w:val", "Normal")
            }, properties.firstChild)
          }
          (listOf(copy) + copy.descendants()).forEach { element ->
            if (element.namespaceURI == W && element.localName in listOf("pStyle", "rStyle")) {
              styleIds[element.getAttributeNS(W, "val")]?.let { element.setAttributeNS(W, "w:val", it) }
            }
            if (element.hasAttributeNS(R, "embed")) {
              element.setAttributeNS(R, "r:embed", relationshipIds.getValue(element.getAttributeNS(R, "embed")))
            }
            if (element.namespaceURI == WP && element.localName == "docPr") element.setAttribute("id", (++drawingId).toString())
            if (element.localName in listOf("extent", "ext") && element.namespaceURI in listOf(WP, A)) {
              val cx = element.getAttribute("cx").toLongOrNull()
              val cy = element.getAttribute("cy").toLongOrNull()
              if (cx != null && cy != null && cx > 0 && cy > 0) {
                val scale = minOf(1.0, width.toDouble() / cx, height.toDouble() / cy)
                element.setAttribute("cx", (cx * scale).toLong().coerceAtLeast(1).toString())
                element.setAttribute("cy", (cy * scale).toLong().coerceAtLeast(1).toString())
              }
            }
          }
          body.insertBefore(copy, section)
        }
        val replacements = mapOf(DOCUMENT to serialize(document), STYLES to serialize(styles), RELS to serialize(rels), TYPES to serialize(types))
        ZipOutputStream(ExportFileIo.checkedOutput(destination.outputStream().buffered(), beforeWrite)).use { output ->
          base.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
            output.putNextEntry(ZipEntry(entry.name))
            replacements[entry.name]?.let(output::write) ?: base.getInputStream(entry).use { it.copyTo(output) }
            output.closeEntry()
          }
          media.forEach { (source, target) ->
            output.putNextEntry(ZipEntry(target))
            extra.getInputStream(requireNotNull(extra.getEntry(source))).use { it.copyTo(output) }
            output.closeEntry()
          }
        }
        ZipFile(destination).use { result ->
          replacements.keys.forEach { read(result, it) }
          media.values.forEach { check(requireNotNull(result.getEntry(it)).size > 0) { "Word 图片写入不完整" } }
        }
      } }
    } catch (error: Throwable) {
      destination.delete()
      throw error
    }
  }

  private fun bytes(zip: ZipFile, name: String, required: Boolean = true): ByteArray {
    val entry = zip.getEntry(name)
    if (entry == null) { require(!required) { "模板缺少 $name" }; return byteArrayOf() }
    require(entry.size in 0..XML_LIMIT.toLong()) { "Word XML 文件过大" }
    return zip.getInputStream(entry).use { stream -> readBounded(stream) }
  }

  private fun readBounded(stream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = stream.read(buffer)
      if (count < 0) break
      require(output.size() + count <= XML_LIMIT) { "Word XML 文件过大" }
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  private fun read(zip: ZipFile, name: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = true
      // Android parsers may not expose these SAX features; DTDs are also rejected before parsing.
      for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities")) {
        try { setFeature(feature, false) } catch (_: javax.xml.parsers.ParserConfigurationException) { }
      }
    }
    val source = bytes(zip, name)
    // Removing NULs also catches UTF-16/32 DTDs before the XML parser can expand entities.
    require(!String(source, Charsets.ISO_8859_1).replace("\u0000", "").contains("<!DOCTYPE", ignoreCase = true)) { "不支持含 DTD 的模板" }
    return factory.newDocumentBuilder().apply {
      setEntityResolver { _, _ -> throw IllegalArgumentException("不支持外部 XML 实体") }
    }.parse(ByteArrayInputStream(source)).also { require(it.doctype == null) { "不支持含 DTD 的模板" } }
  }

  private fun serialize(document: Document): ByteArray = ByteArrayOutputStream().also { stream ->
    TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(stream))
  }.toByteArray()

  private fun Document.elements(namespace: String, localName: String): List<Element> =
    getElementsByTagNameNS(namespace, localName).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

  private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
  private fun Element.descendants(): List<Element> = getElementsByTagName("*").let { nodes ->
    (0 until nodes.length).map { nodes.item(it) as Element }
  }
}
