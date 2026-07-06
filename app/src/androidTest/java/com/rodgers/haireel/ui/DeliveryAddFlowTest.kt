package com.rodgers.haireel.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rodgers.haireel.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DeliveryAddFlowTest {

    @Before
    fun setUp() { Intents.init() }

    @After
    fun tearDown() { Intents.release() }

    @Test
    fun inputActivity_住所入力してインポートボタンが押せる() {
        ActivityScenario.launch(InputActivity::class.java).use {
            onView(withId(R.id.editTextAddresses))
                .perform(typeText("東京都新宿区西新宿1-1-1"), closeSoftKeyboard())
            onView(withId(R.id.buttonImport))
                .check(matches(isDisplayed()))
                .perform(click())
        }
    }

    @Test
    fun inputActivity_空欄でインポートボタンを押しても画面が残る() {
        ActivityScenario.launch(InputActivity::class.java).use {
            onView(withId(R.id.editTextAddresses)).perform(clearText())
            onView(withId(R.id.buttonImport)).perform(click())
            onView(withId(R.id.buttonImport)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun inputActivity_ペーストボタンが表示される() {
        ActivityScenario.launch(InputActivity::class.java).use {
            onView(withId(R.id.buttonPaste)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun inputActivity_CSVボタンがファイルピッカーIntentを発火する() {
        val result = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent())
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)
        ActivityScenario.launch(InputActivity::class.java).use {
            onView(withId(R.id.buttonCsv)).perform(click())
            Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        }
    }
}
