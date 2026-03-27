# widgets.py
# This file contains custom widget classes that extend Kivy's built-in widgets.
# These widgets provide specialized functionality for the workout tracking app,
# including exercise management, set tracking, and interactive UI components.

from kivy.clock import Clock
from kivy.metrics import dp
from kivy.properties import BooleanProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.popup import Popup
from kivy.uix.scrollview import ScrollView
from kivy.uix.spinner import Spinner
from kivy.app import App

from .utils import add_rounded_background, create_action_button, create_themed_label, scroll_to_top


class ExerciseRow(BoxLayout):
    """
    A complex widget representing a single exercise entry in the workout editor.

    This widget manages the complete lifecycle of an exercise within a workout,
    including exercise selection, set management, expand/collapse functionality,
    and dynamic layout adjustments. It serves as a container for multiple set rows
    and provides a collapsible interface to save screen space.

    Key Features:
    - Exercise name selection via popup picker
    - Dynamic addition/removal of set rows
    - Expandable/collapsible detail view
    - Automatic height adjustment based on content
    - Summary display when collapsed
    """

    # BooleanProperty tracks expansion state and triggers UI updates
    expanded = BooleanProperty(True)

    # Layout constants for consistent sizing across all instances
    HEADER_HEIGHT = dp(52)      # Height of the exercise name picker row
    ACTION_HEIGHT = dp(40)      # Height of the action buttons row
    SET_ROW_HEIGHT = dp(52)     # Height of each individual set row
    SUMMARY_HEIGHT = dp(22)     # Height of collapsed summary text
    OUTER_SPACING = dp(8)       # Spacing between major sections
    OUTER_PADDING = dp(8)       # Padding around the entire widget

    def on_kv_post(self, _base_widget):
        """
        Called after the KV language has been applied to this widget.

        Initializes references to child widgets, refreshes exercise options,
        and ensures at least one set row exists.

        Args:
            _base_widget: The base widget (unused parameter from Kivy)
        """
        # Store references to key child widgets for efficient access
        self._detail_area = self.ids.detail_area.__self__
        self._set_rows_container = self.ids.set_rows.__self__
        self._collapsed_summary_label = self.ids.collapsed_summary.__self__
        self._toggle_button = self.ids.toggle_btn.__self__

        # Populate exercise dropdown and ensure we have at least one set
        self.refresh_exercise_options()
        if not self._set_rows_container.children:
            self.add_set_row()
        else:
            self.refresh_summary()

    def _stack_height(self, child_count, row_height, spacing):
        """
        Calculate the total height needed for a stack of equally-sized items.

        Used for determining the height of the set rows container when expanded.

        Args:
            child_count (int): Number of items in the stack
            row_height (int): Height of each individual item
            spacing (int): Space between items

        Returns:
            int: Total height required for the stack
        """
        if child_count <= 0:
            return 0
        # Height = (items × item_height) + ((items - 1) × spacing)
        return (row_height * child_count) + (spacing * max(0, child_count - 1))

    def update_layout_height(self):
        """
        Recalculate and update the total height of this widget based on its current state.

        This method dynamically adjusts the widget's height based on whether it's
        expanded or collapsed, and how many set rows it contains.
        """
        set_count = len(self._set_rows_container.children)

        # Calculate heights for different sections
        set_rows_height = self._stack_height(set_count, self.SET_ROW_HEIGHT, self.OUTER_SPACING) if self.expanded else 0
        summary_height = 0 if self.expanded else self.SUMMARY_HEIGHT
        detail_height = set_rows_height if self.expanded else summary_height

        # Apply calculated heights to child widgets
        self._set_rows_container.height = set_rows_height
        self._collapsed_summary_label.height = summary_height
        self._detail_area.height = detail_height

        # Calculate total widget height
        self.height = (
            (self.OUTER_PADDING * 2)      # Top and bottom padding
            + self.HEADER_HEIGHT          # Exercise picker row
            + self.ACTION_HEIGHT          # Action buttons row
            + detail_height              # Detail area (sets or summary)
            + (self.OUTER_SPACING * 2)   # Spacing between sections
        )

    def refresh_exercise_options(self, selected_name=None):
        """
        Update the available exercise options in the picker button.

        Retrieves current exercise options from the app and updates the
        display text appropriately.

        Args:
            selected_name (str, optional): Specific exercise name to select
        """
        app = App.get_running_app()
        options = app.get_exercise_options()

        if selected_name:
            self.ids.name.text = selected_name
        elif self.ids.name.text not in options:
            # Reset to placeholder if current selection is no longer valid
            self.ids.name.text = "Select exercise"

    def set_exercise_name(self, name):
        """
        Set the exercise name, handling empty/whitespace-only names.

        Args:
            name (str): The exercise name to set
        """
        self.refresh_exercise_options(selected_name=name.strip() if name else None)

    def open_exercise_picker(self):
        """
        Display a popup for selecting an exercise from available options.

        Creates a scrollable list of exercise buttons in a modal popup.
        If no exercises are available, shows an informative message instead.
        """
        app = App.get_running_app()
        options = app.get_exercise_options()

        # Handle case where no exercises exist yet
        if not options:
            app.show_popup("Exercise Picker", "No saved exercises yet. Add one from Settings or save a workout first.")
            return

        # Create popup content container
        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, app.card_color, 22)
        content.add_widget(create_themed_label("Choose an exercise", font_size="16sp", bold=True, height=28))

        # Create scrollable list of exercise options
        option_list = BoxLayout(orientation="vertical", size_hint_y=None, spacing=dp(8), padding=[0, 0, 0, 0])
        option_list.bind(minimum_height=option_list.setter("height"))

        # Add a button for each available exercise
        for option in options:
            choice = create_action_button(option, app.panel_color, text_color=app.text_color)
            choice.halign = "left"
            choice.text_size = (0, 0)
            # Allow text wrapping within button bounds
            choice.bind(size=lambda inst, _value: setattr(inst, "text_size", (inst.width - dp(20), inst.height)))
            # Bind selection to exercise choice method
            choice.bind(on_release=lambda *_args, selected=option: self._choose_exercise(selected))
            option_list.add_widget(choice)

        # Create scrollable container for the option list
        scroll = ScrollView(do_scroll_x=False, size_hint_y=None, height=dp(280))
        scroll.add_widget(option_list)
        content.add_widget(scroll)

        # Add close button
        close_btn = create_action_button("Close", app.panel_color, text_color=app.text_color)
        content.add_widget(close_btn)

        # Create and configure the popup
        popup = Popup(
            title="Exercise Picker",
            content=content,
            size_hint=(0.9, 0.72),  # 90% width, 72% height of screen
            separator_color=app.primary_color,
            title_color=app.text_color,
            background_color=(0, 0, 0, 0.75 if app.theme_mode == "dark" else 0.4),  # Semi-transparent overlay
        )
        self._picker_popup = popup

        # Bind close button and auto-scroll to top when opened
        close_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.bind(on_open=lambda *_args: Clock.schedule_once(lambda _dt: scroll_to_top(scroll), 0))
        popup.open()

    def _choose_exercise(self, name):
        """
        Handle selection of an exercise from the picker popup.

        Updates the exercise name and closes the popup.

        Args:
            name (str): The selected exercise name
        """
        self.set_exercise_name(name)
        if hasattr(self, "_picker_popup") and self._picker_popup:
            self._picker_popup.dismiss()
            self._picker_popup = None

    def add_set_row(self, reps="", weights=""):
        """
        Add a new set row to this exercise with optional pre-filled values.

        Creates a new ExerciseSetRow widget and adds it to the set container.
        Automatically expands the exercise if it was collapsed.

        Args:
            reps (str): Pre-filled reps value for the new set
            weights (str): Pre-filled weights value for the new set
        """
        row = ExerciseSetRow()
        row.exercise_row = self  # Reference back to parent exercise
        row.ids.reps.text = reps
        row.ids.weights.text = weights
        self._set_rows_container.add_widget(row)
        self.expanded = True  # Ensure exercise is expanded when adding sets
        self.refresh_summary()

    def toggle_expanded(self):
        """
        Toggle between expanded and collapsed states.

        Switches the expansion state and updates the UI accordingly.
        """
        self.expanded = not self.expanded
        self.refresh_summary()

    def collapse(self):
        """
        Collapse the exercise details to show only the summary.
        """
        self.expanded = False
        self.refresh_summary()

    def expand(self):
        """
        Expand the exercise details to show all set rows.
        """
        self.expanded = True
        self.refresh_summary()

    def get_set_rows(self):
        """
        Get all set rows in reverse order (for proper display order).

        Returns:
            list: List of ExerciseSetRow widgets in display order
        """
        return self._set_rows_container.children[::-1]

    def refresh_summary(self):
        """
        Update the UI to reflect current expansion state and set count.

        Adjusts button text, visibility of different sections, and triggers
        layout height recalculation.
        """
        set_count = len(self._set_rows_container.children)

        # Update toggle button text based on state
        self._toggle_button.text = "Hide" if self.expanded else "Show"

        # Update summary text for collapsed state
        self._collapsed_summary_label.text = f"{set_count} set{'s' if set_count != 1 else ''} ready to edit"

        # Switch between showing sets or summary based on expansion state
        self._detail_area.clear_widgets()
        if self.expanded:
            self._set_rows_container.opacity = 1
            self._collapsed_summary_label.opacity = 0
            self._detail_area.add_widget(self._set_rows_container)
        else:
            self._set_rows_container.opacity = 0
            self._collapsed_summary_label.opacity = 1
            self._detail_area.add_widget(self._collapsed_summary_label)

        self.update_layout_height()

    def remove_self(self):
        """
        Remove this exercise row from its parent container.

        Called when the user clicks the 'X' button to delete the exercise.
        """
        if self.parent:
            self.parent.remove_widget(self)


class ExerciseSetRow(BoxLayout):
    """
    A widget representing a single set within an exercise.

    Contains input fields for reps and weights, plus a remove button.
    This is the atomic unit of workout data entry.
    """

    exercise_row = None  # Reference to parent ExerciseRow

    def remove_self(self):
        """
        Remove this set row from its parent exercise.

        Ensures that an exercise always has at least one set row.
        If this is the last set, automatically adds a new empty one.
        """
        parent = self.parent
        if not parent:
            return

        # Find the parent ExerciseRow if not already set
        exercise_row = self.exercise_row
        if exercise_row is None:
            ancestor = parent
            while ancestor is not None and not isinstance(ancestor, ExerciseRow):
                ancestor = ancestor.parent
            exercise_row = ancestor

        if exercise_row is None:
            # Fallback: just remove from immediate parent
            parent.remove_widget(self)
            return

        # Ensure exercise always has at least one set
        if len(parent.children) <= 1:
            exercise_row.add_set_row()

        # Remove this set and refresh the parent exercise's summary
        self.parent.remove_widget(self)
        exercise_row.refresh_summary()