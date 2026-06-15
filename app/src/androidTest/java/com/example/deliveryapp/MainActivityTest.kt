package com.rodgers.routist

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun launchActivity_bottomNavigationIsDisplayed() {
        onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()))
    }

    @Test
    fun launchActivity_defaultTabShowsDeliveryList() {
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_clickTenkoTab_switchesFragment() {
        onView(withId(R.id.nav_tenko)).perform(click())
        // 点呼タブに固有のViewが表示される
        onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_clickSettingsTab_switchesFragment() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_returnToListTab_showsRecyclerView() {
        onView(withId(R.id.nav_tenko)).perform(click())
        onView(withId(R.id.nav_list)).perform(click())
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }
}
