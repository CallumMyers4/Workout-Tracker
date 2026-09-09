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

    @Test
    fun metricDisplayIsRoundedToTwoDecimalPlaces() {
        assertEquals("12.35", WeightsUnit.METRIC.formatKilograms(12.345))
    }

    @Test
    fun weightInputAllowsAtMostTwoDecimalPlaces() {
        assertEquals(true, "12.34".isValidWeightInput())
        assertEquals(true, ".5".isValidWeightInput())
        assertEquals(true, "12.".isValidWeightInput())
        assertEquals(false, "12.345".isValidWeightInput())
        assertEquals(false, "weight".isValidWeightInput())
    }

    @Test
    fun distanceUnitsRoundTripThroughMeters() {
        assertEquals(5.0, WeightsUnit.METRIC.fromMeters(WeightsUnit.METRIC.toMeters(5.0)), 0.0)
        assertEquals(3.1, WeightsUnit.IMPERIAL.fromMeters(WeightsUnit.IMPERIAL.toMeters(3.1)), 1e-12)
        assertEquals("km", WeightsUnit.METRIC.distanceSymbol)
        assertEquals("mi", WeightsUnit.IMPERIAL.distanceSymbol)
    }

    @Test
    fun splitDurationAcceptsMinutesAndSeconds() {
        assertEquals(754L, CardioEntryDraft("12", "34").durationSecondsOrNull())
        assertEquals(3723L, CardioEntryDraft("62", "03").durationSecondsOrNull())
        assertEquals(0L, CardioEntryDraft().durationSecondsOrNull())
        assertEquals(null, CardioEntryDraft("1", "60").durationSecondsOrNull())
    }
}
