# utils.py
# This file contains utility functions that provide common functionality used throughout the mobile app.
# These functions handle date parsing and formatting, choice cycling, UI widget creation,
# and other helper operations that are needed in multiple parts of the application.

from datetime import datetime
from collections import OrderedDict
from kivy.graphics import Color, RoundedRectangle
from kivy.metrics import dp
from kivy.properties import BooleanProperty, ListProperty, StringProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.app import App


def parse_workout_date(date_text):
    """
    Parse a date string in YYYY-MM-DD format into a datetime object.

    This function is used to convert database-stored dates (which are in ISO format)
    into Python datetime objects for date manipulation and comparison.

    Args:
        date_text (str): Date string in "YYYY-MM-DD" format

    Returns:
        datetime or None: Parsed datetime object, or None if parsing fails

    Example:
        >>> parse_workout_date("2024-01-15")
        datetime.datetime(2024, 1, 15, 0, 0)
        >>> parse_workout_date("invalid")
        None
    """
    try:
        return datetime.strptime(date_text, "%Y-%m-%d")
    except (TypeError, ValueError):
        return None


def format_date_display(date_text):
    """
    Format a date string for display in a user-friendly DD MMM YYYY format.

    Converts database dates to a more readable format for the UI, such as
    "15 Jan 2024" instead of "2024-01-15".

    Args:
        date_text (str): Date string in database format

    Returns:
        str: Formatted date string, or original text if parsing fails

    Example:
        >>> format_date_display("2024-01-15")
        "15 Jan 2024"
    """
    parsed = parse_workout_date(date_text)
    if not parsed:
        return date_text
    return parsed.strftime("%d %b %Y")


def parse_editor_date(date_text):
    """
    Parse a date string in various formats (DD-MM-YYYY or YYYY-MM-DD) into a datetime object.

    This function handles user input in the workout editor, which may come in different
    formats depending on user preference or locale.

    Args:
        date_text (str): Date string in either "DD-MM-YYYY" or "YYYY-MM-DD" format

    Returns:
        datetime or None: Parsed datetime object, or None if parsing fails

    Example:
        >>> parse_editor_date("15-01-2024")
        datetime.datetime(2024, 1, 15, 0, 0)
        >>> parse_editor_date("2024-01-15")
        datetime.datetime(2024, 1, 15, 0, 0)
    """
    for fmt in ("%d-%m-%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(date_text, fmt)
        except (TypeError, ValueError):
            continue
    return None


def format_editor_date(date_text):
    """
    Format a date string for the editor in DD-MM-YYYY format.

    Converts dates to the format expected by the workout editor input fields.
    Falls back to today's date if the input cannot be parsed.

    Args:
        date_text (str): Date string to format

    Returns:
        str: Date in "DD-MM-YYYY" format

    Example:
        >>> format_editor_date("2024-01-15")
        "15-01-2024"
    """
    parsed = parse_editor_date(date_text)
    if not parsed:
        return datetime.now().strftime("%d-%m-%Y")
    return parsed.strftime("%d-%m-%Y")


def get_choice_label(choices, value):
    """
    Get the display label for a given choice value from a list of choices.

    Used to convert internal choice keys (like "newest") to user-friendly labels
    (like "Newest") for display in the UI.

    Args:
        choices (list): List of (label, value) tuples
        value (str): The value to find the label for

    Returns:
        str: The corresponding label, or the first choice's label if not found

    Example:
        >>> choices = [("Newest", "newest"), ("Oldest", "oldest")]
        >>> get_choice_label(choices, "newest")
        "Newest"
    """
    for label, key in choices:
        if key == value:
            return label
    return choices[0][0]


def cycle_choice(choices, current_value):
    """
    Cycle to the next choice in the list, wrapping around to the beginning if needed.

    Used for cycling through sort/filter options when users tap buttons to change
    the current selection.

    Args:
        choices (list): List of (label, value) tuples
        current_value (str): The current choice value

    Returns:
        str: The next choice value in the cycle

    Example:
        >>> choices = [("A", "a"), ("B", "b"), ("C", "c")]
        >>> cycle_choice(choices, "a")
        "b"
        >>> cycle_choice(choices, "c")
        "a"
    """
    keys = [key for _label, key in choices]
    if current_value not in keys:
        return keys[0]
    next_index = (keys.index(current_value) + 1) % len(keys)
    return keys[next_index]


def add_rounded_background(widget, rgba, radius):
    """
    Add a rounded background to a Kivy widget using canvas instructions.

    Creates a RoundedRectangle with the specified color and corner radius,
    and binds it to update when the widget's position or size changes.

    Args:
        widget: The Kivy widget to add the background to
        rgba (list): Color as [r, g, b, a] values (0-1 range)
        radius (int): Corner radius in pixels

    Note:
        This modifies the widget's canvas.before to add the background.
        The background will automatically resize with the widget.
    """
    from kivy.graphics import Color, RoundedRectangle

    with widget.canvas.before:
        Color(*rgba)
        widget._bg_rect = RoundedRectangle(pos=widget.pos, size=widget.size, radius=[radius])

    def update_rect(*_args):
        widget._bg_rect.pos = widget.pos
        widget._bg_rect.size = widget.size

    widget.bind(pos=update_rect, size=update_rect)


def shade_color(rgba, factor=0.88):
    """
    Shade a color by multiplying each RGB component by a factor.

    Used to create darker/lighter variants of colors for different UI states.
    The alpha channel is preserved unchanged.

    Args:
        rgba (list): Color as [r, g, b, a] values (0-1 range)
        factor (float): Multiplier for RGB components (default 0.88 for slight darkening)

    Returns:
        tuple: Shaded color as (r, g, b, a)

    Example:
        >>> shade_color([1, 0, 0, 1], 0.5)
        (0.5, 0, 0, 1)
    """
    return (
        max(0, min(1, rgba[0] * factor)),
        max(0, min(1, rgba[1] * factor)),
        max(0, min(1, rgba[2] * factor)),
        rgba[3] if len(rgba) > 3 else 1,
    )


def add_stateful_rounded_background(widget, rgba, radius, pressed_factor=0.88):
    """
    Add a rounded background that changes color when the widget is pressed.

    Creates a background that darkens when the widget's state is "down" (pressed)
    and returns to normal when released. This provides visual feedback for buttons.

    Args:
        widget: The Kivy widget (typically a Button) to add the background to
        rgba (list): Normal state color as [r, g, b, a]
        radius (int): Corner radius in pixels
        pressed_factor (float): Factor to darken color when pressed

    Note:
        Only works on widgets that have a "state" property (like Button widgets).
    """
    from kivy.graphics import Color, RoundedRectangle

    with widget.canvas.before:
        widget._bg_color_instruction = Color(*rgba)
        widget._bg_rect = RoundedRectangle(pos=widget.pos, size=widget.size, radius=[radius])

    def update_rect(*_args):
        widget._bg_rect.pos = widget.pos
        widget._bg_rect.size = widget.size

    def update_color(*_args):
        active_rgba = shade_color(rgba, pressed_factor) if getattr(widget, "state", "normal") == "down" else rgba
        widget._bg_color_instruction.rgba = active_rgba

    widget.bind(pos=update_rect, size=update_rect)
    if hasattr(widget, "state"):
        widget.bind(state=update_color)
    update_color()


def create_themed_card(height=None, padding=dp(14), spacing=dp(8)):
    """
    Create a themed card widget with rounded background and proper theming.

    Cards are the main container widgets used throughout the app for grouping
    related content. They automatically use the current app theme colors.

    Args:
        height (int, optional): Fixed height in dp, or None for auto-sizing
        padding (int): Internal padding in dp (default 14)
        spacing (int): Spacing between child widgets in dp (default 8)

    Returns:
        BoxLayout: Themed card widget ready to add content to

    Note:
        If height is None, the card will auto-size based on its content.
    """
    card = BoxLayout(orientation="vertical", spacing=spacing, padding=padding, size_hint_y=None)
    if height is not None:
        card.height = dp(height)
    else:
        card.bind(minimum_height=card.setter("height"))
    app = App.get_running_app()
    add_rounded_background(card, app.card_color, 22)
    return card


def create_themed_label(text, font_size="15sp", bold=False, color=None, height=24):
    """
    Create a themed label with proper text sizing and theming.

    Labels are used for displaying text throughout the app. This function
    ensures consistent theming and proper text wrapping.

    Args:
        text (str): The text to display
        font_size (str): Font size (default "15sp")
        bold (bool): Whether to make text bold
        color (list, optional): Custom color, or None to use theme text color
        height (int): Height in dp for the label

    Returns:
        Label: Themed label widget
    """
    app = App.get_running_app()
    label = Label(
        text=text,
        font_size=font_size,
        bold=bold,
        color=color or app.text_color,
        size_hint_y=None,
        height=dp(height),
        halign="left",
        valign="middle",
    )
    label.bind(size=lambda inst, _value: setattr(inst, "text_size", inst.size))
    return label


def create_action_button(text, rgba, width=None, text_color=(1, 1, 1, 1)):
    """
    Create a themed action button with rounded background and state feedback.

    Action buttons are used for primary actions like "Save", "Open", "Delete", etc.
    They have visual feedback when pressed and use the specified color scheme.

    Args:
        text (str): Button text
        rgba (list): Button background color as [r, g, b, a]
        width (int, optional): Fixed width in dp, or None for full width
        text_color (list): Text color (default white)

    Returns:
        Button: Themed action button
    """
    button = Button(
        text=text,
        size_hint_y=None,
        height=dp(40),
        size_hint_x=1 if width is None else None,
        width=dp(width) if width is not None else 0,
        background_normal="",
        background_down="",
        background_color=(0, 0, 0, 0),
        color=text_color,
        bold=True,
        font_size="14sp",
    )
    add_stateful_rounded_background(button, rgba, 14)
    return button


def create_workout_card(summary):
    """
    Create a card widget displaying workout summary information.

    This function builds a complete workout card for the workout list screen,
    showing the workout name, date, exercise count, and a preview of exercises.
    Includes an "Open" button to navigate to the workout details.

    Args:
        summary (dict): Workout summary data with keys:
            - "id": Workout ID
            - "name": Workout name
            - "date": Workout date string
            - "exercise_count": Number of exercises
            - "exercise_names": Preview text of exercise names

    Returns:
        BoxLayout: Complete workout card widget

    Note:
        The card height adjusts dynamically based on the exercise preview text.
    """
    app = App.get_running_app()
    workout_id = summary["id"]
    card = create_themed_card(spacing=dp(6))

    title = create_themed_label(summary["name"], font_size="19sp", bold=True, height=30)
    title.text_size = (0, None)
    title.shorten = True
    title.max_lines = 1

    subtitle = create_themed_label(
        f"{format_date_display(summary['date'])}  |  {summary['exercise_count']} exercises",
        font_size="13sp",
        color=app.muted_text_color,
        height=22,
    )
    exercise_preview = summary["exercise_names"] or "No exercise entries yet"
    preview = create_themed_label(
        exercise_preview,
        font_size="14sp",
        color=app.muted_text_color,
        height=42,
    )
    preview.valign = "top"
    preview.bind(
        width=lambda inst, value: setattr(inst, "text_size", (max(value, 1), None)),
        texture_size=lambda inst, value: setattr(inst, "height", max(dp(24), value[1])),
    )

    actions = BoxLayout(size_hint_y=None, height=dp(40), spacing=dp(8))
    open_btn = create_action_button("Open", app.primary_color)
    open_btn.bind(on_release=lambda *_args, wid=workout_id: app.open_detail(wid))
    actions.add_widget(open_btn)
    card.add_widget(title)
    card.add_widget(subtitle)
    card.add_widget(preview)
    card.add_widget(actions)
    card.height = dp(14) * 2 + title.height + subtitle.height + preview.height + actions.height + dp(6) * 3
    preview.bind(
        height=lambda *_args: setattr(
            card,
            "height",
            dp(14) * 2 + title.height + subtitle.height + preview.height + actions.height + dp(6) * 3,
        )
    )
    return card


def create_section_label(text):
    """
    Create a section label for grouping headers in lists.

    Section labels are used to separate different groups of items, such as
    different months or years in grouped workout lists.

    Args:
        text (str): The section header text

    Returns:
        Label: Themed section label widget
    """
    app = App.get_running_app()
    section = Label(
        text=text,
        size_hint_y=None,
        height=dp(28),
        color=app.muted_text_color,
        font_size="13sp",
        bold=True,
        halign="left",
        valign="middle",
    )
    section.bind(size=lambda inst, _value: setattr(inst, "text_size", inst.size))
    return section


def create_group_header(label, is_collapsed):
    """
    Create a collapsible group header button for grouped lists.

    Group headers allow users to expand/collapse sections of grouped workouts.
    Shows a "+" when collapsed and "-" when expanded.

    Args:
        label (str): The group name (e.g., "January 2024")
        is_collapsed (bool): Whether the group is currently collapsed

    Returns:
        Button: Interactive group header button
    """
    app = App.get_running_app()
    chevron = "+" if is_collapsed else "-"
    header = Button(
        text=f"{chevron}  {label}",
        size_hint_y=None,
        height=dp(42),
        background_normal="",
        background_down="",
        background_color=(0, 0, 0, 0),
        color=app.text_color,
        bold=True,
        halign="left",
        valign="middle",
        padding=(dp(16), 0),
    )
    header.bind(size=lambda inst, _value: setattr(inst, "text_size", inst.size))
    add_stateful_rounded_background(header, app.panel_color, 18)
    return header


def scroll_to_top(scroll_view, *_args):
    """
    Scroll a ScrollView to the top position.

    Used when navigating to new screens or after adding items to ensure
    the user sees the most recent content at the top.

    Args:
        scroll_view: The ScrollView widget to scroll
        *_args: Additional arguments (ignored, for use as event callback)

    Note:
        scroll_y = 1 means scrolled to the top (100% from bottom).
    """
    if scroll_view is not None:
        scroll_view.scroll_y = 1