package com.rodgers.haireel.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AppSettingsTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── getClosingDay デフォルト値 ────────────────────────────

    @Test
    fun `getClosingDayのデフォルト値は25`() {
        assertEquals(25, AppSettings.getClosingDay(ctx))
    }

    @Test
    fun `setClosingDayで設定した値をgetClosingDayで取得できる`() {
        AppSettings.setClosingDay(ctx, 15)
        assertEquals(15, AppSettings.getClosingDay(ctx))
    }

    // ── getDriverName ─────────────────────────────────────────

    @Test
    fun `getDriverNameのデフォルト値は空文字`() {
        assertEquals("", AppSettings.getDriverName(ctx))
    }

    @Test
    fun `setDriverNameで設定した値をgetDriverNameで取得できる`() {
        AppSettings.setDriverName(ctx, "田中太郎")
        assertEquals("田中太郎", AppSettings.getDriverName(ctx))
    }

    // ── isTermsAgreed / setTermsAgreed ───────────────────────

    @Test
    fun `isTermsAgreedのデフォルト値はfalse`() {
        assertFalse(AppSettings.isTermsAgreed(ctx))
    }

    @Test
    fun `setTermsAgreed後にisTermsAgreedがtrueになる`() {
        AppSettings.setTermsAgreed(ctx)
        assertTrue(AppSettings.isTermsAgreed(ctx))
    }

    // ── isOnboardingDone / setOnboardingDone ─────────────────

    @Test
    fun `isOnboardingDoneのデフォルト値はfalse`() {
        assertFalse(AppSettings.isOnboardingDone(ctx))
    }

    @Test
    fun `setOnboardingDone後にisOnboardingDoneがtrueになる`() {
        AppSettings.setOnboardingDone(ctx)
        assertTrue(AppSettings.isOnboardingDone(ctx))
    }

    // ── isColAlc / setColAlc ──────────────────────────────────

    @Test
    fun `isColAlcのデフォルト値はtrue`() {
        assertTrue(AppSettings.isColAlc(ctx))
    }

    @Test
    fun `setColAlcfalseで取得がfalseになる`() {
        AppSettings.setColAlc(ctx, false)
        assertFalse(AppSettings.isColAlc(ctx))
    }
}
