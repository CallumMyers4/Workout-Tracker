package com.example.workouttracker.core.model

import java.math.BigDecimal
import java.math.RoundingMode

// Metadata for each exercise created in the catalog on settings page
data class CatalogExercise(
    val id: Long,
    val name: String,
    val goalKg: Double? = null,
    val note: String? = null,
    val type: ExerciseType = ExerciseType.STRENGTH,
    val cardioGoalDistanceMeters: Double? = null,
    val cardioGoalDurationSeconds: Long? = null,
)

// Best recorded set for each exercise
data class BestSet(
    val reps: Int,
    val weightKg: Double,
)

// Progress towards hitting the goal of each exercise
data class ExerciseProgress(
    val exercise: CatalogExercise,
    val bestSet: BestSet?,
    val percentage: Double?,
)

data class CardioProgress(
    val exercise: CatalogExercise,
    val latest: CardioEntry? = null,
    val longestDistanceMeters: Double? = null,
    val fastestPaceSecondsPerMeter: Double? = null,
    val bestQualifying: CardioEntry? = null,
    val percentage: Double? = null,
)

// Amount of time to show workouts from on the home page
enum class WeightsUnit {
    METRIC,
    IMPERIAL;

    // Return the short unit label shown beside weights
    val symbol: String get() = if (this == METRIC) "kg" else "lb"
    val distanceSymbol: String get() = if (this == METRIC) "km" else "mi"

    // Convert the kilogram value used by storage into the selected display unit
    fun fromKilograms(kilograms: Double): Double =
        if (this == METRIC) kilograms else kilograms * POUNDS_PER_KILOGRAM

    // Convert user input back into kilograms before it reaches storage
    fun toKilograms(weight: Double): Double =
        if (this == METRIC) weight else weight / POUNDS_PER_KILOGRAM

    // Round a value in the selected unit to the precision accepted by weight inputs
    fun format(weight: Double): String =
        BigDecimal.valueOf(weight)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    // Format a stored kilogram value for display without changing the stored value
    fun formatKilograms(kilograms: Double): String =
        format(fromKilograms(kilograms))

    fun fromMeters(meters: Double): Double =
        if (this == METRIC) meters / 1000.0 else meters / METERS_PER_MILE

    fun toMeters(distance: Double): Double =
        if (this == METRIC) distance * 1000.0 else distance * METERS_PER_MILE

    fun formatMeters(meters: Double): String = format(fromMeters(meters))

    private companion object {
        const val POUNDS_PER_KILOGRAM = 2.2046226218487757
        const val METERS_PER_MILE = 1609.344
    }
}

// Accept blank and partial decimal input while limiting weights to two decimal places
fun String.isValidWeightInput(): Boolean = matches(Regex("^\\d*(?:\\.\\d{0,2})?$"))
