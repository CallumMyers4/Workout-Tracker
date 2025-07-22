# workout_tracker.py

from PyQt5.QtWidgets import (
    QWidget, QVBoxLayout, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QMessageBox
)
from PyQt5.QtCore import Qt
from workout_editor import WorkoutEditor


class WorkoutTracker(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self.setWindowTitle("Workout Tracker")
        self.resize(700, 500)

        self.layout = QVBoxLayout(self)

        self.add_workout_btn = QPushButton("Add Workout")
        self.add_workout_btn.clicked.connect(self.open_workout_editor)
        self.layout.addWidget(self.add_workout_btn)

        self.table = QTableWidget()
        self.table.setColumnCount(3)
        self.table.setHorizontalHeaderLabels(["Exercise / Workout", "Reps", "Weight (Kg)"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.cellClicked.connect(self.toggle_workout_details)
        self.layout.addWidget(self.table)

        self.expanded_row = None
        self.expanded_workout_id = None

        self.load_workouts()

        self.delete_workout_btn = QPushButton("Delete Workout")
        self.delete_workout_btn.clicked.connect(self.delete_selected_workout)
        self.layout.addWidget(self.delete_workout_btn)

    def load_workouts(self):
        self.table.setRowCount(0)
        workouts = self.db.get_all_workouts()
        for workout in workouts:
            self.add_workout_row(workout)

    def add_workout_row(self, workout):
        row = self.table.rowCount()
        self.table.insertRow(row)
        workout_id, name, date = workout
        label = f"{name} ({date})"
        item = QTableWidgetItem(label)
        item.setData(Qt.UserRole, workout_id)
        self.table.setItem(row, 0, item)
        self.table.setSpan(row, 0, 1, 3)
        self.table.setRowHeight(row, 30)

    def toggle_workout_details(self, row, column):
        clicked_item = self.table.item(row, 0)
        if not clicked_item:
            return

        workout_id = clicked_item.data(Qt.UserRole)
        if workout_id is None:
            return

        # If clicking the same workout that's already expanded — collapse it
        if self.expanded_workout_id == workout_id:
            self.collapse_details()
            return

        # Collapse previous workout
        if self.expanded_row is not None:
            self.collapse_details()
            # Adjust row index if needed
            if row > self.expanded_row:
                row -= self.details_count

        exercises = self.db.get_exercises_for_workout(workout_id)
        if not exercises:
            QMessageBox.information(self, "No Exercises", "This workout has no exercises.")
            return

        self.expanded_row = row
        self.expanded_workout_id = workout_id
        self.details_count = 0
        self.exercise_set_rows = {}

        for exercise_index, (exercise_name, sets, reps_str, weight_str) in enumerate(exercises):
            insert_row = self.expanded_row + 1 + self.details_count

            reps_list = [r.strip() for r in reps_str.split(",")]
            weight_list = [w.strip() for w in weight_str.split(",")]

            exercise_data = {
                "name": exercise_name,
                "sets": sets,
                "reps": reps_list,
                "weights": weight_list
            }

            self.table.insertRow(insert_row)

            expand_btn = QPushButton(f"▶ {exercise_name}")
            expand_btn.setFlat(True)
            expand_btn.setStyleSheet("text-align: left;")
            expand_btn.clicked.connect(lambda checked, b=expand_btn, d=exercise_data: self.toggle_exercise_sets(b, d))
            self.table.setCellWidget(insert_row, 0, expand_btn)

            self.table.setItem(insert_row, 1, QTableWidgetItem(""))
            self.table.setItem(insert_row, 2, QTableWidgetItem(""))

            self.exercise_set_rows[insert_row] = 0
            self.details_count += 1


    def collapse_details(self):
        if self.expanded_row is not None:
            for _ in range(self.details_count):
                self.table.removeRow(self.expanded_row + 1)
        self.expanded_row = None
        self.expanded_workout_id = None
        self.details_count = 0
        self.exercise_set_rows = {}


    def open_workout_editor(self):
        editor = WorkoutEditor(self.db, self)
        if editor.exec_():
            self.expanded_row = None
            self.expanded_workout_id = None
            self.load_workouts()

    def delete_selected_workout(self):
        selected_row = self.table.currentRow()

        if selected_row == -1:
            QMessageBox.warning(self, "No Selection", "Please select a workout to delete.")
            return

        workout_item = self.table.item(selected_row, 0)
        if not workout_item:
            QMessageBox.warning(self, "Invalid Selection", "Please select a valid workout row.")
            return

        workout_id = workout_item.data(Qt.UserRole)
        if workout_id is None:
            QMessageBox.warning(self, "Invalid Selection", "You must select a workout row (not an exercise row).")
            return

        reply = QMessageBox.question(
            self,
            "Confirm Deletion",
            "Are you sure you want to delete this workout?",
            QMessageBox.Yes | QMessageBox.No
        )

        if reply == QMessageBox.Yes:
            self.db.delete_workout(workout_id)
            self.expanded_row = None
            self.expanded_workout_id = None
            self.load_workouts()

    def toggle_exercise_sets(self, button, exercise_data):
        # Find the row of this button
        row = self.table.indexAt(button.pos()).row()
        if row == -1:
            return  # Button not found in table?

        is_expanded = self.exercise_set_rows.get(row, 0) > 0

        if is_expanded:
            # Collapse sets
            for _ in range(self.exercise_set_rows[row]):
                self.table.removeRow(row + 1)
            button.setText(f"▶ {exercise_data['name']}")
            self.details_count -= self.exercise_set_rows[row]
            self.exercise_set_rows[row] = 0
        else:
            # Expand sets
            sets = exercise_data["sets"]
            reps_list = exercise_data["reps"]
            weights_list = exercise_data["weights"]

            for i in range(sets):
                insert_row = row + 1 + i
                reps = reps_list[i] if i < len(reps_list) else ""
                weight = weights_list[i] if i < len(weights_list) else ""

                self.table.insertRow(insert_row)
                self.table.setItem(insert_row, 0, QTableWidgetItem(f"Set {i + 1}"))
                self.table.setItem(insert_row, 1, QTableWidgetItem(reps))
                self.table.setItem(insert_row, 2, QTableWidgetItem(weight))

            button.setText(f"▼ {exercise_data['name']}")
            self.exercise_set_rows[row] = sets
            self.details_count += sets
