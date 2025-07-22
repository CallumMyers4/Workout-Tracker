# db_helper.py
import sqlite3

class DBHelper:
    def __init__(self, db_path="workouts.db"):
        self.conn = sqlite3.connect(db_path)
        self.init_db()

    def init_db(self):
        c = self.conn.cursor()
        c.execute("""
            CREATE TABLE IF NOT EXISTS workouts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                date TEXT
            )
        """)
        c.execute("""
            CREATE TABLE IF NOT EXISTS exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                workout_id INTEGER,
                name TEXT,
                sets INTEGER,
                reps TEXT,
                weight TEXT,
                FOREIGN KEY(workout_id) REFERENCES workouts(id) ON DELETE CASCADE
            )
        """)

        c.execute("""
            CREATE TABLE IF NOT EXISTS exercises_catalog (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE
            )
        """)

        self.conn.commit()

    def add_workout(self, name, date):
        c = self.conn.cursor()
        c.execute("INSERT INTO workouts (name, date) VALUES (?, ?)", (name, date))
        self.conn.commit()
        return c.lastrowid

    def add_exercise(self, workout_id, name, sets, reps, weight):
        c = self.conn.cursor()
        c.execute(
            "INSERT INTO exercises (workout_id, name, sets, reps, weight) VALUES (?, ?, ?, ?, ?)",
            (workout_id, name, sets, reps, weight)
        )
        self.conn.commit()

    def get_all_workouts(self):
        c = self.conn.cursor()
        c.execute("SELECT id, name, date FROM workouts ORDER BY date DESC, id DESC")
        return c.fetchall()

    def get_exercises_for_workout(self, workout_id):
        c = self.conn.cursor()
        c.execute(
            "SELECT name, sets, reps, weight FROM exercises WHERE workout_id = ?",
            (workout_id,)
        )
        return c.fetchall()

    def delete_workout(self, workout_id):
        c = self.conn.cursor()
        c.execute("DELETE FROM workouts WHERE id = ?", (workout_id,))
        self.conn.commit()

    def get_all_exercise_names(self):
        c = self.conn.cursor()
        c.execute("SELECT name FROM exercises_catalog ORDER BY name ASC")
        return [row[0] for row in c.fetchall()]

    def add_exercise_to_catalog(self, name):
        try:
            c = self.conn.cursor()
            c.execute("INSERT INTO exercises_catalog (name) VALUES (?)", (name,))
            self.conn.commit()
        except sqlite3.IntegrityError:
            pass  # Already exists
