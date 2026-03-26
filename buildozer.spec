[app]

# App basics
title = Workout Tracker
package.name = workouttracker
package.domain = org.callu

# Source
source.dir = .
source.include_exts = py,kv,png,jpg,jpeg,ico,json,db,txt
source.exclude_dirs = .git,build,dist,__pycache__
source.exclude_patterns = *.pyc,*.pyo,token.pickle

# Versioning
version = 1.0

# Requirements
requirements = python3,kivy,certifi

# Orientation
orientation = portrait

# Window / fullscreen
fullscreen = 0

# Android
android.api = 34
android.minapi = 26
android.sdk = 34
android.ndk = 25b
android.archs = arm64-v8a
android.accept_sdk_license = True
android.permissions = INTERNET

# Keep the bundled database available to the app package.
android.add_assets = workouts.db:.,drive_oauth_config.json:.

# Packaging
presplash_color = #0f1420

[buildozer]
log_level = 2
warn_on_root = 1
