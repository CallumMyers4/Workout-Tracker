import matplotlib.pyplot as plt

class ExerciseGraph:
    @staticmethod
    def compute_avg_weight_per_rep(history):
        dates = []
        avg_weights = []
        max_weights = []

        for date_str, reps_str, weights_str in history:
            try:
                reps_list = [int(r.strip()) for r in reps_str.split(",") if r.strip().isdigit()]
                weights_list = [float(w.strip()) for w in weights_str.split(",") if w.strip()]
                num_sets = min(len(reps_list), len(weights_list))

                if num_sets == 0:
                    continue

                total_weight = 0
                total_reps = 0
                max_weight = 0

                for i in range(num_sets):
                    weight = weights_list[i]
                    reps = reps_list[i]
                    total_weight += weight * reps
                    total_reps += reps
                    if weight > max_weight:
                        max_weight = weight

                if total_reps == 0:
                    continue

                avg = total_weight / total_reps

                dates.append(date_str)
                avg_weights.append(avg)
                max_weights.append(max_weight)

            except Exception as e:
                print(f"Error parsing history entry: {e}")
                continue

        return dates, avg_weights, max_weights

    @staticmethod
    def plot_average_weight_per_rep(dates, avg_weights, exercise_name, goal=None, max_weights=None):
        fig, ax = plt.subplots()

        ax.plot(dates, avg_weights, marker='o', label='Avg Weight per Rep', color='blue')

        if max_weights:
            ax.plot(dates, max_weights, marker='s', linestyle='--', label='Max Weight per Day', color='green')

        if goal is not None:
            ax.axhline(y=goal, color='red', linestyle='--', label=f'Goal: {goal} kg')

        ax.set_title(f"{exercise_name} – Avg & Max Weight per Day")
        ax.set_xlabel("Date")
        ax.set_ylabel("Weight (kg)")
        ax.grid(True)
        ax.legend()
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.show(block=False)
