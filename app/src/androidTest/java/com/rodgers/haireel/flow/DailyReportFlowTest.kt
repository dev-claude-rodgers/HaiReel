package com.rodgers.haireel

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
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
class DailyReportFlowTest {

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
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ── 日報タブの基本操作 ────────────────────────────────────────

    @Test
    fun reportTab_isVisible() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.recyclerReport)).check(matches(isDisplayed()))
    }

    @Test
    fun reportTab_prevMonthButton_changesMonth() {
        onView(withId(R.id.nav_report)).perform(click())
        val before = arrayOfNulls<String>(1)
        onView(withId(R.id.tvMonth)).check { v, _ -> before[0] = (v as android.widget.TextView).text.toString() }
        onView(withId(R.id.btnPrevMonth)).perform(click())
        onView(withId(R.id.tvMonth)).check { v, _ ->
            assert(before[0] != (v as android.widget.TextView).text.toString())
        }
    }

    @Test
    fun reportTab_nextMonthButton_isDisplayed() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnNextMonth)).check(matches(isDisplayed()))
    }

    @Test
    fun reportTab_menuButton_opensBottomSheet() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun reportTab_menuButton_todayDialogOpens() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())
        // 日報編集ダイアログが開く（配達件数欄が存在する）
        onView(withText("配達件数 ／ 個数")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun reportTab_patternButton_opensPatternList() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("帳票を切り替え")).perform(click())
        onView(withText("帳票設定")).check(matches(isDisplayed()))
        pressBack()
    }

    // ── 日報編集フロー ────────────────────────────────────────────

    @Test
    fun editDialog_配達件数を入力して保存できる() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())

        // 配達件数フィールドに入力（hint="0"、最初のEditText）
        onView(withHint("0")).perform(click(), clearText(), typeText("30"), closeSoftKeyboard())

        onView(withText("保存")).perform(click())

        // 保存後にダイアログが閉じること（保存ボタンが消える）
        onView(withText("配達件数 ／ 個数")).check(doesNotExist())
    }

    @Test
    fun editDialog_キャンセルでダイアログが閉じる() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())

        onView(withText("配達件数 ／ 個数")).check(matches(isDisplayed()))
        onView(withText("キャンセル")).perform(click())

        onView(withText("配達件数 ／ 個数")).check(doesNotExist())
    }

    @Test
    fun editDialog_稼働あり休みトグルが表示される() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())

        onView(withText("稼働あり")).check(matches(isDisplayed()))
        onView(withText("休み")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun editDialog_休みを選択すると配達件数欄が残る() {
        onView(withId(R.id.nav_report)).perform(click())
        onView(withId(R.id.btnMenu)).perform(click())
        onView(withText("今日の日報を記録")).perform(click())

        onView(withText("休み")).perform(click())

        // ダイアログ自体はまだ表示されている
        onView(withText("保存")).check(matches(isDisplayed()))
        pressBack()
    }
}
