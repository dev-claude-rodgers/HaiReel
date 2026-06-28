package com.rodgers.haireel.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rodgers.haireel.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingFlowTest {

    @Test
    fun onboarding_スキップボタンが表示される() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withId(R.id.btnSkip)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun onboarding_ViewPagerが表示される() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
        }
    }
}
