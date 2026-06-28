package com.rodgers.haireel

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
class DashboardFlowTest {

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
        // 収支タブは報告（report）タブのネスト内なので、報告タブに移動
        // DashboardFragmentはReportContainerFragment内に含まれる
        onView(withId(R.id.nav_report)).perform(click())
    }

    @After
    fun tearDown() { scenario.close() }

    @Test
    fun reportContainer_isDisplayed() {
        onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun reportContainer_prevMonthButton_isDisplayed() {
        onView(withId(R.id.btnPrevMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun reportContainer_nextMonthButton_isDisplayed() {
        onView(withId(R.id.btnNextMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun reportContainer_menuButton_isDisplayed() {
        onView(withId(R.id.btnMenu)).check(matches(isDisplayed()))
    }

    @Test
    fun reportContainer_prevMonth_changesMonth() {
        val before = arrayOfNulls<String>(1)
        onView(withId(R.id.tvMonth)).check { v, _ -> before[0] = (v as android.widget.TextView).text.toString() }
        onView(withId(R.id.btnPrevMonth)).perform(click())
        onView(withId(R.id.tvMonth)).check { v, _ ->
            assert(before[0] != (v as android.widget.TextView).text.toString())
        }
    }
}
