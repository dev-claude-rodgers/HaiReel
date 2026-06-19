package com.rodgers.routist.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"

        private val migration9to10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `geocoding_cache` (
                        `address` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lng` REAL NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`address`)
                    )"""
                )
            }
        }

        private val migration8to9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `delivery_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color_hex` TEXT NOT NULL,
                        `pattern_id` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    // ── v9 → v10 ────────────────────────────────────────────────

    @Test
    fun v9からv10に移行するとgeocodingCacheテーブルが作成される() {
        helper.createDatabase(TEST_DB, 9).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, migration9to10)

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='geocoding_cache'"
        )
        assertTrue("geocoding_cache テーブルが存在しない", cursor.moveToFirst())
        cursor.close()
        db.close()
    }

    @Test
    fun v9からv10の移行後にキャッシュデータを書き込める() {
        helper.createDatabase(TEST_DB, 9).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, migration9to10)

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO geocoding_cache (address, lat, lng, cachedAt) VALUES (?, ?, ?, ?)",
            arrayOf("東京都新宿区", 35.69, 139.70, now)
        )

        val cursor = db.query("SELECT address, lat FROM geocoding_cache")
        assertTrue(cursor.moveToFirst())
        val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
        assertTrue("読み込んだ住所が一致しない", address == "東京都新宿区")
        cursor.close()
        db.close()
    }

    // ── v8 → v9 ────────────────────────────────────────────────

    @Test
    fun v8からv9に移行するとdeliveryGroupsテーブルが作成される() {
        helper.createDatabase(TEST_DB, 8).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, migration8to9)

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='delivery_groups'"
        )
        assertTrue("delivery_groups テーブルが存在しない", cursor.moveToFirst())
        cursor.close()
        db.close()
    }

    // ── v8 → v10 連続移行 ────────────────────────────────────────

    @Test
    fun v8からv10に連続移行できる() {
        helper.createDatabase(TEST_DB, 8).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, migration8to9, migration9to10)

        val c1 = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='delivery_groups'"
        )
        assertTrue("delivery_groups テーブルが存在しない", c1.moveToFirst())
        c1.close()

        val c2 = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='geocoding_cache'"
        )
        assertTrue("geocoding_cache テーブルが存在しない", c2.moveToFirst())
        c2.close()
        db.close()
    }
}
