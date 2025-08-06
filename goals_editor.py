from PyQt5.QtWidgets import (
    QDialog, QVBoxLayout, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QMessageBox
)
from PyQt5.QtCore import Qt
import matplotlib.pyplot as plt
from exercise_graph import ExerciseGraph

class GoalsEditor(QDialog):
    def __init__(self, db, parent=None):
        super().__init__(parent)
        self.db = db

        self.setWindowTitle("View and Change Goals")
        self.resize(800, 600)

        self.layout = QVBoxLayout(self)
        self.setLayout(self.layout)

        # Table Setup
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels([
            "Exercise", "Current Goal (Kg)", "Highest Weight (Kg)", "% Reached", "Progress"
        ])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.setEditTriggers(QAbstractItemView.DoubleClicked | QAbstractItemView.SelectedClicked)
        self.layout.addWidget(self.table)

        # Save Button
        self.save_btn = QPushButton("Save Goals")
        self.save_btn.clicked.connect(self.save_goals)
        self.layout.addWidget(self.save_btn)

        # Load initial data
        self.load_goals()

    def load_goals(self):
        self.table.setRowCount(0)
        goals = self.db.get_all_goals()
        for exercise in goals:
            self.add_goal_row(exercise)

    def add_goal_row(self, exercise):
        row = self.table.rowCount()
        self.table.insertRow(row)

        exercise_id, name, goal = exercise

        # Get highest weight and percent reached
        highest_weight = self.db.get_highest_weight_for_exercise(name)
        highest_weight = highest_weight if highest_weight is not None else 0
        percent_reached = self.calculate_percentage(highest_weight, goal)

        # Set up table items
        name_item = QTableWidgetItem(name)
        goal_item = QTableWidgetItem("none" if goal is None else str(goal))
        high_item = QTableWidgetItem(str(highest_weight))
        percent_item = QTableWidgetItem(percent_reached)

        # Set editability
        name_item.setFlags(name_item.flags() & ~Qt.ItemIsEditable)
        high_item.setFlags(high_item.flags() & ~Qt.ItemIsEditable)
        percent_item.setFlags(percent_item.flags() & ~Qt.ItemIsEditable)
        goal_item.setFlags(goal_item.flags() | Qt.ItemIsEditable)

        # Store exercise ID
        name_item.setData(Qt.UserRole, exercise_id)

        # Add to table
        self.table.setItem(row, 0, name_item)
        self.table.setItem(row, 1, goal_item)
        self.table.setItem(row, 2, high_item)
        self.table.setItem(row, 3, percent_item)

        # Graph Button
        graph_btn = QPushButton("📈")
        graph_btn.clicked.connect(lambda _, btn=graph_btn: self.show_exercise_progress(btn))
        self.table.setCellWidget(row, 4, graph_btn)

        self.table.setRowHeight(row, 30)

    def calculate_percentage(self, highest_weight, goal):
        if goal is None or goal == 0:
            return "N/A"
        percent = (highest_weight / goal) * 100
        return f"{percent:.1f}%"

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
                    new_goal = float(goal_text)
                except ValueError:
                    QMessageBox.warning(
                        self, "Invalid Input",
                        f"Row {row + 1}: Goal must be a number or 'none'."
                    )
                    return

            self.db.update_goal(exercise_id, new_goal)

        QMessageBox.information(self, "Success", "Goals saved successfully.")
        self.accept()
            
    def show_exercise_progress(self, button):
        # Find the row associated with the clicked button
        for row in range(self.table.rowCount()):
            if self.table.cellWidget(row, 4) == button:
                name_item = self.table.item(row, 0)
                goal_item = self.table.item(row, 1)
                exercise_name = name_item.text()
                goal_text = goal_item.text().strip().lower()
                goal = None if goal_text == "none" else float(goal_text)
                break
        else:
            QMessageBox.warning(self, "Error", "Could not determine which exercise to plot.")
            return

        # Fetch and process history
        history = self.db.get_exercise_history(exercise_name)
        if not history:
            QMessageBox.information(self, "No Data", f"No history for '{exercise_name}'.")
            return

        dates, avg_weights, max_weights = ExerciseGraph.compute_avg_weight_per_rep(history)

        if not dates:
            QMessageBox.information(self, "No Valid Sets", f"No usable data for '{exercise_name}'.")
            return

        # Show graph
        ExerciseGraph.plot_average_weight_per_rep(dates, avg_weights, exercise_name, goal, max_weights)