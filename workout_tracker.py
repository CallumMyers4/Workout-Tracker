from PyQt5.QtWidgets import (
    QWidget, QVBoxLayout, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QMessageBox, QHBoxLayout, QMenu
)
from PyQt5.QtCore import Qt, QDate
from workout_editor import WorkoutEditor
from goals_editor import GoalsEditor

class WorkoutTracker(QWidget):
    def __init__(self, db_helper):
        #Construct and get database
        super().__init__()
        self.db = db_helper

        #Setup the main window
        self.setWindowTitle("Workout Tracker")
        self.resize(800, 500)

        #Add layout box
        self.layout = QVBoxLayout(self)

        #Create a horizontal layout for the buttons
        self.top_menu_buttons_layout = QHBoxLayout()

        #Add new workout button
        self.add_workout_btn = QPushButton("Add Workout")
        self.add_workout_btn.clicked.connect(lambda: self.open_workout_editor())
        self.top_menu_buttons_layout.addWidget(self.add_workout_btn)

        #Add goal set/view button
        self.view_goals_btn = QPushButton("View and Change Goals")
        self.view_goals_btn.clicked.connect(self.open_goals_editor)
        self.top_menu_buttons_layout.addWidget(self.view_goals_btn)

        #Add top menu buttons to the main layout
        self.layout.addLayout(self.top_menu_buttons_layout)

        #Table to display workouts
        self.table = QTableWidget()
        self.table.setColumnCount(3)
        self.table.setHorizontalHeaderLabels(["Exercise / Workout", "Reps", "Weight (Kg)"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.cellClicked.connect(self.toggle_workout_details)
        self.table.verticalHeader().setVisible(False)
        self.layout.addWidget(self.table)

        #Add a right-click menu for the table
        self.table.setContextMenuPolicy(Qt.CustomContextMenu)
        self.table.customContextMenuRequested.connect(self.show_context_menu)

        #Expanded row tracking — initialize to -1 (no row expanded)
        self.expanded_row = -1
        self.expanded_workout_id = None

        #Show the workouts
        self.load_workouts()

        #Button to delete selected workout
        self.delete_workout_btn = QPushButton("Delete Workout")
        self.delete_workout_btn.clicked.connect(self.delete_selected_workout)
        self.layout.addWidget(self.delete_workout_btn)

    #Get the workouts from the database and display them
    def load_workouts(self):
        self.table.setRowCount(0)

        #Get all workouts from the database
        workouts = self.db.get_all_workouts()

        #Add a row for each workout
        for workout in workouts:
            self.add_workout_row(workout)

    #Add a row for the workout in the table
    def add_workout_row(self, workout):
        #Add a new row to the table
        row = self.table.rowCount()
        self.table.insertRow(row)

        #Seperate the workout data, then show name and date
        workout_id, name, date = workout
        label = f"{name} ({date})"

        #Add the workout name as the first item in the row
        item = QTableWidgetItem(label)
        item.setData(Qt.UserRole, workout_id)

        #Set row data
        self.table.setItem(row, 0, item)
        self.table.setSpan(row, 0, 1, 3)
        self.table.setRowHeight(row, 30)

    #Show or hide the details of the workout when clicked
    def toggle_workout_details(self, row, column):
        #Get the clicked item
        clicked_item = self.table.item(row, 0)

        #If no item is clicked or it's not a workout row, do nothing
        if not clicked_item:
            return

        #Get the workout ID from the clicked item
        workout_id = clicked_item.data(Qt.UserRole)
        #If no workout ID is found, do nothing
        if workout_id is None:
            return

        #If clicking the same workout that's already expanded — collapse it
        if self.expanded_workout_id == workout_id:
            self.collapse_details()
            return

        #Collapse previous workout if any
        if self.expanded_row != -1:
            self.collapse_details()

            # Adjust row index if needed after collapsing previous rows
            if row > self.expanded_row:
                row -= self.details_count

        #Get the exercises for the clicked workout from the database
        exercises = self.db.get_exercises_for_workout(workout_id)

        #If there are no exercises, show a message and return
        if not exercises:
            QMessageBox.information(self, "No Exercises", "This workout has no exercises.")
            return

        
        self.expanded_row = row
        self.expanded_workout_id = workout_id
        self.details_count = 0
        self.exercise_set_rows = {}

        #For each exercise in the workout
        for exercise_index, (exercise_name, sets, reps_str, weight_str) in enumerate(exercises):
            #Create a new row for the exercise
            insert_row = self.expanded_row + 1 + self.details_count

            #Split the reps and weights strings into lists
            #Assuming reps_str and weight_str are comma-separated strings
            reps_list = [r.strip() for r in reps_str.split(",")]
            weight_list = [w.strip() for w in weight_str.split(",")]

            #Create a dictionary to hold the exercise data
            exercise_data = {
                "name": exercise_name,
                "sets": sets,
                "reps": reps_list,
                "weights": weight_list
            }

            #Add a new row for the exercise
            self.table.insertRow(insert_row)

            #Create a button to expand/collapse the sets
            expand_btn = QPushButton(f"▶ {exercise_name}")
            expand_btn.setFlat(True)
            expand_btn.setStyleSheet("text-align: left;")
            expand_btn.clicked.connect(lambda checked, b=expand_btn, d=exercise_data: self.toggle_exercise_sets(b, d))
            self.table.setCellWidget(insert_row, 0, expand_btn)

            self.table.setItem(insert_row, 1, QTableWidgetItem(""))
            self.table.setItem(insert_row, 2, QTableWidgetItem(""))

            self.exercise_set_rows[insert_row] = 0
            self.details_count += 1

    #When the details are expanded, collapse them
    def collapse_details(self):

        #If collapsing
        if self.expanded_row != -1:
            #For each of the exercises in workout being collapsed
            for _ in range(self.details_count):
                #Remove it
                self.table.removeRow(self.expanded_row + 1)

        #Reset variables
        self.expanded_row = -1
        self.expanded_workout_id = None
        self.details_count = 0
        self.exercise_set_rows = {}

    #View or edit a workout
    def open_workout_editor(self, workout_id=None, template_from_id=None):

        #If editing an existing workout
        if workout_id is not None:

            #Open the workout editor with the existing workout data
            editor = WorkoutEditor(self.db, self, workout_id)

            #Fill the editor with the workout data
            workout = self.db.get_workout_by_id(workout_id)
            exercises = self.db.get_exercises_for_workout(workout_id)
            editor.set_workout_data(workout[1], workout[2], exercises)
        elif template_from_id is not None:
            #Open the workout editor with a template from an existing workout
            editor = WorkoutEditor(self.db, self)

            #Use today's date and fill the editor with the template workout data
            workout = self.db.get_workout_by_id(template_from_id)
            exercises = self.db.get_exercises_for_workout(template_from_id)
            today_str = QDate.currentDate().toString("yyyy-MM-dd")
            editor.set_workout_data(workout[1], today_str, exercises)
        else:
            #Open the workout editor for a new workout
            editor = WorkoutEditor(self.db, self)

        #Connect the editor's finished signal to reload workouts
        if editor.exec_():
            self.load_workouts()

    #Delete the selected workout from the database
    def delete_selected_workout(self):
        selected_row = self.table.currentRow()

        #If no row is selected, show a warning
        if selected_row == -1:
            QMessageBox.warning(self, "No Selection", "Please select a workout to delete.")
            return

        #Get the workout item from the selected row
        workout_item = self.table.item(selected_row, 0)

        #If no workout item is found, show a warning
        if not workout_item:
            QMessageBox.warning(self, "Invalid Selection", "Please select a valid workout row.")
            return

        #Get the workout ID from the item
        workout_id = workout_item.data(Qt.UserRole)

        #If no workout ID is found, show a warning
        if workout_id is None:
            QMessageBox.warning(self, "Invalid Selection", "You must select a workout row (not an exercise row).")
            return

        #Confirm deletion with the user
        reply = QMessageBox.question(
            self,
            "Confirm Deletion",
            "Are you sure you want to delete this workout?",
            QMessageBox.Yes | QMessageBox.No
        )

        #If the user confirms, delete the workout
        if reply == QMessageBox.Yes:
            self.db.delete_workout(workout_id)
            self.expanded_row = -1
            self.expanded_workout_id = None
            self.load_workouts()

    #Show or hide the sets of an exercise when the button is clicked
    def toggle_exercise_sets(self, button, exercise_data):
        #Find the row of this button
        row = self.table.indexAt(button.pos()).row()

        #If the row is invalid, do nothing
        if row == -1:
            return

        #Check if the sets are currently expanded or collapsed
        is_expanded = self.exercise_set_rows.get(row, 0) > 0

        #If collapsing
        if is_expanded:
            #For each set in the exercise
            for _ in range(self.exercise_set_rows[row]):
                #Remove the set row
                self.table.removeRow(row + 1)

            #Update the button text and reset the count
            button.setText(f"▶ {exercise_data['name']}")
            self.details_count -= self.exercise_set_rows[row]
            self.exercise_set_rows[row] = 0
        else:
            #Get each set of the exercise
            sets = exercise_data["sets"]
            reps_list = exercise_data["reps"]
            weights_list = exercise_data["weights"]

            #For each set
            for i in range(sets):
                #Create a new row for the set
                insert_row = row + 1 + i

                #Fill set row with reps and weights
                reps = reps_list[i] if i < len(reps_list) else ""
                weight = weights_list[i] if i < len(weights_list) else ""

                #Insert the new row and set the data
                self.table.insertRow(insert_row)
                self.table.setItem(insert_row, 0, QTableWidgetItem(f"Set {i + 1}"))
                self.table.setItem(insert_row, 1, QTableWidgetItem(reps))
                self.table.setItem(insert_row, 2, QTableWidgetItem(weight))

            #Update the button text and count
            button.setText(f"▼ {exercise_data['name']}")
            self.exercise_set_rows[row] = sets
            self.details_count += sets

    #Open the goals editor
    def open_goals_editor(self):
        self.editor = GoalsEditor(self.db, self)
        self.editor.finished.connect(lambda: self.load_workouts())
        self.editor.show()

    #Show a context menu for the workout table
    def show_context_menu(self, pos):
        menu = QMenu()

        #Add actions for the context menu
        duplicate_action = menu.addAction("Use as Template")
        edit_action = menu.addAction("Edit Workout")

        #Execute the menu at the clicked position
        action = menu.exec_(self.table.viewport().mapToGlobal(pos))

        #If duplicating workout
        if action == duplicate_action:
            #Get the selected row
            selected_row = self.table.currentRow()

            #Get the workout ID from the selected row
            workout_id = self.table.item(selected_row, 0).data(Qt.UserRole)

            #Open the workout editor with the data from the selected workout
            self.open_workout_editor(template_from_id=workout_id)
        #If editing workout
        elif action == edit_action:
            #Get the selected row
            selected_row = self.table.currentRow()

            #Get the workout ID from the selected row
            workout_id = self.table.item(selected_row, 0).data(Qt.UserRole)

            #Open the workout editor with the selected workout 
            self.open_workout_editor(workout_id=workout_id)
