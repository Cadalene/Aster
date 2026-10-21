package com.example.dengbaoevidence.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataRepositoryTest {
  private lateinit var database: EvidenceDatabase
  private lateinit var repository: DataRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, EvidenceDatabase::class.java).build()
    repository = DataRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun snapshotsVerificationMethodWithoutChangingHistoricalEvidenceOrProgress() = runBlocking {
    val templateId = repository.createDeviceType("Method template")
    val itemId = repository.addTemplateItem(templateId, "Policy")
    repository.updateTemplateItem(itemId, "Policy", 0, true, 0, "show policy\n检查配置")
    val copyId = repository.createDeviceType("Method copy", templateId)
    assertEquals("show policy\n检查配置", requireNotNull(database.evidenceDao().getDeviceType(copyId)).items.first { it.name == "Policy" }.verificationMethod)
    val projectId = repository.createProject("Method project")
    val oldDeviceId = repository.createDevice(projectId, "Before edit", templateId)
    val oldItem = requireNotNull(database.evidenceDao().getDeviceWithItems(oldDeviceId)).items.first { it.item.name == "Policy" }
    assertEquals("show policy\n检查配置", oldItem.item.verificationMethod)
    assertFalse(oldItem.isRecorded)
    repository.updateTemplateItem(itemId, "Policy", 0, true, 0, "changed")
    assertEquals(oldItem, requireNotNull(database.evidenceDao().getDeviceWithItems(oldDeviceId)).items.first { it.item.name == "Policy" })
    val newDeviceId = repository.createDevice(projectId, "After edit", templateId)
    assertEquals("changed", requireNotNull(database.evidenceDao().getDeviceWithItems(newDeviceId)).items.first { it.item.name == "Policy" }.item.verificationMethod)
    val packageData = TemplateTransfer.decode(TemplateTransfer.encode(database.evidenceDao().getDeviceTypes()))
    assertEquals("changed", packageData.templates.first { it.name == "Method template" }.items.first { it.name == "Policy" }.verificationMethod)
    assertTrue(repository.previewTemplateImport(packageData).differentNames.isEmpty())
    val changedPackage = packageData.copy(templates = packageData.templates.map { template ->
      if (template.name != "Method template") template else template.copy(items = template.items.map { it.copy(verificationMethod = "new") })
    })
    assertTrue(repository.previewTemplateImport(changedPackage).differenceDetails.getValue("Method template").any { it.contains("核查方式") })
    repository.importDeviceTypes(changedPackage, TemplateConflictStrategy.OVERWRITE)
    assertEquals(oldItem, requireNotNull(database.evidenceDao().getDeviceWithItems(oldDeviceId)).items.first { it.item.name == "Policy" })
  }

  @Test
  fun oldJsonDefaultsMethodToBlankAndOverviewAndOtherCanSnapshotMethod() = runBlocking {
    val old = TemplateTransfer.decode("""{"formatVersion":1,"templates":[{"name":"Legacy","items":[{"name":"Policy"}]}]}""")
    assertTrue(old.templates.single().items.all { it.verificationMethod.isEmpty() })
    repository.ensureDefaultTemplates()
    val overview = database.evidenceDao().getDeviceTypes().single()
    val other = overview.items.single()
    repository.updateTemplateItem(other.id, other.name, other.displayOrder, other.isKeyScreenshot, other.wordOrder, "Overview instructions")
    val projectId = repository.createProject("Overview method")
    val item = database.evidenceDao().getDevices(projectId).single().items.single()
    assertEquals("Overview instructions", item.item.verificationMethod)
    assertFalse(item.isRecorded)
    assertTrue(item.item.excludedFromProgress)
    assertTrue(runCatching { repository.updateTemplateItem(other.id, "Renamed", other.displayOrder, false, null, "") }.isFailure)
  }

  @Test
  fun rejectsDuplicateProjectCreationAndRenameWithoutChangingExistingData() = runBlocking {
    val firstId = repository.createProject("First")
    val secondId = repository.createProject("Second")
    val originalDevices = database.evidenceDao().getDevices(firstId)
    assertTrue(runCatching { repository.createProject(" First ") }.isFailure)
    assertTrue(runCatching { repository.renameProject(secondId, " First ") }.isFailure)
    repository.renameProject(firstId, " First ")
    assertEquals("First", database.evidenceDao().getProject(firstId)?.name)
    assertEquals("Second", database.evidenceDao().getProject(secondId)?.name)
    assertEquals(2, repository.getProjectSummariesSnapshot().size)
    assertEquals(originalDevices, database.evidenceDao().getDevices(firstId))
  }

  @Test
  fun concurrentSameNameCreationCommitsOnlyOneProject() = runBlocking {
    val results = coroutineScope {
      List(4) { async { runCatching { repository.createProject("Same") } } }.awaitAll()
    }
    assertEquals(1, results.count { it.isSuccess })
    assertEquals(1, repository.getProjectSummariesSnapshot().size)
    assertEquals(1, database.evidenceDao().getDevices(results.first { it.isSuccess }.getOrThrow()).size)
  }

  @Test
  fun historicalDuplicateProjectsAreNotRenamedOrDeletedByStartup() = runBlocking {
    val dao = database.evidenceDao()
    val first = dao.insertProject(ProjectEntity(name = "Legacy", createdAt = 1))
    val second = dao.insertProject(ProjectEntity(name = "Legacy", createdAt = 2))
    repository.ensureDefaultTemplates()
    assertEquals("Legacy", dao.getProject(first)?.name)
    assertEquals("Legacy", dao.getProject(second)?.name)
    assertEquals(2, repository.getProjectSummariesSnapshot().size)
    assertTrue(runCatching { repository.createProject("Legacy") }.isFailure)
  }

  @Test
  fun initializesOnlyProjectOverviewTemplateWithOtherItem() = runBlocking {
    repository.ensureDefaultTemplates()

    val templates = database.evidenceDao().getDeviceTypes()
    assertEquals(1, templates.size)
    assertEquals("项目整体情况", templates.single().template.name)
    assertTrue(templates.single().template.isSystemTemplate)
    assertEquals(listOf("其他"), templates.single().items.sortedBy { it.displayOrder }.map { it.name })
  }

  @Test
  fun copiesTemplateItemsAndRenamesDeviceWithoutChangingItsType() = runBlocking {
    val sourceTemplateId = repository.createDeviceType("源模板")
    repository.addTemplateItem(sourceTemplateId, "版本")
    val source = requireNotNull(database.evidenceDao().getDeviceType(sourceTemplateId))
    val versionItem = requireNotNull(source.items.firstOrNull { it.name == "版本" })
    repository.updateTemplateItem(
      itemId = versionItem.id,
      name = versionItem.name,
      displayOrder = versionItem.displayOrder,
      isKeyScreenshot = true,
      wordOrder = 2,
    )

    val copyTemplateId = repository.createDeviceType("复制模板", sourceTemplateId)
    val updatedSource = requireNotNull(database.evidenceDao().getDeviceType(sourceTemplateId))
    val copy = requireNotNull(database.evidenceDao().getDeviceType(copyTemplateId))
    val sourceProperties = updatedSource.items.sortedBy { it.displayOrder }.map {
      listOf(it.name, it.displayOrder, it.isKeyScreenshot, it.wordOrder, it.excludedFromProgress)
    }
    val copyProperties = copy.items.sortedBy { it.displayOrder }.map {
      listOf(it.name, it.displayOrder, it.isKeyScreenshot, it.wordOrder, it.excludedFromProgress)
    }
    assertEquals(sourceProperties, copyProperties)
    assertNotEquals(updatedSource.items.map { it.id }.toSet(), copy.items.map { it.id }.toSet())

    val projectId = repository.createProject("项目")
    val deviceId = repository.createDevice(projectId, "旧名称", copyTemplateId)
    repository.renameDevice(deviceId, "新名称")
    val renamed = requireNotNull(database.evidenceDao().getDevice(deviceId))
    assertEquals("新名称", renamed.name)
    assertEquals("复制模板", renamed.typeName)
  }

  @Test
  fun reordersDeviceTypesAndAppendsNewTemplatesAtTheEnd() = runBlocking {
    val firstId = repository.createDeviceType("类型一")
    val secondId = repository.createDeviceType("类型二")
    val thirdId = repository.createDeviceType("类型三")

    repository.reorderDeviceTypes(listOf(thirdId, firstId, secondId))
    assertEquals(
      listOf("类型三", "类型一", "类型二"),
      database.evidenceDao().getDeviceTypes().map { it.template.name },
    )

    repository.createDeviceType("类型四")
    assertEquals(
      listOf("类型三", "类型一", "类型二", "类型四"),
      database.evidenceDao().getDeviceTypes().map { it.template.name },
    )
  }

  @Test
  fun deviceListUsesTemplateOrderThenCreationTime() = runBlocking {
    val firstTemplateId = repository.createDeviceType("类型一")
    val secondTemplateId = repository.createDeviceType("类型二")
    val projectId = repository.createProject("排序项目")
    val secondDeviceId = repository.createDevice(projectId, "类型二设备", secondTemplateId)
    val firstDeviceId = repository.createDevice(projectId, "类型一设备", firstTemplateId)

    repository.reorderDeviceTypes(
      listOf(
        requireNotNull(database.evidenceDao().findSystemDeviceType("project_overview")).template.id,
        secondTemplateId,
        firstTemplateId,
      ),
    )

    val ordered = database.evidenceDao().getDevices(projectId).map { it.device.id }
    assertEquals(
      listOf(
        requireNotNull(database.evidenceDao().getDevices(projectId).first { it.device.id != secondDeviceId && it.device.id != firstDeviceId }).device.id,
        secondDeviceId,
        firstDeviceId,
      ),
      ordered,
    )
  }

  @Test
  fun createsProjectOverviewDeviceAndRecordsNotApplicableConclusion() = runBlocking {
    val projectId = repository.createProject("测试项目")
    val devices = database.evidenceDao().getDevices(projectId)

    assertEquals(1, devices.size)
    val overview = devices.single()
    assertEquals("项目整体情况", overview.device.name)
    assertEquals("项目整体情况", overview.device.typeName)
    assertEquals(listOf("其他"), overview.orderedItems.map { it.item.name })

    val firstItemId = overview.orderedItems.first().item.id
    repository.updateConclusion(firstItemId, EvidenceConclusion.NOT_APPLICABLE)
    val updated = requireNotNull(database.evidenceDao().getDeviceWithItems(overview.device.id))
      .orderedItems.first()
    assertTrue(updated.item.isNotApplicable)
    assertTrue(updated.item.isCompliant)
    assertTrue(updated.isRecorded)
    assertTrue(overview.device.requiresEvidence)
  }

  @Test
  fun deviceEvidenceToggleOnlyChangesMissingScreenshotWarnings() = runBlocking {
    val templateId = repository.createDeviceType("开关测试模板")
    repository.addTemplateItem(templateId, "关键项")
    val template = requireNotNull(database.evidenceDao().getDeviceType(templateId))
    val item = requireNotNull(template.items.single { it.name == "关键项" })
    repository.updateTemplateItem(item.id, item.name, item.displayOrder, true, 0)
    val projectId = repository.createProject("开关测试项目")
    val deviceId = repository.createDevice(projectId, "开关测试设备", templateId)
    val before = requireNotNull(database.evidenceDao().getDeviceWithItems(deviceId))
    val beforeProgress = before.recordedCount to before.totalCount

    assertTrue(before.device.requiresEvidence)
    assertEquals(1, before.missingKeyScreenshotCount)
    repository.setDeviceRequiresEvidence(deviceId, false)
    val after = requireNotNull(database.evidenceDao().getDeviceWithItems(deviceId))
    assertFalse(after.device.requiresEvidence)
    assertEquals(0, after.missingKeyScreenshotCount)
    assertEquals(beforeProgress, after.recordedCount to after.totalCount)
    assertEquals(before.orderedItems, after.orderedItems)

    repository.setDeviceRequiresEvidence(deviceId, true)
    assertEquals(1, requireNotNull(database.evidenceDao().getDeviceWithItems(deviceId)).missingKeyScreenshotCount)
  }

  @Test
  fun renamesProjectAndDeletesDeviceAndProjectPhotoFiles() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testDirectory = File(context.cacheDir, "repository_delete_${System.nanoTime()}").apply { mkdirs() }
    try {
      val templateId = repository.createDeviceType("删除测试模板")
      val projectId = repository.createProject("旧项目名称")
      val firstDeviceId = repository.createDevice(projectId, "设备01", templateId)
      val firstDevice = requireNotNull(database.evidenceDao().getDeviceWithItems(firstDeviceId))
      val firstItemId = firstDevice.items.first().item.id
      val firstFile = File(testDirectory, "first.jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
      val firstPhotoId = repository.registerPhoto(firstItemId, firstFile, "hash")

      repository.renameProject(projectId, " 新项目名称 ")
      assertEquals("新项目名称", requireNotNull(database.evidenceDao().getProject(projectId)).name)

      val deviceResult = repository.deleteDevice(firstDeviceId)
      assertEquals(1, deviceResult.photoCount)
      assertEquals(0, deviceResult.failedFileCount)
      assertFalse(firstFile.exists())
      assertNull(database.evidenceDao().getDevice(firstDeviceId))
      assertNull(database.evidenceDao().getEvidenceItem(firstItemId))
      assertNull(database.evidenceDao().getPhoto(firstPhotoId))

      val secondDeviceId = repository.createDevice(projectId, "设备02", templateId)
      val secondItemId = requireNotNull(database.evidenceDao().getDeviceWithItems(secondDeviceId)).items.first().item.id
      val secondFile = File(testDirectory, "second.jpg").also { it.writeBytes(byteArrayOf(4, 5, 6)) }
      repository.registerPhoto(secondItemId, secondFile, "hash")

      val projectResult = repository.deleteProject(projectId)
      assertEquals(1, projectResult.photoCount)
      assertEquals(0, projectResult.failedFileCount)
      assertFalse(secondFile.exists())
      assertNull(database.evidenceDao().getProject(projectId))
      assertNull(database.evidenceDao().getDevice(secondDeviceId))
    } finally {
      testDirectory.deleteRecursively()
    }
  }
}
