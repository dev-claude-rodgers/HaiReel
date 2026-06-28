package com.rodgers.haireel.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rodgers.haireel.MainActivity
import com.rodgers.haireel.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class BackupFlowTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() { hiltRule.inject(); Intents.init() }

    @After
    fun tearDown() { Intents.release() }

    @Test
    fun settings_バックアップ作成ボタンが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_settings)).perform(click())
            onView(withId(R.id.rowBackupCreate)).perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_バックアップ復元ボタンが表示される() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_settings)).perform(click())
            onView(withId(R.id.rowBackupRestore)).perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun settings_バックアップ復元がファイルピッカーを開く() {
        val result = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent())
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_settings)).perform(click())
            onView(withId(R.id.rowBackupRestore)).perform(scrollTo(), click())
            Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        }
    }
}
