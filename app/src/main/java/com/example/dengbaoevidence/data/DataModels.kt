package com.example.dengbaoevidence.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAt: Long,
)

@Entity(
  tableName = "device_type_templates",
  indices = [Index(value = ["name"], unique = true)],
)
data class DeviceTypeTemplateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAt: Long,
  @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
  @ColumnInfo(defaultValue = "0") val isSystemTemplate: Boolean = false,
  val systemKey: String? = null,
)

@Entity(
  tableName = "template_items",
  foreignKeys = [
    ForeignKey(
      entity = DeviceTypeTemplateEntity::class,
      parentColumns = ["id"],
      childColumns = ["templateId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("templateId")],
)
data class TemplateItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val templateId: Long,
  val name: String,
  val displayOrder: Int,
  val isKeyScreenshot: Boolean,
  val wordOrder: Int?,
  val excludedFromProgress: Boolean = false,
)

@Entity(
  tableName = "devices",
  foreignKeys = [
    ForeignKey(
      entity = ProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("projectId")],
)
data class DeviceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: Long,
  val name: String,
  val typeName: String,
  val createdAt: Long,
)

@Entity(
  tableName = "evidence_items",
  foreignKeys = [
    ForeignKey(
      entity = DeviceEntity::class,
      parentColumns = ["id"],
      childColumns = ["deviceId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("deviceId")],
)
data class EvidenceItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val deviceId: Long,
  val name: String,
  val displayOrder: Int,
  val isKeyScreenshot: Boolean,
  val wordOrder: Int?,
  val excludedFromProgress: Boolean,
  val isTemporary: Boolean,
  val isCompliant: Boolean = true,
  @ColumnInfo(defaultValue = "0") val isNotApplicable: Boolean = false,
  val note: String = "",
  val updatedAt: Long,
)

enum class EvidenceConclusion {
  COMPLIANT,
  NON_COMPLIANT,
  NOT_APPLICABLE,
}

val EvidenceItemEntity.conclusion: EvidenceConclusion
  get() = when {
    isNotApplicable -> EvidenceConclusion.NOT_APPLICABLE
    isCompliant -> EvidenceConclusion.COMPLIANT
    else -> EvidenceConclusion.NON_COMPLIANT
  }

@Entity(
  tableName = "photos",
  foreignKeys = [
    ForeignKey(
      entity = EvidenceItemEntity::class,
      parentColumns = ["id"],
      childColumns = ["evidenceItemId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("evidenceItemId"), Index(value = ["path"], unique = true)],
)
data class PhotoEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val evidenceItemId: Long,
  val path: String,
  val createdAt: Long,
  val sortOrder: Int,
  val sha256: String,
)

data class ProjectSummary(
  @Embedded val project: ProjectEntity,
  val deviceCount: Int,
)

data class ProjectExportData(
  val project: ProjectEntity,
  val devices: List<DeviceWithItems>,
)

data class DeviceTypeWithItems(
  @Embedded val template: DeviceTypeTemplateEntity,
  @Relation(parentColumn = "id", entityColumn = "templateId")
  val items: List<TemplateItemEntity>,
)

data class EvidenceItemWithPhotos(
  @Embedded val item: EvidenceItemEntity,
  @Relation(parentColumn = "id", entityColumn = "evidenceItemId")
  val photos: List<PhotoEntity>,
) {
  val isRecorded: Boolean get() = item.isNotApplicable || item.note.isNotBlank() || photos.isNotEmpty()
}

data class DeviceWithItems(
  @Embedded val device: DeviceEntity,
  @Relation(
    entity = EvidenceItemEntity::class,
    parentColumn = "id",
    entityColumn = "deviceId",
  )
  val items: List<EvidenceItemWithPhotos>,
) {
  val orderedItems: List<EvidenceItemWithPhotos> get() = items.sortedBy { it.item.displayOrder }
  val progressItems: List<EvidenceItemWithPhotos> get() = items.filterNot { it.item.excludedFromProgress }
  val recordedCount: Int get() = progressItems.count { it.isRecorded }
  val totalCount: Int get() = progressItems.size
  val missingKeyScreenshotCount: Int
    get() = items.count { it.item.isKeyScreenshot && it.photos.isEmpty() }
}
