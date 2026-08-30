package com.rodgers.haireel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rodgers.haireel.model.FuelRecord
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.model.Vehicle
import com.rodgers.haireel.model.WorkRecord

@Database(
    entities = [WorkRecord::class, TenkoRecord::class, DeliveryEntity::class, DeliveryGroupEntity::class, GeocodingCacheEntity::class, KnownAddressEntity::class, FuelRecord::class, Vehicle::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workRecordDao(): WorkRecordDao
    abstract fun tenkoDao(): TenkoDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun deliveryGroupDao(): DeliveryGroupDao
    abstract fun geocodingCacheDao(): GeocodingCacheDao
    abstract fun knownAddressDao(): KnownAddressDao
    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `fuel_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `liters` REAL NOT NULL,
                        `pricePerLiter` INTEGER NOT NULL,
                        `totalCost` INTEGER NOT NULL,
                        `odometer` INTEGER NOT NULL DEFAULT 0,
                        `note` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `initialOdometer` INTEGER NOT NULL DEFAULT 0,
                        `note` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE `fuel_records` ADD COLUMN `vehicleId` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deliveries` ADD COLUMN `open_time` TEXT")
                db.execSQL("ALTER TABLE `deliveries` ADD COLUMN `close_time` TEXT")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deliveries` ADD COLUMN `dwell_minutes` INTEGER")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `fuel_records` ADD COLUMN `assignmentId` TEXT NOT NULL DEFAULT ''")
            }
        }

        // dwell_minutes列を削除（出発・滞在設定機能の廃止に伴う）。SQLiteのバージョンによっては
        // ALTER TABLE DROP COLUMNが使えないため、テーブル再作成方式で行う
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `deliveries_new` (
                        `id` TEXT NOT NULL,
                        `group_id` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `name` TEXT,
                        `name_kana` TEXT,
                        `address` TEXT NOT NULL,
                        `geocoded_address` TEXT,
                        `note` TEXT,
                        `photo_uri` TEXT,
                        `photo_uris_json` TEXT,
                        `rooms_json` TEXT,
                        `time_slot` TEXT,
                        `open_time` TEXT,
                        `close_time` TEXT,
                        `package_count` INTEGER NOT NULL,
                        `lat` REAL NOT NULL,
                        `lng` REAL NOT NULL,
                        `is_completed` INTEGER NOT NULL,
                        `is_geocoded` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `deliveries_new` (
                        id, group_id, sort_order, name, name_kana, address, geocoded_address,
                        note, photo_uri, photo_uris_json, rooms_json, time_slot, open_time,
                        close_time, package_count, lat, lng, is_completed, is_geocoded
                    )
                    SELECT
                        id, group_id, sort_order, name, name_kana, address, geocoded_address,
                        note, photo_uri, photo_uris_json, rooms_json, time_slot, open_time,
                        close_time, package_count, lat, lng, is_completed, is_geocoded
                    FROM `deliveries`
                """.trimIndent())
                db.execSQL("DROP TABLE `deliveries`")
                db.execSQL("ALTER TABLE `deliveries_new` RENAME TO `deliveries`")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // バージョンダウングレード時のみ破壊的マイグレーションを許容する。
                // アップグレード時にMigrationが不足している場合はクラッシュさせ、
                // データを無言で失う（fallbackToDestructiveMigration）事故を防ぐ
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
            }
    }
}
