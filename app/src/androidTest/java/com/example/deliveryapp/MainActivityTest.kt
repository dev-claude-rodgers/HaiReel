package com.rodgers.routist

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
class MainActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        // ドライバーモードを設定しモード選択ダイアログを抑制してから起動
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences(com.rodgers.routist.util.AppSettings.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("driver_mode", true)
            .putBoolean("show_mode_on_launch", false)
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ── 起動・BottomNavigation ────────────────────────────────────

    @Test
    fun launchActivity_bottomNavigationIsDisplayed() {
        onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()))
    }

    @Test
    fun launchActivity_defaultTabShowsDeliveryList() {
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_allTabsReachable() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.nav_list)).perform(click())
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_returnToListTab_showsRecyclerView() {
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.nav_list)).perform(click())
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    // ── 日報タブ ─────────────────────────────────────────────────

    @Test
    fun reportTab_monthNavigationDisplayed() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnPrevMonth)).check(matches(isDisplayed()))
        onView(withId(R.id.btnNextMonth)).check(matches(isDisplayed()))
        onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun reportTab_menuButtonDisplayed() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).check(matches(isDisplayed()))
    }

    @Test
    fun reportTab_prevMonthChangesMonthText() {
        onView(withId(R.id.nav_report)).perform(click())
        val before = arrayOfNulls<String>(1)
        onView(withId(R.id.tvMonth)).check { view, _ ->
            before[0] = (view as android.widget.TextView).text.toString()
        }
        onView(withId(R.id.btnPrevMonth)).perform(click())
        onView(withId(R.id.tvMonth)).check { view, _ ->
            val after = (view as android.widget.TextView).text.toString()
            assert(before[0] != after) { "前月ボタンで月が変わらなかった" }
        }
    }

    // ── 設定タブ ─────────────────────────────────────────────────

    @Test
    fun settingsTab_settingsRootDisplayed() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_apiKeyRowDisplayed() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowApiKey)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_helpRowDisplayed() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowHelp)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun settingsTab_helpRowClick_opensDialog() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowHelp)).perform(scrollTo(), click())
        onView(withText("❓ 使い方・ヘルプ")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun settingsTab_backupRowDisplayed() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.rowBackupCreate)).check(matches(isDisplayed()))
    }

    // ── 点呼タブ ─────────────────────────────────────────────────

    @Test
    fun tenkoTab_listDisplayed() {
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.recyclerTenko)).check(matches(isDisplayed()))
    }

    @Test
    fun tenkoTab_monthNavigationDisplayed() {
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun tenkoTab_menuButtonDisplayed() {
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.btnTenkoMenu)).check(matches(isDisplayed()))
    }
}
