package com.example.dengbaoevidence.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class ProjectPackageExportSummary(
  val deviceCount: Int,
  val itemCount: Int,
  val photoCount: Int,
)

object ProjectPackageExporter {
  fun export(context: Context, data: ProjectExportData, destination: File): ProjectPackageExportSummary {
    destination.parentFile?.mkdirs()
    val exportedAt = System.currentTimeMillis()
    val plan = buildPlan(data)
    try {
      ZipOutputStream(destination.outputStream().buffered()).use { zip ->
        zip.setLevel(Deflater.BEST_SPEED)
        writeAsset(context, zip, VIEWER_ASSET_NAME, INDEX_ENTRY_NAME)

        zip.setLevel(Deflater.NO_COMPRESSION)
        plan.photos.forEach { photo -> writeOriginal(zip, photo) }

        zip.setLevel(Deflater.BEST_SPEED)
        plan.photos.forEach { photo -> writeThumbnail(zip, photo) }
        writeText(zip, DATA_ENTRY_NAME, "window.EVIDENCE_EXPORT = ${buildDataJson(data, plan, exportedAt)};\n")
        writeText(zip, MANIFEST_ENTRY_NAME, buildManifestJson(data, plan, exportedAt).toString(2))
      }
      validatePackage(destination, plan.photos.size)
      return ProjectPackageExportSummary(
        deviceCount = plan.devices.size,
        itemCount = plan.devices.sumOf { it.items.size },
        photoCount = plan.photos.size,
      )
    } catch (throwable: Throwable) {
      destination.delete()
      throw throwable
    }
  }

  private fun buildPlan(data: ProjectExportData): ExportPlan {
    val usedTypePaths = mutableSetOf<String>()
    val typePaths = mutableMapOf<String, String>()
    val usedDevicePaths = mutableMapOf<String, MutableSet<String>>()
    var photoIndex = 0

    val devices = data.devices
      .sortedWith(compareBy<DeviceWithItems> { it.device.typeName }.thenBy { it.device.createdAt })
      .map { sourceDevice ->
        val typePath = typePaths.getOrPut(sourceDevice.device.typeName) {
          uniquePathSegment(sourceDevice.device.typeName, usedTypePaths, "未分类设备")
        }
        val devicePath = uniquePathSegment(
          sourceDevice.device.name,
          usedDevicePaths.getOrPut(typePath) { mutableSetOf() },
          "未命名设备",
        )
        val usedPhotoNames = mutableSetOf<String>()
        val items = sourceDevice.orderedItems.map { sourceItem ->
          val photos = sourceItem.photos.sortedBy { it.sortOrder }.mapIndexed { index, sourcePhoto ->
            val source = File(sourcePhoto.path)
            require(source.isFile && source.length() > 0L) {
              "照片文件不存在：${sourceDevice.device.name} / ${sourceItem.item.name} / 第 ${index + 1} 张"
            }
            photoIndex += 1
            val extension = safeExtension(source.extension)
            val preferredName = listOf(
              safePathSegment(sourceDevice.device.name, "设备"),
              safePathSegment(sourceItem.item.name, "取证项"),
              (index + 1).toString().padStart(2, '0'),
            ).joinToString("_") + ".$extension"
            val fileName = uniqueFileName(preferredName, usedPhotoNames)
            ExportPhoto(
              id = sourcePhoto.id,
              source = source,
              createdAt = sourcePhoto.createdAt,
              sortOrder = sourcePhoto.sortOrder,
              expectedSha256 = sourcePhoto.sha256,
              originalPath = "originals/$typePath/$devicePath/$fileName",
              thumbnailPath = "thumbnails/photo_${sourcePhoto.id}_$photoIndex.jpg",
              mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream",
            )
          }
          ExportItem(sourceItem, photos)
        }
        ExportDevice(sourceDevice, items)
      }
    return ExportPlan(devices, devices.flatMap { it.items }.flatMap { it.photos })
  }

  private fun writeOriginal(zip: ZipOutputStream, photo: ExportPhoto) {
    val digest = MessageDigest.getInstance("SHA-256")
    zip.putNextEntry(ZipEntry(photo.originalPath).also { it.time = photo.createdAt })
    try {
      photo.source.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          digest.update(buffer, 0, read)
          zip.write(buffer, 0, read)
        }
      }
    } finally {
      zip.closeEntry()
    }
    val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    if (photo.expectedSha256.isNotBlank()) {
      check(actualHash.equals(photo.expectedSha256.trim(), ignoreCase = true)) {
        "照片完整性校验失败：${photo.source.name}"
      }
    }
    photo.verifiedSha256 = actualHash
  }

  private fun writeThumbnail(zip: ZipOutputStream, photo: ExportPhoto) {
    val decoded = runCatching { decodeThumbnail(photo.source) }.getOrElse {
      photo.thumbnailAvailable = false
      createPlaceholderThumbnail()
    }
    val output = if (decoded.hasAlpha()) {
      createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also { target ->
        Canvas(target).apply {
          drawColor(Color.WHITE)
          drawBitmap(decoded, 0f, 0f, null)
        }
      }
    } else {
      decoded
    }
    try {
      zip.putNextEntry(ZipEntry(photo.thumbnailPath).also { it.time = photo.createdAt })
      try {
        check(output.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, zip)) { "缩略图生成失败" }
      } finally {
        zip.closeEntry()
      }
    } finally {
      if (output !== decoded) output.recycle()
      decoded.recycle()
    }
  }

  private fun decodeThumbnail(file: File): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val target = fitWithin(info.size.width, info.size.height, THUMBNAIL_MAX_EDGE_PX)
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)
        decoder.setTargetSize(target.first, target.second)
      }
    } else {
      val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
      var sampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > THUMBNAIL_MAX_EDGE_PX) sampleSize *= 2
      val decoded = requireNotNull(
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().also { it.inSampleSize = sampleSize }),
      ) { "无法读取图片" }
      rotateBitmap(decoded, readOrientation(file))
    }

  private fun createPlaceholderThumbnail(): Bitmap =
    createBitmap(480, 320, Bitmap.Config.ARGB_8888).also { bitmap ->
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.rgb(232, 237, 241))
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(148, 163, 175)
        strokeWidth = 8f
      }
      canvas.drawLine(176f, 112f, 304f, 208f, paint)
      canvas.drawLine(304f, 112f, 176f, 208f, paint)
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

  private fun buildDataJson(data: ProjectExportData, plan: ExportPlan, exportedAt: Long): String {
    val root = JSONObject()
      .put("schemaVersion", EXPORT_FORMAT_VERSION)
      .put("exportedAt", exportedAt)
      .put("project", JSONObject()
        .put("id", data.project.id)
        .put("name", data.project.name)
        .put("createdAt", data.project.createdAt))
      .put("summary", JSONObject()
        .put("deviceCount", plan.devices.size)
        .put("itemCount", plan.devices.sumOf { it.items.size })
        .put("photoCount", plan.photos.size))
      .put("devices", JSONArray().also { array ->
        plan.devices.forEach { device -> array.put(deviceJson(device)) }
      })
    return root.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
  }

  private fun deviceJson(device: ExportDevice): JSONObject = JSONObject()
    .put("id", device.source.device.id)
    .put("name", device.source.device.name)
    .put("typeName", device.source.device.typeName)
    .put("createdAt", device.source.device.createdAt)
    .put("recordedCount", device.source.recordedCount)
    .put("totalCount", device.source.totalCount)
    .put("missingKeyScreenshotCount", device.source.missingKeyScreenshotCount)
    .put("items", JSONArray().also { array ->
      device.items.forEach { item -> array.put(itemJson(item)) }
    })

  private fun itemJson(item: ExportItem): JSONObject = JSONObject()
    .put("id", item.source.item.id)
    .put("name", item.source.item.name)
    .put("displayOrder", item.source.item.displayOrder)
    .put("isKeyScreenshot", item.source.item.isKeyScreenshot)
    .put("wordOrder", item.source.item.wordOrder ?: JSONObject.NULL)
    .put("excludedFromProgress", item.source.item.excludedFromProgress)
    .put("isTemporary", item.source.item.isTemporary)
    .put("isCompliant", item.source.item.isCompliant)
    .put("isNotApplicable", item.source.item.isNotApplicable)
    .put("note", item.source.item.note)
    .put("updatedAt", item.source.item.updatedAt)
    .put("isRecorded", item.source.isRecorded)
    .put("photos", JSONArray().also { array ->
      item.photos.forEach { photo -> array.put(photoJson(photo)) }
    })

  private fun photoJson(photo: ExportPhoto): JSONObject = JSONObject()
    .put("id", photo.id)
    .put("createdAt", photo.createdAt)
    .put("sortOrder", photo.sortOrder)
    .put("sha256", photo.verifiedSha256)
    .put("fileSize", photo.source.length())
    .put("mimeType", photo.mimeType)
    .put("originalPath", photo.originalPath)
    .put("thumbnailPath", photo.thumbnailPath)
    .put("thumbnailAvailable", photo.thumbnailAvailable)

  private fun buildManifestJson(data: ProjectExportData, plan: ExportPlan, exportedAt: Long): JSONObject =
    JSONObject()
      .put("format", "dengbao-evidence-project")
      .put("formatVersion", EXPORT_FORMAT_VERSION)
      .put("exportedAt", Instant.ofEpochMilli(exportedAt).toString())
      .put("project", JSONObject()
        .put("id", data.project.id)
        .put("name", data.project.name)
        .put("createdAt", Instant.ofEpochMilli(data.project.createdAt).toString()))
      .put("summary", JSONObject()
        .put("deviceCount", plan.devices.size)
        .put("itemCount", plan.devices.sumOf { it.items.size })
        .put("photoCount", plan.photos.size))
      .put("photos", JSONArray().also { array ->
        plan.photos.forEach { photo ->
          array.put(JSONObject()
            .put("id", photo.id)
            .put("originalPath", photo.originalPath)
            .put("thumbnailPath", photo.thumbnailPath)
            .put("fileSize", photo.source.length())
            .put("sha256", photo.verifiedSha256))
        }
      })

  private fun writeAsset(context: Context, zip: ZipOutputStream, assetName: String, entryName: String) {
    zip.putNextEntry(ZipEntry(entryName))
    try {
      context.assets.open(assetName).buffered().use { it.copyTo(zip) }
    } finally {
      zip.closeEntry()
    }
  }

  private fun writeText(zip: ZipOutputStream, entryName: String, content: String) {
    zip.putNextEntry(ZipEntry(entryName))
    try {
      zip.write(content.toByteArray(Charsets.UTF_8))
    } finally {
      zip.closeEntry()
    }
  }

  private fun validatePackage(file: File, expectedPhotoCount: Int) {
    check(file.isFile && file.length() > 0L) { "项目导出包生成失败" }
    ZipFile(file).use { zip ->
      REQUIRED_ENTRIES.forEach { entryName ->
        requireNotNull(zip.getEntry(entryName)) { "项目导出包缺少 $entryName" }
      }
      val originalCount = zip.entries().asSequence().count { !it.isDirectory && it.name.startsWith("originals/") }
      val thumbnailCount = zip.entries().asSequence().count { !it.isDirectory && it.name.startsWith("thumbnails/") }
      check(originalCount == expectedPhotoCount) { "项目导出包原始照片数量不完整" }
      check(thumbnailCount == expectedPhotoCount) { "项目导出包缩略图数量不完整" }
      val manifest = zip.getInputStream(zip.getEntry(MANIFEST_ENTRY_NAME)).bufferedReader(Charsets.UTF_8).use { it.readText() }
      check(JSONObject(manifest).getJSONObject("summary").getInt("photoCount") == expectedPhotoCount) {
        "项目导出包清单不完整"
      }
    }
  }

  private fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "无法读取图片尺寸" }
    val largest = maxOf(width, height)
    if (largest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / largest
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
  }

  private fun uniquePathSegment(preferred: String, used: MutableSet<String>, fallback: String): String {
    val base = safePathSegment(preferred, fallback)
    var candidate = base
    var suffix = 2
    while (!used.add(candidate.lowercase())) candidate = "${base}_${suffix++}"
    return candidate
  }

  private fun uniqueFileName(preferred: String, used: MutableSet<String>): String {
    val extension = preferred.substringAfterLast('.', "bin")
    val base = preferred.substringBeforeLast('.', preferred)
    var candidate = preferred
    var suffix = 2
    while (!used.add(candidate.lowercase())) candidate = "${base}_${suffix++}.$extension"
    return candidate
  }

  private fun safePathSegment(value: String, fallback: String): String {
    val sanitized = value
      .replace(Regex("[\\u0000-\\u001f\\u007f\\\\/:*?\"<>|]"), "_")
      .trim()
      .trimEnd('.', ' ')
      .take(MAX_PATH_SEGMENT_LENGTH)
    return sanitized.ifBlank { fallback }
  }

  private fun safeExtension(value: String): String = value
    .lowercase()
    .filter { it.isLetterOrDigit() }
    .take(10)
    .ifBlank { "bin" }

  private data class ExportPlan(val devices: List<ExportDevice>, val photos: List<ExportPhoto>)

  private data class ExportDevice(val source: DeviceWithItems, val items: List<ExportItem>)

  private data class ExportItem(val source: EvidenceItemWithPhotos, val photos: List<ExportPhoto>)

  private data class ExportPhoto(
    val id: Long,
    val source: File,
    val createdAt: Long,
    val sortOrder: Int,
    val expectedSha256: String,
    val originalPath: String,
    val thumbnailPath: String,
    val mimeType: String,
    var verifiedSha256: String = "",
    var thumbnailAvailable: Boolean = true,
  )

  private const val VIEWER_ASSET_NAME = "project_viewer.html"
  private const val INDEX_ENTRY_NAME = "index.html"
  private const val DATA_ENTRY_NAME = "data.js"
  private const val MANIFEST_ENTRY_NAME = "manifest.json"
  private const val EXPORT_FORMAT_VERSION = "1.1"
  private const val THUMBNAIL_MAX_EDGE_PX = 720
  private const val THUMBNAIL_JPEG_QUALITY = 82
  private const val MAX_PATH_SEGMENT_LENGTH = 80
  private val REQUIRED_ENTRIES = listOf(INDEX_ENTRY_NAME, DATA_ENTRY_NAME, MANIFEST_ENTRY_NAME)
}
