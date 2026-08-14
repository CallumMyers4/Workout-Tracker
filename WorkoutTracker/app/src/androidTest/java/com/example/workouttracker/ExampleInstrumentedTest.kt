package com.example.workouttracker

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

// Check the installed app identity on an Android device
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    // Confirm that instrumentation opens the expected application package
    fun useAppContext() {
        // Get the application context provided by the device test runner
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.callu.workouttracker", appContext.packageName)
    }
}
