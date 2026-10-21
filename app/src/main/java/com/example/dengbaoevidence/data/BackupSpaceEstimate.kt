package com.example.dengbaoevidence.data

import java.io.File
import java.io.IOException
import kotlin.math.max

data class BackupSpaceEstimate(
  val originalBytes: Long,
  val archiveBytes: Long,
  val projectCount: Int,
) {
  fun peakBytes(): Long = multiplyRatioCeiling(archiveBytes, 2, 1)

  fun reserveBytes(): Long = multiplyRatioCeiling(archiveBytes, 1, 10)

  fun requiredInternalBytes(): Long = multiplyRatioCeiling(archiveBytes, 21, 10)

  companion object { const val MIN_ARCHIVE_ESTIMATE_BYTES = 1024L * 1024L }
}

private fun multiplyRatioCeiling(value: Long, numerator: Long, denominator: Long): Long {
  val whole = Math.multiplyExact(value / denominator, numerator)
  val remainder = value % denominator
  val extra = (remainder * numerator + denominator - 1) / denominator
  return Math.addExact(whole, extra)
}

object BackupSpaceEstimator {
  fun estimate(
    projects: List<ProjectExportData>,
    templateBytes: Long,
    checkCancelled: () -> Unit = {},
  ): BackupSpaceEstimate {
    var originalBytes = 0L
    projects.forEach { data ->
      checkCancelled()
      data.devices.forEach { device ->
        device.items.forEach { item ->
          item.photos.forEach { photo ->
            checkCancelled()
            val source = File(photo.path)
            check(source.isFile && source.length() > 0) { "照片文件不存在：${device.device.name} / ${item.item.name}" }
            originalBytes = Math.addExact(originalBytes, source.length())
          }
        }
      }
    }
    val byPhotos = multiplyRatioCeiling(originalBytes, 6, 5)
    val archiveBytes = max(
      BackupSpaceEstimate.MIN_ARCHIVE_ESTIMATE_BYTES,
      max(byPhotos, Math.addExact(templateBytes, 64 * 1024L)),
    )
    return BackupSpaceEstimate(originalBytes, archiveBytes, projects.size)
  }
}

// Shared by all backup streams; checks are bounded by bytes and time, not by progress percentages.
class BackupSpaceMonitor(
  private val label: String,
  private val reserveBytes: Long,
  private val availableBytes: () -> Long,
  private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
  private var bytesSinceCheck = 0L
  private var lastCheckAt: Long? = null

  fun requireAdditional(bytes: Long = 0) {
    val available = availableBytes()
    val required = Math.addExact(reserveBytes, bytes.coerceAtLeast(0))
    if (available < required) {
      throw IOException("${label}空间不足，为保护数据已停止备份；请至少再释放 ${formatBytes(required - available)}")
    }
    bytesSinceCheck = 0
    lastCheckAt = clockMillis()
  }

  fun beforeWrite(bytes: Int) {
    require(bytes >= 0)
    bytesSinceCheck += bytes
    val now = clockMillis()
    if (lastCheckAt == null || bytesSinceCheck >= CHECK_INTERVAL_BYTES || now - requireNotNull(lastCheckAt) >= 1_000) {
      requireAdditional(max(CHECK_INTERVAL_BYTES, bytes.toLong()))
    }
  }

  companion object {
    const val CHECK_INTERVAL_BYTES = 1024 * 1024L

    fun formatBytes(bytes: Long): String {
      val mib = bytes.coerceAtLeast(0).toDouble() / (1024 * 1024)
      return if (mib >= 1024) java.lang.String.format(java.util.Locale.getDefault(), "%.2f GB", mib / 1024)
      else java.lang.String.format(java.util.Locale.getDefault(), "%.1f MB", mib)
    }
  }
}
