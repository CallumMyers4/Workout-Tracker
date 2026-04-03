# constants.py
# This file contains all the constant definitions used throughout the mobile app.
# Constants are values that do not change during the execution of the program.
# They are defined here for easy maintenance and to avoid magic numbers/strings.

# SORT_CHOICES: Defines the available sorting options for workout lists.
# Each tuple contains a human-readable label and a key used internally.
# - "Newest": Sorts workouts by date, most recent first.
# - "Oldest": Sorts workouts by date, oldest first.
# - "A-Z": Sorts workouts alphabetically by name, ascending.
SORT_CHOICES = [
    ("Newest", "newest"),
    ("Oldest", "oldest"),
]

# FILTER_CHOICES: Defines the available filtering options for workout lists.
# Allows users to view workouts from specific time periods.
# - "All Time": Shows all workouts regardless of date.
# - "Recent 30": Shows workouts from the last 30 days.
# - "Recent 90": Shows workouts from the last 90 days.
# - "This Year": Shows workouts from the current year.
FILTER_CHOICES = [
    ("All Time", "all"),
    ("Recent 30", "30_days"),
    ("Recent 90", "90_days"),
    ("This Year", "this_year"),
]

# GROUP_CHOICES: Defines how workouts can be grouped in the list view.
# Grouping helps organize workouts by time periods for better navigation.
# - "None": No grouping, shows all workouts in a flat list.
# - "Month": Groups workouts by month and year (e.g., "January 2024").
# - "Year": Groups workouts by year (e.g., "2024").
GROUP_CHOICES = [
    ("None", "none"),
    ("Month", "month"),
    ("Year", "year"),
    ("Workout Name", "name"),
]

# THEMES: Dictionary containing color schemes for light and dark themes.
# Each theme defines RGBA color values for various UI elements.
# Colors are normalized to 0-1 range (divide RGB by 255).
# - "bg": Background color of the app.
# - "card": Color for card-like UI elements (e.g., workout entries).
# - "panel": Color for panels and secondary backgrounds.
# - "input": Color for input fields and text boxes.
# - "text": Primary text color.
# - "muted": Secondary/muted text color for less important information.
# - "primary": Main accent color for buttons and highlights.
# - "accent": Secondary accent color for positive actions.
# - "danger": Color for error states and destructive actions.
# - "overlay": Modal/transition scrim color layered above the current screen.
THEMES = {
    "light": {
        "bg": [0.94, 0.96, 0.99, 1],      # Light blue-gray background
        "card": [1, 1, 1, 1],             # Pure white for cards
        "panel": [0.88, 0.91, 0.96, 1],   # Light blue-gray for panels
        "input": [1, 1, 1, 1],            # White for input fields
        "text": [0.13, 0.18, 0.26, 1],    # Dark blue-gray for text
        "muted": [0.45, 0.52, 0.61, 1],   # Medium gray for muted text
        "primary": [0.88, 0.91, 0.96, 1], # Blue for primary actions
        "accent": [0.22, 0.22, 0.62, 1],  # Green for accent actions
        "danger": [0.83, 0.29, 0.29, 1],  # Red for danger/error
        "overlay": [0.82, 0.88, 0.95, 0.72],  # Soft tinted scrim for popups/transitions
    },
    "dark": {
        "bg": [0.08, 0.08, 0.08, 1],      # Dark blue-gray background
        "card": [0.1, 0.1, 0.1, 1],    # Dark gray for cards
        "panel": [0.12, 0.12, 0.12, 1],   # Medium dark gray for panels
        "input": [0.12, 0.12, 0.12, 1],   # Dark gray for input fields
        "text": [0.8, 0.8, 0.8, 1],    # Light gray for text
        "muted": [0.4, 0.4, 0.4, 1],    # Medium light gray for muted text
        "primary": [0.12, 0.12, 0.12, 1],  # Bright blue for primary actions
        "accent": [0.05, 0.05, 0.2, 1],  # Blue for accent actions
        "danger": [0.6, 0.22, 0.22, 1],  # Bright red for danger/error
        "overlay": [0, 0, 0, 0.75],  # Dark scrim keeps modal focus in dark mode
    },
}
