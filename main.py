import sys


def run_desktop_app():
    from PyQt5.QtWidgets import QApplication

    from common.db_helper import DBHelper
    from desktop_app.workout_tracker import WorkoutTracker

    app = QApplication(sys.argv)
    db = DBHelper()
    window = WorkoutTracker(db)
    window.show()
    sys.exit(app.exec_())
def main():
    run_desktop_app()


if __name__ == "__main__":
    main()
