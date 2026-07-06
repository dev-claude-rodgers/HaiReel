package com.rodgers.haireel.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingFlowTest {

    @Before
    fun setUp() {
        // オンボーディング未完了状態にして Activity がスキップされないようにする
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("kado_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", false).commit()
    }

    @Test
    fun onboarding_スキップボタンが表示される() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            // プログラマティックUIのためテキストで検索
            onView(withText("スキップ")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun onboarding_次へボタンが表示される() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withText("次へ")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun onboarding_スキップで利用規約ダイアログが表示される() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withText("スキップ")).perform(click())
            onView(withText("利用規約")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun onboarding_利用規約ダイアログに同意して始めるボタンがある() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withText("スキップ")).perform(click())
            onView(withText("同意して始める")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun onboarding_利用規約ダイアログのキャンセルで閉じる() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withText("スキップ")).perform(click())
            onView(withText("利用規約")).check(matches(isDisplayed()))
            onView(withText("キャンセル")).perform(click())
            onView(withText("利用規約")).check(doesNotExist())
        }
    }
}
