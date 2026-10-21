package com.example.dengbaoevidence.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupSpaceEstimateTest {
  @get:Rule val files = TemporaryFolder()

  private fun project(id: Long, photos: List<PhotoEntity>) = ProjectExportData(
    ProjectEntity(id, "project-$id", id),
    listOf(DeviceWithItems(DeviceEntity(id, id, "device-$id", "type", id), listOf(EvidenceItemWithPhotos(
      EvidenceItemEntity(id, id, "item", 0, true, null, false, false, updatedAt = id), photos,
    )))),
  )

  @Test fun estimatesArchiveAtOnePointTwoTimesAllReferencedOriginals() {
    val first = File(files.root, "first.jpg").apply { RandomAccessFile(this, "rw").use { it.setLength(10_000_000) } }
    val second = File(files.root, "second.jpg").apply { RandomAccessFile(this, "rw").use { it.setLength(5_000_000) } }
    val projects = listOf(
      project(1, listOf(PhotoEntity(1, 1, first.path, 1, 0, ""))),
      project(2, listOf(PhotoEntity(2, 2, second.path, 2, 0, ""))),
    )

    val estimate = BackupSpaceEstimator.estimate(projects, 20_000)

    assertEquals(15_000_000L, estimate.originalBytes)
    assertEquals(18_000_000L, estimate.archiveBytes)
    assertEquals(2, estimate.projectCount)
  }

  @Test fun requiredPhoneSpaceIsTwoPointOneTimesEstimatedArchive() {
    val estimate = BackupSpaceEstimate(10_000_000, 12_000_000, 1)

    assertEquals(24_000_000L, estimate.peakBytes())
    assertEquals(1_200_000L, estimate.reserveBytes())
    assertEquals(25_200_000L, estimate.requiredInternalBytes())
  }

  @Test fun emptyBackupHasSmallNonzeroEstimateForTemplatesAndMetadata() {
    val empty = BackupSpaceEstimator.estimate(emptyList(), 32)
    val templateOnly = BackupSpaceEstimator.estimate(emptyList(), 2_000_000)

    assertEquals(BackupSpaceEstimate.MIN_ARCHIVE_ESTIMATE_BYTES, empty.archiveBytes)
    assertEquals(2_065_536L, templateOnly.archiveBytes)
    assertEquals(0, empty.projectCount)
  }

  @Test fun missingPhotoAndArithmeticOverflowAreExplicit() {
    val missing = PhotoEntity(1, 1, File(files.root, "missing").path, 1, 0, "")
    assertThrows(IllegalStateException::class.java) {
      BackupSpaceEstimator.estimate(listOf(project(1, listOf(missing))), 0)
    }
    assertThrows(ArithmeticException::class.java) {
      BackupSpaceEstimate(Long.MAX_VALUE, Long.MAX_VALUE, 1).requiredInternalBytes()
    }
  }

  @Test fun cancelledEstimateDoesNotProduceAResult() {
    assertThrows(IOException::class.java) {
      BackupSpaceEstimator.estimate(listOf(project(1, emptyList())), 0) { throw IOException("Cancelled") }
    }
  }

  @Test fun shortageBlocksAndRecheckSucceedsAfterFreeingSpace() {
    var available = 999L
    val monitor = BackupSpaceMonitor("test", 500, availableBytes = { available })
    assertThrows(IOException::class.java) { monitor.requireAdditional(500) }
    available = 1000
    monitor.requireAdditional(500)
  }

  @Test fun monitorDetectsSpaceDropByBytesEvenWhenProgressDoesNotChange() {
    var available = 20_000_000L
    val monitor = BackupSpaceMonitor("test", 500, availableBytes = { available }, clockMillis = { 0 })
    monitor.beforeWrite(1)
    available = 500
    assertThrows(IOException::class.java) { monitor.beforeWrite(BackupSpaceMonitor.CHECK_INTERVAL_BYTES.toInt()) }
  }

  @Test fun monitorDetectsSlowWriteByTimeEvenWithFewBytes() {
    var available = 20_000_000L
    var time = 0L
    val monitor = BackupSpaceMonitor("test", 500, availableBytes = { available }, clockMillis = { time })
    monitor.beforeWrite(1)
    available = 500
    time = 1000
    assertThrows(IOException::class.java) { monitor.beforeWrite(1) }
  }

  @Test fun unknownDestinationDoesNotPretendCapacityIsKnown() {
    val estimate = BackupSpaceEstimate(1_000_000, 1_200_000, 1)
    val preview = BackupSpacePreview(estimate, 3_000_000, null, true)
    assertNull(preview.availableDestinationBytes)
    assertTrue(preview.canStart)
    assertEquals(2_520_000L, preview.requiredInternalBytes)
    assertFalse(preview.copy(availableInternalBytes = 1_000_000).canStart)
    assertFalse(preview.copy(availableDestinationBytes = 1).canStart)
  }

  @Test fun actualFinalSizeIsCheckedBeforeCopyNotOnlyTheInitialEstimate() {
    val file = files.newFile().apply { writeBytes(ByteArray(2048)) }
    val internal = BackupSpaceMonitor("internal", 500, availableBytes = { 2000 })
    val guard = BackupStorageGuard(internal, null, true)
    assertThrows(IOException::class.java) { guard.beforeSave(file) }
    assertEquals(2048, file.length())
  }
}
