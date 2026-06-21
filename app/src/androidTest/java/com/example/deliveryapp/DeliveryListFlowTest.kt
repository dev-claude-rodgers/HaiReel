package com.rodgers.routist

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
class DeliveryListFlowTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
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

    // ── 配達リストタブの基本操作 ──────────────────────────────────

    @Test
    fun deliveryList_isDisplayedOnLaunch() {
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun deliveryList_menuButton_isDisplayed() {
        onView(withId(R.id.buttonListMenu)).check(matches(isDisplayed()))
    }

    @Test
    fun deliveryList_menuButton_opensSheet() {
        onView(withId(R.id.buttonListMenu)).perform(click())
        onView(withText("住所をインポート")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun deliveryList_filterChip_clickable() {
        onView(withId(R.id.chipIncomplete)).perform(click())
        onView(withId(R.id.chipIncomplete)).check(matches(isDisplayed()))
    }

    @Test
    fun deliveryList_mapToggleButton_isDisplayed() {
        onView(withId(R.id.buttonMapToggle)).check(matches(isDisplayed()))
    }

    @Test
    fun deliveryList_filterChip_isDisplayed() {
        onView(withId(R.id.chipIncomplete)).check(matches(isDisplayed()))
    }
}
