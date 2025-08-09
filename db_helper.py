# db_helper.py
import sqlite3

#Class for managing the database
class DBHelper:
    #Initialize the database connection
    def __init__(self, db_path="workouts.db"):
        self.conn = sqlite3.connect(db_path)
        self.conn.execute("PRAGMA foreign_keys = ON")
        self.init_db()
    
    #Initialize the database schema
    def init_db(self):
        #Create workouts table if it doesn't exist
        c = self.conn.cursor()
        c.execute("""
            CREATE TABLE IF NOT EXISTS workouts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                date TEXT
            )
        """)

        #Create exercises table if it doesn't exist
        c.execute("""
            CREATE TABLE IF NOT EXISTS exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                workout_id INTEGER,
                name TEXT,
                sets INTEGER,
                reps TEXT,
                weight REAL,
                FOREIGN KEY(workout_id) REFERENCES workouts(id) ON DELETE CASCADE
            )
        """)

        #Create exercises options table if it doesn't exist
        c.execute("""
            CREATE TABLE IF NOT EXISTS exercises_catalog (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE,
                goal REAL
            )
        """)

        #Commit the changes
        self.conn.commit()

    #Add a new workout to the database
    def add_workout(self, name, date):
        c = self.conn.cursor()
        c.execute("INSERT INTO workouts (name, date) VALUES (?, ?)", (name, date))
        self.conn.commit()
        return c.lastrowid

    #Add a new exercise to a workout
    def add_exercise(self, workout_id, name, sets, reps, weight):
        c = self.conn.cursor()
        c.execute(
            "INSERT INTO exercises (workout_id, name, sets, reps, weight) VALUES (?, ?, ?, ?, ?)",
            (workout_id, name, sets, reps, weight)
        )
        self.conn.commit()

    #Get all goals from the exercises catalog
    def get_all_goals(self):
        c = self.conn.cursor()
        c.execute("SELECT id, name, goal FROM exercises_catalog")
        return c.fetchall()
    
    #Get all workouts from the database
    def get_all_workouts(self):
        c = self.conn.cursor()
        c.execute("SELECT id, name, date FROM workouts ORDER BY date DESC, id DESC")
        return c.fetchall()

    #Get all exercises for a specific workout
    def get_exercises_for_workout(self, workout_id):
        c = self.conn.cursor()
        c.execute(
            "SELECT name, sets, reps, weight FROM exercises WHERE workout_id = ?",
            (workout_id,)
        )
        return c.fetchall()

    #Delete a workout by ID
    def delete_workout(self, workout_id):
        c = self.conn.cursor()
        c.execute("DELETE FROM workouts WHERE id = ?", (workout_id,))
        self.conn.commit()

    #Get all exercise names from the catalog
    def get_all_exercise_names(self):
        c = self.conn.cursor()
        c.execute("SELECT name FROM exercises_catalog ORDER BY name ASC")
        return [row[0] for row in c.fetchall()]

    #Add a new exercise to the catalog
    def add_exercise_to_catalog(self, name):
        try:
            c = self.conn.cursor()
            c.execute("INSERT INTO exercises_catalog (name) VALUES (?)", (name,))
            self.conn.commit()
        except sqlite3.IntegrityError:
            pass  # Already exists

    #Update the goal for an exercise in the catalog
    def update_goal(self, exercise_id, new_goal):
        c = self.conn.cursor()
        c.execute("UPDATE exercises_catalog SET goal = ? WHERE id = ?", (new_goal, exercise_id))
        self.conn.commit()

    #Get the highest weight for a specific exercise
    def get_highest_weight_for_exercise(self, exercise_name):
        #Get all weights for the exercise
        c = self.conn.cursor()
        c.execute("SELECT weight FROM exercises WHERE name = ?", (exercise_name,))
        all_weights = c.fetchall()  # List of tuples, e.g. [('50,60,60,50',), ('40,45',), ...]

        max_weight = 0.0

        #Iterate through all weights
        for (weight_str,) in all_weights:
            #If the weight string is not empty
            if weight_str:
                #Split the CSV string into floats
                weights = [float(w.strip()) for w in weight_str.split(",") if w.strip()]
                
                #If there are weights in the list
                if weights:
                    #Get the maximum weight in this record
                    max_in_record = max(weights)

                    #If the maximum weight in this record is greater than the current max weight
                    if max_in_record > max_weight:
                        #Update the max weight
                        max_weight = max_in_record

        return max_weight
 
    #List all weights for a specific exercise
    def list_exercise_weights(self, exercise_name):
        c = self.conn.cursor()
        c.execute("SELECT weight FROM exercises WHERE name = ?", (exercise_name,))
        return c.fetchall()

    #Get a workout by ID
    def get_workout_by_id(self, workout_id):
        c = self.conn.cursor()
        c.execute("SELECT id, name, date FROM workouts WHERE id = ?", (workout_id,))
        return c.fetchone()

    #Update a workout in the database
    def update_workout(self, workout_id, name, date):
        self.conn.execute("UPDATE workouts SET name=?, date=? WHERE id=?", (name, date, workout_id))
        self.conn.commit()

    #Delete exercises for a specific workout
    def delete_exercises_for_workout(self, workout_id):
        self.conn.execute("DELETE FROM exercises WHERE workout_id=?", (workout_id,))
        self.conn.commit()

    #Get the exercise history for a specific exercise
    def get_exercise_history(self, exercise_name):
        query = """
            SELECT w.date, e.reps, e.weight
            FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE e.name = ?
            ORDER BY w.date
        """
        return self.conn.execute(query, (exercise_name,)).fetchall()
