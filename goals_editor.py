from PyQt5.QtWidgets import (
    QDialog, QVBoxLayout, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QMessageBox
)
from PyQt5.QtCore import Qt
import matplotlib.pyplot as plt
from exercise_graph import ExerciseGraph

class GoalsEditor(QDialog):
    def __init__(self, db, parent=None):
        #Create self and database connection
        super().__init__(parent)
        self.db = db

        #Setup window
        self.setWindowTitle("View and Change Goals")
        self.resize(800, 600)

        #Add a layout
        self.layout = QVBoxLayout(self)
        self.setLayout(self.layout)

        #Create the goals table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels([
            "Exercise", "Current Goal (Kg)", "Highest Weight (Kg)", "% Reached", "Progress"
        ])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)

        #What to do when a cell is clicked (edit)
        self.table.setEditTriggers(QAbstractItemView.DoubleClicked | QAbstractItemView.SelectedClicked)
        self.layout.addWidget(self.table)

        #Create Save Button
        self.save_btn = QPushButton("Save Goals")
        self.save_btn.clicked.connect(self.save_goals)
        self.layout.addWidget(self.save_btn)

        #Load initial data
        self.load_goals()

    #Load goals
    def load_goals(self):
        #Clear table
        self.table.setRowCount(0)

        #Get current goals from db
        goals = self.db.get_all_goals()

        #Add a row for each exercise
        for exercise in goals:
            self.add_goal_row(exercise)

    #Add a new row
    def add_goal_row(self, exercise):
        #Get current row number, and create a new one at this position
        row = self.table.rowCount()
        self.table.insertRow(row)

        #Seperate exercise data
        exercise_id, name, goal = exercise

        #Get highest weight and percentange of goal reached
        highest_weight = self.db.get_highest_weight_for_exercise(name)
        highest_weight = highest_weight if highest_weight is not None else 0
        percent_reached = self.calculate_percentage(highest_weight, goal)

        #Fill out the row with data
        name_item = QTableWidgetItem(name)
        goal_item = QTableWidgetItem("none" if goal is None else str(goal))
        high_item = QTableWidgetItem(str(highest_weight))
        percent_item = QTableWidgetItem(percent_reached)

        #Set nothing editable except the goal
        name_item.setFlags(name_item.flags() & ~Qt.ItemIsEditable)
        high_item.setFlags(high_item.flags() & ~Qt.ItemIsEditable)
        percent_item.setFlags(percent_item.flags() & ~Qt.ItemIsEditable)
        goal_item.setFlags(goal_item.flags() | Qt.ItemIsEditable)

        #Store exercise ID
        name_item.setData(Qt.UserRole, exercise_id)

        #Add data to row
        self.table.setItem(row, 0, name_item)
        self.table.setItem(row, 1, goal_item)
        self.table.setItem(row, 2, high_item)
        self.table.setItem(row, 3, percent_item)

        #Add a graph button to the row
        graph_btn = QPushButton("📈")
        graph_btn.clicked.connect(lambda _, btn=graph_btn: self.show_exercise_progress(btn))
        self.table.setCellWidget(row, 4, graph_btn)

        self.table.setRowHeight(row, 30)

    #Work out the percentage of the goal reached
    def calculate_percentage(self, highest_weight, goal):
        if goal is None or goal == 0:
            return "N/A"
        percent = (highest_weight / goal) * 100
        return f"{percent:.1f}%"

    #Save any updated data
    def save_goals(self):
        #Go through each row 
        for row in range(self.table.rowCount()):
            name_item = self.table.item(row, 0)
            goal_item = self.table.item(row, 1)

            #If it is empty, skip
            if not name_item or not goal_item:
                continue
            
            #Get the exercise ID and goal value
            exercise_id = name_item.data(Qt.UserRole)
            goal_text = goal_item.text().strip().lower()

            #Skip if still no goal
            if goal_text == "none":
                new_goal = None
            else:
                try:
                    #Extract the value entered
                    new_goal = float(goal_text)
                except ValueError:
                    #Error if invalid input
                    QMessageBox.warning(
                        self, "Invalid Input",
                        f"Row {row + 1}: Goal must be a number or 'none'."
                    )
                    return

            #Update the goal in the database
            self.db.update_goal(exercise_id, new_goal)

        #Show success, and close
        QMessageBox.information(self, "Success", "Goals saved successfully.")
        self.accept()
    
    #Show the graph for the exercise
    def show_exercise_progress(self, button):
        #Find the row associated with the clicked button
        for row in range(self.table.rowCount()):
            #If clicking the graph button
            if self.table.cellWidget(row, 4) == button:
                #Extract the data
                name_item = self.table.item(row, 0)
                goal_item = self.table.item(row, 1)
                exercise_name = name_item.text()
                goal_text = goal_item.text().strip().lower()

                #Either return no goal set, or convert data to float
                goal = None if goal_text == "none" else float(goal_text)
                break
        else:
            #Show warining if no exercise found
            QMessageBox.warning(self, "Error", "Could not determine which exercise to plot.")
            return

        #Fetch and process history
        history = self.db.get_exercise_history(exercise_name)

        #Show warning if no history
        if not history:
            QMessageBox.information(self, "No Data", f"No history for '{exercise_name}'.")
            return

        #Work out average weight per rep for this exercise
        dates, avg_weights, max_weights = ExerciseGraph.compute_avg_weight_per_rep(history)

        #If no valid data, show warning
        if not dates:
            QMessageBox.information(self, "No Valid Sets", f"No usable data for '{exercise_name}'.")
            return

        #Show graph
        ExerciseGraph.plot_average_weight_per_rep(dates, avg_weights, exercise_name, goal, max_weights)