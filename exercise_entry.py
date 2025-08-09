from PyQt5.QtWidgets import (
    QWidget, QHBoxLayout, QVBoxLayout,
    QLabel, QLineEdit, QSpinBox, QComboBox, QPushButton
)

#Class to add a new exercise entry
class ExerciseEntry(QWidget):
    def __init__(self, db_helper, parent=None):
        #Initialize the widget, set up the database helper
        super().__init__(parent)
        self.db = db_helper

        #Main layout for the widget
        self.main_layout = QVBoxLayout()
        self.setLayout(self.main_layout)

        #Show the exercises dropdown
        top_layout = QHBoxLayout()
        top_layout.addWidget(QLabel("Exercise:"))

        self.exercise_dropdown = QComboBox()
        self.exercise_dropdown.setEditable(True)
        self.exercise_dropdown.setInsertPolicy(QComboBox.NoInsert)

        #Load current exercise options from DB
        self.exercise_dropdown.addItems(self.db.get_all_exercise_names())

        #Add the dropdown to the layout
        top_layout.addWidget(self.exercise_dropdown)

        #Button to add a new exercise to the catalog
        self.add_exercise_btn = QPushButton("+ Add")
        self.add_exercise_btn.setFixedWidth(60)
        self.add_exercise_btn.clicked.connect(self.add_new_exercise)
        top_layout.addWidget(self.add_exercise_btn)

        #Input for number of sets
        self.sets = QSpinBox()
        self.sets.setMinimum(1)
        self.sets.setMaximum(20)
        self.sets.setValue(3)
        top_layout.addWidget(QLabel("Sets:"))
        top_layout.addWidget(self.sets)

        #Add the top layout to the main layout
        self.main_layout.addLayout(top_layout)

        #Container for the sets inputs
        self.sets_inputs_container = QVBoxLayout()
        self.main_layout.addLayout(self.sets_inputs_container)

        self.reps_inputs = []
        self.weight_inputs = []

        #Create the inputs for the sets
        self.sets.valueChanged.connect(self.update_set_inputs)
        self.update_set_inputs()

    #Update the set inputs based on the number of sets
    def update_set_inputs(self):
        #While there are existing inputs
        while self.sets_inputs_container.count():
            #Remove the first input set
            item = self.sets_inputs_container.takeAt(0)
            widget = item.widget()
            #If there's a widget, delete it
            if widget:
                widget.deleteLater()
            #If the item has a layout, remove all widgets in it
            elif item.layout():
                layout = item.layout()
                #If there's a layout, remove all widgets in it
                while layout.count():
                    sub_item = layout.takeAt(0)
                    sub_widget = sub_item.widget()
                    #If there's a sub-widget, delete it
                    if sub_widget is not None:
                        sub_widget.deleteLater()

        self.reps_inputs = []
        self.weight_inputs = []

        #For each set
        for i in range(self.sets.value()):
            #Create a horizontal layout for the set inputs
            row_layout = QHBoxLayout()

            #Show the set number, reps, and weight input boxes
            set_label = QLabel(f"Set {i + 1}:")
            set_label.setFixedWidth(50)
            row_layout.addWidget(set_label)

            reps_label = QLabel("Reps:")
            reps_label.setFixedWidth(40)
            row_layout.addWidget(reps_label)

            reps_input = QLineEdit()
            reps_input.setPlaceholderText(f"Reps for set {i + 1}")
            reps_input.setFixedWidth(80)
            row_layout.addWidget(reps_input)

            weight_label = QLabel("Weight:")
            weight_label.setFixedWidth(50)
            row_layout.addWidget(weight_label)

            weight_input = QLineEdit()
            weight_input.setPlaceholderText(f"Weight for set {i + 1}")
            weight_input.setFixedWidth(80)
            row_layout.addWidget(weight_input)

            #Add the row layout to the sets inputs container
            self.sets_inputs_container.addLayout(row_layout)

            #Store the inputs for later retrieval
            self.reps_inputs.append(reps_input)
            self.weight_inputs.append(weight_input)

    #Add a new exercise to the catalog
    def add_new_exercise(self):
        #Get the new exercise name from the dropdown
        new_exercise = self.exercise_dropdown.currentText().strip()

        #If the new exercise name is not empty
        if new_exercise:
            #Check if the exercise already exists in the dropdown
            existing = [self.exercise_dropdown.itemText(i) for i in range(self.exercise_dropdown.count())]

            #If the new exercise is not already in the dropdown
            if new_exercise not in existing:
                #Add the new exercise to the dropdown
                self.exercise_dropdown.addItem(new_exercise)

                #Set the current text to the new exercise
                self.exercise_dropdown.setCurrentText(new_exercise)

            #Update the database with the new exercise
            self.db.add_exercise_to_catalog(new_exercise)

    #Get the data from the exercise entry
    def get_data(self):
        return {
            "name": self.exercise_dropdown.currentText().strip(),
            "sets": self.sets.value(),

            #Get the reps and weights as lists from the input fields
            "reps": [r.text().strip() for r in self.reps_inputs],
            "weight": [w.text().strip() for w in self.weight_inputs]
        }
    
    #Set the data for the exercise entry
    def set_data(self, name, sets, reps, weights):
        #Set exercise name in combo box
        index = self.exercise_dropdown.findText(name)
        if index == -1:
            self.exercise_dropdown.addItem(name)
            index = self.exercise_dropdown.count() - 1
        self.exercise_dropdown.setCurrentIndex(index)

        #Set sets count - triggers updating reps/weight inputs
        self.sets.setValue(int(sets))

        #Convert reps and weights to lists if they are strings
        if isinstance(reps, str):
            reps = reps.split(',')
        if isinstance(weights, str):
            weights = weights.split(',')

        #Fill in reps and weights inputs
        for i, (r_input, w_input) in enumerate(zip(self.reps_inputs, self.weight_inputs)):
            r_input.setText(reps[i].strip() if i < len(reps) else "")
            w_input.setText(weights[i].strip() if i < len(weights) else "")

