package com.example.dengbaoevidence.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VerificationMethodMigrationTest {
  @Test fun migratesVersionFiveInPlaceAndRoomValidatesSchema() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val name = "migration_method_${System.nanoTime()}.db"
    // Derive the previous schema from Room's current DDL without the two additive columns.
    val memory = Room.inMemoryDatabaseBuilder(context, EvidenceDatabase::class.java).build()
    val ddl = memory.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name").use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.getString(0).replace(", `verificationMethod` TEXT NOT NULL DEFAULT ''", "")) }
    }
    memory.close()
    val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          ddl.forEach(db::execSQL)
          db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
          db.execSQL("INSERT INTO room_master_table VALUES (42, 'version-five-test-schema')")
          db.execSQL("INSERT INTO projects VALUES (1, 'Retained project', 123)")
          db.execSQL("INSERT INTO device_type_templates VALUES (1, 'Retained type', 123, 0, 0, NULL)")
          db.execSQL("INSERT INTO template_items VALUES (1, 1, 'Policy', 0, 1, 0, 0)")
          db.execSQL("INSERT INTO devices VALUES (1, 1, 'Retained device', 'Retained type', 123, 0)")
          db.execSQL("INSERT INTO evidence_items (id,deviceId,name,displayOrder,isKeyScreenshot,wordOrder,excludedFromProgress,isTemporary,isCompliant,isNotApplicable,note,updatedAt) VALUES (1,1,'Policy',0,1,0,0,0,0,0,'Preserved note',123)")
          db.execSQL("INSERT INTO photos (id,evidenceItemId,path,createdAt,sortOrder,sha256) VALUES (1,1,'originals/retained.heic',123,0,'retained hash')")
        }
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }).build())
    try {
      helper.writableDatabase
      helper.close()
      val migrated = Room.databaseBuilder(context, EvidenceDatabase::class.java, name)
        .addMigrations(EvidenceDatabase.MIGRATION_5_6).build()
      try {
        val dao = migrated.evidenceDao()
        assertEquals("Retained project", dao.getProject(1)?.name)
        assertEquals("", dao.getTemplateItem(1)?.verificationMethod)
        val device = requireNotNull(dao.getDeviceWithItems(1))
        assertFalse(device.device.requiresEvidence)
        val item = device.items.single()
        assertEquals("", item.item.verificationMethod)
        assertEquals("Preserved note", item.item.note)
        assertFalse(item.item.isCompliant)
        assertEquals("originals/retained.heic", item.photos.single().path)
        assertEquals("retained hash", item.photos.single().sha256)
      } finally { migrated.close() }
    } finally { helper.close(); context.deleteDatabase(name) }
  }
}
