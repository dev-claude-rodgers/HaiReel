package com.rodgers.haireel.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    // ── v1 → v2: fuel_records テーブル追加 ──────────────────────

    @Test
    fun v1からv2でfuelRecordsテーブルが作成される() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, AppDatabase.MIGRATION_1_2
        )

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='fuel_records'"
        )
        assertTrue("fuel_records テーブルが存在しない", cursor.moveToFirst())
        cursor.close()
        db.close()
    }

    @Test
    fun v1からv2の移行後にfuelRecordを書き込める() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, AppDatabase.MIGRATION_1_2
        )

        db.execSQL(
            "INSERT INTO fuel_records (date, liters, pricePerLiter, totalCost, odometer, note) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("2026-07-05", 40.0f, 170, 6800, 12345, "テスト")
        )

        val cursor = db.query("SELECT date, liters FROM fuel_records")
        assertTrue(cursor.moveToFirst())
        assertEquals("2026-07-05", cursor.getString(cursor.getColumnIndexOrThrow("date")))
        assertEquals(40.0f, cursor.getFloat(cursor.getColumnIndexOrThrow("liters")), 0.01f)
        cursor.close()
        db.close()
    }

    // ── v2 → v3: vehicles テーブル追加 + vehicleId カラム追加 ────

    @Test
    fun v2からv3でvehiclesテーブルが作成される() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 3, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        )

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='vehicles'"
        )
        assertTrue("vehicles テーブルが存在しない", cursor.moveToFirst())
        cursor.close()
        db.close()
    }

    @Test
    fun v2からv3でfuelRecordsにvehicleIdカラムが追加される() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 3, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        )

        // vehicleId カラムへの書き込みが成功すれば追加済み
        db.execSQL(
            "INSERT INTO fuel_records (date, liters, pricePerLiter, totalCost, odometer, note, vehicleId) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf("2026-07-05", 30.0f, 175, 5250, 20000, "", 0L)
        )

        val cursor = db.query("SELECT vehicleId FROM fuel_records")
        assertTrue(cursor.moveToFirst())
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("vehicleId")))
        cursor.close()
        db.close()
    }

    @Test
    fun v2からv3でvehicleを書き込める() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 3, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        )

        db.execSQL(
            "INSERT INTO vehicles (name, initialOdometer, note) VALUES (?, ?, ?)",
            arrayOf("トヨタハイエース", 10000, "")
        )

        val cursor = db.query("SELECT name, initialOdometer FROM vehicles")
        assertTrue(cursor.moveToFirst())
        assertEquals("トヨタハイエース", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals(10000, cursor.getInt(cursor.getColumnIndexOrThrow("initialOdometer")))
        cursor.close()
        db.close()
    }

    // ── v1 → v3 連続移行 ─────────────────────────────────────────

    @Test
    fun v1からv3に連続移行すると両テーブルが揃う() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 3, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        )

        val c1 = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='fuel_records'"
        )
        assertTrue("fuel_records テーブルが存在しない", c1.moveToFirst())
        c1.close()

        val c2 = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='vehicles'"
        )
        assertTrue("vehicles テーブルが存在しない", c2.moveToFirst())
        c2.close()

        db.close()
    }
}
