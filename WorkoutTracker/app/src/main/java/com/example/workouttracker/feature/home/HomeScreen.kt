package com.example.workouttracker.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.ui.theme.PageTitle
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onViewHistory: () -> Unit,
    onWorkoutSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTitle(text = "Home", icon = painterResource(R.drawable.icon_home))
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null -> Text(
                uiState.errorMessage,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.error,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StreakCard(uiState.streakDays, uiState.workoutsThisWeek) }
                item { LastThirtyDaysCard(uiState) }
                item {
                    RecentWorkouts(
                        workouts = uiState.recentWorkouts,
                        onViewHistory = onViewHistory,
                        onWorkoutSelected = onWorkoutSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakCard(streakDays: Int, workoutsThisWeek: Int) {
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.icon_flame),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = androidx.compose.ui.graphics.Color.Unspecified,
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    "$streakDays day streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "$workoutsThisWeek ${if (workoutsThisWeek == 1) "workout" else "workouts"} this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LastThirtyDaysCard(state: HomeUiState) {
    DashboardCard {
        Text("Last 30 days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkoutDonut(
                total = state.workoutsLast30Days,
                strength = state.strengthWorkouts,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Strength", state.strengthWorkouts, state.workoutsLast30Days, true)
                LegendItem("Cardio", state.cardioWorkouts, state.workoutsLast30Days, false)
            }
        }
    }
}

@Composable
private fun WorkoutDonut(total: Int, strength: Int, modifier: Modifier = Modifier) {
    val strengthColor = MaterialTheme.colorScheme.primary
    val cardioColor = MaterialTheme.colorScheme.primaryContainer
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.semantics { contentDescription = "$total workouts in the last 30 days" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = size.minDimension * 0.22f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            if (total == 0) {
                drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            } else {
                val strengthSweep = strength.toFloat() / total * 360f
                drawArc(cardioColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                drawArc(strengthColor, -90f, strengthSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("workouts", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LegendItem(label: String, count: Int, total: Int, strength: Boolean) {
    val colour = if (strength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    Row(verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 3.dp).size(22.dp).semantics { contentDescription = "$label chart colour" }) {
            drawCircle(colour)
        }
        val percentage = if (total == 0) 0 else (count * 100f / total).roundToInt()
        Text(
            "$label\n$percentage%\n($count)",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecentWorkouts(
    workouts: List<WorkoutSummary>,
    onViewHistory: () -> Unit,
    onWorkoutSelected: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent workouts", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.clickable(onClick = onViewHistory).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View all", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Icon(
                painterResource(R.drawable.icon_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (workouts.isEmpty()) {
        Text(
            "No workouts yet. Tap + to record your first workout.",
            modifier = Modifier.padding(vertical = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        workouts.forEach { workout ->
            RecentWorkoutCard(workout, onClick = { onWorkoutSelected(workout.id) })
            Spacer(Modifier.size(6.dp))
        }
    }
}

@Composable
private fun RecentWorkoutCard(workout: WorkoutSummary, onClick: () -> Unit) {
    DashboardCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(
                    if (workout.type == WorkoutType.CARDIO) R.drawable.icon_bike else R.drawable.icon_weights,
                ),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
                Text(workout.name, fontWeight = FontWeight.SemiBold)
                Text(
                    workout.date?.format(DATE_FORMAT) ?: "Unknown date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.icon_chevron_right),
                contentDescription = "Open ${workout.name}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) { content() }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu")
