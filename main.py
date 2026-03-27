import os
import sys


def run_mobile_app():
    from mobile_app import WorkoutApp

    WorkoutApp().run()


def run_desktop_app():
    from PyQt5.QtWidgets import QApplication

    from common.db_helper import DBHelper
    from desktop_app.workout_tracker import WorkoutTracker

    app = QApplication(sys.argv)
    db = DBHelper()
    window = WorkoutTracker(db)
    window.show()
    sys.exit(app.exec_())


def is_android_environment():
    return "ANDROID_ARGUMENT" in os.environ or "ANDROID_PRIVATE" in os.environ


def main():
    if is_android_environment() or os.environ.get("WORKOUT_TRACKER_USE_MOBILE") == "1":
        run_mobile_app()
    else:
        run_desktop_app()


if __name__ == "__main__":
    main()
