package com.rodgers.routist.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.model.WorkRecord

@Database(entities = [WorkRecord::class, TenkoRecord::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workRecordDao(): WorkRecordDao
    abstract fun tenkoDao(): TenkoDao

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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "report_db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigrationFrom(1, 2, 3)  // v1〜v3 はリリース前の開発版のみ
                .build()
                .also { INSTANCE = it }
            }
    }
}
