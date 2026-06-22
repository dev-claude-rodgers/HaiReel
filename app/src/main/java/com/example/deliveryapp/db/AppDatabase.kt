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
    entities = [WorkRecord::class, TenkoRecord::class, DeliveryEntity::class, DeliveryGroupEntity::class, GeocodingCacheEntity::class],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workRecordDao(): WorkRecordDao
    abstract fun tenkoDao(): TenkoDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun deliveryGroupDao(): DeliveryGroupDao
    abstract fun geocodingCacheDao(): GeocodingCacheDao

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

        // v8→v9: delivery_groups テーブル追加（SharedPreferences JSON → Room）
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `delivery_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color_hex` TEXT NOT NULL,
                        `pattern_id` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        // v10→v11: geocoding_cache に formattedAddress カラム追加
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `geocoding_cache` ADD COLUMN `formattedAddress` TEXT NOT NULL DEFAULT ''")
            }
        }

        // v9→v10: geocoding_cache テーブル追加
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `geocoding_cache` (
                        `address` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lng` REAL NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`address`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigrationFrom(1, 2, 3)  // v1〜v3 はリリース前の開発版のみ
                .build()
                .also { INSTANCE = it }
            }
    }
}
