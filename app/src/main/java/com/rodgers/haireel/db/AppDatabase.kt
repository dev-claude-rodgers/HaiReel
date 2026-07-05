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
    version = 3,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
    }
}
