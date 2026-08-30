package com.rodgers.haireel.flow

import com.rodgers.haireel.MainActivity
import com.rodgers.haireel.R

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// RecyclerView内の指定位置にある子ビュー（chipBefore/chipAfter等、複数行に同一IDが
// 存在するためwithId単体ではAmbiguousViewMatcherExceptionになる）をクリックする
private fun clickChildViewWithId(id: Int): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isDisplayed()
    override fun getDescription(): String = "Click on a child view with specified id."
    override fun perform(uiController: UiController, view: View) {
        view.findViewById<View>(id).performClick()
    }
}

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
            .putBoolean("iap_subscription_active", true)
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
        onView(withText("Excel出力")).check(matches(isDisplayed()))
        pressBack()
    }

    // ── 点呼記録フロー ────────────────────────────────────────
    // chipBefore/chipAfterはRecyclerViewの各日付行に同一IDで存在するため、
    // 先頭行（当月1日、稼働あり想定）を指定してクリックする

    @Test
    fun tenkoTab_chipBefore_opensDialog() {
        onView(withId(R.id.recyclerTenko)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, clickChildViewWithId(R.id.chipBefore))
        )
        onView(withText("体調")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun tenkoTab_chipAfter_opensDialog() {
        onView(withId(R.id.recyclerTenko)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, clickChildViewWithId(R.id.chipAfter))
        )
        onView(withText("体調")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun tenkoTab_chipBefore_dialogHasSaveButton() {
        onView(withId(R.id.recyclerTenko)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, clickChildViewWithId(R.id.chipBefore))
        )
        onView(withText("保存")).check(matches(isDisplayed()))
        onView(withText("キャンセル")).perform(click())
    }

    @Test
    fun tenkoTab_chipBefore_cancelDismissesDialog() {
        onView(withId(R.id.recyclerTenko)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, clickChildViewWithId(R.id.chipBefore))
        )
        onView(withText("体調")).check(matches(isDisplayed()))
        onView(withText("キャンセル")).perform(click())
        onView(withText("体調")).check(doesNotExist())
    }
}
