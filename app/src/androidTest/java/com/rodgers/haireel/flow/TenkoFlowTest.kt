package com.rodgers.haireel

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
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
class TenkoFlowTest {

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
        onView(withId(R.id.nav_tenko)).perform(click())
    }

    @After
    fun tearDown() { scenario.close() }

    @Test
    fun tenkoTab_listIsDisplayed() {
        onView(withId(R.id.recyclerTenko)).check(matches(isDisplayed()))
    }

    @Test
    fun tenkoTab_monthTextIsDisplayed() {
        onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun tenkoTab_prevMonthChangesMonth() {
        val before = arrayOfNulls<String>(1)
        onView(withId(R.id.tvMonth)).check { v, _ -> before[0] = (v as android.widget.TextView).text.toString() }
        onView(withId(R.id.btnPrevMonth)).perform(click())
        onView(withId(R.id.tvMonth)).check { v, _ ->
            assert(before[0] != (v as android.widget.TextView).text.toString())
        }
    }

    @Test
    fun tenkoTab_menuButton_opensSheet() {
        onView(withId(R.id.btnTenkoMenu)).perform(click())
        onView(withText("点呼設定")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun tenkoTab_menuButton_excelOptionIsDisplayed() {
        onView(withId(R.id.btnTenkoMenu)).perform(click())
        onView(withText("点呼簿を出力")).check(matches(isDisplayed()))
        pressBack()
    }
}
