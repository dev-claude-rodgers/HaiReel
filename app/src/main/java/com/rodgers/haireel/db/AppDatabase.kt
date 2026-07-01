package com.rodgers.haireel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.model.WorkRecord

@Database(
    entities = [WorkRecord::class, TenkoRecord::class, DeliveryEntity::class, DeliveryGroupEntity::class, GeocodingCacheEntity::class, KnownAddressEntity::class],
    version = 1,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .build()
                .also { INSTANCE = it }
            }
    }
}
