from PyQt5.QtWidgets import QDialog, QVBoxLayout, QLabel, QLineEdit, QPushButton, QScrollArea, QWidget, QHBoxLayout, QMessageBox, QDateEdit
from PyQt5.QtCore import QDate

from db_helper import DBHelper
from exercise_entry import ExerciseEntry

#Class to create a new workout or edit an existing one
class WorkoutEditor(QDialog):
    def __init__(self, db, parent=None, workout_id=None):
        #Create self, get database helper and workout ID
        super().__init__(parent)
        self.db = db
        self.workout_id = workout_id 

        #Setup the dialog
        self.setWindowTitle("Add/Edit Workout")
        self.resize(600, 600)

        #Add layout box
        self.layout = QVBoxLayout()
        self.setLayout(self.layout)

        #Name the workout
        self.workout_name_input = QLineEdit()
        self.workout_name_input.setPlaceholderText("Workout Name")
        self.layout.addWidget(QLabel("Workout Name:"))
        self.layout.addWidget(self.workout_name_input)

        #Set the date
        self.workout_date_input = QDateEdit()
        self.workout_date_input.setCalendarPopup(True)
        self.workout_date_input.setDate(QDate.currentDate())
        self.layout.addWidget(QLabel("Workout Date:"))
        self.layout.addWidget(self.workout_date_input)

        #Box containing all exercises for the workout
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll_content = QWidget()
        self.scroll.setWidget(self.scroll_content)

        #Layout box for the workout exercises
        self.exercises_container_layout = QVBoxLayout()
        self.scroll_content.setLayout(self.exercises_container_layout)
        self.layout.addWidget(self.scroll)

        #Button to add exercises
        self.add_exercise_btn = QPushButton("Add Exercise")
        self.add_exercise_btn.clicked.connect(self.add_exercise_entry)
        self.layout.addWidget(self.add_exercise_btn)

        #Save workout button
        self.save_btn = QPushButton("Save Workout")
        self.save_btn.clicked.connect(self.save_workout)
        self.layout.addWidget(self.save_btn)

        #Set all exercise entries to empty, and create the first one
        self.exercise_entries = []
        self.add_exercise_entry()

    #Add a new exercise entry to the workout
    def add_exercise_entry(self):
        #Get the database helper and create a new exercise entry
        helper = DBHelper()
        entry = ExerciseEntry(helper)

        #Add the entry to the layout and the list of entries
        self.exercises_container_layout.addWidget(entry)
        self.exercise_entries.append(entry)

    #Save the workout to the database
    def save_workout(self):
        #Get the workout name and date
        name = self.workout_name_input.text().strip()
        date = self.workout_date_input.date().toString("yyyy-MM-dd")

        #Warn if no name is provided
        if not name:
            QMessageBox.warning(self, "Validation Error", "Workout name cannot be empty.")
            return

        #For each exercise entered
        exercises_data = []
        for entry in self.exercise_entries:

            #Collect all data from the boxes
            data = entry.get_data()

            #Warn if any of the fields are empty
            if not data["name"]:
                QMessageBox.warning(self, "Validation Error", "Exercise name cannot be empty.")
                return

            if not all(data["reps"]) or not all(data["weight"]):
                QMessageBox.warning(self, "Validation Error", "Reps and weights must be filled for all sets.")
                return

            if len(data["reps"]) != data["sets"] or len(data["weight"]) != data["sets"]:
                QMessageBox.warning(self, "Validation Error", "Mismatch in sets, reps, and weights counts.")
                return

            #Add the data to the list of exercises
            exercises_data.append(data)

        #If it's a new workout
        if self.workout_id is None:
            #Add to the database
            workout_id = self.db.add_workout(name, date)
        else:
            #Update the existing entry of the database
            workout_id = self.workout_id
            self.db.update_workout(workout_id, name, date)

            # Delete old exercises for this workout
            self.db.delete_exercises_for_workout(workout_id)

        #For each exercise
        for ex in exercises_data:

            #Convert the reps and weights to strings
            reps_str = ",".join(ex["reps"])
            weights_str = ",".join(ex["weight"])

            #Add the exercise to the database
            self.db.add_exercise(workout_id, ex["name"], ex["sets"], reps_str, weights_str)

        #Confirm the save
        QMessageBox.information(self, "Success", "Workout saved successfully!")
        self.accept()

    #Set the workout data in the editor
    def set_workout_data(self, name, date, exercises):
        #Set the workout name
        self.workout_name_input.setText(name)

        #If editing, keep date stored, else use current date
        if date:
            self.workout_date_input.setDate(QDate.fromString(date, "yyyy-MM-dd"))
        else:
            self.workout_date_input.setDate(QDate.currentDate())

        #Clear old entries from layout and list
        for entry in self.exercise_entries:
            entry.setParent(None)
        self.exercise_entries.clear()

        #For each exercise in the workout
        for ex in exercises:
            #Seperate the exercise data
            ex_name, sets, reps, weights = ex

            # Create a new exercise entry
            entry = ExerciseEntry(self.db)

            #Set the data in the entry and add it to the layout and list
            entry.set_data(ex_name, sets, reps, weights)
            self.exercises_container_layout.addWidget(entry)
            self.exercise_entries.append(entry)
