package com.example.dengbaoevidence.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
  @Query(
    """
      SELECT p.*, COUNT(d.id) AS deviceCount
      FROM projects p
      LEFT JOIN devices d ON d.projectId = p.id
      GROUP BY p.id
      ORDER BY p.createdAt DESC
    """
  )
  fun observeProjects(): Flow<List<ProjectSummary>>

  @Query("SELECT * FROM projects WHERE id = :projectId")
  fun observeProject(projectId: Long): Flow<ProjectEntity?>

  @Query("SELECT * FROM projects WHERE id = :projectId")
  suspend fun getProject(projectId: Long): ProjectEntity?

  @Query("UPDATE projects SET name = :name WHERE id = :projectId")
  suspend fun updateProjectName(projectId: Long, name: String): Int

  @Query("DELETE FROM projects WHERE id = :projectId")
  suspend fun deleteProject(projectId: Long): Int

  @Insert
  suspend fun insertProject(project: ProjectEntity): Long

  @Transaction
  @Query("SELECT * FROM device_type_templates ORDER BY sortOrder, createdAt, name")
  fun observeDeviceTypes(): Flow<List<DeviceTypeWithItems>>

  @Transaction
  @Query("SELECT * FROM device_type_templates WHERE id = :templateId")
  suspend fun getDeviceType(templateId: Long): DeviceTypeWithItems?

  @Transaction
  @Query("SELECT * FROM device_type_templates ORDER BY sortOrder, createdAt, name")
  suspend fun getDeviceTypes(): List<DeviceTypeWithItems>

  @Transaction
  @Query("SELECT * FROM device_type_templates WHERE systemKey = :systemKey LIMIT 1")
  suspend fun findSystemDeviceType(systemKey: String): DeviceTypeWithItems?

  @Query("SELECT * FROM device_type_templates WHERE name = :name LIMIT 1")
  suspend fun findDeviceType(name: String): DeviceTypeTemplateEntity?

  @Query("UPDATE device_type_templates SET isSystemTemplate = 1, systemKey = :systemKey WHERE id = :templateId")
  suspend fun markSystemTemplate(templateId: Long, systemKey: String): Int

  @Query("SELECT COUNT(*) FROM template_items WHERE templateId = :templateId")
  suspend fun templateItemCount(templateId: Long): Int

  @Query("SELECT COUNT(*) FROM device_type_templates")
  suspend fun deviceTypeCount(): Int

  @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM device_type_templates")
  suspend fun maxDeviceTypeOrder(): Int

  @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM template_items WHERE templateId = :templateId")
  suspend fun maxTemplateItemOrder(templateId: Long): Int

  @Query(
    "UPDATE template_items SET displayOrder = displayOrder + 1 " +
      "WHERE templateId = :templateId AND displayOrder >= :fromOrder",
  )
  suspend fun shiftTemplateItemOrders(templateId: Long, fromOrder: Int): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertDeviceType(template: DeviceTypeTemplateEntity): Long

  @Insert
  suspend fun insertTemplateItems(items: List<TemplateItemEntity>)

  @Insert
  suspend fun insertTemplateItem(item: TemplateItemEntity): Long

  @Query("UPDATE device_type_templates SET name = :name WHERE id = :templateId")
  suspend fun updateDeviceTypeName(templateId: Long, name: String): Int

  @Query("UPDATE device_type_templates SET sortOrder = :sortOrder WHERE id = :templateId")
  suspend fun updateDeviceTypeOrder(templateId: Long, sortOrder: Int): Int

  @Query("DELETE FROM device_type_templates WHERE id = :templateId")
  suspend fun deleteDeviceType(templateId: Long): Int

  @Query("SELECT * FROM template_items WHERE id = :itemId")
  suspend fun getTemplateItem(itemId: Long): TemplateItemEntity?

  @Query(
    """
      UPDATE template_items
      SET name = :name,
          displayOrder = :displayOrder,
          isKeyScreenshot = :isKeyScreenshot,
          wordOrder = :wordOrder
      WHERE id = :itemId
    """
  )
  suspend fun updateTemplateItem(
    itemId: Long,
    name: String,
    displayOrder: Int,
    isKeyScreenshot: Boolean,
    wordOrder: Int?,
  ): Int

  @Query("UPDATE template_items SET displayOrder = :displayOrder WHERE id = :itemId")
  suspend fun updateTemplateItemDisplayOrder(itemId: Long, displayOrder: Int): Int

  @Query("DELETE FROM template_items WHERE id = :itemId")
  suspend fun deleteTemplateItem(itemId: Long): Int

  @Query("DELETE FROM template_items WHERE templateId = :templateId")
  suspend fun deleteTemplateItems(templateId: Long): Int

  @Transaction
  @Query(
    """
      SELECT d.*
      FROM devices d
      LEFT JOIN device_type_templates t ON t.name = d.typeName
      WHERE d.projectId = :projectId
      ORDER BY COALESCE(t.sortOrder, 2147483647), d.typeName, d.createdAt
    """
  )
  fun observeDevices(projectId: Long): Flow<List<DeviceWithItems>>

  @Transaction
  @Query(
    """
      SELECT d.*
      FROM devices d
      LEFT JOIN device_type_templates t ON t.name = d.typeName
      WHERE d.projectId = :projectId
      ORDER BY COALESCE(t.sortOrder, 2147483647), d.typeName, d.createdAt
    """
  )
  suspend fun getDevices(projectId: Long): List<DeviceWithItems>

  @Transaction
  @Query("SELECT * FROM devices WHERE id = :deviceId")
  fun observeDevice(deviceId: Long): Flow<DeviceWithItems?>

  @Query("SELECT * FROM devices WHERE id = :deviceId")
  suspend fun getDevice(deviceId: Long): DeviceEntity?

  @Transaction
  @Query("SELECT * FROM devices WHERE id = :deviceId")
  suspend fun getDeviceWithItems(deviceId: Long): DeviceWithItems?

  @Query("UPDATE devices SET name = :name WHERE id = :deviceId")
  suspend fun updateDeviceName(deviceId: Long, name: String): Int

  @Query("DELETE FROM devices WHERE id = :deviceId")
  suspend fun deleteDevice(deviceId: Long): Int

  @Insert
  suspend fun insertDevice(device: DeviceEntity): Long

  @Insert
  suspend fun insertEvidenceItems(items: List<EvidenceItemEntity>)

  @Insert
  suspend fun insertEvidenceItem(item: EvidenceItemEntity): Long

  @Transaction
  @Query("SELECT * FROM evidence_items WHERE id = :itemId")
  fun observeEvidenceItem(itemId: Long): Flow<EvidenceItemWithPhotos?>

  @Query("SELECT * FROM evidence_items WHERE id = :itemId")
  suspend fun getEvidenceItem(itemId: Long): EvidenceItemEntity?

  @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM evidence_items WHERE deviceId = :deviceId")
  suspend fun maxEvidenceItemOrder(deviceId: Long): Int

  @Query("UPDATE evidence_items SET note = :note, updatedAt = :updatedAt WHERE id = :itemId")
  suspend fun updateNote(itemId: Long, note: String, updatedAt: Long): Int

  @Query(
    "UPDATE evidence_items SET isCompliant = :isCompliant, isNotApplicable = :isNotApplicable, " +
      "updatedAt = :updatedAt WHERE id = :itemId",
  )
  suspend fun updateConclusion(
    itemId: Long,
    isCompliant: Boolean,
    isNotApplicable: Boolean,
    updatedAt: Long,
  ): Int

  @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM photos WHERE evidenceItemId = :itemId")
  suspend fun maxPhotoOrder(itemId: Long): Int

  @Query("SELECT * FROM photos WHERE id = :photoId")
  suspend fun getPhoto(photoId: Long): PhotoEntity?

  @Query("DELETE FROM photos WHERE id = :photoId")
  suspend fun deletePhoto(photoId: Long): Int

  @Insert
  suspend fun insertPhoto(photo: PhotoEntity): Long
}
