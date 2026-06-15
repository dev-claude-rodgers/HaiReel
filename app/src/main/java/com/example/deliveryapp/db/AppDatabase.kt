package com.rodgers.routist.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.model.WorkRecord

@Database(
    entities = [WorkRecord::class, TenkoRecord::class, DeliveryEntity::class],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workRecordDao(): WorkRecordDao
    abstract fun tenkoDao(): TenkoDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v4→v5: work_records に assignmentId 追加
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_records ADD COLUMN assignmentId TEXT NOT NULL DEFAULT ''")
            }
        }

        // v5→v6: tenko_records に assignmentId 追加
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tenko_records ADD COLUMN assignmentId TEXT NOT NULL DEFAULT ''")
            }
        }

        // v6→v7: tenko_records に vehicleNumber 追加
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tenko_records ADD COLUMN vehicleNumber TEXT")
            }
        }

        // v7→v8: deliveries テーブル追加（SharedPreferences JSON → Room）
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `deliveries` (
                        `id` TEXT NOT NULL,
                        `group_id` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `name` TEXT,
                        `address` TEXT NOT NULL,
                        `geocoded_address` TEXT,
                        `note` TEXT,
                        `photo_uri` TEXT,
                        `photo_uris_json` TEXT,
                        `rooms_json` TEXT,
                        `time_slot` TEXT,
                        `package_count` INTEGER NOT NULL,
                        `lat` REAL NOT NULL,
                        `lng` REAL NOT NULL,
                        `is_completed` INTEGER NOT NULL,
                        `is_geocoded` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deliveries_group_id` ON `deliveries` (`group_id`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigrationFrom(1, 2, 3)  // v1〜v3 はリリース前の開発版のみ
                .build()
                .also { INSTANCE = it }
            }
    }
}
