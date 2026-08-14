package com.example.workouttracker.core.model

// Amount of time to show workouts from on the home page
enum class WorkoutFilter {
    ALL_TIME,
    RECENT_30_DAYS,
    RECENT_90_DAYS,
    THIS_YEAR,
}

// Order to list workouts in
enum class WorkoutSort {
    NEWEST,
    OLDEST,
}

// How to create collapsable/explandable groups of workouts on the home page
enum class WorkoutGrouping {
    NONE,
    MONTH,
    YEAR,
    WORKOUT_NAME,
}
