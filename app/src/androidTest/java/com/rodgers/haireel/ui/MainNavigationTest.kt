package com.rodgers.haireel.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rodgers.haireel.MainActivity
import com.rodgers.haireel.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainNavigationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() { hiltRule.inject() }

    @Test
    fun bottomNav_起動時にBottomNavigationが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun bottomNav_配達タブに切り替えられる() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_list)).perform(click())
            onView(withId(R.id.nav_list)).check(matches(isSelected()))
        }
    }

    @Test
    fun bottomNav_日報タブに切り替えられる() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_report)).perform(click())
            onView(withId(R.id.nav_report)).check(matches(isSelected()))
        }
    }

    @Test
    fun bottomNav_点呼タブに切り替えられる() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_tenko)).perform(click())
            onView(withId(R.id.nav_tenko)).check(matches(isSelected()))
        }
    }

    @Test
    fun bottomNav_設定タブに切り替えられる() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_settings)).perform(click())
            onView(withId(R.id.nav_settings)).check(matches(isSelected()))
        }
    }
}
