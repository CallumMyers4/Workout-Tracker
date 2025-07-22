# main.py
import sys
from PyQt5.QtWidgets import QApplication
from db_helper import DBHelper
from workout_tracker import WorkoutTracker

def main():
    app = QApplication(sys.argv)
    db = DBHelper()
    window = WorkoutTracker(db)
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()