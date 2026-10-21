package com.example.dengbaoevidence.data

import java.io.File
import java.io.IOException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element

class DocxTemplateMergerTest {
  @get:Rule val files = TemporaryFolder()
  private val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
  private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  private val wp = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
  private val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
  private val rel = "http://schemas.openxmlformats.org/package/2006/relationships"
  private val ct = "http://schemas.openxmlformats.org/package/2006/content-types"
  private val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

  private fun create(name: String, title: String, extra: Map<String, ByteArray> = emptyMap()): File {
    val parts = linkedMapOf(
      "[Content_Types].xml" to """<Types xmlns="$ct"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""".toByteArray(),
      "_rels/.rels" to """<Relationships xmlns="$rel"><Relationship Id="main" Type="$r/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray(),
      "word/document.xml" to """<w:document xmlns:w="$w" xmlns:r="$r" xmlns:wp="$wp" xmlns:a="$a" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>$title</w:t></w:r></w:p><w:p><w:r><w:drawing><wp:inline><wp:extent cx="12000000" cy="4000000"/><wp:docPr id="1" name="picture"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="picture"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="image"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="12000000" cy="4000000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:left="1797" w:right="1797" w:top="1440" w:bottom="1440"/></w:sectPr></w:body></w:document>""".toByteArray(),
      "word/styles.xml" to """<w:styles xmlns:w="$w"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:rPr><w:color w:val="000000"/><w:sz w:val="44"/></w:rPr></w:style></w:styles>""".toByteArray(),
      "word/_rels/document.xml.rels" to """<Relationships xmlns="$rel"><Relationship Id="style" Type="$r/styles" Target="styles.xml"/><Relationship Id="image" Type="$r/image" Target="media/image1.png"/></Relationships>""".toByteArray(),
      "word/media/image1.png" to png,
    )
    parts.putAll(extra)
    return files.newFile(name).also { file -> ZipOutputStream(file.outputStream()).use { zip ->
      parts.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
    } }
  }

  private fun bytes(file: File, path: String) = ZipFile(file).use { it.getInputStream(requireNotNull(it.getEntry(path))).use { stream -> stream.readBytes() } }
  private fun xml(file: File, path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(bytes(file, path).inputStream())

  @Test fun preservesTemplatePartsAndAppendsWithUniqueRelationshipsStylesAndSizedPictures() {
    val header = """<w:hdr xmlns:w="$w"><w:p><w:r><w:t>Original header</w:t></w:r></w:p></w:hdr>""".toByteArray()
    val base = create("base.docx", "Original content", mapOf("word/header1.xml" to header, "customXml/item1.xml" to "<root>KEEP</root>".toByteArray()))
    val before = base.readBytes()
    val generated = create("extra.docx", "Project evidence")
    val output = File(files.root, "sub/output.docx")
    DocxTemplateMerger.append(base, generated, output)
    assertArrayEquals(before, base.readBytes())
    assertArrayEquals(header, bytes(output, "word/header1.xml"))
    assertArrayEquals(bytes(base, "customXml/item1.xml"), bytes(output, "customXml/item1.xml"))
    assertArrayEquals(png, bytes(output, "word/media/image1.png"))
    val doc = xml(output, "word/document.xml")
    val body = doc.getElementsByTagNameNS(w, "body").item(0)
    assertEquals("sectPr", body.lastChild.localName)
    val text = doc.getElementsByTagNameNS(w, "t")
    assertEquals("Original content", text.item(0).textContent)
    assertEquals("Project evidence", text.item(1).textContent)
    val styles = doc.getElementsByTagNameNS(w, "pStyle")
    assertEquals("Heading1", (styles.item(0) as Element).getAttributeNS(w, "val"))
    val importedStyle = (styles.item(1) as Element).getAttributeNS(w, "val")
    assertTrue(importedStyle.startsWith("Aster"))
    assertTrue(importedStyle.endsWith("Heading1"))
    val pictures = doc.getElementsByTagNameNS(wp, "docPr")
    assertEquals("1", (pictures.item(0) as Element).getAttribute("id"))
    assertEquals("2", (pictures.item(1) as Element).getAttribute("id"))
    val extent = doc.getElementsByTagNameNS(wp, "extent").item(1) as Element
    assertEquals((11906L - 1797 - 1797) * 635, extent.getAttribute("cx").toLong())
    val relations = xml(output, "word/_rels/document.xml.rels").getElementsByTagNameNS(rel, "Relationship")
    val ids = (0 until relations.length).map { (relations.item(it) as Element).getAttribute("Id") }
    assertEquals(ids.size, ids.distinct().size)
    val blip = doc.getElementsByTagNameNS(a, "blip").item(1) as Element
    assertTrue(blip.getAttributeNS(r, "embed") in ids)
    ZipFile(output).use { zip -> assertEquals(2, zip.entries().asSequence().count { it.name.startsWith("word/media/") }) }
  }

  @Test fun failureDoesNotDamageTemplateAndRemovesPartialOutput() {
    val base = create("base.docx", "Original")
    val before = base.readBytes()
    val generated = create("extra.docx", "Append")
    val output = File(files.root, "failed.docx")
    assertTrue(runCatching { DocxTemplateMerger.append(base, generated, output) { throw IOException("full") } }.isFailure)
    assertFalse(output.exists())
    assertArrayEquals(before, base.readBytes())
    assertTrue(runCatching { DocxTemplateMerger.append(base, generated, base) }.isFailure)
    assertArrayEquals(before, base.readBytes())
  }

  @Test fun rejectsUtf16DtdAndProtectedAndInvalidTemplates() {
    val malicious = """<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE document [<!ENTITY x "expanded">]><w:document xmlns:w="$w"><w:body><w:p>&x;</w:p></w:body></w:document>""".toByteArray(Charsets.UTF_16)
    val dtd = create("dtd.docx", "", mapOf("word/document.xml" to malicious))
    assertTrue(runCatching { DocxTemplateMerger.validate(dtd) }.exceptionOrNull()?.message.orEmpty().contains("DTD"))
    val protected = create("protected.docx", "", mapOf("word/settings.xml" to """<w:settings xmlns:w="$w"><w:documentProtection w:enforcement="1"/></w:settings>""".toByteArray()))
    assertTrue(runCatching { DocxTemplateMerger.validate(protected) }.isFailure)
    val notProtected = create("unprotected.docx", "", mapOf("word/settings.xml" to """<w:settings xmlns:w="$w"><w:documentProtection w:enforcement="0"/></w:settings>""".toByteArray()))
    DocxTemplateMerger.validate(notProtected)
    assertTrue(runCatching { DocxTemplateMerger.validate(files.newFile("invalid.docx")) }.isFailure)
  }

  @Test fun userSuppliedTemplateCanBeAppendedWithoutChangingUntouchedParts() {
    val path = System.getenv("ASTER_WORD_TEMPLATE_FIXTURE") ?: return
    val base = File(path)
    val generated = create("sample_body.docx", "Aster export verification")
    val output = File("build/reports/word-template/merged-sample.docx").absoluteFile
    DocxTemplateMerger.append(base, generated, output)
    ZipFile(base).use { source -> ZipFile(output).use { result ->
      source.entries().asSequence().filterNot { it.isDirectory || it.name in setOf("word/document.xml", "word/styles.xml", "word/_rels/document.xml.rels", "[Content_Types].xml") }.forEach {
        assertArrayEquals(it.name, source.getInputStream(it).use { stream -> stream.readBytes() }, result.getInputStream(requireNotNull(result.getEntry(it.name))).use { stream -> stream.readBytes() })
      }
    } }
    assertTrue(xml(output, "word/document.xml").documentElement.textContent.contains("Aster export verification"))
  }
}
