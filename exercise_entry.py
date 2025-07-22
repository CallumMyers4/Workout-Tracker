from PyQt5.QtWidgets import (
    QWidget, QHBoxLayout, QVBoxLayout,
    QLabel, QLineEdit, QSpinBox, QComboBox, QPushButton
)

class ExerciseEntry(QWidget):
    def __init__(self, db_helper, parent=None):
        super().__init__(parent)
        self.db = db_helper

        self.main_layout = QVBoxLayout()
        self.setLayout(self.main_layout)

        top_layout = QHBoxLayout()
        top_layout.addWidget(QLabel("Exercise:"))

        self.exercise_dropdown = QComboBox()
        self.exercise_dropdown.setEditable(True)
        self.exercise_dropdown.setInsertPolicy(QComboBox.NoInsert)

        # Load from DB
        self.exercise_dropdown.addItems(self.db.get_all_exercise_names())

        top_layout.addWidget(self.exercise_dropdown)

        self.add_exercise_btn = QPushButton("+ Add")
        self.add_exercise_btn.setFixedWidth(60)
        self.add_exercise_btn.clicked.connect(self.add_new_exercise)
        top_layout.addWidget(self.add_exercise_btn)

        self.sets = QSpinBox()
        self.sets.setMinimum(1)
        self.sets.setMaximum(20)
        self.sets.setValue(3)
        top_layout.addWidget(QLabel("Sets:"))
        top_layout.addWidget(self.sets)

        self.main_layout.addLayout(top_layout)

        self.sets_inputs_container = QVBoxLayout()
        self.main_layout.addLayout(self.sets_inputs_container)

        self.reps_inputs = []
        self.weight_inputs = []

        self.sets.valueChanged.connect(self.update_set_inputs)
        self.update_set_inputs()

    def update_set_inputs(self):
        while self.sets_inputs_container.count():
            item = self.sets_inputs_container.takeAt(0)
            widget = item.widget()
            if widget:
                widget.deleteLater()
            elif item.layout():
                layout = item.layout()
                while layout.count():
                    sub_item = layout.takeAt(0)
                    sub_widget = sub_item.widget()
                    if sub_widget is not None:
                        sub_widget.deleteLater()

        self.reps_inputs = []
        self.weight_inputs = []

        for i in range(self.sets.value()):
            row_layout = QHBoxLayout()

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

            self.sets_inputs_container.addLayout(row_layout)

            self.reps_inputs.append(reps_input)
            self.weight_inputs.append(weight_input)

    def add_new_exercise(self):
        new_exercise = self.exercise_dropdown.currentText().strip()
        if new_exercise:
            existing = [self.exercise_dropdown.itemText(i) for i in range(self.exercise_dropdown.count())]
            if new_exercise not in existing:
                self.exercise_dropdown.addItem(new_exercise)
                self.exercise_dropdown.setCurrentText(new_exercise)

            self.db.add_exercise_to_catalog(new_exercise)

    def get_data(self):
        return {
            "name": self.exercise_dropdown.currentText().strip(),
            "sets": self.sets.value(),
            "reps": [r.text().strip() for r in self.reps_inputs],
            "weight": [w.text().strip() for w in self.weight_inputs]
        }
