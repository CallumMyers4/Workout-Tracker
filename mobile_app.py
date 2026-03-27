import os
import webbrowser
from collections import OrderedDict
from datetime import datetime, timedelta

from kivy.app import App
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.metrics import dp
from kivy.core.window import Window
from kivy.properties import BooleanProperty, ListProperty, StringProperty
from kivy.storage.jsonstore import JsonStore
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.popup import Popup
from kivy.uix.screenmanager import Screen, ScreenManager, SlideTransition
from kivy.uix.scrollview import ScrollView
from kivy.uix.spinner import Spinner
from kivy.uix.textinput import TextInput

from db_helper import DBHelper
from google_drive_helper import GoogleDriveHelper


SORT_CHOICES = [
    ("Newest", "newest"),
    ("Oldest", "oldest"),
    ("A-Z", "name_asc"),
    ("Z-A", "name_desc"),
]

FILTER_CHOICES = [
    ("All Time", "all"),
    ("Recent 30", "30_days"),
    ("Recent 90", "90_days"),
    ("This Year", "this_year"),
]

GROUP_CHOICES = [
    ("None", "none"),
    ("Month", "month"),
    ("Year", "year"),
]

THEMES = {
    "light": {
        "bg": [0.94, 0.96, 0.99, 1],
        "card": [1, 1, 1, 1],
        "panel": [0.88, 0.91, 0.96, 1],
        "input": [1, 1, 1, 1],
        "text": [0.13, 0.18, 0.26, 1],
        "muted": [0.45, 0.52, 0.61, 1],
        "primary": [0.14, 0.45, 0.78, 1],
        "accent": [0.11, 0.62, 0.45, 1],
        "danger": [0.83, 0.29, 0.29, 1],
    },
    "dark": {
        "bg": [0.08, 0.11, 0.15, 1],
        "card": [0.13, 0.16, 0.21, 1],
        "panel": [0.18, 0.22, 0.28, 1],
        "input": [0.15, 0.18, 0.24, 1],
        "text": [0.92, 0.95, 0.98, 1],
        "muted": [0.63, 0.7, 0.79, 1],
        "primary": [0.3, 0.61, 0.96, 1],
        "accent": [0.18, 0.78, 0.57, 1],
        "danger": [0.93, 0.42, 0.42, 1],
    },
}

KV = """
<Screen>:
    canvas.before:
        Color:
            rgba: app.bg_color
        Rectangle:
            pos: self.pos
            size: self.size

<Label>:
    color: app.text_color
    font_size: '15sp'

<TitleLabel@Label>:
    color: app.text_color
    font_size: '26sp'
    bold: True

<BodyLabel@Label>:
    color: app.text_color
    font_size: '15sp'

<MutedLabel@Label>:
    color: app.muted_text_color
    font_size: '13sp'

<SurfaceCard@BoxLayout>:
    orientation: 'vertical'
    size_hint_y: None
    height: self.minimum_height
    padding: dp(14)
    spacing: dp(8)
    canvas.before:
        Color:
            rgba: app.card_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [22]

<PrimaryButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: 1, 1, 1, 1
    bold: True
    font_size: '15sp'
    size_hint_y: None
    height: dp(48)
    canvas.before:
        Color:
            rgba: (app.primary_color[0] * 0.86, app.primary_color[1] * 0.86, app.primary_color[2] * 0.86, app.primary_color[3]) if self.state == 'down' else app.primary_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]

<AccentButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: 1, 1, 1, 1
    bold: True
    font_size: '15sp'
    size_hint_y: None
    height: dp(48)
    canvas.before:
        Color:
            rgba: (app.accent_color[0] * 0.86, app.accent_color[1] * 0.86, app.accent_color[2] * 0.86, app.accent_color[3]) if self.state == 'down' else app.accent_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]

<GhostButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: app.text_color
    bold: True
    font_size: '13sp' if app.compact_mode else '14sp'
    size_hint_y: None
    height: dp(46)
    canvas.before:
        Color:
            rgba: (app.panel_color[0] * 0.9, app.panel_color[1] * 0.9, app.panel_color[2] * 0.9, app.panel_color[3]) if self.state == 'down' else app.panel_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]

<DangerButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: 1, 1, 1, 1
    bold: True
    font_size: '14sp'
    size_hint_y: None
    height: dp(46)
    canvas.before:
        Color:
            rgba: (app.danger_color[0] * 0.86, app.danger_color[1] * 0.86, app.danger_color[2] * 0.86, app.danger_color[3]) if self.state == 'down' else app.danger_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]

<ModernInput@TextInput>:
    multiline: False
    background_normal: ''
    background_active: ''
    background_color: app.input_color
    foreground_color: app.text_color
    hint_text_color: app.muted_text_color
    cursor_color: app.primary_color
    padding: [dp(16), dp(14), dp(16), dp(14)]
    font_size: '15sp'
    size_hint_y: None
    height: dp(52)

<PickerButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: app.text_color if self.text != 'Select exercise' else app.muted_text_color
    font_size: '15sp'
    size_hint_y: None
    height: dp(52)
    halign: 'left'
    valign: 'middle'
    padding: [dp(16), 0]
    text_size: self.size
    canvas.before:
        Color:
            rgba: (app.input_color[0] * 0.94, app.input_color[1] * 0.94, app.input_color[2] * 0.94, app.input_color[3]) if self.state == 'down' else app.input_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [16]

<WorkoutListScreen>:
    BoxLayout:
        orientation: 'vertical'
        padding: [dp(14), dp(18), dp(14), dp(14)]
        spacing: dp(12)

        SurfaceCard:
            size_hint_y: None
            height: dp(88)
            TitleLabel:
                text: 'Workout Library'
                size_hint_y: None
                height: dp(34)
                halign: 'left'
                text_size: self.width, None
            MutedLabel:
                text: app.browse_summary
                size_hint_y: None
                height: dp(40)
                halign: 'left'
                valign: 'top'
                text_size: self.width, self.height

        ModernInput:
            id: search_input
            text: app.search_text
            hint_text: 'Search workouts, dates, or exercises'
            on_text: root.update_search(self.text)

        BoxLayout:
            size_hint_y: None
            height: dp(100) if app.compact_mode else dp(46)
            GridLayout:
                cols: 2 if app.compact_mode else 4
                spacing: dp(8)
                GhostButton:
                    text: 'Filter: ' + app.filter_label
                    on_release: root.cycle_filter()
                GhostButton:
                    text: 'Sort: ' + app.sort_label
                    on_release: root.cycle_sort()
                GhostButton:
                    text: 'Group: ' + app.group_label
                    on_release: root.cycle_group()
                GhostButton:
                    text: 'Refresh'
                    on_release: root.refresh_list()

        ScrollView:
            do_scroll_x: False
            BoxLayout:
                id: workout_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(104) if app.compact_mode else dp(56)
            spacing: dp(8)
            AccentButton:
                text: 'Home'
                on_release: app.show_list()
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

<WorkoutDetailScreen>:
    BoxLayout:
        orientation: 'vertical'
        padding: [dp(14), dp(18), dp(14), dp(14)]
        spacing: dp(12)

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 3
            height: dp(104) if app.compact_mode else dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Back'
                on_release: app.show_list()
            PrimaryButton:
                text: 'Edit'
                on_release: app.open_editor(app.active_workout_id)
            DangerButton:
                text: 'Delete'
                on_release: app.delete_workout(app.active_workout_id)

        SurfaceCard:
            size_hint_y: None
            height: dp(116)
            TitleLabel:
                id: workout_label
                text: ''
                size_hint_y: None
                height: dp(34)
                halign: 'left'
                text_size: self.width, None
            MutedLabel:
                id: workout_meta
                text: ''
                size_hint_y: None
                height: dp(24)
                halign: 'left'
                text_size: self.width, None
            BodyLabel:
                text: 'Exercises'
                bold: True
                size_hint_y: None
                height: dp(24)

        ScrollView:
            do_scroll_x: False
            BoxLayout:
                id: exercise_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(104) if app.compact_mode else dp(56)
            spacing: dp(8)
            AccentButton:
                text: 'Home'
                on_release: app.show_list()
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

<WorkoutEditorScreen>:
    BoxLayout:
        orientation: 'vertical'
        padding: [dp(14), dp(18), dp(14), dp(14)]
        spacing: dp(12)

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 3
            height: dp(104) if app.compact_mode else dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Cancel'
                on_release: app.show_list()
            GhostButton:
                text: 'Add Exercise'
                on_release: root.add_exercise_row()
            AccentButton:
                text: 'Save'
                on_release: root.save_workout()

        TitleLabel:
            text: 'Workout Editor'
            size_hint_y: None
            height: dp(34)
            halign: 'left'
            text_size: self.width, None

        MutedLabel:
            text: 'Capture the session cleanly, then fine-tune details later.'
            size_hint_y: None
            height: dp(20)
            halign: 'left'
            text_size: self.width, None

        BodyLabel:
            text: 'Workout Name'
            size_hint_y: None
            height: dp(22)
            bold: True
        ModernInput:
            id: workout_name
            hint_text: 'Push Day, Long Run, Lower Body'

        BodyLabel:
            text: 'Workout Date'
            size_hint_y: None
            height: dp(22)
            bold: True
        PickerButton:
            id: workout_date
            text: 'Select date'
            on_release: root.open_date_picker()

        BoxLayout:
            size_hint_y: None
            height: dp(0)
            spacing: dp(8)
            padding: [dp(8), 0, dp(8), 0]
            opacity: 0
            BoxLayout:
                size_hint_x: 1
                padding: [dp(16), 0, 0, 0]
                BodyLabel:
                    text: 'Exercise'
                    bold: True
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size
            BoxLayout:
                size_hint_x: 0.45
                padding: [dp(16), 0, 0, 0]
                BodyLabel:
                    text: 'Sets'
                    bold: True
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size
            BoxLayout:
                size_hint_x: 1
                padding: [dp(16), 0, 0, 0]
                BodyLabel:
                    text: 'Reps'
                    bold: True
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size
            BoxLayout:
                size_hint_x: 1
                padding: [dp(16), 0, 0, 0]
                BodyLabel:
                    text: 'Weights'
                    bold: True
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size
            Widget:
                size_hint_x: None
                width: dp(48)

        ScrollView:
            do_scroll_x: False
            BoxLayout:
                id: exercise_rows
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(104) if app.compact_mode else dp(56)
            spacing: dp(8)
            GhostButton:
                text: 'Home'
                on_release: app.show_list()
            AccentButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

<ExerciseRow>:
    size_hint_y: None
    orientation: 'vertical'
    height: dp(0)
    spacing: dp(8)
    padding: [dp(8), dp(8), dp(8), dp(8)]
    canvas.before:
        Color:
            rgba: app.card_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]
    BoxLayout:
        size_hint_y: None
        height: dp(52)
        spacing: dp(8)
        PickerButton:
            id: name
            text: 'Select exercise'
            on_release: root.open_exercise_picker()
        DangerButton:
            text: 'X'
            size_hint_x: None
            width: dp(48)
            on_release: root.remove_self()
    BoxLayout:
        size_hint_y: None
        height: dp(40)
        spacing: dp(8)
        GhostButton:
            id: toggle_btn
            text: 'Hide'
            on_release: root.toggle_expanded()
        GhostButton:
            text: '+ Set'
            on_release: root.add_set_row()
    BoxLayout:
        id: detail_area
        orientation: 'vertical'
        size_hint_y: None
        height: 0
        spacing: 0
        BoxLayout:
            id: set_rows
            orientation: 'vertical'
            size_hint_y: None
            height: 0
            spacing: dp(8)
        MutedLabel:
            id: collapsed_summary
            text: ''
            size_hint_y: None
            height: dp(22)
            opacity: 0
            halign: 'left'
            valign: 'middle'
            text_size: self.size

<ExerciseSetRow>:
    size_hint_y: None
    height: dp(52)
    spacing: dp(8)
    ModernInput:
        id: reps
        hint_text: 'Reps'
    ModernInput:
        id: weights
        hint_text: 'Weight'
    DangerButton:
        text: '-'
        size_hint_x: None
        width: dp(48)
        on_release: root.remove_self()

<GoalsScreen>:
    BoxLayout:
        orientation: 'vertical'
        padding: [dp(14), dp(18), dp(14), dp(14)]
        spacing: dp(12)
        GridLayout:
            size_hint_y: None
            cols: 2
            height: dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Back'
                on_release: app.show_list()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()
        SurfaceCard:
            size_hint_y: None
            height: dp(92)
            TitleLabel:
                text: 'Goals'
                size_hint_y: None
                height: dp(32)
                halign: 'left'
                text_size: self.width, None
            MutedLabel:
                text: 'Track your strongest lifts against each target.'
                size_hint_y: None
                height: dp(24)
                halign: 'left'
                text_size: self.width, None
        ScrollView:
            do_scroll_x: False
            BoxLayout:
                id: goals_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(104) if app.compact_mode else dp(56)
            spacing: dp(8)
            GhostButton:
                text: 'Home'
                on_release: app.show_list()
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            AccentButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

<SettingsScreen>:
    BoxLayout:
        orientation: 'vertical'
        padding: [dp(14), dp(18), dp(14), dp(14)]
        spacing: dp(12)

        GridLayout:
            size_hint_y: None
            cols: 2
            height: dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Back'
                on_release: app.show_list()
            AccentButton:
                text: 'Theme: ' + app.theme_label
                on_release: root.toggle_theme()

        ScrollView:
            do_scroll_x: False
            BoxLayout:
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(12)
                padding: [dp(2), dp(2), dp(2), dp(8)]

                SurfaceCard:
                    TitleLabel:
                        text: 'Settings'
                        size_hint_y: None
                        height: dp(32)
                        halign: 'left'
                        text_size: self.width, None
                    MutedLabel:
                        text: 'Personalize the look, sync backups, and tune how workouts are browsed.'
                        size_hint_y: None
                        height: dp(42)
                        halign: 'left'
                        valign: 'top'
                        text_size: self.width, self.height

                SurfaceCard:
                    BodyLabel:
                        text: 'Google Drive Sync'
                        bold: True
                        size_hint_y: None
                        height: dp(24)
                    MutedLabel:
                        text: app.sync_status_text
                        size_hint_y: None
                        height: dp(52)
                        halign: 'left'
                        valign: 'top'
                        text_size: self.width, self.height
                    BoxLayout:
                        size_hint_y: None
                        height: dp(46)
                        spacing: dp(8)
                        PrimaryButton:
                            text: app.drive_action_label
                            on_release: root.sign_in_drive()
                        GhostButton:
                            text: 'Backup'
                            on_release: root.backup_drive()
                        GhostButton:
                            text: 'Restore'
                            on_release: root.restore_drive()

                SurfaceCard:
                    BodyLabel:
                        text: 'Browse Defaults'
                        bold: True
                        size_hint_y: None
                        height: dp(24)
                    MutedLabel:
                        text: 'Current view: Filter ' + app.filter_label + ' | Sort ' + app.sort_label + ' | Group ' + app.group_label
                        size_hint_y: None
                        height: dp(42)
                        halign: 'left'
                        valign: 'top'
                        text_size: self.width, self.height
                    BoxLayout:
                        size_hint_y: None
                        height: dp(46)
                        spacing: dp(8)
                        GhostButton:
                            text: 'Cycle Filter'
                            on_release: root.cycle_filter()
                        GhostButton:
                            text: 'Cycle Sort'
                            on_release: root.cycle_sort()
                        GhostButton:
                            text: 'Cycle Group'
                            on_release: root.cycle_group()
                    GhostButton:
                        text: 'Reset Preferences'
                        on_release: root.reset_preferences()

                SurfaceCard:
                    BodyLabel:
                        text: 'Exercise Library'
                        bold: True
                        size_hint_y: None
                        height: dp(24)
                    MutedLabel:
                        text: 'Reuse exercise names in the editor and manage renames across your history.'
                        size_hint_y: None
                        height: dp(42)
                        halign: 'left'
                        valign: 'top'
                        text_size: self.width, self.height
                    GhostButton:
                        text: 'Manage Exercises'
                        on_release: root.manage_exercises()

        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(104) if app.compact_mode else dp(56)
            spacing: dp(8)
            GhostButton:
                text: 'Home'
                on_release: app.show_list()
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            AccentButton:
                text: 'Settings'
                on_release: app.show_settings()

"""


def parse_workout_date(date_text):
    try:
        return datetime.strptime(date_text, "%Y-%m-%d")
    except (TypeError, ValueError):
        return None


def format_date_display(date_text):
    parsed = parse_workout_date(date_text)
    if not parsed:
        return date_text
    return parsed.strftime("%d %b %Y")


def parse_editor_date(date_text):
    for fmt in ("%d-%m-%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(date_text, fmt)
        except (TypeError, ValueError):
            continue
    return None


def format_editor_date(date_text):
    parsed = parse_editor_date(date_text)
    if not parsed:
        return datetime.now().strftime("%d-%m-%Y")
    return parsed.strftime("%d-%m-%Y")


def get_choice_label(choices, value):
    for label, key in choices:
        if key == value:
            return label
    return choices[0][0]


def cycle_choice(choices, current_value):
    keys = [key for _label, key in choices]
    if current_value not in keys:
        return keys[0]
    next_index = (keys.index(current_value) + 1) % len(keys)
    return keys[next_index]


def add_rounded_background(widget, rgba, radius):
    from kivy.graphics import Color, RoundedRectangle

    with widget.canvas.before:
        Color(*rgba)
        widget._bg_rect = RoundedRectangle(pos=widget.pos, size=widget.size, radius=[radius])

    def update_rect(*_args):
        widget._bg_rect.pos = widget.pos
        widget._bg_rect.size = widget.size

    widget.bind(pos=update_rect, size=update_rect)


def shade_color(rgba, factor=0.88):
    return (
        max(0, min(1, rgba[0] * factor)),
        max(0, min(1, rgba[1] * factor)),
        max(0, min(1, rgba[2] * factor)),
        rgba[3] if len(rgba) > 3 else 1,
    )


def add_stateful_rounded_background(widget, rgba, radius, pressed_factor=0.88):
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
    card = BoxLayout(orientation="vertical", spacing=spacing, padding=padding, size_hint_y=None)
    if height is not None:
        card.height = dp(height)
    else:
        card.bind(minimum_height=card.setter("height"))
    add_rounded_background(card, App.get_running_app().card_color, 22)
    return card


def create_themed_label(text, font_size="15sp", bold=False, color=None, height=24):
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
    if scroll_view is not None:
        scroll_view.scroll_y = 1


class WorkoutListScreen(Screen):
    def on_pre_enter(self):
        self.refresh_list()

    def update_search(self, value):
        app = App.get_running_app()
        if app.search_text != value:
            app.search_text = value
            app.save_preferences()
            self.refresh_list()

    def cycle_filter(self):
        App.get_running_app().cycle_filter()
        self.refresh_list()

    def cycle_sort(self):
        App.get_running_app().cycle_sort()
        self.refresh_list()

    def cycle_group(self):
        App.get_running_app().cycle_group()
        self.refresh_list()

    def refresh_list(self):
        app = App.get_running_app()
        container = self.ids.workout_list
        container.clear_widgets()

        workouts = app.get_filtered_workouts()
        app.browse_summary = app.build_browse_summary(workouts)

        if not workouts:
            empty = create_themed_card(height=92)
            empty.add_widget(create_themed_label("No workouts match your current view.", font_size="16sp", bold=True, height=28))
            empty.add_widget(create_themed_label("Try a different search, filter, or grouping option.", font_size="14sp", color=app.muted_text_color, height=24))
            container.add_widget(empty)
            return

        grouped = app.group_workouts(workouts)
        for section_title, items in grouped.items():
            if section_title:
                is_collapsed = app.is_group_collapsed(section_title)
                header = create_group_header(section_title, is_collapsed)
                header.bind(on_release=lambda *_args, group=section_title: self.toggle_group(group))
                container.add_widget(header)
                if is_collapsed:
                    continue
            for summary in items:
                container.add_widget(create_workout_card(summary))

    def toggle_group(self, group_name):
        app = App.get_running_app()
        app.toggle_group(group_name)
        self.refresh_list()


class WorkoutDetailScreen(Screen):
    def set_workout(self, workout_id):
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
            card = create_themed_card(height=96)
            card.add_widget(create_themed_label(ex_name, font_size="17sp", bold=True, height=26))
            card.add_widget(create_themed_label(f"{sets} sets", font_size="14sp", color=app.muted_text_color, height=20))
            card.add_widget(create_themed_label(f"Reps: {reps}   |   Weight: {weight}", font_size="14sp", color=app.muted_text_color, height=22))
            self.ids.exercise_list.add_widget(card)


class ExerciseRow(BoxLayout):
    expanded = BooleanProperty(True)
    HEADER_HEIGHT = dp(52)
    ACTION_HEIGHT = dp(40)
    SET_ROW_HEIGHT = dp(52)
    SUMMARY_HEIGHT = dp(22)
    OUTER_SPACING = dp(8)
    OUTER_PADDING = dp(8)

    def on_kv_post(self, _base_widget):
        self._detail_area = self.ids.detail_area.__self__
        self._set_rows_container = self.ids.set_rows.__self__
        self._collapsed_summary_label = self.ids.collapsed_summary.__self__
        self._toggle_button = self.ids.toggle_btn.__self__
        self.refresh_exercise_options()
        if not self._set_rows_container.children:
            self.add_set_row()
        else:
            self.refresh_summary()

    def _stack_height(self, child_count, row_height, spacing):
        if child_count <= 0:
            return 0
        return (row_height * child_count) + (spacing * max(0, child_count - 1))

    def update_layout_height(self):
        set_count = len(self._set_rows_container.children)
        set_rows_height = self._stack_height(set_count, self.SET_ROW_HEIGHT, self.OUTER_SPACING) if self.expanded else 0
        summary_height = 0 if self.expanded else self.SUMMARY_HEIGHT
        detail_height = set_rows_height if self.expanded else summary_height

        self._set_rows_container.height = set_rows_height
        self._collapsed_summary_label.height = summary_height
        self._detail_area.height = detail_height
        self.height = (
            (self.OUTER_PADDING * 2)
            + self.HEADER_HEIGHT
            + self.ACTION_HEIGHT
            + detail_height
            + (self.OUTER_SPACING * 2)
        )

    def refresh_exercise_options(self, selected_name=None):
        app = App.get_running_app()
        options = app.get_exercise_options()
        if selected_name:
            self.ids.name.text = selected_name
        elif self.ids.name.text not in options:
            self.ids.name.text = "Select exercise"

    def set_exercise_name(self, name):
        self.refresh_exercise_options(selected_name=name.strip() if name else None)

    def open_exercise_picker(self):
        app = App.get_running_app()
        options = app.get_exercise_options()
        if not options:
            app.show_popup("Exercise Picker", "No saved exercises yet. Add one from Settings or save a workout first.")
            return

        content = BoxLayout(orientation="vertical", spacing=dp(10), padding=dp(12))
        add_rounded_background(content, app.card_color, 22)
        content.add_widget(create_themed_label("Choose an exercise", font_size="16sp", bold=True, height=28))

        option_list = BoxLayout(orientation="vertical", size_hint_y=None, spacing=dp(8), padding=[0, 0, 0, 0])
        option_list.bind(minimum_height=option_list.setter("height"))
        for option in options:
            choice = create_action_button(option, app.panel_color, text_color=app.text_color)
            choice.halign = "left"
            choice.text_size = (0, 0)
            choice.bind(size=lambda inst, _value: setattr(inst, "text_size", (inst.width - dp(20), inst.height)))
            choice.bind(on_release=lambda *_args, selected=option: self._choose_exercise(selected))
            option_list.add_widget(choice)

        scroll = ScrollView(do_scroll_x=False, size_hint_y=None, height=dp(280))
        scroll.add_widget(option_list)
        content.add_widget(scroll)

        close_btn = create_action_button("Close", app.panel_color, text_color=app.text_color)
        content.add_widget(close_btn)

        popup = Popup(
            title="Exercise Picker",
            content=content,
            size_hint=(0.9, 0.72),
            separator_color=app.primary_color,
            title_color=app.text_color,
            background_color=(0, 0, 0, 0.75 if app.theme_mode == "dark" else 0.4),
        )
        self._picker_popup = popup
        close_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.bind(on_open=lambda *_args: Clock.schedule_once(lambda _dt: scroll_to_top(scroll), 0))
        popup.open()

    def _choose_exercise(self, name):
        self.set_exercise_name(name)
        if hasattr(self, "_picker_popup") and self._picker_popup:
            self._picker_popup.dismiss()
            self._picker_popup = None

    def add_set_row(self, reps="", weights=""):
        row = ExerciseSetRow()
        row.exercise_row = self
        row.ids.reps.text = reps
        row.ids.weights.text = weights
        self._set_rows_container.add_widget(row)
        self.expanded = True
        self.refresh_summary()

    def toggle_expanded(self):
        self.expanded = not self.expanded
        self.refresh_summary()

    def collapse(self):
        self.expanded = False
        self.refresh_summary()

    def expand(self):
        self.expanded = True
        self.refresh_summary()

    def get_set_rows(self):
        return self._set_rows_container.children[::-1]

    def refresh_summary(self):
        set_count = len(self._set_rows_container.children)
        self._toggle_button.text = "Hide" if self.expanded else "Show"
        self._collapsed_summary_label.text = f"{set_count} set{'s' if set_count != 1 else ''} ready to edit"
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
        if self.parent:
            self.parent.remove_widget(self)


class ExerciseSetRow(BoxLayout):
    exercise_row = None

    def remove_self(self):
        parent = self.parent
        if not parent:
            return
        exercise_row = self.exercise_row
        if exercise_row is None:
            ancestor = parent
            while ancestor is not None and not isinstance(ancestor, ExerciseRow):
                ancestor = ancestor.parent
            exercise_row = ancestor
        if exercise_row is None:
            parent.remove_widget(self)
            return
        if len(parent.children) <= 1:
            exercise_row.add_set_row()
        self.parent.remove_widget(self)
        exercise_row.refresh_summary()


class WorkoutEditorScreen(Screen):
    editing_id = None

    def on_pre_enter(self):
        if self.editing_id is None:
            App.get_running_app().restore_editor_draft()

    def on_pre_leave(self):
        if self.editing_id is None:
            App.get_running_app().capture_editor_draft()

    def open_date_picker(self):
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
        self.editing_id = None
        self.ids.workout_name.text = ""
        self.ids.workout_date.text = datetime.now().strftime("%d-%m-%Y")
        self.ids.exercise_rows.clear_widgets()
        self.add_exercise_row()

    def build_draft(self):
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
        App.get_running_app().show_popup("Validation", message)


class GoalsScreen(Screen):
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
            highest_weight = app.db.get_highest_weight_for_exercise(name)
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
            card.add_widget(create_themed_label(f"Best: {highest_weight} kg   |   Progress: {percent}", font_size="14sp", color=app.muted_text_color, height=22))
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
    def on_pre_enter(self):
        App.get_running_app().refresh_sync_status()

    def toggle_theme(self):
        App.get_running_app().toggle_theme()

    def cycle_filter(self):
        App.get_running_app().cycle_filter()

    def cycle_sort(self):
        App.get_running_app().cycle_sort()

    def cycle_group(self):
        App.get_running_app().cycle_group()

    def sign_in_drive(self):
        App.get_running_app().connect_google_drive()

    def backup_drive(self):
        App.get_running_app().backup_to_drive()

    def restore_drive(self):
        App.get_running_app().restore_from_drive()

    def reset_preferences(self):
        App.get_running_app().reset_preferences()

    def manage_exercises(self):
        App.get_running_app().show_exercise_library()


class WorkoutApp(App):
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
    drive_connected = BooleanProperty(False)

    def build(self):
        self.db = DBHelper("workouts.db")
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
        self.sm = ScreenManager(transition=SlideTransition())
        self.sm.add_widget(WorkoutListScreen(name="list"))
        self.sm.add_widget(WorkoutDetailScreen(name="detail"))
        self.sm.add_widget(WorkoutEditorScreen(name="editor"))
        self.sm.add_widget(GoalsScreen(name="goals"))
        self.sm.add_widget(SettingsScreen(name="settings"))
        return self.sm

    def update_responsive_state(self, _window, size):
        width, _height = size
        self.compact_mode = width < dp(430)

    def on_stop(self):
        self.save_preferences()
        self.db.close()

    def load_preferences(self):
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
        self.store.put(
            "preferences",
            theme_mode=self.theme_mode,
            search_text=self.search_text,
            filter_mode=self.filter_mode,
            sort_mode=self.sort_mode,
            group_mode=self.group_mode,
        )

    def reset_preferences(self):
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
        self.theme_label = self.theme_mode.title()
        self.filter_label = get_choice_label(FILTER_CHOICES, self.filter_mode)
        self.sort_label = get_choice_label(SORT_CHOICES, self.sort_mode)
        self.group_label = get_choice_label(GROUP_CHOICES, self.group_mode)

    def get_exercise_options(self):
        self.db.sync_exercise_catalog()
        return self.db.get_all_exercise_names()

    def sync_exercise_library(self):
        self.db.sync_exercise_catalog()
        if hasattr(self, "sm") and self.sm.has_screen("editor"):
            editor = self.sm.get_screen("editor")
            for row in editor.ids.exercise_rows.children:
                if hasattr(row, "refresh_exercise_options"):
                    row.refresh_exercise_options(row.ids.name.text if row.ids.name.text != "Select exercise" else None)
        if hasattr(self, "sm") and self.sm.has_screen("goals"):
            self.sm.get_screen("goals").refresh_goals()

    def replace_editor_exercise_name(self, old_name, new_name):
        if not hasattr(self, "sm") or not self.sm.has_screen("editor"):
            return
        editor = self.sm.get_screen("editor")
        for row in editor.ids.exercise_rows.children:
            if row.ids.name.text.strip().lower() == old_name.strip().lower():
                row.set_exercise_name(new_name)

    def apply_theme(self, theme_name):
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
        self.theme_label = self.theme_mode.title()

    def toggle_theme(self):
        new_theme = "dark" if self.theme_mode == "light" else "light"
        self.apply_theme(new_theme)
        self.save_preferences()
        self.refresh_all_lists()

    def cycle_filter(self):
        self.filter_mode = cycle_choice(FILTER_CHOICES, self.filter_mode)
        self.refresh_preference_labels()
        self.save_preferences()

    def cycle_sort(self):
        self.sort_mode = cycle_choice(SORT_CHOICES, self.sort_mode)
        self.refresh_preference_labels()
        self.save_preferences()

    def cycle_group(self):
        self.group_mode = cycle_choice(GROUP_CHOICES, self.group_mode)
        self.collapsed_groups = set()
        self.refresh_preference_labels()
        self.save_preferences()

    def get_workout_summaries(self):
        summaries = []
        for workout_id, name, date, _entries, exercise_count, exercise_names in self.db.get_workout_summaries():
            summaries.append({
                "id": workout_id,
                "name": name or "Untitled Workout",
                "date": date or "",
                "exercise_count": exercise_count or 0,
                "exercise_names": ", ".join([item.strip() for item in exercise_names.split(",") if item.strip()][:4]),
                "parsed_date": parse_workout_date(date),
            })
        return summaries

    def get_filtered_workouts(self):
        items = self.get_workout_summaries()
        search = self.search_text.strip().lower()
        dated_items = [item["parsed_date"] for item in items if item["parsed_date"]]
        reference_date = max(dated_items) if dated_items else datetime.now()

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
        if self.group_mode == "none":
            return OrderedDict([("", workouts)])

        grouped = OrderedDict()
        for item in workouts:
            parsed = item["parsed_date"]
            if parsed is None:
                label = "Unknown Date"
            elif self.group_mode == "month":
                label = parsed.strftime("%B %Y")
            else:
                label = parsed.strftime("%Y")
            grouped.setdefault(label, []).append(item)
        return grouped

    def is_group_collapsed(self, group_name):
        return group_name in self.collapsed_groups

    def toggle_group(self, group_name):
        if group_name in self.collapsed_groups:
            self.collapsed_groups.remove(group_name)
        else:
            self.collapsed_groups.add(group_name)

    def build_browse_summary(self, workouts):
        count = len(workouts)
        noun = "workout" if count == 1 else "workouts"
        return f"{count} {noun} shown. Filter {self.filter_label} | Sort {self.sort_label} | Group {self.group_label}"

    def refresh_all_lists(self):
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
        try:
            available, message = GoogleDriveHelper.environment_status()
        except Exception as exc:
            available, message = False, str(exc)

        if self.drive_error:
            available = False
            message = self.drive_error

        if self.drive_connected and available:
            self.sync_status_text = "Connected to Google Drive. Backups and restores are ready."
            self.drive_action_label = "Connected"
        elif self.pending_drive_sign_in:
            self.sync_status_text = "Waiting for Google authorization to finish."
            self.drive_action_label = "Signing In..."
        else:
            self.sync_status_text = message
            self.drive_action_label = "Sign In"

    def show_popup(self, title, message):
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
            background_color=(0, 0, 0, 0.75 if self.theme_mode == "dark" else 0.4),
        )
        close_btn.bind(on_release=lambda *_args: popup.dismiss())
        popup.open()

    def show_list(self):
        self.sm.current = "list"

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
            if self.editor_draft:
                editor.load_draft(self.editor_draft)
            elif not editor.ids.exercise_rows.children or editor.editing_id is not None:
                editor.reset_new_workout()
            else:
                self.restore_editor_draft()
        else:
            editor.load_workout(workout_id)
        self.sm.current = "editor"

    def capture_editor_draft(self):
        if not hasattr(self, "sm") or not self.sm.has_screen("editor"):
            return
        editor = self.sm.get_screen("editor")
        if editor.editing_id is None:
            draft = editor.build_draft()
            has_content = draft["workout_name"].strip() or any(
                exercise["name"].strip() or any(value.strip() for value in exercise["reps"] + exercise["weights"])
                for exercise in draft["exercises"]
            )
            self.editor_draft = draft if has_content else None

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
            background_color=(0, 0, 0, 0.75 if self.theme_mode == "dark" else 0.4),
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
            background_color=(0, 0, 0, 0.75 if self.theme_mode == "dark" else 0.4),
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
            background_color=(0, 0, 0, 0.75 if self.theme_mode == "dark" else 0.4),
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
            background_color=(0, 0, 0, 0.75 if self.theme_mode == "dark" else 0.4),
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
            self.pending_drive_sign_in = False
            self.drive_connected = False
            self.drive_error = str(exc)
            self.refresh_sync_status()
            self.show_popup("Google Drive", str(exc))

    def backup_to_drive(self):
        if not self.drive_connected or self.drive_helper is None:
            self.show_popup("Google Drive", "Sign in to Google Drive first.")
            return

        try:
            file_id = self.drive_helper.upload_to_folder(
                self.db.db_path,
                folder_name="Workout Tracker Backups",
                mime_type="application/x-sqlite3",
            )
            self.show_popup("Backup Complete", f"Database uploaded successfully.\nDrive File ID: {file_id}")
        except Exception as exc:
            self.show_popup("Backup Failed", str(exc))

    def restore_from_drive(self):
        if not self.drive_connected or self.drive_helper is None:
            self.show_popup("Google Drive", "Sign in to Google Drive first.")
            return

        db_path = self.db.db_path
        try:
            db_dir = os.path.dirname(os.path.abspath(db_path)) or "."
            db_name = os.path.basename(db_path)
            self.db.close()
            downloaded = self.drive_helper.download_from_folder(
                folder_name="Workout Tracker Backups",
                local_dir=db_dir,
                files=[db_name],
            )
            self.db = DBHelper(db_path)
            self.refresh_all_lists()
            self.show_popup("Restore Complete", f"Restored backup:\n{os.path.basename(downloaded[0])}")
        except FileNotFoundError:
            self.db = DBHelper(db_path)
            self.show_popup("Restore Failed", "No matching backup was found in Google Drive.")
        except Exception as exc:
            self.db = DBHelper(db_path)
            self.show_popup("Restore Failed", str(exc))


if __name__ == "__main__":
    WorkoutApp().run()
