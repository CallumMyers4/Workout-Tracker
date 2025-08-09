import matplotlib.pyplot as plt

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