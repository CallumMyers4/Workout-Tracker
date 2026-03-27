# desktop_app/__init__.py
# This package contains the desktop version of the workout tracker.

from .workout_tracker import WorkoutTracker
from .goals_editor import GoalsEditor
from .workout_editor import WorkoutEditor
from .exercise_entry import ExerciseEntry
from .exercise_graph import ExerciseGraph

__all__ = ["WorkoutTracker", "GoalsEditor", "WorkoutEditor", "ExerciseEntry", "ExerciseGraph"]