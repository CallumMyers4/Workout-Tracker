# main.py
import sys
from PyQt5.QtWidgets import QApplication
from db_helper import DBHelper
from workout_tracker import WorkoutTracker

def main():
    #Open the application
    app = QApplication(sys.argv)

    #Create the database helper
    db = DBHelper()

    #Create the main window and show it
    window = WorkoutTracker(db)
    window.show()
    sys.exit(app.exec_())

#Run the application
if __name__ == "__main__":
    main()