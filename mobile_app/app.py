# app.py
# This file contains the main WorkoutApp class and application logic.

import os
import shutil
import threading
from datetime import datetime, timedelta
from collections import OrderedDict

from kivy.app import App
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.metrics import dp
from kivy.properties import BooleanProperty, ListProperty, StringProperty
from kivy.storage.jsonstore import JsonStore
from kivy.core.window import Window
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.popup import Popup
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.screenmanager import ScreenManager

from common.db_helper import DBHelper
from common.google_drive_helper import GoogleDriveHelper
from .constants import SORT_CHOICES, FILTER_CHOICES, GROUP_CHOICES, THEMES
from .kv import KV
from .screens import WorkoutListScreen, WorkoutDetailScreen, WorkoutEditorScreen, GoalsScreen, SettingsScreen
from .utils import get_choice_label, cycle_choice, parse_workout_date, create_action_button, add_rounded_background, create_themed_label, scroll_to_top

class WorkoutApp(App):
    """
    Main application class for the Workout Tracker mobile app.
    Handles UI, data management, and user interactions.
    """
    compact_mode = BooleanProperty(False)
    bg_color = ListProperty(THEMES["light"]["bg"])
    card_color = ListProperty(THEMES["light"]["card"])
    panel_color = ListProperty(THEMES["light"]["panel"])
    input_color = ListProperty(THEMES["light"]["input"])
    text_color = ListProperty(THEMES["light"]["text"])
    muted_text_color = ListProperty(THEMES["light"]["muted"])
    primary_color = ListProperty(THEMES["light"]["primary"])
    accent_color = ListProperty(THEMES["light"]["accent"])
    danger_color = ListProperty(THEMES["light"]["danger"])
    overlay_color = ListProperty(THEMES["light"]["overlay"])

    theme_mode = StringProperty("light")
    theme_label = StringProperty("Light")
    search_text = StringProperty("")
    filter_mode = StringProperty("all")
    filter_label = StringProperty("All Time")
    sort_mode = StringProperty("newest")
    sort_label = StringProperty("Newest")
    group_mode = StringProperty("none")
    group_label = StringProperty("None")
    browse_summary = StringProperty("Browse all workouts with clean filters and quick access.")
    sync_status_text = StringProperty("Google Drive sync is checking availability.")
    drive_action_label = StringProperty("Sign In")
    backup_action_label = StringProperty("Backup")
    restore_action_label = StringProperty("Restore")
    drive_connected = BooleanProperty(False)
    pending_drive_backup = BooleanProperty(False)
    pending_drive_restore = BooleanProperty(False)
    
    def build(self):
        """
        Initialize the app, database, and UI screens.

        - Opens local SQLite database, ensures exercise catalog is synced.
        - Initializes Google Drive helper for cloud sync.
        - Loads user preferences and theme settings.
        - Sets up the Kivy ScreenManager with all app screens.

        Returns:
            kivy.uix.screenmanager.ScreenManager: root screen manager instance
        """
        self.db = DBHelper()
        self.db.sync_exercise_catalog()
        self.active_workout_id = None
        self.editor_draft = None
        self.drive_helper = None
        self.drive_error = ""
        try:
            self.drive_helper = GoogleDriveHelper(auto_login=False)
            if self.drive_helper.has_saved_android_token():
                self.drive_connected = True
        except Exception as exc:
            self.drive_error = str(exc)
        self.pending_drive_sign_in = False
        self.collapsed_groups = set()
        self.store = JsonStore(os.path.join(self.user_data_dir, "mobile_settings.json"))
        self.update_responsive_state(Window, Window.size)
        try:
            Window.softinput_mode = "below_target"
        except Exception:
            pass
        Window.bind(size=self.update_responsive_state)

        self.load_preferences()
        self.apply_theme(self.theme_mode)
        self.refresh_preference_labels()
        self.refresh_sync_status()

        Builder.load_string(KV)
        self.sm = ScreenManager(size_hint=(1, 1))
        self.sm.add_widget(WorkoutListScreen(name="list"))
        self.sm.add_widget(WorkoutDetailScreen(name="detail"))
        self.sm.add_widget(WorkoutEditorScreen(name="editor"))
        self.sm.add_widget(GoalsScreen(name="goals"))
        self.sm.add_widget(SettingsScreen(name="settings"))
        return self.sm

    def update_responsive_state(self, _window, size):
        """
        Update responsive mode state based on window width.

        If the screen width is smaller than 430dp, compact_mode is enabled.
        This property controls some smaller device UI presentation choices.

        Args:
            _window: Kivy window object (unused, required by binding signature)
            size (tuple): Current window (width, height)
        """
        width, _height = size
        self.compact_mode = width < dp(430)

    def on_stop(self):
        """
        Called when the app is closing.

        Persists user preferences and closes database connection cleanly.
        """
        self.save_preferences()
        self.db.close()

    def load_preferences(self):
        """
        Load persisted user preferences from JsonStore.

        Applies fallback defaults when there is no stored data.
        """
        settings = {
            "theme_mode": "light",
            "search_text": "",
            "filter_mode": "all",
            "sort_mode": "newest",
            "group_mode": "none",
        }
        if self.store.exists("preferences"):
            settings.update(self.store.get("preferences"))

        self.theme_mode = settings["theme_mode"]
        self.search_text = settings["search_text"]
        self.filter_mode = settings["filter_mode"]
        self.sort_mode = settings["sort_mode"]
        self.group_mode = settings["group_mode"]

    def save_preferences(self):
        """
        Persist current app preferences into JsonStore.

        This is usually called on state changes, and on app stop.
        """
        self.store.put(
            "preferences",
            theme_mode=self.theme_mode,
            search_text=self.search_text,
            filter_mode=self.filter_mode,
            sort_mode=self.sort_mode,
            group_mode=self.group_mode,
        )

    def reset_preferences(self):
        """
        Restore browse and visual settings to the default values.

        Applies updates across UI, persists defaults, and refreshes display.
        """
        self.theme_mode = "light"
        self.search_text = ""
        self.filter_mode = "all"
        self.sort_mode = "newest"
        self.group_mode = "none"
        self.apply_theme(self.theme_mode)
        self.refresh_preference_labels()
        self.save_preferences()
        self.refresh_all_lists()
        self.show_popup("Preferences Reset", "Theme and browse options are back to their default values.")

    def refresh_preference_labels(self):        
        """
        Update UI label strings for theme/filter/sort/group states.

        Uses choice lookups so labels reflect the current internal modes.
        """        
        self.theme_label = self.theme_mode.title()
        self.filter_label = get_choice_label(FILTER_CHOICES, self.filter_mode)
        self.sort_label = get_choice_label(SORT_CHOICES, self.sort_mode)
        self.group_label = get_choice_label(GROUP_CHOICES, self.group_mode)

    def get_exercise_options(self):
        """
        Return a list of exercise names from the catalog.

        This function synchronizes the local catalog before retrieval.
        """
        self.db.sync_exercise_catalog()
        return self.db.get_all_exercise_names()

    def sync_exercise_library(self):
        """
        Synchronize exercise catalog and propagate updates to relevant screens.

        Ensures editor and goals data are refreshed after the catalog update.
        """
        self.db.sync_exercise_catalog()
        if hasattr(self, "sm") and self.sm.has_screen("editor"):
            editor = self.sm.get_screen("editor")
            for row in editor.ids.exercise_rows.children:
                if hasattr(row, "refresh_exercise_options"):
                    row.refresh_exercise_options(row.ids.name.text if row.ids.name.text != "Select exercise" else None)
            if editor.editing_id is None:
                self.capture_editor_draft()
        if hasattr(self, "sm") and self.sm.has_screen("goals"):
            self.sm.get_screen("goals").refresh_goals()

    def replace_editor_exercise_name(self, old_name, new_name):
        """
        Replace exercise names in open editor rows when a catalog item is renamed.

        Args:
            old_name (str): original exercise name
            new_name (str): replacement exercise name
        """
        if not hasattr(self, "sm") or not self.sm.has_screen("editor"):
            return
        editor = self.sm.get_screen("editor")
        for row in editor.ids.exercise_rows.children:
            if row.ids.name.text.strip().lower() == old_name.strip().lower():
                row.set_exercise_name(new_name)

    def apply_theme(self, theme_name):
        """
        Apply visual theme colors and state variables based on selected theme.

        Args:
            theme_name (str): one of the supported theme keys ("light"/"dark")
        """
        theme = THEMES.get(theme_name, THEMES["light"])
        self.theme_mode = theme_name if theme_name in THEMES else "light"
        self.bg_color = theme["bg"]
        self.card_color = theme["card"]
        self.panel_color = theme["panel"]
        self.input_color = theme["input"]
        self.text_color = theme["text"]
        self.muted_text_color = theme["muted"]
        self.primary_color = theme["primary"]
        self.accent_color = theme["accent"]
        self.danger_color = theme["danger"]
        self.overlay_color = theme["overlay"]
        Window.clearcolor = theme["bg"]
        self.theme_label = self.theme_mode.title()

    def toggle_theme(self):
        """
        Toggle between light and dark themes and persist the change.

        Also refreshes all visible data to reflect updated colors.
        """
        new_theme = "dark" if self.theme_mode == "light" else "light"
        self.apply_theme(new_theme)
        self.save_preferences()
        self.refresh_all_lists()

    def cycle_filter(self):
        """
        Cycle to the next filter mode and update labels and stored preferences.
        """
        self.filter_mode = cycle_choice(FILTER_CHOICES, self.filter_mode)
        self.refresh_preference_labels()
        self.save_preferences()

    def cycle_sort(self):
        """
        Cycle to the next sort mode and update labels and stored preferences.
        """
        self.sort_mode = cycle_choice(SORT_CHOICES, self.sort_mode)
        self.refresh_preference_labels()
        self.save_preferences()

    def cycle_group(self):
        """
        Cycle to the next group mode and reset collapsed groups.
        """
        self.group_mode = cycle_choice(GROUP_CHOICES, self.group_mode)
        self.collapsed_groups = set()
        self.refresh_preference_labels()
        self.save_preferences()

    def get_workout_summaries(self):
        """
        Retrieve and normalize workout summary data from the database.

        Converts raw SQL rows into front-end friendly dictionaries, including parsed date.

        Returns:
            list[dict]: workout summary data for display
        """
        summaries = []
        for workout_id, name, date, _entries, exercise_count, exercise_names in self.db.get_workout_summaries():
            summaries.append({
                "id": workout_id,
                "name": name or "Untitled Workout",
                "date": date or "",
                "exercise_count": exercise_count or 0,
                "exercise_names": ", ".join([item.strip() for item in exercise_names.split(",")]),
                "parsed_date": parse_workout_date(date),
            })
        return summaries

    def get_filtered_workouts(self):
        """
        Filter and sort unpacked workouts according to user settings.

        Applies search text, date range filters, and sort mode to the precomputed summaries.

        Returns:
            list[dict]: workouts ready for list display
        """
        items = self.get_workout_summaries()
        search = self.search_text.strip().lower()
        reference_date = datetime.now()

        filtered = []
        for item in items:
            haystack = " ".join([item["name"], item["date"], item["exercise_names"]]).lower()
            if search and search not in haystack:
                continue

            workout_date = item["parsed_date"]
            if self.filter_mode == "30_days" and workout_date and workout_date < reference_date - timedelta(days=30):
                continue
            if self.filter_mode == "90_days" and workout_date and workout_date < reference_date - timedelta(days=90):
                continue
            if self.filter_mode == "this_year" and workout_date and workout_date.year != reference_date.year:
                continue

            filtered.append(item)

        if self.sort_mode == "newest":
            filtered.sort(key=lambda item: (item["parsed_date"] or datetime.min, item["id"]), reverse=True)
        elif self.sort_mode == "oldest":
            filtered.sort(key=lambda item: (item["parsed_date"] or datetime.min, item["id"]))
        elif self.sort_mode == "name_asc":
            filtered.sort(key=lambda item: item["name"].lower())
        elif self.sort_mode == "name_desc":
            filtered.sort(key=lambda item: item["name"].lower(), reverse=True)

        return filtered

    def group_workouts(self, workouts):
        """
        Group workouts into OrderedDict sections based on group mode.

        Returns:
            OrderedDict: key=group label, value=list of workouts
        """
        if self.group_mode == "none":
            return OrderedDict([("", workouts)])

        grouped = OrderedDict()
        for item in workouts:
            parsed = item["parsed_date"]
            if self.group_mode == "name":
                label = item["name"] or "Untitled Workout"
            elif parsed is None:
                label = "Unknown Date"
            elif self.group_mode == "month":
                label = parsed.strftime("%B %Y")
            else:
                label = parsed.strftime("%Y")
            grouped.setdefault(label, []).append(item)
        return grouped

    def is_group_collapsed(self, group_name):
        """
        Check whether a group is currently collapsed.

        Returns True when group header has collapsed state set.
        """
        return group_name in self.collapsed_groups

    def toggle_group(self, group_name):
        """
        Toggle collapsed/expanded state for a grouped section.

        This does not refresh UI directly; caller side triggers list reload.
        """
        if group_name in self.collapsed_groups:
            self.collapsed_groups.remove(group_name)
        else:
            self.collapsed_groups.add(group_name)

    def build_browse_summary(self, workouts):
        """
        Build one-line summary of currently shown workouts.

        Includes count and current filter/sort/group labels.
        """
        count = len(workouts)
        noun = "workout" if count == 1 else "workouts"
        return f"{count} {noun} shown. Filter {self.filter_label} | Sort {self.sort_label} | Group {self.group_label}"

    def refresh_all_lists(self):
        """
        Refresh all screen list content after data or settings change.

        Updates exercise catalog and triggers each screen to re-render current data.
        """
        self.sync_exercise_library()
        self.refresh_preference_labels()
        if hasattr(self, "sm"):
            if self.sm.has_screen("list"):
                self.sm.get_screen("list").refresh_list()
            if self.sm.has_screen("goals"):
                self.sm.get_screen("goals").refresh_goals()
            if self.sm.has_screen("detail") and self.active_workout_id:
                self.sm.get_screen("detail").set_workout(self.active_workout_id)

    def refresh_sync_status(self):
        """
        Determine and update Google Drive sync status labels.

        This method checks environment state and local pending flags to reflect
        available actions in settings UI.
        """
        try:
            available, message = GoogleDriveHelper.environment_status()
        except Exception as exc:
            available, message = False, str(exc)

        if self.drive_error:
            available = False
            message = self.drive_error

        self.backup_action_label = "[...] Backup" if self.pending_drive_backup else "Backup"
        self.restore_action_label = "[...] Restore" if self.pending_drive_restore else "Restore"

        if self.pending_drive_backup:
            self.sync_status_text = "Uploading your latest backup to Google Drive..."
            self.drive_action_label = "Connected"
        elif self.pending_drive_restore:
            self.sync_status_text = "Restoring the latest backup from Google Drive..."
            self.drive_action_label = "Connected"
        elif self.drive_connected and available:
            self.sync_status_text = "Connected to Google Drive. Backups and restores are ready."
            self.drive_action_label = "Sign Out"
        elif self.pending_drive_sign_in:
            self.sync_status_text = "Waiting for Google authorization to finish."
            self.drive_action_label = "[...] Sign In"
        else:
            self.sync_status_text = message
            self.drive_action_label = "Sign In"

    def disconnect_google_drive(self):
        """
        Disconnect from Google Drive and clear authentication.
        """
        try:
            if self.drive_helper is not None:
                self.drive_helper.logout()
        except Exception:
            pass
        finally:
            self.drive_helper = None
            self.drive_connected = False
            self.drive_error = "Not signed in"
            self.refresh_sync_status()

    def show_popup(self, title, message):
        """
        Show a modal informative popup with provided title and text.

        Supports multiline messages and ensures proper sizing in the popup.

        Args:
            title (str): popup title bar text
            message (str): body message to display to user
        """
        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, self.card_color, 22)

        scroll = ScrollView(size_hint=(1, 1), do_scroll_x=False, bar_width=dp(4))
        message_label = Label(
            text=message,
            font_size="14sp",
            color=self.text_color,
            size_hint_y=None,
            halign="left",
            valign="top",
        )

        def update_message_layout(instance, _value):
            if instance.width <= 0:
                return
            instance.text_size = (instance.width, None)
            texture = instance.texture_size
            instance.height = max(dp(72), texture[1] + dp(8))

        message_label.bind(width=update_message_layout, texture_size=update_message_layout)
        Clock.schedule_once(lambda _dt: update_message_layout(message_label, None), 0)
        scroll.add_widget(message_label)
        content.add_widget(scroll)

        close_btn = create_action_button("Close", self.primary_color)
        content.add_widget(close_btn)
        popup = Popup(
            title=title,
            content=content,
            size_hint=(0.88, 0.5),
            separator_color=self.primary_color,
            title_color=self.text_color,
            background_color=self.overlay_color,
        )
        close_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.open()

    def show_list(self, check=True):
        if check == True and self.active_workout_id != None:
            self.show_detail(self.active_workout_id)
        else:
            self.sm.current = "list"
            self.active_workout_id = None

    def show_goals(self):
        self.sm.current = "goals"

    def show_settings(self):
        self.refresh_sync_status()
        self.sm.current = "settings"

    def show_detail(self, workout_id):
        self.active_workout_id = workout_id
        self.sm.get_screen("detail").set_workout(workout_id)
        self.sm.current = "detail"

    def open_detail(self, workout_id):
        self.show_detail(workout_id)

    def open_editor(self, workout_id=None):
        editor = self.sm.get_screen("editor")
        if workout_id is None:
            if self.sm.current == "editor" and editor.editing_id is None and editor.ids.exercise_rows.children:
                # If we're already in the new-workout editor, snapshot live values first.
                try:
                    self.editor_draft = editor.build_draft()
                    editor.load_draft(self.editor_draft)
                except Exception:
                    editor.reset_new_workout()
            elif self.editor_draft:
                editor.load_draft(self.editor_draft)
            elif editor.editing_id is None and editor.ids.exercise_rows.children:
                # Rebuild from current values so reopening the screen never reuses stale row widgets.
                try:
                    self.editor_draft = editor.build_draft()
                    editor.load_draft(self.editor_draft)
                except Exception:
                    editor.reset_new_workout()
            else:
                editor.reset_new_workout()
        else:
            editor.load_workout(workout_id)
        self.sm.current = "editor"

    def capture_editor_draft(self):
        if not hasattr(self, "sm") or not self.sm.has_screen("editor"):
            return
        editor = self.sm.get_screen("editor")
        if editor.editing_id is None:
            draft = editor.build_draft()
            # Keep drafts even if partially filled, so switching tabs doesn't drop progress.
            self.editor_draft = draft

    def restore_editor_draft(self):
        if not hasattr(self, "sm") or not self.sm.has_screen("editor"):
            return
        editor = self.sm.get_screen("editor")
        if self.editor_draft:
            editor.load_draft(self.editor_draft)
        else:
            editor.reset_new_workout()

    def show_exercise_library(self):
        self.sync_exercise_library()
        exercises = self.db.get_all_catalog_exercises()
        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, self.card_color, 22)

        if not exercises:
            content.add_widget(create_themed_label("No exercises in the library yet.", font_size="15sp", bold=True, height=28))
            content.add_widget(create_themed_label("Save a workout or add one manually below.", font_size="14sp", color=self.muted_text_color, height=24))
        else:
            scroll = BoxLayout(orientation="vertical", size_hint_y=None, spacing=dp(8), padding=[0, 0, 0, 0])
            scroll.bind(minimum_height=scroll.setter("height"))
            for exercise_id, name, goal in exercises:
                row = BoxLayout(size_hint_y=None, height=dp(46), spacing=dp(8))
                label = create_themed_label(name, font_size="15sp", bold=True, height=46)
                meta = f"Goal {goal:g} kg" if goal not in (None, "") else "No goal"
                label.text = f"{name}  |  {meta}"
                rename_btn = create_action_button("Rename", self.primary_color, width=88, text_color=(0.92, 0.95, 0.98, 1))
                rename_btn.bind(on_release=lambda *_args, eid=exercise_id, ename=name: self.show_rename_exercise_popup(eid, ename))
                row.add_widget(label)
                row.add_widget(rename_btn)
                scroll.add_widget(row)
            scroll_view = ScrollView(do_scroll_x=False, size_hint_y=None, height=dp(280), bar_width=dp(6))
            scroll_view.add_widget(scroll)
            content.add_widget(scroll_view)

        buttons = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        add_btn = create_action_button("Add Exercise", self.accent_color)
        close_btn = create_action_button("Close", self.panel_color, text_color=self.text_color)
        buttons.add_widget(add_btn)
        buttons.add_widget(close_btn)
        content.add_widget(buttons)

        popup = Popup(
            title="Exercise Library",
            content=content,
            size_hint=(0.9, 0.72),
            separator_color=self.primary_color,
            title_color=self.text_color,
            background_color=self.overlay_color,
        )
        add_btn.bind(on_release=lambda *_args: (popup.dismiss(), self.show_add_exercise_popup()))
        close_btn.bind(on_release=lambda *_args: popup.dismiss())
        if exercises:
            popup.bind(on_open=lambda *_args: Clock.schedule_once(lambda _dt: scroll_to_top(scroll_view), 0))
        popup.open()

    def show_add_exercise_popup(self):
        self._show_exercise_name_popup("Add Exercise", "", self.add_library_exercise)

    def show_rename_exercise_popup(self, exercise_id, current_name):
        self._show_exercise_name_popup(
            "Rename Exercise",
            current_name,
            lambda new_name: self.rename_library_exercise(exercise_id, current_name, new_name),
        )

    def _show_exercise_name_popup(self, title, initial_name, on_confirm):
        from kivy.uix.textinput import TextInput
        from .utils import create_action_button, add_rounded_background

        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, self.card_color, 22)
        input_name = TextInput(
            text=initial_name,
            multiline=False,
            hint_text="Exercise name",
            background_normal="",
            background_active="",
            background_color=self.input_color,
            foreground_color=self.text_color,
            hint_text_color=self.muted_text_color,
            cursor_color=self.primary_color,
            keyboard_suggestions=True,
            use_bubble=True,
            use_handles=True,
            padding=[dp(16), dp(14), dp(16), dp(14)],
            size_hint_y=None,
            height=dp(48),
        )
        content.add_widget(input_name)
        buttons = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        cancel_btn = create_action_button("Cancel", self.panel_color, text_color=self.text_color)
        save_btn = create_action_button("Save", self.primary_color)
        buttons.add_widget(cancel_btn)
        buttons.add_widget(save_btn)
        content.add_widget(buttons)
        popup = Popup(
            title=title,
            content=content,
            size_hint=(0.86, 0.34),
            separator_color=self.primary_color,
            title_color=self.text_color,
            background_color=self.overlay_color,
        )

        def confirm(_instance):
            try:
                on_confirm(input_name.text.strip())
            except ValueError as exc:
                self.show_popup(title, str(exc))
                return
            popup.dismiss()

        save_btn.bind(on_release=confirm)
        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.open()

    def add_library_exercise(self, name):
        self.db.add_exercise_to_catalog(name)
        self.sync_exercise_library()
        self.refresh_all_lists()
        self.show_popup("Exercise Library", f"{name} added to the exercise list.")

    def rename_library_exercise(self, exercise_id, current_name, new_name):
        existing = self.db.get_catalog_exercise_by_name(new_name) if new_name else None
        if existing and existing[0] != exercise_id:
            self.show_combine_exercise_popup(exercise_id, current_name, existing[1], new_name)
            return

        result = self.db.rename_exercise_in_catalog(exercise_id, new_name, combine_existing=False)
        self.replace_editor_exercise_name(current_name, result["name"])
        self.sync_exercise_library()
        self.refresh_all_lists()
        self.show_popup("Exercise Library", f"Renamed to {result['name']}.")

    def show_combine_exercise_popup(self, exercise_id, current_name, target_name, requested_name):
        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, self.card_color, 22)
        content.add_widget(
            create_themed_label(
                f"{requested_name} already exists.\nCombine {current_name} into {target_name}?",
                font_size="14sp",
                height=68,
            )
        )
        buttons = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        cancel_btn = create_action_button("Cancel", self.panel_color, text_color=self.text_color)
        combine_btn = create_action_button("Combine", self.accent_color)
        buttons.add_widget(cancel_btn)
        buttons.add_widget(combine_btn)
        content.add_widget(buttons)
        popup = Popup(
            title="Combine Exercises",
            content=content,
            size_hint=(0.88, 0.38),
            separator_color=self.primary_color,
            title_color=self.text_color,
            background_color=self.overlay_color,
        )

        def confirm(_instance):
            result = self.db.rename_exercise_in_catalog(exercise_id, requested_name, combine_existing=True)
            popup.dismiss()
            self.replace_editor_exercise_name(current_name, result["name"])
            self.sync_exercise_library()
            self.refresh_all_lists()
            self.show_popup("Exercise Library", f"Combined into {result['name']}.")

        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        combine_btn.bind(on_release=confirm)
        popup.open()

    def delete_workout(self, workout_id):
        if not workout_id:
            return

        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, self.card_color, 22)
        content.add_widget(create_themed_label("Delete this workout?", font_size="15sp", bold=True, height=28))
        buttons = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        cancel_btn = create_action_button("Cancel", self.panel_color, text_color=self.text_color)
        confirm_btn = create_action_button("Delete", self.danger_color)
        buttons.add_widget(cancel_btn)
        buttons.add_widget(confirm_btn)
        content.add_widget(buttons)
        popup = Popup(
            title="Confirm Delete",
            content=content,
            size_hint=(0.84, 0.32),
            separator_color=self.primary_color,
            title_color=self.text_color,
            background_color=self.overlay_color,
        )

        def confirm(_instance):
            self.db.delete_workout(workout_id)
            popup.dismiss()
            self.active_workout_id = None
            self.refresh_all_lists()
            self.show_list()

        cancel_btn.bind(on_release=lambda *_args: popup.dismiss())
        confirm_btn.bind(on_release=confirm)
        popup.open()

    def connect_google_drive(self):
        try:
            if self.drive_helper is None:
                self.drive_helper = GoogleDriveHelper(auto_login=False)
                self.drive_error = ""

            if "ANDROID_ARGUMENT" in os.environ or "ANDROID_PRIVATE" in os.environ:
                self.pending_drive_sign_in = True
                self.refresh_sync_status()

                def on_success(_access_token):
                    def finalize(_dt):
                        self.pending_drive_sign_in = False
                        self.drive_connected = True
                        self.drive_error = ""
                        self.refresh_sync_status()
                        self.show_popup("Google Drive", "Signed in successfully. Backups are ready.")

                    Clock.schedule_once(finalize, 0)

                def on_failure(message):
                    def finalize(_dt):
                        self.pending_drive_sign_in = False
                        self.drive_connected = False
                        self.drive_error = message
                        self.refresh_sync_status()
                        self.show_popup("Google Drive", message)

                    Clock.schedule_once(finalize, 0)

                self.drive_helper.start_native_android_authorization(on_success, on_failure)
                self.show_popup(
                    "Google Drive Sign-In",
                    "Google's native authorization sheet should appear now.\n\nIf it does not, wait a moment and try again.",
                )
                return

            self.drive_helper.login()
            self.pending_drive_sign_in = False
            self.drive_connected = True
            self.drive_error = ""
            self.refresh_sync_status()
            self.show_popup("Google Drive", "Signed in successfully. Backups are ready.")
        except Exception as exc:
            error_text = str(exc)
            self.pending_drive_sign_in = False
            self.drive_connected = False
            self.drive_error = error_text
            self.refresh_sync_status()
            self.show_popup("Google Drive", error_text)

    def backup_to_drive(self):
        if not self.drive_connected or self.drive_helper is None:
            self.show_popup("Google Drive", "Sign in to Google Drive first.")
            return
        if self.pending_drive_sign_in or self.pending_drive_backup or self.pending_drive_restore:
            return

        self.pending_drive_backup = True
        self.refresh_sync_status()

        def worker():
            try:
                file_id = self.drive_helper.upload_to_folder(
                    self.db.db_path,
                    folder_name="Workout Tracker Backups",
                    mime_type="application/x-sqlite3",
                )
                Clock.schedule_once(
                    lambda _dt: self._finish_drive_backup(success=True, payload=file_id),
                    0,
                )
            except Exception as exc:
                error_text = str(exc)
                Clock.schedule_once(
                    lambda _dt: self._finish_drive_backup(success=False, payload=error_text),
                    0,
                )

        threading.Thread(target=worker, daemon=True).start()

    def restore_from_drive(self):
        if not self.drive_connected or self.drive_helper is None:
            self.show_popup("Google Drive", "Sign in to Google Drive first.")
            return
        if self.pending_drive_sign_in or self.pending_drive_backup or self.pending_drive_restore:
            return

        db_path = self.db.db_path
        db_dir = os.path.dirname(os.path.abspath(db_path)) or "."
        db_name = os.path.basename(db_path)
        self.pending_drive_restore = True
        self.refresh_sync_status()
        temp_restore_dir = os.path.join(db_dir, "_restore_temp")
        os.makedirs(temp_restore_dir, exist_ok=True)

        def worker():
            try:
                downloaded = self.drive_helper.download_from_folder(
                    folder_name="Workout Tracker Backups",
                    local_dir=temp_restore_dir,
                    files=[db_name],
                )
                Clock.schedule_once(
                    lambda _dt: self._finish_drive_restore(db_path, temp_restore_dir, downloaded=downloaded),
                    0,
                )
            except FileNotFoundError:
                Clock.schedule_once(
                    lambda _dt: self._finish_drive_restore(db_path, temp_restore_dir, not_found=True),
                    0,
                )
            except Exception as exc:
                error_text = str(exc)
                Clock.schedule_once(
                    lambda _dt: self._finish_drive_restore(db_path, temp_restore_dir, error=error_text),
                    0,
                )

        threading.Thread(target=worker, daemon=True).start()

    def _finish_drive_backup(self, success, payload):
        self.pending_drive_backup = False
        self.refresh_sync_status()
        if success:
            self.show_popup("Backup Complete", f"Database uploaded successfully.\nDrive File ID: {payload}")
        else:
            # Auto-disconnect on backup failure
            self.disconnect_google_drive()
            self.show_popup("Backup Failed", f"{payload}\n\nYou have been signed out. Please sign in again to retry.")

    def _finish_drive_restore(self, db_path, temp_restore_dir, downloaded=None, not_found=False, error=None):
        self.pending_drive_restore = False
        try:
            if downloaded:
                self.db.close()
                try:
                    shutil.copyfile(downloaded[0], db_path)
                    self.db = DBHelper(db_path)
                    self.refresh_all_lists()
                    self.show_popup("Restore Complete", f"Restored backup:\n{os.path.basename(downloaded[0])}")
                except Exception as exc:
                    self.db = DBHelper(db_path)
                    # Auto-disconnect on restore failure
                    self.disconnect_google_drive()
                    self.show_popup("Restore Failed", f"{str(exc)}\n\nYou have been signed out. Please sign in again to retry.")
                return

            self.db = DBHelper(db_path)
            if not_found:
                self.show_popup("Restore Failed", "No matching backup was found in Google Drive.")
                return

            # Auto-disconnect on restore failure
            self.disconnect_google_drive()
            self.show_popup("Restore Failed", f"{error or 'Restore failed.'}\n\nYou have been signed out. Please sign in again to retry.")
        finally:
            self.refresh_sync_status()
            shutil.rmtree(temp_restore_dir, ignore_errors=True)
