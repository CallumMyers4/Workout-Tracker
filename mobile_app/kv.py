# kv.py
# This file contains the Kivy language (KV) string that defines the UI layouts and styles for the mobile app.
# Kivy language is a declarative way to describe user interfaces, similar to CSS for HTML.
# It defines widget properties, layouts, and event handlers in a clean, hierarchical format.
# The KV string is loaded by the Builder in the app and applied to create the visual interface.

KV = """
# Base Screen class - applied to all screens in the app
# Sets a consistent background color for every screen
<Screen>:
    canvas.before:
        Color:
            rgba: app.bg_color  # Background color from current theme
        Rectangle:
            pos: self.pos
            size: self.size

# Global Label styling - applied to all Label widgets
# Ensures consistent text color and font size across the app
<Label>:
    color: app.text_color      # Primary text color from theme
    font_size: '15sp'          # Standard font size (sp = scalable pixels)

# Custom label classes for different text styles
<TitleLabel@Label>:
    color: app.text_color      # Primary text color
    font_size: '26sp'          # Large font for titles
    bold: True                 # Bold weight for emphasis

<BodyLabel@Label>:
    color: app.text_color      # Primary text color
    font_size: '15sp'          # Standard body text size

<MutedLabel@Label>:
    color: app.muted_text_color # Secondary/muted text color
    font_size: '13sp'          # Smaller font for less important text

# SurfaceCard - reusable card component with rounded background
# Used throughout the app for containing related content
<SurfaceCard@BoxLayout>:
    orientation: 'vertical'           # Stack children vertically
    size_hint_y: None                 # Fixed height, not proportional
    height: self.minimum_height       # Auto-size based on content
    padding: dp(14)                  # Internal padding (dp = density-independent pixels)
    spacing: dp(8)                   # Space between child widgets
    canvas.before:
        Color:
            rgba: app.card_color     # Card background color from theme
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [22]             # Corner radius for rounded appearance

# PrimaryButton - main action button with primary color theme
# Used for primary actions like "Save", "Open", etc.
<PrimaryButton@Button>:
    background_normal: ''            # No default background image
    background_down: ''              # No pressed background image
    background_color: 0, 0, 0, 0    # Transparent background (let canvas draw it)
    color: 1, 1, 1, 1               # White text
    bold: True                       # Bold text
    font_size: '15sp'                # Standard font size
    size_hint_y: None                # Fixed height
    height: dp(48)                   # Button height
    canvas.before:
        Color:
            rgba: (app.primary_color[0] * 0.86, app.primary_color[1] * 0.86, app.primary_color[2] * 0.86, app.primary_color[3]) if self.state == 'down' else app.primary_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]             # Rounded corners

# AccentButton - secondary action button with accent color
# Used for positive but secondary actions
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

# GhostButton - tertiary button with subtle background
# Used for less important actions, adapts to compact mode
<GhostButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: app.text_color              # Text color matches theme
    bold: True
    font_size: '13sp' if app.compact_mode else '14sp'  # Smaller in compact mode
    size_hint_y: None
    height: dp(46)
    canvas.before:
        Color:
            rgba: (app.panel_color[0] * 0.9, app.panel_color[1] * 0.9, app.panel_color[2] * 0.9, app.panel_color[3]) if self.state == 'down' else app.panel_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [18]

# DangerButton - destructive action button with danger color
# Used for delete/remove actions
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

# ModernInput - styled text input field
# Used for user text input throughout the app
<ModernInput@TextInput>:
    multiline: False                  # Single line input
    background_normal: ''            # No background image
    background_active: ''            # No active background image
    background_color: app.input_color # Input field background from theme
    foreground_color: app.text_color # Text color
    hint_text_color: app.muted_text_color  # Placeholder text color
    cursor_color: app.primary_color   # Cursor color
    keyboard_suggestions: True       # Show keyboard suggestions
    use_bubble: True                 # Use bubble for text selection
    use_handles: True                # Use handles for text selection
    padding: [dp(16), dp(14), dp(16), dp(14)]  # Internal padding
    font_size: '15sp'
    size_hint_y: None
    height: dp(52)

# PickerButton - button styled like an input field
# Used for selection inputs that look like text fields
<PickerButton@Button>:
    background_normal: ''
    background_down: ''
    background_color: 0, 0, 0, 0
    color: app.text_color if self.text != 'Select exercise' else app.muted_text_color  # Different color for placeholder
    font_size: '15sp'
    size_hint_y: None
    height: dp(52)
    halign: 'left'                   # Left-align text
    valign: 'middle'                 # Center text vertically
    padding: [dp(16), 0]            # Left padding only
    text_size: self.size             # Allow text wrapping within button
    canvas.before:
        Color:
            rgba: (app.input_color[0] * 0.94, app.input_color[1] * 0.94, app.input_color[2] * 0.94, app.input_color[3]) if self.state == 'down' else app.input_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [16]

# WorkoutListScreen - main screen showing list of workouts
# Displays workout library with search, filtering, and navigation
<WorkoutListScreen>:
    BoxLayout:
        orientation: 'vertical'
        size_hint: (1, 1)
        padding: [dp(14), dp(56), dp(14), dp(56)]  # Top: 56dp (status bar + safe margin), Bottom: 56dp (nav bar)
        spacing: dp(10)

        # Header card with title and summary
        SurfaceCard:
            size_hint_y: None
            height: dp(80)  # Restored from dp(70)
            TitleLabel:
                text: 'Workout Library'
                size_hint_y: None
                height: dp(34)
                halign: 'left'
                text_size: self.width, None  # Allow text wrapping
            MutedLabel:
                text: app.browse_summary     # Dynamic summary text
                size_hint_y: None
                height: dp(40)
                halign: 'left'
                valign: 'top'
                text_size: self.width, self.height

        # Search input field
        ModernInput:
            id: search_input
            text: app.search_text           # Bound to app's search text
            hint_text: 'Search workouts, dates, or exercises'
            on_text: root.update_search(self.text)  # Call screen method on text change
            size_hint_y: None
            height: dp(48)

        # Control buttons for filtering and sorting
        BoxLayout:
            size_hint_y: None
            height: dp(46)  # Restored
            GridLayout:
                cols: 2 if app.compact_mode else 4  # Fewer columns in compact mode
                spacing: dp(8)
                GhostButton:
                    text: 'Filter: ' + app.filter_label  # Dynamic label
                    on_release: root.cycle_filter()      # Call screen method
                GhostButton:
                    text: 'Sort: ' + app.sort_label
                    on_release: root.cycle_sort()
                GhostButton:
                    text: 'Group: ' + app.group_label
                    on_release: root.cycle_group()
                GhostButton:
                    text: 'Refresh'
                    on_release: root.refresh_list()

        # Spacer to prevent content overlap
        Widget:
            size_hint_y: None
            height: dp(40) if app.compact_mode else dp(4)

        # Scrollable workout list
        ScrollView:
            do_scroll_x: False              # Vertical scrolling only
            size_hint_y: 1                  # Take remaining space
            BoxLayout:
                id: workout_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(8), dp(2), dp(12)]

        # Bottom navigation bar
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(100) if app.compact_mode else dp(48)
            spacing: dp(6)
            AccentButton:
                text: 'Home'
                on_release: app.show_list(check=False)
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

# WorkoutDetailScreen - screen showing detailed view of a single workout
# Displays workout information and list of exercises
<WorkoutDetailScreen>:
    BoxLayout:
        orientation: 'vertical'
        size_hint: (1, 1)
        padding: [dp(14), dp(56), dp(14), dp(56)]  # Top: 56dp (status bar + safe margin), Bottom: 56dp (nav bar)
        spacing: dp(10)

        # Top action buttons
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 3
            height: dp(104) if app.compact_mode else dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Back'
                on_release: app.show_list(check=False)
            PrimaryButton:
                text: 'Edit'
                on_release: app.open_editor(app.active_workout_id)
            DangerButton:
                text: 'Delete'
                on_release: app.delete_workout(app.active_workout_id)

        # Workout header information
        SurfaceCard:
            size_hint_y: None
            height: dp(116)
            TitleLabel:
                id: workout_label
                text: ''                    # Set dynamically
                size_hint_y: None
                height: dp(34)
                halign: 'left'
                text_size: self.width, None
            MutedLabel:
                id: workout_meta
                text: ''                    # Date and exercise count
                size_hint_y: None
                height: dp(24)
                halign: 'left'
                text_size: self.width, None
            BodyLabel:
                text: 'Exercises'
                bold: True
                size_hint_y: None
                height: dp(24)

        # Spacer to prevent content overlap
        Widget:
            size_hint_y: None
            height: dp(6)

        # Scrollable exercise list
        ScrollView:
            do_scroll_x: False
            size_hint_y: 1                  # Take remaining space
            BoxLayout:
                id: exercise_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        # Bottom navigation bar
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(100) if app.compact_mode else dp(48)
            spacing: dp(6)
            AccentButton:
                text: 'Home'
                on_release: app.show_list(check=False)
            GhostButton:
                text: 'New Workout'
                on_release: app.open_editor()
            GhostButton:
                text: 'Goals'
                on_release: app.show_goals()
            GhostButton:
                text: 'Settings'
                on_release: app.show_settings()

# WorkoutEditorScreen - screen for creating/editing workouts
# Contains form fields for workout details and exercise management
<WorkoutEditorScreen>:
    BoxLayout:
        orientation: 'vertical'
        size_hint: (1, 1)
        padding: [dp(14), dp(56), dp(14), dp(56)]  # Top: 56dp (status bar + safe margin), Bottom: 56dp (nav bar)
        spacing: dp(10)

        # Top action buttons
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 3
            height: dp(104) if app.compact_mode else dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Clear'
                on_release: root.confirm_clear_workout()
            GhostButton:
                text: 'Add Exercise'
                on_release: root.add_exercise_row()
            AccentButton:
                text: 'Save'
                on_release: root.save_workout()

        # Screen title and description
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

        # Workout name input
        BodyLabel:
            text: 'Workout Name'
            size_hint_y: None
            height: dp(22)
            bold: True
        ModernInput:
            id: workout_name
            hint_text: 'Push Day, Long Run, Lower Body'
            size_hint_y: None

        # Workout date picker
        BodyLabel:
            text: 'Workout Date'
            size_hint_y: None
            height: dp(22)
            bold: True
        PickerButton:
            id: workout_date
            text: 'Select date'
            on_release: root.open_date_picker()
            size_hint_y: None

        # Hidden column headers (shown when exercises are present)
        BoxLayout:
            size_hint_y: None
            height: dp(0)                  # Initially hidden
            spacing: dp(8)
            padding: [dp(8), 0, dp(8), 0]
            opacity: 0                     # Initially transparent
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

        # Spacer to prevent content overlap
        Widget:
            size_hint_y: None
            height: dp(6)

        # Scrollable exercise rows container
        ScrollView:
            do_scroll_x: False
            size_hint_y: 1                  # Take remaining space
            BoxLayout:
                id: exercise_rows
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(10)
                padding: [dp(2), dp(2), dp(2), dp(12)]

        # Bottom navigation bar
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(100) if app.compact_mode else dp(48)
            spacing: dp(6)
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

# ExerciseRow - widget for managing a single exercise in the editor
# Contains exercise picker, expand/collapse controls, and set management
<ExerciseRow>:
    size_hint_y: None
    orientation: 'vertical'
    height: dp(0)                      # Initially collapsed
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
            text: 'Hide'               # Changes to 'Show' when collapsed
            on_release: root.toggle_expanded()
        GhostButton:
            text: '+ Set'
            on_release: root.add_set_row()
    BoxLayout:
        id: detail_area
        orientation: 'vertical'
        size_hint_y: None
        height: 0                      # Initially hidden
        spacing: 0
        BoxLayout:
            id: set_rows
            orientation: 'vertical'
            size_hint_y: None
            height: 0                  # Initially collapsed
            spacing: dp(8)
        MutedLabel:
            id: collapsed_summary
            text: ''                    # Shows summary when collapsed
            size_hint_y: None
            height: dp(22)
            opacity: 0                 # Initially hidden
            halign: 'left'
            valign: 'middle'
            text_size: self.size

# ExerciseSetRow - widget for a single set within an exercise
# Contains inputs for reps and weight, plus remove button
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

# GoalsScreen - screen for viewing fitness goals and progress
# Shows goals tracking and navigation
<GoalsScreen>:
    BoxLayout:
        orientation: 'vertical'
        size_hint: (1, 1)
        padding: [dp(14), dp(56), dp(14), dp(56)]  # Top: 56dp (status bar + safe margin), Bottom: 56dp (nav bar)
        spacing: dp(10)
        GridLayout:
            size_hint_y: None
            cols: 2
            height: dp(48)
            spacing: dp(10)
            GhostButton:
                text: 'Home'
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
        
        # Spacer to prevent content overlap
        Widget:
            size_hint_y: None
            height: dp(6)

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
            height: dp(100) if app.compact_mode else dp(48)
            spacing: dp(6)
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

# SettingsScreen - screen for app configuration and preferences
# Contains theme toggle, sync settings, browse defaults, and exercise management
<SettingsScreen>:
    BoxLayout:
        orientation: 'vertical'
        size_hint: (1, 1)
        padding: [dp(14), dp(56), dp(14), dp(56)]  # Top: 56dp (status bar + safe margin), Bottom: 56dp (nav bar)
        spacing: dp(8)

        # Top buttons for navigation and theme toggle
        GridLayout:
            size_hint_y: None
            cols: 2
            height: dp(48)
            spacing: dp(10)
            AccentButton:
                text: 'Theme: ' + app.theme_label
                on_release: root.toggle_theme()

        # Spacer to prevent content overlap
        Widget:
            size_hint_y: None
            height: dp(6)

        # Scrollable settings content
        ScrollView:
            do_scroll_x: False
            BoxLayout:
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(12)
                padding: [dp(2), dp(2), dp(2), dp(8)]

                # Settings header
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

                # Google Drive sync settings
                SurfaceCard:
                    BodyLabel:
                        text: 'Google Drive Sync'
                        bold: True
                        size_hint_y: None
                        height: dp(24)
                    MutedLabel:
                        text: app.sync_status_text    # Dynamic status text
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
                            disabled: app.pending_drive_sign_in or app.pending_drive_backup or app.pending_drive_restore
                            on_release: root.sign_in_drive()
                        GhostButton:
                            text: app.backup_action_label
                            disabled: (not app.drive_connected) or app.pending_drive_sign_in or app.pending_drive_backup or app.pending_drive_restore
                            on_release: root.backup_drive()
                        GhostButton:
                            text: app.restore_action_label
                            disabled: (not app.drive_connected) or app.pending_drive_sign_in or app.pending_drive_backup or app.pending_drive_restore
                            on_release: root.restore_drive()

                # Browse defaults settings
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

                # Exercise library management
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

        # Bottom navigation bar
        GridLayout:
            size_hint_y: None
            cols: 2 if app.compact_mode else 4
            height: dp(100) if app.compact_mode else dp(48)
            spacing: dp(6)
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