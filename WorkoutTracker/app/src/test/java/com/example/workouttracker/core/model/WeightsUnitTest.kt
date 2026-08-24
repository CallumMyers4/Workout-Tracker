package com.example.workouttracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightsUnitTest {
    @Test
    fun imperialConvertsKilogramsToPounds() {
        assertEquals(220.46226218487757, WeightsUnit.IMPERIAL.fromKilograms(100.0), 1e-12)
    }

    @Test
    fun imperialInputConvertsBackToKilograms() {
        assertEquals(100.0, WeightsUnit.IMPERIAL.toKilograms(220.46226218487757), 1e-12)
    }

    @Test
    fun metricLeavesCanonicalKilogramsUnchanged() {
        assertEquals(87.5, WeightsUnit.METRIC.fromKilograms(87.5), 0.0)
        assertEquals(87.5, WeightsUnit.METRIC.toKilograms(87.5), 0.0)
    }

    @Test
    fun imperialDisplayIsRoundedWithoutChangingCanonicalValue() {
        assertEquals("220.46", WeightsUnit.IMPERIAL.formatKilograms(100.0))
        assertEquals(100.0, WeightsUnit.METRIC.toKilograms(100.0), 0.0)
    }
}
