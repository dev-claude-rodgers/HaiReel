package com.rodgers.haireel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.model.WorkRecord

@Database(
    entities = [WorkRecord::class, TenkoRecord::class, DeliveryEntity::class, DeliveryGroupEntity::class, GeocodingCacheEntity::class, KnownAddressEntity::class],
    version = 14,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workRecordDao(): WorkRecordDao
    abstract fun tenkoDao(): TenkoDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun deliveryGroupDao(): DeliveryGroupDao
    abstract fun geocodingCacheDao(): GeocodingCacheDao
    abstract fun knownAddressDao(): KnownAddressDao

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

        // v11→v12: known_addresses テーブル追加（住所履歴・オートコンプリート用）
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `known_addresses` (
                        `address` TEXT NOT NULL,
                        `name` TEXT,
                        `deliveryCount` INTEGER NOT NULL,
                        `lastDeliveredAt` INTEGER NOT NULL,
                        PRIMARY KEY(`address`)
                    )
                """.trimIndent())
            }
        }

        // v13→v14: deliveries に name_kana カラム追加（TTS読み上げ用ふりがな）
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deliveries` ADD COLUMN `name_kana` TEXT")
            }
        }

        // v12→v13: known_addresses を正しいスキーマで再作成（v12 でスキーマ不一致があったため）
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `known_addresses`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `known_addresses` (
                        `address` TEXT NOT NULL,
                        `name` TEXT,
                        `deliveryCount` INTEGER NOT NULL,
                        `lastDeliveredAt` INTEGER NOT NULL,
                        PRIMARY KEY(`address`)
                    )
                """.trimIndent())
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigrationFrom(1, 2, 3)  // v1〜v3 はリリース前の開発版のみ
                .build()
                .also { INSTANCE = it }
            }
    }
}
