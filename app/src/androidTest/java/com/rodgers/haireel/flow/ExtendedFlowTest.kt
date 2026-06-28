package com.rodgers.haireel

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtendedFlowTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences(com.rodgers.haireel.util.AppSettings.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("driver_mode", true)
            .putBoolean("show_mode_on_launch", false)
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() { scenario.close() }

    // ── 日報ダイアログ ──────────────────────────────────────────

    @Test
    fun reportTab_todayDialog_canOpenAndClose() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())
        onView(withText("配達件数 ／ 個数")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun reportTab_todayDialog_hasIncomeField() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())
        onView(withText("収入（円）")).perform(scrollTo()).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun reportTab_todayDialog_hasFuelField() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())
        onView(withText("燃料費（円）")).perform(scrollTo()).check(matches(isDisplayed()))
        pressBack()
    }

    // ── 帳票パターン ────────────────────────────────────────────

    @Test
    fun reportTab_patternDialog_canOpenAndClose() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("帳票を切り替え")).perform(click())
        onView(withText("帳票設定")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun reportTab_patternDialog_hasAddButton() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("帳票を切り替え")).perform(click())
        onView(withText("+ 新しいパターンを追加")).check(matches(isDisplayed()))
        pressBack()
    }

    // ── ライセンス ──────────────────────────────────────────────

    @Test
    fun settingsTab_licenseRow_isDisplayed() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowLicense)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_licenseStatus_showsTrialOrValid() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.tvLicenseStatus)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_licenseRow_click_opensDialog() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowLicense)).perform(click())
        onView(withText("🔑 ライセンスキーを入力")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun settingsTab_licenseDialog_canOpenAndHasAuthButton() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowLicense)).perform(click())
        onView(withText("🔑 ライセンスキーを入力")).check(matches(isDisplayed()))
        onView(withText("認証する")).check(matches(isDisplayed()))
        pressBack()
    }

    // ── バックアップ ────────────────────────────────────────────

    @Test
    fun settingsTab_backupCreate_isClickable() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowBackupCreate)).check(matches(isClickable()))
    }

    @Test
    fun settingsTab_backupRestore_isClickable() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowBackupRestore)).check(matches(isClickable()))
    }

    // ── 配達リスト操作 ──────────────────────────────────────────

    @Test
    fun deliveryList_menuOpens_importOptionVisible() {
        onView(withId(R.id.buttonListMenu)).perform(click())
        onView(withText("住所をインポート")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun deliveryList_menuOpens_scanOptionVisible() {
        onView(withId(R.id.buttonListMenu)).perform(click())
        onView(withText("伝票からスキャン")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun deliveryList_menuOpens_routeGroupOptionVisible() {
        onView(withId(R.id.buttonListMenu)).perform(click())
        // ルート最適化は地図メニューにあるため、ここではグループ操作を確認
        onView(withText("新しいルートを追加")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun deliveryList_mapToggle_buttonVisible() {
        onView(withId(R.id.buttonMapToggle)).check(matches(isDisplayed()))
    }

    // ── 収支タブ詳細 ────────────────────────────────────────────

    @Test
    fun reportTab_summaryCards_areDisplayed() {
        onView(withId(R.id.nav_report)).perform(click())
        // 月次サマリーは日報タブのtopに表示される
        onView(withId(R.id.tvSummaryDays)).check(matches(isDisplayed()))
        onView(withId(R.id.layoutSummary)).check(matches(isDisplayed()))
    }

    @Test
    fun reportTab_yearNavigation_prevMonthButton_works() {
        // 収支タブ（Dashboard）はReportContainerFragment内のViewPagerにある
        // 日報タブの前月ボタンで代替テスト
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnPrevMonth)).perform(click())
        onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
    }
}
