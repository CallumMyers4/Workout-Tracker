# Android Build From Windows

This project can be built for Android without installing Linux locally by using GitHub Actions.

## One-time setup

1. Create a GitHub repository for this project or push this folder to an existing repo.
2. Make sure these files are committed:
   `main.py`
   `mobile_app.py`
   `buildozer.spec`
   `.github/workflows/android-apk.yml`
3. Push to `main` or `master`, or run the workflow manually from GitHub.

## Build the APK

1. Open your repo on GitHub.
2. Go to `Actions`.
3. Open `Build Android APK`.
4. Click `Run workflow`.
5. Wait for the build job to finish.
6. Download the `workout-tracker-apk` artifact.
7. Extract the zip and keep the `.apk` file.

## Install on Pixel 8a from Windows

### Enable developer mode on the phone

1. Open `Settings > About phone`.
2. Tap `Build number` 7 times.
3. Go to `Settings > System > Developer options`.
4. Enable `USB debugging`.

### Install ADB on Windows

1. Download Android Platform Tools from Google:
   https://developer.android.com/tools/releases/platform-tools
2. Extract them somewhere simple, for example:
   `C:\platform-tools`

### Connect and install

1. Connect the Pixel 8a with USB.
2. Accept the USB debugging prompt on the phone.
3. In PowerShell:

```powershell
cd C:\Users\callu\OneDrive\Desktop\platform-tools
.\adb devices
.\adb install -r C:\Users\callu\OneDrive\Desktop\Workout_Tracker.apk
```

## Updating later

After future code changes:

1. Commit and push from Windows.
2. Re-run the GitHub Action.
3. Download the new APK.
4. Reinstall with:

```powershell
.\adb install -r C:\path\to\your\downloaded.apk
```

## Notes

- This workflow builds a debug APK.
- Google Drive auth is compile-safe for Android, but real Android sign-in still needs a mobile-native auth flow before sync will fully work on-device.
