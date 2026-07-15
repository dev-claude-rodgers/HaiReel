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
    version = 5,
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

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deliveries` ADD COLUMN `dwell_minutes` INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
