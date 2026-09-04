package com.example.dengbaoevidence.data

import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class PhotoRegistration(
  val file: File,
  val sha256: String,
)

data class PhotoCleanupResult(
  val photoCount: Int,
  val failedFileCount: Int,
)

class DataRepository(
  private val database: EvidenceDatabase,
  private val dao: EvidenceDao = database.evidenceDao(),
) {
  fun observeProjects(): Flow<List<ProjectSummary>> = dao.observeProjects()

  fun observeProject(projectId: Long): Flow<ProjectEntity?> = dao.observeProject(projectId)

  fun observeDeviceTypes(): Flow<List<DeviceTypeWithItems>> = dao.observeDeviceTypes()

  suspend fun getDeviceTypesSnapshot(): List<DeviceTypeWithItems> = database.withTransaction { dao.getDeviceTypes() }

  fun observeDevices(projectId: Long): Flow<List<DeviceWithItems>> = dao.observeDevices(projectId)

  fun observeDevice(deviceId: Long): Flow<DeviceWithItems?> = dao.observeDevice(deviceId)

  fun observeEvidenceItem(itemId: Long): Flow<EvidenceItemWithPhotos?> = dao.observeEvidenceItem(itemId)

  suspend fun getProjectExportData(projectId: Long): ProjectExportData = database.withTransaction {
    ProjectExportData(
      project = requireNotNull(dao.getProject(projectId)) { "项目不存在" },
      devices = dao.getDevices(projectId),
    )
  }

  suspend fun createDeviceType(name: String, copyFromTemplateId: Long? = null): Long = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "设备类型名称不能为空" }
    check(trimmedName != PROJECT_OVERVIEW_TYPE_NAME) { "“项目整体情况”是系统保留模板" }
    check(dao.findDeviceType(trimmedName) == null) { "设备类型已存在" }
    val sourceItems = copyFromTemplateId?.let { sourceId ->
      requireNotNull(dao.getDeviceType(sourceId)) { "复制源模板不存在" }.also {
        check(!it.template.isSystemTemplate) { "不能复制固定的“项目整体情况”模板" }
      }.items
    }
    val templateId = dao.insertDeviceType(
      DeviceTypeTemplateEntity(
        name = trimmedName,
        createdAt = System.currentTimeMillis(),
        sortOrder = dao.maxDeviceTypeOrder() + 1,
      ),
    )
    check(templateId > 0) { "设备类型创建失败" }
    dao.insertTemplateItems(
      sourceItems?.map { source ->
        TemplateItemEntity(
          templateId = templateId,
          name = source.name,
          displayOrder = source.displayOrder,
          isKeyScreenshot = source.isKeyScreenshot,
          wordOrder = source.wordOrder,
          excludedFromProgress = source.excludedFromProgress,
        )
      } ?: listOf(
        TemplateItemEntity(
          templateId = templateId,
          name = OTHER_ITEM_NAME,
          displayOrder = 0,
          isKeyScreenshot = false,
          wordOrder = null,
          excludedFromProgress = true,
        ),
      ),
    )
    templateId
  }

  suspend fun renameDeviceType(templateId: Long, name: String) = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "设备类型名称不能为空" }
    check(trimmedName != PROJECT_OVERVIEW_TYPE_NAME) { "“项目整体情况”是系统保留模板" }
    val current = requireNotNull(dao.getDeviceType(templateId)) { "设备类型不存在" }
    check(!current.template.isSystemTemplate) { "“项目整体情况”是固定模板，不能修改名称" }
    check(current.template.name == trimmedName || dao.findDeviceType(trimmedName) == null) { "设备类型已存在" }
    check(dao.updateDeviceTypeName(templateId, trimmedName) == 1) { "设备类型不存在" }
  }

  suspend fun reorderDeviceTypes(orderedTemplateIds: List<Long>) = database.withTransaction {
    val templates = dao.getDeviceTypes()
    val templateIds = templates.map { it.template.id }.toSet()
    require(orderedTemplateIds.size == templateIds.size && orderedTemplateIds.toSet() == templateIds) {
      "设备类型顺序数据不完整"
    }
    val systemTemplateIds = templates.filter { it.template.isSystemTemplate }.map { it.template.id }
    check(orderedTemplateIds.take(systemTemplateIds.size) == systemTemplateIds) { "固定的“项目整体情况”模板必须保持在列表顶部" }
    orderedTemplateIds.forEachIndexed { index, templateId ->
      check(dao.updateDeviceTypeOrder(templateId, index) == 1) { "设备类型不存在" }
    }
  }

  suspend fun deleteDeviceType(templateId: Long) = database.withTransaction {
    val template = requireNotNull(dao.getDeviceType(templateId)) { "设备类型不存在" }
    check(!template.template.isSystemTemplate) { "“项目整体情况”是固定模板，不能删除" }
    check(dao.deviceTypeCount() > 1) { "至少需要保留一个设备类型" }
    check(dao.deleteDeviceType(templateId) == 1) { "设备类型删除失败" }
  }

  suspend fun addTemplateItem(templateId: Long, name: String): Long = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "取证项名称不能为空" }
    check(trimmedName != OTHER_ITEM_NAME) { "“其他”是模板保留项" }
    val template = requireNotNull(dao.getDeviceType(templateId)) { "设备类型不存在" }
    check(template.items.none { it.name == trimmedName }) { "取证项名称已存在" }
    val otherItem = requireNotNull(template.items.firstOrNull { it.name == OTHER_ITEM_NAME }) { "模板缺少“其他”保留项" }
    val newOrder = otherItem.displayOrder
    dao.shiftTemplateItemOrders(templateId, newOrder)
    dao.insertTemplateItem(
      TemplateItemEntity(
        templateId = templateId,
        name = trimmedName,
        displayOrder = newOrder,
        isKeyScreenshot = false,
        wordOrder = null,
        excludedFromProgress = false,
      ),
    )
  }

  suspend fun updateTemplateItem(
    itemId: Long,
    name: String,
    displayOrder: Int,
    isKeyScreenshot: Boolean,
    wordOrder: Int?,
  ) = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "取证项名称不能为空" }
    require(displayOrder >= 0) { "App 显示顺序必须是非负整数" }
    require(wordOrder == null || wordOrder >= 0) { "Word 顺序必须是非负整数" }
    val current = requireNotNull(dao.getTemplateItem(itemId)) { "取证项不存在" }
    check(current.name != OTHER_ITEM_NAME) { "“其他”是模板保留项" }
    val template = requireNotNull(dao.getDeviceType(current.templateId)) { "设备类型不存在" }
    check(template.items.none { it.id != itemId && it.name == trimmedName }) { "取证项名称已存在" }
    check(dao.updateTemplateItem(itemId, trimmedName, displayOrder, isKeyScreenshot, wordOrder) == 1) { "取证项不存在" }
  }

  suspend fun reorderTemplateItems(templateId: Long, orderedItemIds: List<Long>) = database.withTransaction {
    val template = requireNotNull(dao.getDeviceType(templateId)) { "设备类型不存在" }
    val itemsById = template.items.associateBy { it.id }
    require(orderedItemIds.size == itemsById.size && orderedItemIds.toSet() == itemsById.keys) { "取证项顺序数据不完整" }
    check(itemsById.getValue(orderedItemIds.last()).name == OTHER_ITEM_NAME) { "“其他”必须保留在末尾" }
    orderedItemIds.forEachIndexed { index, itemId ->
      check(dao.updateTemplateItemDisplayOrder(itemId, index) == 1) { "取证项不存在" }
    }
  }

  suspend fun deleteTemplateItem(itemId: Long) = database.withTransaction {
    val item = requireNotNull(dao.getTemplateItem(itemId)) { "取证项不存在" }
    check(item.name != OTHER_ITEM_NAME) { "不能删除“其他”取证项" }
    check(dao.deleteTemplateItem(itemId) == 1) { "取证项删除失败" }
  }

  suspend fun previewTemplateImport(packageData: TemplateTransferPackage): TemplateImportPreview = database.withTransaction {
    val existingNames = dao.getDeviceTypes().map { it.template.name }.toSet()
    TemplateImportPreview(
      templateCount = packageData.templates.size,
      duplicateNames = packageData.templates.map { it.name }.filter { it in existingNames },
    )
  }

  suspend fun importDeviceTypes(
    packageData: TemplateTransferPackage,
    conflictStrategy: TemplateConflictStrategy,
  ): TemplateImportResult = database.withTransaction {
    ensureProjectOverviewTemplate()
    val existingTemplates = dao.getDeviceTypes().associateBy { it.template.name }.toMutableMap()
    val usedNames = existingTemplates.keys.toMutableSet()
    var nextSortOrder = dao.maxDeviceTypeOrder() + 1
    var importedCount = 0
    var overwrittenCount = 0
    var renamedCount = 0
    var skippedCount = 0

    packageData.templates.forEach { imported ->
      val existing = existingTemplates[imported.name]
      when {
        existing?.template?.isSystemTemplate == true && conflictStrategy == TemplateConflictStrategy.OVERWRITE -> {
          dao.deleteTemplateItems(existing.template.id)
          insertImportedItems(existing.template.id, imported.items)
          overwrittenCount++
        }
        existing?.template?.isSystemTemplate == true -> skippedCount++
        existing == null -> {
          val templateId = insertImportedTemplate(imported.name, nextSortOrder++, imported.items)
          existingTemplates[imported.name] = requireNotNull(dao.getDeviceType(templateId))
          usedNames += imported.name
          importedCount++
        }
        conflictStrategy == TemplateConflictStrategy.SKIP -> skippedCount++
        conflictStrategy == TemplateConflictStrategy.OVERWRITE -> {
          dao.deleteTemplateItems(existing.template.id)
          insertImportedItems(existing.template.id, imported.items)
          overwrittenCount++
        }
        else -> {
          val renamed = uniqueImportedTemplateName(imported.name, usedNames)
          val templateId = insertImportedTemplate(renamed, nextSortOrder++, imported.items)
          existingTemplates[renamed] = requireNotNull(dao.getDeviceType(templateId))
          usedNames += renamed
          importedCount++
          renamedCount++
        }
      }
    }
    TemplateImportResult(importedCount, overwrittenCount, renamedCount, skippedCount)
  }

  private suspend fun insertImportedTemplate(
    name: String,
    sortOrder: Int,
    items: List<TransferredTemplateItem>,
  ): Long {
    val templateId = dao.insertDeviceType(
      DeviceTypeTemplateEntity(name = name, createdAt = System.currentTimeMillis(), sortOrder = sortOrder),
    )
    check(templateId > 0) { "设备类型导入失败" }
    insertImportedItems(templateId, items)
    return templateId
  }

  private suspend fun insertImportedItems(templateId: Long, items: List<TransferredTemplateItem>) {
    dao.insertTemplateItems(
      items.mapIndexed { index, item ->
        TemplateItemEntity(
          templateId = templateId,
          name = item.name,
          displayOrder = index,
          isKeyScreenshot = item.isKeyScreenshot,
          wordOrder = item.wordOrder,
          excludedFromProgress = item.excludedFromProgress,
        )
      },
    )
  }

  private fun uniqueImportedTemplateName(baseName: String, usedNames: Set<String>): String {
    val first = "$baseName（导入）"
    if (first !in usedNames) return first
    var suffix = 2
    while ("$baseName（导入$suffix）" in usedNames) suffix++
    return "$baseName（导入$suffix）"
  }

  suspend fun ensureDefaultTemplates() {
    database.withTransaction {
      ensureProjectOverviewTemplate()
    }
  }

  suspend fun createProject(name: String): Long = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "项目名称不能为空" }
    val overviewTemplate = ensureProjectOverviewTemplate()
    val now = System.currentTimeMillis()
    val projectId = dao.insertProject(ProjectEntity(name = trimmedName, createdAt = now))
    val deviceId = dao.insertDevice(
      DeviceEntity(
        projectId = projectId,
        name = PROJECT_OVERVIEW_DEVICE_NAME,
        typeName = overviewTemplate.template.name,
        createdAt = now,
      ),
    )
    dao.insertEvidenceItems(
      overviewTemplate.items.sortedBy { it.displayOrder }.map { source ->
        EvidenceItemEntity(
          deviceId = deviceId,
          name = source.name,
          displayOrder = source.displayOrder,
          isKeyScreenshot = source.isKeyScreenshot,
          wordOrder = source.wordOrder,
          excludedFromProgress = source.excludedFromProgress,
          isTemporary = false,
          updatedAt = now,
        )
      },
    )
    projectId
  }

  suspend fun renameProject(projectId: Long, name: String) = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "项目名称不能为空" }
    requireNotNull(dao.getProject(projectId)) { "项目不存在" }
    check(dao.updateProjectName(projectId, trimmedName) == 1) { "项目不存在" }
  }

  suspend fun deleteProject(projectId: Long): PhotoCleanupResult {
    val photoPaths = database.withTransaction {
      requireNotNull(dao.getProject(projectId)) { "项目不存在" }
      val paths = dao.getDevices(projectId)
        .flatMap { device -> device.items }
        .flatMap { item -> item.photos }
        .map { photo -> photo.path }
      check(dao.deleteProject(projectId) == 1) { "项目删除失败" }
      paths
    }
    return deletePhotoFiles(photoPaths)
  }

  suspend fun createDevice(projectId: Long, name: String, templateId: Long): Long =
    database.withTransaction {
      val template = requireNotNull(dao.getDeviceType(templateId)) { "设备类型不存在" }
      check(!template.template.isSystemTemplate) { "“项目整体情况”只能在创建项目时自动添加" }
      val now = System.currentTimeMillis()
      val deviceId = dao.insertDevice(
        DeviceEntity(
          projectId = projectId,
          name = name.trim(),
          typeName = template.template.name,
          createdAt = now,
        ),
      )
      dao.insertEvidenceItems(
        template.items.map { source ->
          EvidenceItemEntity(
            deviceId = deviceId,
            name = source.name,
            displayOrder = source.displayOrder,
            isKeyScreenshot = source.isKeyScreenshot,
            wordOrder = source.wordOrder,
            excludedFromProgress = source.excludedFromProgress,
            isTemporary = false,
            updatedAt = now,
          )
        },
      )
      deviceId
    }

  suspend fun renameDevice(deviceId: Long, name: String) = database.withTransaction {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "设备名称不能为空" }
    requireNotNull(dao.getDevice(deviceId)) { "设备不存在" }
    check(dao.updateDeviceName(deviceId, trimmedName) == 1) { "设备不存在" }
  }

  suspend fun deleteDevice(deviceId: Long): PhotoCleanupResult {
    val photoPaths = database.withTransaction {
      val device = requireNotNull(dao.getDeviceWithItems(deviceId)) { "设备不存在" }
      val paths = device.items.flatMap { item -> item.photos }.map { photo -> photo.path }
      check(dao.deleteDevice(deviceId) == 1) { "设备删除失败" }
      paths
    }
    return deletePhotoFiles(photoPaths)
  }

  suspend fun addTemporaryEvidenceItem(deviceId: Long, name: String): Long {
    val nextOrder = dao.maxEvidenceItemOrder(deviceId) + 1
    return dao.insertEvidenceItem(
      EvidenceItemEntity(
        deviceId = deviceId,
        name = name.trim(),
        displayOrder = nextOrder,
        isKeyScreenshot = false,
        wordOrder = null,
        excludedFromProgress = false,
        isTemporary = true,
        updatedAt = System.currentTimeMillis(),
      ),
    )
  }

  suspend fun updateNote(itemId: Long, note: String) {
    check(dao.updateNote(itemId, note, System.currentTimeMillis()) == 1) { "取证项不存在" }
  }

  suspend fun updateConclusion(itemId: Long, conclusion: EvidenceConclusion) {
    val isCompliant = conclusion != EvidenceConclusion.NON_COMPLIANT
    val isNotApplicable = conclusion == EvidenceConclusion.NOT_APPLICABLE
    check(dao.updateConclusion(itemId, isCompliant, isNotApplicable, System.currentTimeMillis()) == 1) {
      "取证项不存在"
    }
  }

  suspend fun registerPhoto(itemId: Long, file: File, sha256: String): Long =
    registerPhotos(itemId, listOf(PhotoRegistration(file, sha256))).single()

  suspend fun registerPhotos(itemId: Long, photos: List<PhotoRegistration>): List<Long> =
    database.withTransaction {
      requireNotNull(dao.getEvidenceItem(itemId)) { "取证项不存在" }
      var nextOrder = dao.maxPhotoOrder(itemId) + 1
      photos.map { registration ->
        check(registration.file.isFile && registration.file.length() > 0) { "照片文件未成功保存" }
        dao.insertPhoto(
          PhotoEntity(
            evidenceItemId = itemId,
            path = registration.file.absolutePath,
            createdAt = System.currentTimeMillis(),
            sortOrder = nextOrder++,
            sha256 = registration.sha256,
          ),
        )
      }
    }

  suspend fun deletePhoto(photoId: Long) {
    val path = database.withTransaction {
      val photo = dao.getPhoto(photoId) ?: return@withTransaction null
      check(dao.deletePhoto(photoId) == 1) { "照片不存在" }
      photo.path
    }
    path?.let { deletedPath ->
      withContext(Dispatchers.IO) {
        File(deletedPath).delete()
      }
    }
  }

  private suspend fun deletePhotoFiles(paths: List<String>): PhotoCleanupResult = withContext(Dispatchers.IO) {
    var failedFileCount = 0
    paths.distinct().forEach { path ->
      val file = File(path)
      if (file.exists() && !file.delete()) failedFileCount += 1
    }
    PhotoCleanupResult(photoCount = paths.size, failedFileCount = failedFileCount)
  }

  private suspend fun ensureProjectOverviewTemplate(): DeviceTypeWithItems {
    dao.findSystemDeviceType(PROJECT_OVERVIEW_SYSTEM_KEY)?.let { return it }
    dao.findDeviceType(PROJECT_OVERVIEW_TYPE_NAME)?.let { existing ->
      check(dao.markSystemTemplate(existing.id, PROJECT_OVERVIEW_SYSTEM_KEY) == 1) { "初始化“项目整体情况”模板失败" }
      return requireNotNull(dao.getDeviceType(existing.id))
    }
    val templateId = dao.insertDeviceType(
      DeviceTypeTemplateEntity(
        name = PROJECT_OVERVIEW_TYPE_NAME,
        createdAt = System.currentTimeMillis(),
        sortOrder = 0,
        isSystemTemplate = true,
        systemKey = PROJECT_OVERVIEW_SYSTEM_KEY,
      ),
    )
    check(templateId > 0) { "初始化“项目整体情况”模板失败" }
    dao.insertTemplateItem(
      TemplateItemEntity(
        templateId = templateId,
        name = OTHER_ITEM_NAME,
        displayOrder = 0,
        isKeyScreenshot = false,
        wordOrder = null,
        excludedFromProgress = true,
      ),
    )
    return requireNotNull(dao.getDeviceType(templateId))
  }

  private data class DefaultItem(val name: String, val isKey: Boolean = false, val wordOrder: Int? = null)

  private data class DefaultTemplateSpec(val name: String, val items: List<DefaultItem>)

  private companion object {
    const val OTHER_ITEM_NAME = "其他"
    const val DEFAULT_TYPE_NAME = "防火墙"
    const val PROJECT_OVERVIEW_TYPE_NAME = "项目整体情况"
    const val PROJECT_OVERVIEW_DEVICE_NAME = "项目整体情况"
    const val PROJECT_OVERVIEW_SYSTEM_KEY = "project_overview"

    val DEFAULT_TEMPLATE_SPECS: List<DefaultTemplateSpec> by lazy {
      listOf(DefaultTemplateSpec(DEFAULT_TYPE_NAME, FIREWALL_ITEMS)) + parseTemplateSpecs(DEFAULT_TEMPLATE_TEXT)
    }

    private fun parseTemplateSpecs(text: String): List<DefaultTemplateSpec> =
      text.trim().split(Regex("\\r?\\n\\s*\\r?\\n")).mapNotNull { block ->
        val lines = block.lines().map(String::trim).filter(String::isNotEmpty)
        val templateName = lines.firstOrNull() ?: return@mapNotNull null
        var nextWordOrder = 0
        val items = lines.drop(1).map { line ->
          val isKey = line.endsWith(KEY_MARKER)
          val itemName = if (isKey) line.removeSuffix(KEY_MARKER).trim() else line
          DefaultItem(
            name = itemName,
            isKey = isKey,
            wordOrder = if (isKey) nextWordOrder++ else null,
          )
        } + DefaultItem(OTHER_ITEM_NAME)
        DefaultTemplateSpec(templateName, items)
      }

    private const val KEY_MARKER = "（关键设备截图项）"

    private val DEFAULT_TEMPLATE_TEXT = """
隔离装置
正面照（关键设备截图项）
策略（关键设备截图项）
登录失败锁定
超时退出时间
密码复杂度
密码有效期
日志（关键设备截图项）
日志转发（关键设备截图项）
管理员
时间

纵向加密
正面照（关键设备截图项）
策略（关键设备截图项）
登录失败锁定
超时退出时间
密码复杂度
密码有效期
日志（关键设备截图项）
日志转发（关键设备截图项）
管理员
时间

堡垒机
版本
正面照（关键设备截图项）
接入设备清单（关键设备截图项）
日志（关键设备截图项）
日志转发（关键设备截图项）
密码复杂度
密码有效期
超时退出时间
登录失败锁定
时间
管理员

网络安全管理平台
版本
正面照（关键设备截图项）
接入设备清单（关键设备截图项）
日志（关键设备截图项）
密码复杂度
密码有效期
超时退出时间
登录失败锁定
时间
管理员

应用系统（B/S）
网址
版本及开发厂商全称
管理员账户
日志记录
密码复杂度
密码有效期
登录失败锁定
超时退出时间
剩余信息保护
https加密套件
是否有重要个人信息
存储保密性、完整性

应用系统（C/S）
版本及开发厂商全称
管理员账户
日志记录
密码复杂度
密码有效期
登录失败锁定
超时退出时间
剩余信息保护
是否有重要个人信息
存储保密性、完整性

入侵检测
正面照（关键设备截图项）
策略（关键设备截图项）
规则库版本及更新日期（关键设备截图项）
登录失败锁定
超时退出时间
密码复杂度
密码有效期
日志（关键设备截图项）
日志转发（关键设备截图项）
管理员
时间
snmp
https加密算法

恶意代码终端管理
正面照（关键设备截图项）
规则库版本及更新日期（关键设备截图项）
接入设备清单（关键设备截图项）
登录失败锁定
超时退出
密码复杂度
密码有效期
日志（关键设备截图项）
日志转发（关键设备截图项）
管理员
时间

恶意代码流量采集
正面照（关键设备截图项）
规则库版本及更新日期（关键设备截图项）
登录失败锁定
超时退出
密码复杂度
密码有效期
日志（关键设备截图项）
日志转发（关键设备截图项）
管理员
时间

物理机房
物理位置 共几层位于几层
有没有窗户
电子门禁
视频监控
灭火
温湿度
温湿度越限报警
漏水检测
是否有UPS 电池数量 容量
防静电地板
防静电手环
动环
网线电源线桥架

Linux
设备型号
操作系统版本
IP
密码复杂度
登录失败锁定
密码有效期
超时退出时间
端口
ssh登录地址限制
ssh加密算法
auditd rsyslog是否运行
日志转发 rsyslog.conf
日志流转时间 logrotate.conf
日志留存
用户
时间
防病毒软件

机房（密评）
机房门禁
机房门禁记录
机房监控摄像头
机房监控界面
机房监控记录存储位置（可选）

纵向加密（密评）
设备正面照
登录界面
登录智能密码钥匙
智能密码钥匙的数字证书
隧道配置
策略配置
证书列表
数字证书（RSA）
数字证书（SM2）

反向隔离（密评）
设备正面照
登录界面
登录智能密码钥匙
智能密码钥匙的数字证书
隧道配置
策略配置
证书列表
数字证书（RSA）
数字证书（SM2）
"""

    val FIREWALL_ITEMS = listOf(
      DefaultItem("正面照", isKey = true, wordOrder = 0),
      DefaultItem("版本"),
      DefaultItem("安全策略", isKey = true, wordOrder = 1),
      DefaultItem("地址/地址组", isKey = true, wordOrder = 3),
      DefaultItem("服务/服务组", isKey = true, wordOrder = 2),
      DefaultItem("时间"),
      DefaultItem("管理员"),
      DefaultItem("日志", isKey = true, wordOrder = 4),
      DefaultItem("日志转发", isKey = true, wordOrder = 5),
      DefaultItem("密码复杂度"),
      DefaultItem("密码有效期"),
      DefaultItem("超时时间"),
      DefaultItem("登录失败锁定"),
      DefaultItem("完整性算法"),
      DefaultItem("SNMP"),
      DefaultItem("其他"),
    )
  }
}
