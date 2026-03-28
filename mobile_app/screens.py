# screens.py
# This file contains all the screen classes that make up the user interface of the mobile app.
# Each screen represents a different view or page in the app, handling user interactions
# and displaying relevant data. Screens are managed by a ScreenManager for navigation.

from datetime import datetime
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.popup import Popup
from kivy.uix.screenmanager import Screen
from kivy.uix.scrollview import ScrollView
from kivy.uix.spinner import Spinner
from kivy.uix.textinput import TextInput
from kivy.app import App

from .utils import (
    add_rounded_background,
    create_action_button,
    create_themed_card,
    create_themed_label,
    create_workout_card,
    create_group_header,
    format_date_display,
    format_editor_date,
    parse_editor_date,
    scroll_to_top,
)
from .widgets import ExerciseRow


class WorkoutListScreen(Screen):
    """
    The main screen displaying a list of workouts with search, filtering, sorting, and grouping capabilities.

    This screen serves as the primary interface for browsing and accessing workout data.
    It provides multiple ways to organize and find workouts, and includes navigation to
    other parts of the app.
    """

    def on_pre_enter(self):
        """
        Called just before the screen becomes active.

        Refreshes the workout list to ensure it shows the most current data.
        """
        self.refresh_list()

    def update_search(self, value):
        """
        Handle changes to the search input field.

        Updates the app's search text and refreshes the workout list to show
        only workouts matching the search criteria.

        Args:
            value (str): The current search text
        """
        app = App.get_running_app()
        if app.search_text != value:
            app.search_text = value
            app.save_preferences()
            self.refresh_list()

    def cycle_filter(self):
        """
        Cycle to the next filter option (All Time, Recent 30, etc.).

        Updates the app's filter setting and refreshes the display.
        """
        App.get_running_app().cycle_filter()
        self.refresh_list()

    def cycle_sort(self):
        """
        Cycle to the next sort option (Newest, Oldest, A-Z, Z-A).

        Updates the app's sort setting and refreshes the display.
        """
        App.get_running_app().cycle_sort()
        self.refresh_list()

    def cycle_group(self):
        """
        Cycle to the next grouping option (None, Month, Year).

        Updates the app's grouping setting and refreshes the display.
        """
        App.get_running_app().cycle_group()
        self.refresh_list()

    def refresh_list(self):
        """
        Rebuild the workout list display based on current filters, search, and grouping.

        Clears the existing list and repopulates it with workout cards organized
        according to the current settings. Shows an empty state message if no
        workouts match the criteria.
        """
        app = App.get_running_app()
        container = self.ids.workout_list
        container.clear_widgets()

        # Get filtered workouts and update summary text
        workouts = app.get_filtered_workouts()
        app.browse_summary = app.build_browse_summary(workouts)

        # Handle empty state
        if not workouts:
            empty = create_themed_card(height=92)
            empty.add_widget(create_themed_label("No workouts match your current view.", font_size="16sp", bold=True, height=28))
            empty.add_widget(create_themed_label("Try a different search, filter, or grouping option.", font_size="14sp", color=app.muted_text_color, height=24))
            container.add_widget(empty)
            return

        # Group workouts and create UI elements
        grouped = app.group_workouts(workouts)
        for section_title, items in grouped.items():
            # Add section header if grouping is active
            if section_title:
                is_collapsed = app.is_group_collapsed(section_title)
                header = create_group_header(section_title, is_collapsed)
                header.bind(on_release=lambda *_args, group=section_title: self.toggle_group(group))
                container.add_widget(header)
                if is_collapsed:
                    continue  # Skip items in collapsed groups

            # Add workout cards for this section
            for summary in items:
                container.add_widget(create_workout_card(summary))

    def toggle_group(self, group_name):
        """
        Toggle the collapsed/expanded state of a workout group.

        Args:
            group_name (str): The name of the group to toggle
        """
        app = App.get_running_app()
        app.toggle_group(group_name)
        self.refresh_list()


class WorkoutDetailScreen(Screen):
    """
    Screen for displaying detailed information about a single workout.

    Shows the workout name, date, and a list of all exercises with their
    sets, reps, and weights. Provides navigation to edit or delete the workout.
    """

    def set_workout(self, workout_id):
        """
        Load and display the details of a specific workout.

        Args:
            workout_id (int): The ID of the workout to display
        """
        app = App.get_running_app()
        db = app.db
        self.ids.exercise_list.clear_widgets()

        workout = db.get_workout_by_id(workout_id)
        if not workout:
            self.ids.workout_label.text = "Workout not found."
            self.ids.workout_meta.text = ""
            return

        _, name, date = workout
        exercises = db.get_exercises_for_workout(workout_id)
        self.ids.workout_label.text = name
        self.ids.workout_meta.text = f"{format_date_display(date)}  |  {len(exercises)} exercise entries"

        if not exercises:
            empty = create_themed_card(height=82)
            empty.add_widget(create_themed_label("No exercises recorded.", font_size="15sp", height=24))
            self.ids.exercise_list.add_widget(empty)
            return

        for ex_name, sets, reps, weight in exercises:
            card = create_themed_card(height=40 + sets * 30)
            card.add_widget(create_themed_label(ex_name, font_size="17sp", bold=True, height=26))
            for i in range(sets):
                rep = reps.split(",")[i] if i < len(reps.split(",")) else ""
                weight_value = weight.split(",")[i] if i < len(weight.split(",")) else ""
                card.add_widget(create_themed_label(f"Set {i + 1}: {rep}x{weight_value}kg", font_size="14sp", color=app.muted_text_color, height=22))
            self.ids.exercise_list.add_widget(card)


class WorkoutEditorScreen(Screen):
    """
    Screen for creating new workouts or editing existing ones.

    Provides a comprehensive interface for workout data entry including:
    - Workout name and date input
    - Dynamic exercise management (add/remove exercises)
    - Set management within each exercise (add/remove sets)
    - Data validation and saving
    - Draft saving for work-in-progress
    """

    editing_id = None  # ID of workout being edited, None for new workouts

    def on_pre_enter(self):
        """
        Called when entering the editor screen.

        If creating a new workout, restores any saved draft data.
        """
        if self.editing_id is None:
            app = App.get_running_app()
            if getattr(app, "editor_draft", None):
                app.restore_editor_draft()
            # otherwise keep existing editor fields as-is to avoid accidental clearing

    def on_pre_leave(self):
        """
        Called when leaving the editor screen.

        If creating a new workout, saves current state as a draft.
        """
        if self.editing_id is None:
            App.get_running_app().capture_editor_draft()

    def open_date_picker(self):
        """
        Display a date picker popup for selecting the workout date.

        Creates a custom date picker with spinners for day, month, and year,
        along with a preview of the selected date.
        """
        app = App.get_running_app()
        current = parse_editor_date(self.ids.workout_date.text.strip()) or datetime.now()

        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, app.card_color, 22)
        content.add_widget(create_themed_label("Select workout date", font_size="16sp", bold=True, height=28))

        picker_row = BoxLayout(size_hint_y=None, height=dp(52), spacing=dp(8))
        month_spinner = Spinner(
            text=f"{current.month:02d}",
            values=[f"{month:02d}" for month in range(1, 13)],
            background_normal="",
            background_down="",
            background_color=app.input_color,
            color=app.text_color,
        )
        day_spinner = Spinner(
            text=f"{current.day:02d}",
            values=[f"{day:02d}" for day in range(1, 32)],
            background_normal="",
            background_down="",
            background_color=app.input_color,
            color=app.text_color,
        )
        year_spinner = Spinner(
            text=str(current.year),
            values=[str(year) for year in range(current.year - 20, current.year + 6)],
            background_normal="",
            background_down="",
            background_color=app.input_color,
            color=app.text_color,
        )
        picker_row.add_widget(day_spinner)
        picker_row.add_widget(month_spinner)
        picker_row.add_widget(year_spinner)
        content.add_widget(picker_row)

        preview = create_themed_label(current.strftime("%A, %d %b %Y"), font_size="14sp", color=app.muted_text_color, height=26)
        content.add_widget(preview)

        def update_preview(*_args):
            try:
                selected = datetime(int(year_spinner.text), int(month_spinner.text), int(day_spinner.text))
                preview.text = selected.strftime("%A, %d %b %Y")
            except ValueError:
                preview.text = "Choose a valid calendar date"

        month_spinner.bind(text=update_preview)
        day_spinner.bind(text=update_preview)
        year_spinner.bind(text=update_preview)

        action_row = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        today_btn = create_action_button("Today", app.panel_color, text_color=app.text_color)
        cancel_btn = create_action_button("Cancel", app.panel_color, text_color=app.text_color)
        save_btn = create_action_button("Use Date", app.primary_color)
        action_row.add_widget(today_btn)
        action_row.add_widget(cancel_btn)
        action_row.add_widget(save_btn)
        content.add_widget(action_row)

        popup = Popup(
            title="Workout Date",
            content=content,
            size_hint=(0.9, 0.42),
            separator_color=app.primary_color,
            title_color=app.text_color,
            background_color=(0, 0, 0, 0.75 if app.theme_mode == "dark" else 0.4),
        )

        def set_today(_instance):
            now = datetime.now()
            day_spinner.text = f"{now.day:02d}"
            month_spinner.text = f"{now.month:02d}"
            year_spinner.text = str(now.year)

        def confirm(_instance):
            try:
                selected = datetime(int(year_spinner.text), int(month_spinner.text), int(day_spinner.text))
            except ValueError:
                app.show_popup("Workout Date", "Please choose a valid calendar date.")
                return
            self.ids.workout_date.text = selected.strftime("%d-%m-%Y")
            popup.dismiss()

        today_btn.bind(on_release=set_today)
        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        save_btn.bind(on_release=confirm)
        popup.open()

    def add_exercise_row(self, ex=None):
        """
        Add a new exercise row to the editor.

        Collapses all existing exercise rows for better focus on the new one.
        If exercise data is provided, pre-populates the row with that data.

        Args:
            ex (dict, optional): Exercise data to pre-populate the row with
        """
        for existing_row in self.ids.exercise_rows.children:
            if hasattr(existing_row, "collapse"):
                existing_row.collapse()

        row = ExerciseRow()
        if ex:
            row.set_exercise_name(ex["name"])
            row.ids.set_rows.clear_widgets()
            reps_list = ex["reps"]
            weights_list = ex["weights"]
            max_sets = max(len(reps_list), len(weights_list), ex.get("sets", 0), 1)
            for index in range(max_sets):
                reps = reps_list[index] if index < len(reps_list) else ""
                weights = weights_list[index] if index < len(weights_list) else ""
                row.add_set_row(reps=reps, weights=weights)
            if ex.get("expanded", False):
                row.expand()
            else:
                row.collapse()
        self.ids.exercise_rows.add_widget(row)

    def load_workout(self, workout_id):
        """
        Load an existing workout into the editor for modification.

        Args:
            workout_id (int): ID of the workout to load
        """
        app = App.get_running_app()
        workout = app.db.get_workout_by_id(workout_id)
        if not workout:
            return

        self.editing_id = workout_id
        _, name, date = workout
        self.ids.workout_name.text = name
        self.ids.workout_date.text = format_editor_date(date)
        self.ids.exercise_rows.clear_widgets()

        exercises = app.db.get_exercises_for_workout(workout_id)
        if exercises:
            for ex_name, sets, reps, weight in exercises:
                self.add_exercise_row({
                    "name": ex_name,
                    "sets": sets,
                    "reps": [value.strip() for value in reps.split(",") if value.strip()],
                    "weights": [value.strip() for value in weight.split(",") if value.strip()],
                })
        else:
            self.add_exercise_row()

    def load_draft(self, draft):
        """
        Load a saved draft into the editor.

        Args:
            draft (dict): Draft data to load
        """
        self.editing_id = None
        self.ids.workout_name.text = draft.get("workout_name", "")
        self.ids.workout_date.text = format_editor_date(draft.get("workout_date", datetime.now().strftime("%Y-%m-%d")))
        self.ids.exercise_rows.clear_widgets()

        exercises = draft.get("exercises", [])
        if exercises:
            for exercise in exercises:
                self.add_exercise_row(ex=exercise)
        else:
            self.add_exercise_row()

    def reset_new_workout(self):
        """
        Reset the editor to create a new workout.
        """
        self.editing_id = None
        self.ids.workout_name.text = ""
        self.ids.workout_date.text = datetime.now().strftime("%d-%m-%Y")
        self.ids.exercise_rows.clear_widgets()
        self.add_exercise_row()

    def has_unsaved_content(self):
        name = self.ids.workout_name.text.strip()
        if name:
            return True

        for row in self.ids.exercise_rows.children:
            try:
                row_name = row.ids.name.text.strip()
            except Exception:
                continue
            if row_name and row_name != "Select exercise":
                return True

            set_rows_container = getattr(row, "ids", {}).get("set_rows")
            if set_rows_container:
                for set_row in set_rows_container.children:
                    if set_row.ids.reps.text.strip() or set_row.ids.weights.text.strip():
                        return True

        return False

    def clear_workout(self):
        self.reset_new_workout()
        App.get_running_app().editor_draft = None

    def confirm_clear_workout(self):
        if not self.has_unsaved_content():
            self.clear_workout()
            return

        app = App.get_running_app()
        content = BoxLayout(orientation="vertical", spacing=dp(8), padding=dp(12))
        content.add_widget(create_themed_label("Clear workout?", font_size="16sp", bold=True, height=26))
        content.add_widget(create_themed_label("This will clear all entries in the editor.", font_size="14sp", height=24))

        btn_row = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        cancel_btn = create_action_button("Cancel", app.panel_color, text_color=app.text_color)
        confirm_btn = create_action_button("Clear", app.danger_color)
        btn_row.add_widget(cancel_btn)
        btn_row.add_widget(confirm_btn)
        content.add_widget(btn_row)

        popup = Popup(
            title="Clear Workout",
            content=content,
            size_hint=(0.8, 0.32),
            separator_color=app.primary_color,
            title_color=app.text_color,
            background_color=(0, 0, 0, 0.75 if app.theme_mode == "dark" else 0.4),
        )

        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        confirm_btn.bind(on_release=lambda *_args: (self.clear_workout(), popup.dismiss()))
        popup.open()

    def build_draft(self):
        """
        Build a draft object from the current editor state.

        Returns:
            dict: Draft data containing workout name, date, and exercises
        """
        exercises = []
        for row in self.ids.exercise_rows.children[::-1]:
            set_rows = row.get_set_rows()
            exercises.append({
                "name": row.ids.name.text if row.ids.name.text != "Select exercise" else "",
                "sets": len(set_rows),
                "reps": [set_row.ids.reps.text for set_row in set_rows],
                "weights": [set_row.ids.weights.text for set_row in set_rows],
                "expanded": row.expanded,
            })

        return {
            "workout_name": self.ids.workout_name.text,
            "workout_date": self.ids.workout_date.text or datetime.now().strftime("%d-%m-%Y"),
            "exercises": exercises,
        }

    def validate_inputs(self):
        """
        Validate all user inputs before saving.

        Performs comprehensive validation of workout name, date, and all exercise data.
        Shows error messages for any validation failures.

        Returns:
            dict or None: Validated data ready for saving, or None if validation failed
        """
        name = self.ids.workout_name.text.strip()
        date_text = self.ids.workout_date.text.strip()

        if not name:
            self.show_error("Workout name is required.")
            return None

        parsed_date = parse_editor_date(date_text)
        if parsed_date is None:
            self.show_error("Date must be in DD-MM-YYYY format.")
            return None

        exercises = []
        for row in self.ids.exercise_rows.children[::-1]:
            ex_name = row.ids.name.text.strip()

            if not ex_name or ex_name == "Select exercise":
                self.show_error("Exercise name cannot be empty.")
                return None

            set_rows = row.get_set_rows()
            if not set_rows:
                self.show_error("Each exercise must have at least one set.")
                return None

            reps_list = []
            weights_list = []
            for index, set_row in enumerate(set_rows, start=1):
                reps_value = set_row.ids.reps.text.strip()
                weight_value = set_row.ids.weights.text.strip()
                if not reps_value or not weight_value:
                    self.show_error(f"Set {index} for {ex_name} needs reps and weight.")
                    return None
                reps_list.append(reps_value)
                weights_list.append(weight_value)

            sets = len(set_rows)

            exercises.append({
                "name": ex_name,
                "sets": sets,
                "reps": reps_list,
                "weights": weights_list,
            })

        return {
            "workout_name": name,
            "workout_date": parsed_date.strftime("%Y-%m-%d"),
            "exercises": exercises,
        }

    def save_workout(self):
        """
        Validate inputs and save the workout to the database.

        Handles both creating new workouts and updating existing ones.
        Updates the exercise catalog and refreshes all lists after saving.
        """
        app = App.get_running_app()
        data = self.validate_inputs()
        if not data:
            return

        if self.editing_id is None:
            workout_id = app.db.add_workout(data["workout_name"], data["workout_date"])
        else:
            workout_id = self.editing_id
            app.db.update_workout(workout_id, data["workout_name"], data["workout_date"])
            app.db.delete_exercises_for_workout(workout_id)

        for exercise in data["exercises"]:
            app.db.add_exercise(
                workout_id,
                exercise["name"],
                exercise["sets"],
                ",".join(exercise["reps"]),
                ",".join(exercise["weights"]),
            )
            try:
                app.db.add_exercise_to_catalog(exercise["name"])
            except ValueError:
                pass

        self.editing_id = None
        app.editor_draft = None
        app.active_workout_id = workout_id
        app.sync_exercise_library()
        app.refresh_all_lists()
        app.show_detail(workout_id)

    def show_error(self, message):
        """
        Display a validation error message to the user.

        Args:
            message (str): The error message to display
        """
        App.get_running_app().show_popup("Validation", message)


class GoalsScreen(Screen):
    """
    Screen for viewing and managing fitness goals.

    Displays progress toward weight goals for different exercises,
    allowing users to set and update their targets.
    """

    def on_pre_enter(self):
        self.refresh_goals()

    def refresh_goals(self):
        app = App.get_running_app()
        self.ids.goals_list.clear_widgets()
        goals = app.db.get_all_goals()

        if not goals:
            empty = create_themed_card(height=90)
            empty.add_widget(create_themed_label("No goals set yet.", font_size="16sp", bold=True, height=28))
            empty.add_widget(create_themed_label("Add goals for your main lifts to track progress here.", font_size="14sp", color=app.muted_text_color, height=24))
            self.ids.goals_list.add_widget(empty)
            return

        for exercise_id, name, goal in goals:
            highest_weight, best_reps = app.db.get_highest_weight_for_exercise(name)
            percent = "N/A" if goal in (None, 0) else f"{(highest_weight / goal * 100):.1f}%"

            card = create_themed_card()
            title = create_themed_label(name, font_size="18sp", bold=True, height=30)
            title.valign = "top"
            title.bind(
                width=lambda inst, value: setattr(inst, "text_size", (max(value, 1), None)),
                texture_size=lambda inst, value: setattr(inst, "height", max(dp(28), value[1])),
            )
            card.add_widget(title)
            card.add_widget(create_themed_label(f"Goal: {goal if goal is not None else 'none'} kg", font_size="14sp", color=app.muted_text_color, height=22))
            card.add_widget(create_themed_label(f"Best Set: {best_reps}x{highest_weight} kg   |   Progress: {percent}", font_size="14sp", color=app.muted_text_color, height=22))
            action = create_action_button("Update Goal", app.primary_color)
            action.bind(on_release=lambda *_args, eid=exercise_id: self.show_goal_input(eid))
            card.add_widget(action)
            self.ids.goals_list.add_widget(card)

    def show_goal_input(self, exercise_id):
        app = App.get_running_app()
        popup_content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(popup_content, app.card_color, 22)

        input_goal = TextInput(
            text="",
            multiline=False,
            hint_text="Enter new goal weight",
            input_filter="float",
            background_normal="",
            background_active="",
            background_color=app.input_color,
            foreground_color=app.text_color,
            hint_text_color=app.muted_text_color,
            cursor_color=app.primary_color,
            keyboard_suggestions=True,
            use_bubble=True,
            use_handles=True,
            padding=[dp(16), dp(14), dp(16), dp(14)],
            size_hint_y=None,
            height=dp(48),
        )
        popup_content.add_widget(input_goal)

        buttons = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        cancel_btn = create_action_button("Cancel", app.danger_color)
        save_btn = create_action_button("Save", app.accent_color)
        buttons.add_widget(cancel_btn)
        buttons.add_widget(save_btn)
        popup_content.add_widget(buttons)

        popup = Popup(
            title="Set Goal",
            content=popup_content,
            size_hint=(0.86, 0.38),
            separator_color=app.primary_color,
            title_color=app.text_color,
            background_color=(0, 0, 0, 0.75 if app.theme_mode == "dark" else 0.4),
        )

        def confirm(_instance):
            goal_value = input_goal.text.strip()
            if goal_value == "":
                new_goal = None
            else:
                try:
                    new_goal = float(goal_value)
                except ValueError:
                    app.show_popup("Invalid Input", "Goal must be a number or empty.")
                    return

            app.db.update_goal(exercise_id, new_goal)
            popup.dismiss()
            self.refresh_goals()

        save_btn.bind(on_release=confirm)
        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.open()


class SettingsScreen(Screen):
    """
    Screen for app settings including theme, sync, and preferences.
    """

    def on_pre_enter(self):
        """
        Called when entering the settings screen.

        Refreshes the sync status display.
        """
        App.get_running_app().refresh_sync_status()

    def toggle_theme(self):
        """
        Toggle between light and dark themes.
        """
        App.get_running_app().toggle_theme()

    def cycle_filter(self):
        """
        Cycle to the next filter option.
        """
        App.get_running_app().cycle_filter()

    def cycle_sort(self):
        """
        Cycle to the next sort option.
        """
        App.get_running_app().cycle_sort()

    def cycle_group(self):
        """
        Cycle to the next grouping option.
        """
        App.get_running_app().cycle_group()

    def sign_in_drive(self):
        """
        Toggle Google Drive sign-in/sign-out process.
        If signed in, disconnects. If signed out, initiates sign-in.
        """
        app = App.get_running_app()
        if app.drive_connected:
            app.disconnect_google_drive()
            app.show_popup("Signed Out", "You have been signed out from Google Drive.")
        else:
            app.connect_google_drive()

    def backup_drive(self):
        """
        Backup data to Google Drive.
        """
        App.get_running_app().backup_to_drive()

    def restore_drive(self):
        """
        Restore data from Google Drive.
        """
        App.get_running_app().restore_from_drive()

    def reset_preferences(self):
        """
        Reset all user preferences to defaults.
        """
        App.get_running_app().reset_preferences()

    def manage_exercises(self):
        """
        Open the exercise library management interface.
        """
        App.get_running_app().show_exercise_library()