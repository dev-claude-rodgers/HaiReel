package com.rodgers.haireel.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
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
class SettingsFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() { hiltRule.inject() }

    private fun launchSettings(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            onView(withId(R.id.nav_settings)).perform(click())
        }

    @Test
    fun settings_テーマカラー行が表示される() {
        launchSettings().use {
            onView(withId(R.id.rowTheme)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_APIキー行が表示される() {
        launchSettings().use {
            onView(withId(R.id.rowApiKey)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_ライセンス行が表示される() {
        launchSettings().use {
            onView(withId(R.id.rowLicense)).perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_ヘルプ行が表示される() {
        launchSettings().use {
            onView(withId(R.id.rowHelp)).perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_リセット行タップで確認ダイアログが開く() {
        launchSettings().use {
            onView(withId(R.id.rowResetData)).perform(scrollTo(), click())
            onView(withText("初期化する"))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText("キャンセル"))
                .inRoot(isDialog())
                .perform(click())
        }
    }

    @Test
    fun settings_利用規約行タップでダイアログが開く() {
        launchSettings().use {
            onView(withId(R.id.rowTerms)).perform(scrollTo(), click())
            onView(withText("閉じる"))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText("閉じる"))
                .inRoot(isDialog())
                .perform(click())
        }
    }

    @Test
    fun settings_ヘルプ行タップでダイアログが開く() {
        launchSettings().use {
            onView(withId(R.id.rowHelp)).perform(scrollTo(), click())
            onView(withText("閉じる"))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText("閉じる"))
                .inRoot(isDialog())
                .perform(click())
        }
    }
}
