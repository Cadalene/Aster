package com.example.dengbaoevidence.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.system.Os
import java.io.File

data class BackupSpacePreview(
  val estimate: BackupSpaceEstimate,
  val availableInternalBytes: Long,
  val availableDestinationBytes: Long?,
  val copyUsesInternalStorage: Boolean,
) {
  val requiredInternalBytes: Long get() = estimate.requiredInternalBytes()
  val requiredDestinationBytes: Long get() = estimate.archiveBytes
  val internalShortageBytes: Long get() = (requiredInternalBytes - availableInternalBytes).coerceAtLeast(0)
  val destinationShortageBytes: Long get() = availableDestinationBytes?.let { (requiredDestinationBytes - it).coerceAtLeast(0) } ?: 0
  val canStart: Boolean get() = internalShortageBytes == 0L && destinationShortageBytes == 0L
}

class BackupStorage private constructor(
  private val cacheDirectory: File,
  private val destinationVolume: File?,
  val copyUsesInternalStorage: Boolean,
) {
  fun preview(estimate: BackupSpaceEstimate): BackupSpacePreview = BackupSpacePreview(
    estimate, available(cacheDirectory), destinationVolume?.let(::available), copyUsesInternalStorage,
  )

  fun guard(estimate: BackupSpaceEstimate): BackupStorageGuard {
    val preview = preview(estimate)
    val internal = BackupSpaceMonitor("手机内部存储", estimate.reserveBytes(), availableBytes = { available(cacheDirectory) })
    val destination = destinationVolume?.let { volume -> BackupSpaceMonitor("保存位置", 0, availableBytes = { available(volume) }) }
    internal.requireAdditional(estimate.peakBytes())
    destination?.requireAdditional(estimate.archiveBytes)
    check(preview.canStart) { "空间不足，请重新检查备份空间" }
    return BackupStorageGuard(internal, destination, copyUsesInternalStorage)
  }

  companion object {
    fun inspect(context: Context, destination: Uri): BackupStorage {
      var emulated = false
      val volumePath = runCatching {
        if (destination.authority != "com.android.externalstorage.documents") return@runCatching null
        val volumeId = DocumentsContract.getTreeDocumentId(destination).substringBefore(':')
        if (volumeId.equals("primary", ignoreCase = true)) {
          emulated = true
          Environment.getExternalStorageDirectory()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          context.getSystemService(StorageManager::class.java).storageVolumes
            .firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
            ?.let { volume -> emulated = volume.isEmulated; volume.directory }
        } else null
      }.getOrNull()?.takeIf { path -> runCatching { path.isDirectory && available(path) >= 0 }.getOrDefault(false) }
      // Unknown document providers may stage writes internally: budget as a local copy, never assume extra capacity.
      val shared = volumePath == null || emulated || runCatching {
        Os.stat(volumePath.path).st_dev == Os.stat(context.cacheDir.path).st_dev
      }.getOrDefault(true)
      return BackupStorage(context.cacheDir, volumePath, shared)
    }

    private fun available(directory: File): Long = StatFs(directory.absolutePath).availableBytes
  }
}

class BackupStorageGuard(
  private val internal: BackupSpaceMonitor,
  private val destination: BackupSpaceMonitor?,
  private val copyUsesInternalStorage: Boolean,
) {
  fun beforeProject() = internal.requireAdditional()

  fun beforeInternalWrite(bytes: Int) = internal.beforeWrite(bytes)

  fun beforeSave(file: File) {
    internal.requireAdditional(if (copyUsesInternalStorage) file.length() else 0)
    destination?.requireAdditional(file.length())
  }

  fun beforeDestinationWrite(bytes: Int) {
    internal.beforeWrite(bytes)
    destination?.beforeWrite(bytes)
  }
}
