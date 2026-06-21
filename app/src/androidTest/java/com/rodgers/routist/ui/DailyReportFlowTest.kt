package com.rodgers.routist.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rodgers.routist.MainActivity
import com.rodgers.routist.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class DailyReportFlowTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() { hiltRule.inject() }

    @Test
    fun dailyReport_タブを開くと月ナビが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_report)).perform(click())
            onView(withId(R.id.layoutMonthNav)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun dailyReport_前月ボタンと翌月ボタンが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_report)).perform(click())
            onView(withId(R.id.btnPrevMonth)).check(matches(isDisplayed()))
            onView(withId(R.id.btnNextMonth)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun dailyReport_メニューボタンが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_report)).perform(click())
            onView(withId(R.id.btnMenu)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun dailyReport_前月に移動できる() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_report)).perform(click())
            onView(withId(R.id.btnPrevMonth)).perform(click())
            onView(withId(R.id.tvMonth)).check(matches(isDisplayed()))
        }
    }
}
