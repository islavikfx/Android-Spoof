package com.islavikfx.spoof
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Assert.*


@RunWith(AndroidJUnit4::class)

class InstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AppActivity::class.java)
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.islavikfx.spoof", appContext.packageName)

    }
}