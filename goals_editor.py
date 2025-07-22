from PyQt5.QtWidgets import (
    QWidget, QVBoxLayout, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QMessageBox, QHBoxLayout, QDialog
)
from PyQt5.QtCore import Qt

class GoalsEditor(QDialog):
    def __init__(self, db, parent=None):
        super().__init__(parent)
        self.db = db
        self.setWindowTitle("View and Change Goals")
        self.resize(800, 600)

        self.layout = QVBoxLayout()
        self.setLayout(self.layout)

        # Table to display workouts
        self.table = QTableWidget()
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["Exercise", "Current Goal (Kg)", "Highest Weight (Kg)", "% Reached"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)

        # Allow editing only in the Goal column (column 1)
        self.table.setEditTriggers(QAbstractItemView.DoubleClicked | QAbstractItemView.SelectedClicked)
        self.layout.addWidget(self.table)

        # Save button
        self.save_btn = QPushButton("Save Goals")
        self.save_btn.clicked.connect(self.save_goals)
        self.layout.addWidget(self.save_btn)

        # Load goals initially
        self.load_goals()

    def load_goals(self):
        self.table.setRowCount(0)
        goals = self.db.get_all_goals()
        for goal in goals:
            self.add_goal_row(goal)

    def add_goal_row(self, exercise):
        row = self.table.rowCount()
        self.table.insertRow(row)
        exercise_id, name, goal = exercise

        name_item = QTableWidgetItem(name)
        display_goal = "none" if goal is None else str(goal)
        goal_item = QTableWidgetItem(display_goal)

        # Get highest weight from DB for this exercise
        highest_weight = self.db.get_highest_weight_for_exercise(name)
        highest_weight_display = str(highest_weight) if highest_weight else "0"
        highest_weight_item = QTableWidgetItem(highest_weight_display)

        percent_reached = self.calculate_percentage(name, goal)
        percent_reached_item = QTableWidgetItem(percent_reached)

        # Set editable flags - only goal editable
        goal_item.setFlags(goal_item.flags() | Qt.ItemIsEditable)
        name_item.setFlags(name_item.flags() & ~Qt.ItemIsEditable)
        highest_weight_item.setFlags(highest_weight_item.flags() & ~Qt.ItemIsEditable)
        percent_reached_item.setFlags(percent_reached_item.flags() & ~Qt.ItemIsEditable)

        name_item.setData(Qt.UserRole, exercise_id)

        self.table.setItem(row, 0, name_item)
        self.table.setItem(row, 1, goal_item)
        self.table.setItem(row, 2, highest_weight_item)
        self.table.setItem(row, 3, percent_reached_item)

        self.table.setRowHeight(row, 30)

    def save_goals(self):
        for row in range(self.table.rowCount()):
            name_item = self.table.item(row, 0)
            goal_item = self.table.item(row, 1)
            if not name_item or not goal_item:
                continue

            exercise_id = name_item.data(Qt.UserRole)
            goal_text = goal_item.text().strip().lower()

            if goal_text == "none":
                new_goal = None
            else:
                try:
                    new_goal = float(goal_item.text())
                except ValueError:
                    QMessageBox.warning(self, "Invalid Input",
                        f"Goal in row {row + 1} is not a valid number or 'none'.")
                    return

            # Update the goal in the database
            self.db.update_goal(exercise_id, new_goal)

        QMessageBox.information(self, "Goals Saved", "Goals updated successfully!")
        self.accept()

    def calculate_percentage(self, exercise_name, goal):
        if goal is None or goal == 0:
            return "N/A"

        highest_weight = self.db.get_highest_weight_for_exercise(exercise_name)
        if highest_weight == 0:
            return "0%"

        percent = (highest_weight / goal) * 100
        return f"{percent:.1f}%"