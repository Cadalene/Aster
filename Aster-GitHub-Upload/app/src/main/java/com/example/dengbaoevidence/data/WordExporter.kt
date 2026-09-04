package com.example.dengbaoevidence.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer

data class WordExportSummary(
  val deviceCount: Int,
  val itemCount: Int,
  val photoCount: Int,
)

object WordExporter {
  fun export(context: Context, data: ProjectExportData, destination: File): WordExportSummary {
    destination.parentFile?.mkdirs()
    val plan = buildPlan(context, data)
    try {
      ZipOutputStream(destination.outputStream().buffered()).use { zip ->
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        writeContentTypes(zip, plan.photos)
        writePackageRelationships(zip)
        writeCoreProperties(zip, data.project)
        writeAppProperties(zip)
        writeStyles(zip)
        writeDocumentRelationships(zip, plan.photos)
        writeDocument(zip, data.project.name, plan.devices)
        plan.photos.forEach { photo ->
          zip.putNextEntry(ZipEntry("word/media/${photo.mediaName}"))
          photo.file.inputStream().buffered().use { it.copyTo(zip) }
          zip.closeEntry()
        }
      }
      validateDocument(destination, plan.photos.size)
      return WordExportSummary(
        deviceCount = plan.devices.size,
        itemCount = plan.devices.sumOf { it.items.size },
        photoCount = plan.photos.size,
      )
    } catch (throwable: Throwable) {
      destination.delete()
      throw throwable
    } finally {
      plan.temporaryFiles.forEach(File::delete)
    }
  }

  private fun buildPlan(context: Context, data: ProjectExportData): ExportPlan {
    val temporaryFiles = mutableListOf<File>()
    var imageIndex = 0
    val devices = data.devices.mapNotNull { device ->
      val items = device.items
        .asSequence()
        .filter { it.item.isKeyScreenshot }
        .sortedWith(compareBy<EvidenceItemWithPhotos> { it.item.wordOrder ?: Int.MAX_VALUE }.thenBy { it.item.displayOrder })
        .mapNotNull { item ->
          val photos = item.photos.sortedBy { it.sortOrder }.mapNotNull { photo ->
            val source = File(photo.path).takeIf { it.isFile && it.length() > 0L } ?: return@mapNotNull null
            imageIndex += 1
            prepareImage(context, source, imageIndex).also {
              if (it.temporary) temporaryFiles += it.file
            }
          }
          photos.takeIf { it.isNotEmpty() }?.let { ExportItem(item.item.name, it) }
        }
        .toList()
      items.takeIf { it.isNotEmpty() }?.let { ExportDevice(device.device.name, it) }
    }
    return ExportPlan(devices, devices.flatMap { it.items }.flatMap { it.photos }, temporaryFiles)
  }

  private fun prepareImage(context: Context, source: File, index: Int): ExportPhoto {
    val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.absolutePath, bounds)
    val mimeType = bounds.outMimeType
    val orientation = readOrientation(source)
    val canEmbedOriginal = orientation == ExifInterface.ORIENTATION_NORMAL && mimeType in EMBEDDABLE_IMAGE_TYPES

    if (canEmbedOriginal && bounds.outWidth > 0 && bounds.outHeight > 0) {
      val extension = when (mimeType) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        else -> "jpg"
      }
      return ExportPhoto(
        file = source,
        mediaName = "image$index.$extension",
        contentType = requireNotNull(mimeType),
        relationshipId = "rIdImage$index",
        widthPx = bounds.outWidth,
        heightPx = bounds.outHeight,
        temporary = false,
      )
    }

    val output = File(context.cacheDir, "word_image_${UUID.randomUUID()}.jpg")
    val bitmap = decodeForDocument(source)
    try {
      val opaqueBitmap = if (bitmap.hasAlpha()) {
        createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { target ->
          Canvas(target).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
          }
        }
      } else {
        bitmap
      }
      try {
        output.outputStream().buffered().use { stream ->
          check(opaqueBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "图片转换失败" }
        }
        check(output.isFile && output.length() > 0L) { "图片转换失败" }
        return ExportPhoto(
          file = output,
          mediaName = "image$index.jpg",
          contentType = "image/jpeg",
          relationshipId = "rIdImage$index",
          widthPx = opaqueBitmap.width,
          heightPx = opaqueBitmap.height,
          temporary = true,
        )
      } finally {
        if (opaqueBitmap !== bitmap) opaqueBitmap.recycle()
      }
    } catch (throwable: Throwable) {
      output.delete()
      throw throwable
    } finally {
      bitmap.recycle()
    }
  }

  private fun decodeForDocument(file: File): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val target = fitWithin(info.size.width, info.size.height, MAX_RASTER_EDGE_PX)
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setTargetSize(target.first, target.second)
      }
    } else {
      val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
      var sampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_RASTER_EDGE_PX) sampleSize *= 2
      val decoded = requireNotNull(
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().also { it.inSampleSize = sampleSize }),
      ) { "无法读取图片" }
      rotateBitmap(decoded, readOrientation(file))
    }

  private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.setRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.setRotate(-90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
      else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
      if (it !== bitmap) bitmap.recycle()
    }
  }

  private fun readOrientation(file: File): Int = runCatching {
    ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
  }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

  private fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    val largest = maxOf(width, height)
    if (largest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / largest
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
  }

  private fun writeContentTypes(zip: ZipOutputStream, photos: List<ExportPhoto>) =
    writeXmlEntry(zip, "[Content_Types].xml") { xml ->
      xml.setPrefix("", CONTENT_TYPES_NS)
      xml.startTag(CONTENT_TYPES_NS, "Types")
      writeDefaultType(xml, "rels", "application/vnd.openxmlformats-package.relationships+xml")
      writeDefaultType(xml, "xml", "application/xml")
      photos.distinctBy { it.mediaName.substringAfterLast('.') }.forEach {
        writeDefaultType(xml, it.mediaName.substringAfterLast('.'), it.contentType)
      }
      writeOverrideType(xml, "/word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")
      writeOverrideType(xml, "/word/styles.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml")
      writeOverrideType(xml, "/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml")
      writeOverrideType(xml, "/docProps/app.xml", "application/vnd.openxmlformats-officedocument.extended-properties+xml")
      xml.endTag(CONTENT_TYPES_NS, "Types")
    }

  private fun writeDefaultType(xml: XmlSerializer, extension: String, contentType: String) {
    xml.startTag(CONTENT_TYPES_NS, "Default")
    xml.attribute(null, "Extension", extension)
    xml.attribute(null, "ContentType", contentType)
    xml.endTag(CONTENT_TYPES_NS, "Default")
  }

  private fun writeOverrideType(xml: XmlSerializer, partName: String, contentType: String) {
    xml.startTag(CONTENT_TYPES_NS, "Override")
    xml.attribute(null, "PartName", partName)
    xml.attribute(null, "ContentType", contentType)
    xml.endTag(CONTENT_TYPES_NS, "Override")
  }

  private fun writePackageRelationships(zip: ZipOutputStream) =
    writeXmlEntry(zip, "_rels/.rels") { xml ->
      xml.setPrefix("", PACKAGE_RELATIONSHIPS_NS)
      xml.startTag(PACKAGE_RELATIONSHIPS_NS, "Relationships")
      writeRelationship(xml, "rId1", "$OFFICE_RELATIONSHIPS_NS/officeDocument", "word/document.xml")
      writeRelationship(xml, "rId2", "$PACKAGE_RELATIONSHIPS_NS/metadata/core-properties", "docProps/core.xml")
      writeRelationship(xml, "rId3", "$OFFICE_RELATIONSHIPS_NS/extended-properties", "docProps/app.xml")
      xml.endTag(PACKAGE_RELATIONSHIPS_NS, "Relationships")
    }

  private fun writeDocumentRelationships(zip: ZipOutputStream, photos: List<ExportPhoto>) =
    writeXmlEntry(zip, "word/_rels/document.xml.rels") { xml ->
      xml.setPrefix("", PACKAGE_RELATIONSHIPS_NS)
      xml.startTag(PACKAGE_RELATIONSHIPS_NS, "Relationships")
      writeRelationship(xml, "rIdStyles", "$OFFICE_RELATIONSHIPS_NS/styles", "styles.xml")
      photos.forEach {
        writeRelationship(xml, it.relationshipId, "$OFFICE_RELATIONSHIPS_NS/image", "media/${it.mediaName}")
      }
      xml.endTag(PACKAGE_RELATIONSHIPS_NS, "Relationships")
    }

  private fun writeRelationship(xml: XmlSerializer, id: String, type: String, target: String) {
    xml.startTag(PACKAGE_RELATIONSHIPS_NS, "Relationship")
    xml.attribute(null, "Id", id)
    xml.attribute(null, "Type", type)
    xml.attribute(null, "Target", target)
    xml.endTag(PACKAGE_RELATIONSHIPS_NS, "Relationship")
  }

  private fun writeCoreProperties(zip: ZipOutputStream, project: ProjectEntity) =
    writeXmlEntry(zip, "docProps/core.xml") { xml ->
      xml.setPrefix("cp", CORE_PROPERTIES_NS)
      xml.setPrefix("dc", DC_NS)
      xml.setPrefix("dcterms", DCTERMS_NS)
      xml.setPrefix("xsi", XSI_NS)
      xml.startTag(CORE_PROPERTIES_NS, "coreProperties")
      writeTextElement(xml, DC_NS, "title", "${project.name}关键设备截图")
      writeTextElement(xml, DC_NS, "creator", "等保现场取证")
      xml.startTag(DCTERMS_NS, "created")
      xml.attribute(XSI_NS, "type", "dcterms:W3CDTF")
      xml.text(Instant.ofEpochMilli(project.createdAt).toString())
      xml.endTag(DCTERMS_NS, "created")
      xml.endTag(CORE_PROPERTIES_NS, "coreProperties")
    }

  private fun writeAppProperties(zip: ZipOutputStream) =
    writeXmlEntry(zip, "docProps/app.xml") { xml ->
      xml.setPrefix("", EXTENDED_PROPERTIES_NS)
      xml.setPrefix("vt", VT_NS)
      xml.startTag(EXTENDED_PROPERTIES_NS, "Properties")
      writeTextElement(xml, EXTENDED_PROPERTIES_NS, "Application", "等保现场取证")
      writeTextElement(xml, EXTENDED_PROPERTIES_NS, "AppVersion", "1.0")
      xml.endTag(EXTENDED_PROPERTIES_NS, "Properties")
    }

  private fun writeStyles(zip: ZipOutputStream) =
    writeXmlEntry(zip, "word/styles.xml") { xml ->
      xml.setPrefix("w", WORD_NS)
      xml.startTag(WORD_NS, "styles")
      xml.startTag(WORD_NS, "docDefaults")
      xml.startTag(WORD_NS, "rPrDefault")
      xml.startTag(WORD_NS, "rPr")
      writeRunFonts(xml)
      writeValElement(xml, "sz", "22")
      writeValElement(xml, "szCs", "22")
      xml.startTag(WORD_NS, "lang")
      xml.attribute(WORD_NS, "val", "zh-CN")
      xml.attribute(WORD_NS, "eastAsia", "zh-CN")
      xml.endTag(WORD_NS, "lang")
      xml.endTag(WORD_NS, "rPr")
      xml.endTag(WORD_NS, "rPrDefault")
      xml.startTag(WORD_NS, "pPrDefault")
      xml.startTag(WORD_NS, "pPr")
      writeSpacing(xml, before = 0, after = 120, line = 264)
      xml.endTag(WORD_NS, "pPr")
      xml.endTag(WORD_NS, "pPrDefault")
      xml.endTag(WORD_NS, "docDefaults")
      writeNormalStyle(xml)
      writeHeadingStyle(xml, "Heading1", "heading 1", 44, "000000", 320, 160, 0)
      writeHeadingStyle(xml, "Heading2", "heading 2", 36, "000000", 240, 120, 1)
      writeHeadingStyle(xml, "Heading3", "heading 3", 32, "000000", 160, 80, 2)
      xml.endTag(WORD_NS, "styles")
    }

  private fun writeNormalStyle(xml: XmlSerializer) {
    xml.startTag(WORD_NS, "style")
    xml.attribute(WORD_NS, "type", "paragraph")
    xml.attribute(WORD_NS, "default", "1")
    xml.attribute(WORD_NS, "styleId", "Normal")
    writeValElement(xml, "name", "Normal")
    writeValElement(xml, "qFormat", null)
    xml.startTag(WORD_NS, "pPr")
    writeSpacing(xml, before = 0, after = 120, line = 264)
    xml.endTag(WORD_NS, "pPr")
    xml.startTag(WORD_NS, "rPr")
    writeRunFonts(xml)
    writeValElement(xml, "sz", "22")
    writeValElement(xml, "szCs", "22")
    xml.endTag(WORD_NS, "rPr")
    xml.endTag(WORD_NS, "style")
  }

  private fun writeHeadingStyle(
    xml: XmlSerializer,
    styleId: String,
    name: String,
    size: Int,
    color: String,
    before: Int,
    after: Int,
    outlineLevel: Int,
  ) {
    xml.startTag(WORD_NS, "style")
    xml.attribute(WORD_NS, "type", "paragraph")
    xml.attribute(WORD_NS, "styleId", styleId)
    writeValElement(xml, "name", name)
    writeValElement(xml, "basedOn", "Normal")
    writeValElement(xml, "next", "Normal")
    writeValElement(xml, "uiPriority", (outlineLevel + 1).toString())
    writeValElement(xml, "qFormat", null)
    xml.startTag(WORD_NS, "pPr")
    writeValElement(xml, "keepNext", null)
    writeValElement(xml, "keepLines", null)
    writeSpacing(xml, before, after, line = 264)
    writeValElement(xml, "outlineLvl", outlineLevel.toString())
    xml.endTag(WORD_NS, "pPr")
    xml.startTag(WORD_NS, "rPr")
    writeRunFonts(xml)
    writeValElement(xml, "b", null)
    writeValElement(xml, "color", color)
    writeValElement(xml, "sz", size.toString())
    writeValElement(xml, "szCs", size.toString())
    xml.endTag(WORD_NS, "rPr")
    xml.endTag(WORD_NS, "style")
  }

  private fun writeRunFonts(xml: XmlSerializer) {
    xml.startTag(WORD_NS, "rFonts")
    xml.attribute(WORD_NS, "ascii", "仿宋")
    xml.attribute(WORD_NS, "hAnsi", "仿宋")
    xml.attribute(WORD_NS, "eastAsia", "仿宋")
    xml.attribute(WORD_NS, "cs", "仿宋")
    xml.endTag(WORD_NS, "rFonts")
  }

  private fun writeSpacing(xml: XmlSerializer, before: Int, after: Int, line: Int) {
    xml.startTag(WORD_NS, "spacing")
    xml.attribute(WORD_NS, "before", before.toString())
    xml.attribute(WORD_NS, "after", after.toString())
    xml.attribute(WORD_NS, "line", line.toString())
    xml.attribute(WORD_NS, "lineRule", "auto")
    xml.endTag(WORD_NS, "spacing")
  }

  private fun writeValElement(xml: XmlSerializer, name: String, value: String?) {
    xml.startTag(WORD_NS, name)
    value?.let { xml.attribute(WORD_NS, "val", it) }
    xml.endTag(WORD_NS, name)
  }

  private fun writeDocument(zip: ZipOutputStream, projectName: String, devices: List<ExportDevice>) =
    writeXmlEntry(zip, "word/document.xml") { xml ->
      xml.setPrefix("w", WORD_NS)
      xml.setPrefix("r", OFFICE_RELATIONSHIPS_NS)
      xml.setPrefix("wp", WORD_PROCESSING_DRAWING_NS)
      xml.setPrefix("a", DRAWING_NS)
      xml.setPrefix("pic", PICTURE_NS)
      xml.startTag(WORD_NS, "document")
      xml.startTag(WORD_NS, "body")
      writeHeading(xml, "Heading1", projectName)
      var drawingId = 1
      devices.forEach { device ->
        writeHeading(xml, "Heading2", device.name)
        device.items.forEach { item ->
          writeHeading(xml, "Heading3", item.name)
          item.photos.forEach { photo ->
            writeImageParagraph(xml, photo, drawingId++)
          }
        }
      }
      writeSectionProperties(xml)
      xml.endTag(WORD_NS, "body")
      xml.endTag(WORD_NS, "document")
    }

  private fun writeHeading(xml: XmlSerializer, styleId: String, text: String) {
    xml.startTag(WORD_NS, "p")
    xml.startTag(WORD_NS, "pPr")
    writeValElement(xml, "pStyle", styleId)
    xml.endTag(WORD_NS, "pPr")
    xml.startTag(WORD_NS, "r")
    writeTextElement(xml, WORD_NS, "t", text)
    xml.endTag(WORD_NS, "r")
    xml.endTag(WORD_NS, "p")
  }

  private fun writeImageParagraph(xml: XmlSerializer, photo: ExportPhoto, drawingId: Int) {
    val extent = imageExtent(photo.widthPx, photo.heightPx)
    xml.startTag(WORD_NS, "p")
    xml.startTag(WORD_NS, "pPr")
    writeValElement(xml, "keepLines", null)
    writeSpacing(xml, before = 0, after = 120, line = 240)
    xml.endTag(WORD_NS, "pPr")
    xml.startTag(WORD_NS, "r")
    xml.startTag(WORD_NS, "drawing")
    xml.startTag(WORD_PROCESSING_DRAWING_NS, "inline")
    listOf("distT", "distB", "distL", "distR").forEach { xml.attribute(null, it, "0") }
    xml.startTag(WORD_PROCESSING_DRAWING_NS, "extent")
    xml.attribute(null, "cx", extent.first.toString())
    xml.attribute(null, "cy", extent.second.toString())
    xml.endTag(WORD_PROCESSING_DRAWING_NS, "extent")
    xml.startTag(WORD_PROCESSING_DRAWING_NS, "docPr")
    xml.attribute(null, "id", drawingId.toString())
    xml.attribute(null, "name", "取证照片$drawingId")
    xml.attribute(null, "descr", "关键设备截图")
    xml.endTag(WORD_PROCESSING_DRAWING_NS, "docPr")
    xml.startTag(WORD_PROCESSING_DRAWING_NS, "cNvGraphicFramePr")
    xml.startTag(DRAWING_NS, "graphicFrameLocks")
    xml.attribute(null, "noChangeAspect", "1")
    xml.endTag(DRAWING_NS, "graphicFrameLocks")
    xml.endTag(WORD_PROCESSING_DRAWING_NS, "cNvGraphicFramePr")
    xml.startTag(DRAWING_NS, "graphic")
    xml.startTag(DRAWING_NS, "graphicData")
    xml.attribute(null, "uri", PICTURE_NS)
    xml.startTag(PICTURE_NS, "pic")
    xml.startTag(PICTURE_NS, "nvPicPr")
    xml.startTag(PICTURE_NS, "cNvPr")
    xml.attribute(null, "id", "0")
    xml.attribute(null, "name", photo.mediaName)
    xml.endTag(PICTURE_NS, "cNvPr")
    xml.startTag(PICTURE_NS, "cNvPicPr")
    xml.endTag(PICTURE_NS, "cNvPicPr")
    xml.endTag(PICTURE_NS, "nvPicPr")
    xml.startTag(PICTURE_NS, "blipFill")
    xml.startTag(DRAWING_NS, "blip")
    xml.attribute(OFFICE_RELATIONSHIPS_NS, "embed", photo.relationshipId)
    xml.endTag(DRAWING_NS, "blip")
    xml.startTag(DRAWING_NS, "stretch")
    xml.startTag(DRAWING_NS, "fillRect")
    xml.endTag(DRAWING_NS, "fillRect")
    xml.endTag(DRAWING_NS, "stretch")
    xml.endTag(PICTURE_NS, "blipFill")
    xml.startTag(PICTURE_NS, "spPr")
    xml.startTag(DRAWING_NS, "xfrm")
    xml.startTag(DRAWING_NS, "off")
    xml.attribute(null, "x", "0")
    xml.attribute(null, "y", "0")
    xml.endTag(DRAWING_NS, "off")
    xml.startTag(DRAWING_NS, "ext")
    xml.attribute(null, "cx", extent.first.toString())
    xml.attribute(null, "cy", extent.second.toString())
    xml.endTag(DRAWING_NS, "ext")
    xml.endTag(DRAWING_NS, "xfrm")
    xml.startTag(DRAWING_NS, "prstGeom")
    xml.attribute(null, "prst", "rect")
    xml.startTag(DRAWING_NS, "avLst")
    xml.endTag(DRAWING_NS, "avLst")
    xml.endTag(DRAWING_NS, "prstGeom")
    xml.endTag(PICTURE_NS, "spPr")
    xml.endTag(PICTURE_NS, "pic")
    xml.endTag(DRAWING_NS, "graphicData")
    xml.endTag(DRAWING_NS, "graphic")
    xml.endTag(WORD_PROCESSING_DRAWING_NS, "inline")
    xml.endTag(WORD_NS, "drawing")
    xml.endTag(WORD_NS, "r")
    xml.endTag(WORD_NS, "p")
  }

  private fun writeSectionProperties(xml: XmlSerializer) {
    xml.startTag(WORD_NS, "sectPr")
    xml.startTag(WORD_NS, "pgSz")
    xml.attribute(WORD_NS, "w", "11906")
    xml.attribute(WORD_NS, "h", "16838")
    xml.endTag(WORD_NS, "pgSz")
    xml.startTag(WORD_NS, "pgMar")
    xml.attribute(WORD_NS, "top", "1440")
    xml.attribute(WORD_NS, "right", "1440")
    xml.attribute(WORD_NS, "bottom", "1440")
    xml.attribute(WORD_NS, "left", "1440")
    xml.attribute(WORD_NS, "header", "708")
    xml.attribute(WORD_NS, "footer", "708")
    xml.attribute(WORD_NS, "gutter", "0")
    xml.endTag(WORD_NS, "pgMar")
    xml.endTag(WORD_NS, "sectPr")
  }

  private fun imageExtent(widthPx: Int, heightPx: Int): Pair<Long, Long> {
    val naturalWidth = widthPx / DEFAULT_IMAGE_DPI
    val naturalHeight = heightPx / DEFAULT_IMAGE_DPI
    val scale = minOf(1.0, A4_CONTENT_WIDTH_IN / naturalWidth, MAX_IMAGE_HEIGHT_IN / naturalHeight)
    return maxOf(1L, (naturalWidth * scale * EMU_PER_INCH).roundToLong()) to
      maxOf(1L, (naturalHeight * scale * EMU_PER_INCH).roundToLong())
  }

  private fun writeTextElement(xml: XmlSerializer, namespace: String, name: String, value: String) {
    xml.startTag(namespace, name)
    xml.text(value)
    xml.endTag(namespace, name)
  }

  private fun writeXmlEntry(zip: ZipOutputStream, name: String, block: (XmlSerializer) -> Unit) {
    zip.putNextEntry(ZipEntry(name))
    val xml = Xml.newSerializer()
    xml.setOutput(zip, "UTF-8")
    xml.startDocument("UTF-8", true)
    block(xml)
    xml.endDocument()
    xml.flush()
    zip.closeEntry()
  }

  private fun validateDocument(file: File, expectedImageCount: Int) {
    check(file.isFile && file.length() > 0L) { "Word 文档生成失败" }
    ZipFile(file).use { zip ->
      REQUIRED_XML_ENTRIES.forEach { name ->
        val entry = requireNotNull(zip.getEntry(name)) { "Word 文档缺少 $name" }
        val parser = Xml.newPullParser()
        zip.getInputStream(entry).buffered().use { parser.setInput(it, "UTF-8")
          while (parser.next() != XmlPullParser.END_DOCUMENT) Unit
        }
      }
      val imageCount = zip.entries().asSequence().count { !it.isDirectory && it.name.startsWith("word/media/") }
      check(imageCount == expectedImageCount) { "Word 文档图片数量不完整" }
    }
  }

  private data class ExportPlan(
    val devices: List<ExportDevice>,
    val photos: List<ExportPhoto>,
    val temporaryFiles: List<File>,
  )

  private data class ExportDevice(val name: String, val items: List<ExportItem>)

  private data class ExportItem(val name: String, val photos: List<ExportPhoto>)

  private data class ExportPhoto(
    val file: File,
    val mediaName: String,
    val contentType: String,
    val relationshipId: String,
    val widthPx: Int,
    val heightPx: Int,
    val temporary: Boolean,
  )

  private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
  private const val OFFICE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  private const val PACKAGE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
  private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
  private const val WORD_PROCESSING_DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
  private const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
  private const val PICTURE_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"
  private const val CORE_PROPERTIES_NS = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
  private const val EXTENDED_PROPERTIES_NS = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
  private const val DC_NS = "http://purl.org/dc/elements/1.1/"
  private const val DCTERMS_NS = "http://purl.org/dc/terms/"
  private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
  private const val VT_NS = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
  private const val DEFAULT_IMAGE_DPI = 96.0
  private const val EMU_PER_INCH = 914400.0
  private const val A4_CONTENT_WIDTH_IN = 6.2677
  private const val MAX_IMAGE_HEIGHT_IN = 8.8
  private const val MAX_RASTER_EDGE_PX = 2400

  private val EMBEDDABLE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif")
  private val REQUIRED_XML_ENTRIES = listOf(
    "[Content_Types].xml",
    "_rels/.rels",
    "word/document.xml",
    "word/_rels/document.xml.rels",
    "word/styles.xml",
    "docProps/core.xml",
    "docProps/app.xml",
  )
}
