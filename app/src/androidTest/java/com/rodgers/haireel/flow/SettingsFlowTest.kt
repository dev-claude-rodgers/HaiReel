package com.rodgers.haireel

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
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
class SettingsFlowTest {

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
        onView(withId(R.id.nav_settings)).perform(click())
    }

    @After
    fun tearDown() { scenario.close() }

    @Test
    fun settingsTab_appSettingsRowIsDisplayed() {
        onView(withId(R.id.rowAppSettings)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_apiKeyRowIsDisplayed() {
        onView(withId(R.id.rowApiKey)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_backupCreateRowIsDisplayed() {
        onView(withId(R.id.rowBackupCreate)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_helpRowIsDisplayed() {
        onView(withId(R.id.rowHelp)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_resetDataRowIsDisplayed() {
        onView(withId(R.id.rowResetData)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_exitRowIsDisplayed() {
        onView(withId(R.id.rowExit)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_helpDialog_opensAndCloses() {
        onView(withId(R.id.rowHelp)).perform(scrollTo(), click())
        onView(withText("❓ 使い方・ヘルプ")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun settingsTab_resetDataDialog_showsConfirmation() {
        onView(withId(R.id.rowResetData)).perform(scrollTo(), click())
        onView(withText("⚠️ データをすべて初期化")).check(matches(isDisplayed()))
        // キャンセルして戻る
        onView(withText("キャンセル")).perform(click())
    }

    @Test
    fun settingsTab_apiKeyDialog_opensAndCloses() {
        onView(withId(R.id.rowApiKey)).perform(click())
        // APIキー未設定の場合はウィザードが開く
        onView(withText("🔑 Google APIキー設定")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun settingsTab_themeRowIsDisplayed() {
        onView(withId(R.id.rowTheme)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_appSettingsDialog_opensAndCloses() {
        onView(withId(R.id.rowAppSettings)).perform(click())
        onView(withText("アプリ設定")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun settingsTab_helpDialog_canCloseWithButton() {
        onView(withId(R.id.rowHelp)).perform(scrollTo(), click())
        onView(withText("❓ 使い方・ヘルプ")).check(matches(isDisplayed()))
        onView(withText("閉じる")).perform(click())
    }

    @Test
    fun settingsTab_licenseRowIsDisplayed() {
        onView(withId(R.id.rowLicense)).check(matches(isDisplayed()))
    }
}
