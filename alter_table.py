from db_helper import DBHelper

def cleanup_orphaned_exercises(db):
    db.conn.execute("DELETE FROM exercises WHERE workout_id NOT IN (SELECT id FROM workouts)")
    db.conn.commit()
    print("✅ Orphaned exercises removed.\n")

def print_all_tables(db):
    print("\n--- Workouts ---")
    workouts = db.conn.execute("SELECT * FROM workouts").fetchall()
    for row in workouts:
        print(row)

    print("\n--- Exercises ---")
    exercises = db.conn.execute("SELECT * FROM exercises").fetchall()
    for row in exercises:
        print(row)

    print("\n--- Exercises Catalog ---")
    catalog = db.conn.execute("SELECT * FROM exercises_catalog").fetchall()
    for row in catalog:
        print(row)

if __name__ == "__main__":
    db = DBHelper("workouts.db")
    cleanup_orphaned_exercises(db)
    print_all_tables(db)
