import matplotlib.pyplot as plt
from matplotlib.widgets import CheckButtons
from PyQt5.QtWidgets import QMessageBox

#Class to show graphs of exercise data
class ExerciseGraph:
    @staticmethod
    #Get the average weight per rep for an exercise
    def compute_avg_weight_per_rep(history):
        dates = []
        avg_weights = []
        max_weights = []

        #For each entry in the history
        for date_str, reps_str, weights_str in history:
            try:
                #Get the reps and weights as lists
                reps_list = [int(r.strip()) for r in reps_str.split(",") if r.strip().isdigit()]
                weights_list = [float(w.strip()) for w in weights_str.split(",") if w.strip()]
                num_sets = min(len(reps_list), len(weights_list))

                #If there are no sets, skip this entry
                if num_sets == 0:
                    continue

                total_weight = 0
                total_reps = 0
                max_weight = 0

                #For each set
                for i in range(num_sets):
                    #Get the weight and reps for this set
                    weight = weights_list[i]
                    reps = reps_list[i]

                    #Calculate the total weight across all sets
                    total_weight += weight * reps

                    #Update the total reps
                    total_reps += reps

                    #Update the max weight
                    if weight > max_weight:
                        max_weight = weight

                #If there are no reps, skip this entry
                if total_reps == 0:
                    continue

                #Calculate the average weight per rep
                avg = total_weight / total_reps

                #Add the date, average weight, and max weight to the lists
                dates.append(date_str)
                avg_weights.append(avg)
                max_weights.append(max_weight)

            #If there's an error parsing the history entry, skip it
            except Exception as e:
                print(f"Error parsing history entry: {e}")
                continue

        #Return the dates, average weights, and max weights
        return dates, avg_weights, max_weights

    @staticmethod
    #Plot the average weight per rep for an exercise
    def plot_average_weight_per_rep(dates, avg_weights, exercise_name, goal=None, max_weights=None):
        #Create a new figure and axis for the plot
        fig, ax = plt.subplots()

        #Plot the average weight per rep
        ax.plot(dates, avg_weights, marker='o', label='Avg Weight per Rep', color='blue')

        #Plot the max weight
        if max_weights:
            ax.plot(dates, max_weights, marker='s', linestyle='--', label='Max Weight per Day', color='green')

        #Show the goal line if set
        if goal is not None:
            ax.axhline(y=goal, color='red', linestyle='--', label=f'Goal: {goal} kg')

        #Set the title and labels
        ax.set_title(f"{exercise_name} – Avg & Max Weight per Day")
        ax.set_xlabel("Date")
        ax.set_ylabel("Weight (kg)")
        ax.grid(True)
        ax.legend()
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.show(block=False)

    @staticmethod
    def compute_1rm_potential(history):
        dates = []
        e1rm_values = []

        for date_str, reps_str, weights_str in history:
            try:
                reps_list = [int(r.strip()) for r in reps_str.split(",") if r.strip().isdigit()]
                weights_list = [float(w.strip()) for w in weights_str.split(",") if w.strip()]
                num_sets = min(len(reps_list), len(weights_list))

                if num_sets == 0:
                    continue

                max_e1rm = 0
                for i in range(num_sets):
                    weight = weights_list[i]
                    reps = reps_list[i]
                    e1rm = weight * (1 + reps / 30)
                    max_e1rm = max(max_e1rm, e1rm)

                dates.append(date_str)
                e1rm_values.append(max_e1rm)

            except Exception as e:
                print(f"Error parsing history entry: {e}")
                continue

        return dates, e1rm_values

    @staticmethod
    def compute_performance(history):
        dates = []
        perf_values = []

        for date_str, reps_str, weights_str in history:
            try:
                reps_list = [int(r.strip()) for r in reps_str.split(",") if r.strip().isdigit()]
                weights_list = [float(w.strip()) for w in weights_str.split(",") if w.strip()]
                num_sets = min(len(reps_list), len(weights_list))

                if num_sets == 0:
                    continue

                total_weighted_e1rm = 0
                total_reps = 0

                for i in range(num_sets):
                    weight = weights_list[i]
                    reps = reps_list[i]
                    if reps == 0:
                        continue
                    e1rm = weight * (1 + reps / 30)
                    total_weighted_e1rm += e1rm * reps
                    total_reps += reps

                perf = total_weighted_e1rm / total_reps if total_reps > 0 else 0
                dates.append(date_str)
                perf_values.append(perf)

            except Exception as e:
                print(f"Error parsing history entry: {e}")
                continue

        return dates, perf_values

    @staticmethod
    def plot_1rm_potential(dates, e1rm_values, exercise_name, goal=None):
        fig, ax = plt.subplots()
        ax.plot(dates, e1rm_values, marker='o', label='Estimated 1RM Potential', color='purple')

        if goal is not None:
            ax.axhline(y=goal, color='red', linestyle='--', label=f'Goal: {goal} kg')

        ax.set_title(f"{exercise_name} – Estimated 1RM Potential")
        ax.set_xlabel("Date")
        ax.set_ylabel("Weight (kg)")
        ax.grid(True)
        ax.legend()
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.show(block=False)

    @staticmethod
    def plot_performance(dates, perf_values, exercise_name):
        fig, ax = plt.subplots()
        ax.plot(dates, perf_values, marker='o', label='Performance', color='orange')
        ax.set_title(f"{exercise_name} – Performance")
        ax.set_xlabel("Date")
        ax.set_ylabel("Performance Score")
        ax.grid(True)
        ax.legend()
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.show(block=False)


from PyQt5.QtWidgets import QDialog, QVBoxLayout, QSizePolicy
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
from matplotlib.widgets import CheckButtons

class ExerciseProgressGraph(QDialog):
    def __init__(self, exercise_name, goal, history, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"{exercise_name} – Progress Overview")
        self.resize(900, 600)

        self.layout = QVBoxLayout(self)
        self.setLayout(self.layout)

        # Create Figure and Canvas
        self.figure = Figure(constrained_layout=True)
        self.canvas = FigureCanvas(self.figure)
        self.canvas.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.canvas.updateGeometry()
        self.layout.addWidget(self.canvas)

        # Plot data
        self.plot(exercise_name, goal, history)

    def plot(self, exercise_name, goal, history):
        self.figure.clear()
        ax = self.figure.add_subplot(111)

        lines = []
        labels = []

        # Compute data series (reuse your ExerciseGraph static methods)
        dates_avg, avg_weights, max_weights = ExerciseGraph.compute_avg_weight_per_rep(history)
        dates_1rm, e1rm = ExerciseGraph.compute_1rm_potential(history)
        dates_perf, perf = ExerciseGraph.compute_performance(history)

        if dates_avg:
            l_avg, = ax.plot(dates_avg, avg_weights, marker='o', color='blue', label='Avg Weight per Rep')
            lines.append(l_avg)
            labels.append('Avg Weight per Rep')

        if dates_avg and max_weights:
            l_max, = ax.plot(dates_avg, max_weights, marker='s', linestyle='--', color='green', label='Max Weight per Day')
            lines.append(l_max)
            labels.append('Max Weight per Day')

        if dates_1rm:
            l_1rm, = ax.plot(dates_1rm, e1rm, marker='o', color='purple', label='Estimated 1RM Potential')
            lines.append(l_1rm)
            labels.append('Estimated 1RM Potential')

            if goal is not None:
                goal_line = ax.axhline(y=goal, color='red', linestyle='--', label=f'Goal: {goal} kg')
                lines.append(goal_line)
                labels.append('Goal')

        if dates_perf:
            l_perf, = ax.plot(dates_perf, perf, marker='o', color='orange', label='Performance')
            lines.append(l_perf)
            labels.append('Performance')

        ax.set_xlabel("Date")
        ax.set_ylabel("Value")
        ax.grid(True)
        ax.legend(lines, labels, loc='upper left')
        ax.tick_params(axis='x', rotation=45)

        # Checkbox axes inside figure
        checkbox_ax = self.figure.add_axes([0.01, 0.1, 0.15, 0.3])
        visibility = [line.get_visible() for line in lines]
        self.check = CheckButtons(checkbox_ax, labels, visibility)

        def toggle_visibility(label):
            idx = labels.index(label)
            lines[idx].set_visible(not lines[idx].get_visible())
            self.canvas.draw_idle()

        self.check.on_clicked(toggle_visibility)

        self.canvas.draw_idle()
