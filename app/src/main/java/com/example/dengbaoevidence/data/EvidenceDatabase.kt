package com.example.dengbaoevidence.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    ProjectEntity::class,
    DeviceTypeTemplateEntity::class,
    TemplateItemEntity::class,
    DeviceEntity::class,
    EvidenceItemEntity::class,
    PhotoEntity::class,
  ],
  version = 6,
  exportSchema = false,
)
abstract class EvidenceDatabase : RoomDatabase() {
  abstract fun evidenceDao(): EvidenceDao

  companion object {
    @Volatile private var instance: EvidenceDatabase? = null

    fun getInstance(context: Context): EvidenceDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          EvidenceDatabase::class.java,
          "evidence.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
      }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE device_type_templates ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
      }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE evidence_items ADD COLUMN isNotApplicable INTEGER NOT NULL DEFAULT 0")
      }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE device_type_templates ADD COLUMN isSystemTemplate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE device_type_templates ADD COLUMN systemKey TEXT")
      }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE devices ADD COLUMN requiresEvidence INTEGER NOT NULL DEFAULT 1")
      }
    }

    internal val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE template_items ADD COLUMN verificationMethod TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE evidence_items ADD COLUMN verificationMethod TEXT NOT NULL DEFAULT ''")
      }
    }
  }
}
