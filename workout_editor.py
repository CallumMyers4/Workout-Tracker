from PyQt5.QtWidgets import QDialog, QVBoxLayout, QLabel, QLineEdit, QPushButton, QScrollArea, QWidget, QHBoxLayout, QMessageBox, QDateEdit
from PyQt5.QtCore import QDate

from db_helper import DBHelper
from exercise_entry import ExerciseEntry

class WorkoutEditor(QDialog):
    def __init__(self, db, parent=None):
        super().__init__(parent)
        self.db = db
        self.setWindowTitle("Add/Edit Workout")
        self.resize(600, 600)

        self.layout = QVBoxLayout()
        self.setLayout(self.layout)

        # Workout name input
        self.workout_name_input = QLineEdit()
        self.workout_name_input.setPlaceholderText("Workout Name")
        self.layout.addWidget(QLabel("Workout Name:"))
        self.layout.addWidget(self.workout_name_input)

        # Workout date input
        self.workout_date_input = QDateEdit()
        self.workout_date_input.setCalendarPopup(True)
        self.workout_date_input.setDate(QDate.currentDate())
        self.layout.addWidget(QLabel("Workout Date:"))
        self.layout.addWidget(self.workout_date_input)

        # Scroll area for exercises
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll_content = QWidget()
        self.scroll.setWidget(self.scroll_content)

        self.exercises_container_layout = QVBoxLayout()
        self.scroll_content.setLayout(self.exercises_container_layout)

        self.layout.addWidget(self.scroll)

        # Button to add exercises
        self.add_exercise_btn = QPushButton("Add Exercise")
        self.add_exercise_btn.clicked.connect(self.add_exercise_entry)
        self.layout.addWidget(self.add_exercise_btn)

        # Save workout button
        self.save_btn = QPushButton("Save Workout")
        self.save_btn.clicked.connect(self.save_workout)
        self.layout.addWidget(self.save_btn)

        self.exercise_entries = []
        self.add_exercise_entry()

    def add_exercise_entry(self):
        helper = DBHelper()
        entry = ExerciseEntry(helper)
        self.exercises_container_layout.addWidget(entry)
        self.exercise_entries.append(entry)

    def save_workout(self):
        name = self.workout_name_input.text().strip()
        date = self.workout_date_input.date().toString("yyyy-MM-dd")

        if not name:
            QMessageBox.warning(self, "Validation Error", "Workout name cannot be empty.")
            return

        exercises_data = []
        for entry in self.exercise_entries:
            data = entry.get_data()
            if not data["name"]:
                QMessageBox.warning(self, "Validation Error", "Exercise name cannot be empty.")
                return

            # Optional: validate reps and weights counts and values here
            if not all(data["reps"]) or not all(data["weight"]):
                QMessageBox.warning(self, "Validation Error", "Reps and weights must be filled for all sets.")
                return

            if len(data["reps"]) != data["sets"] or len(data["weight"]) != data["sets"]:
                QMessageBox.warning(self, "Validation Error", "Mismatch in sets, reps, and weights counts.")
                return

            exercises_data.append(data)

        # Save workout and exercises to DB (adjust to your DB helper functions)
        workout_id = self.db.add_workout(name, date)

        for ex in exercises_data:
            # Convert reps and weights list to comma separated strings for DB
            reps_str = ",".join(ex["reps"])
            weights_str = ",".join(ex["weight"])
            self.db.add_exercise(workout_id, ex["name"], ex["sets"], reps_str, weights_str)

        QMessageBox.information(self, "Success", "Workout saved successfully!")
        self.accept()
