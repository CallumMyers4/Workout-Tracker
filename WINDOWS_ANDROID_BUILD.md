# Android Build From Windows

This project can be built from Windows by pushing to GitHub and letting the workflow in `.github/workflows/android-apk.yml` build the APK on Ubuntu.

The important part is using one permanent signing key. That gives you:

- update installs with `adb install -r`
- one stable SHA-1 to register for Google OAuth on Android

## One-Time Setup

### 1. Create a release keystore on Windows

Run this in PowerShell with a JDK installed:

```powershell
keytool -genkeypair -v `
  -keystore C:\Users\callu\OneDrive\Desktop\workouttracker-release.keystore `
  -alias workouttracker `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

If you are using Command Prompt instead of PowerShell, use one line:

```cmd
keytool -genkeypair -v -keystore C:\Users\callu\OneDrive\Desktop\workouttracker-release.keystore -alias workouttracker -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore file, alias, and both passwords somewhere safe. Every future Android build must use the same keystore.

### 2. Get the signing certificate SHA-1

```powershell
keytool -list -v `
  -keystore C:\Users\callu\OneDrive\Desktop\workouttracker-release.keystore `
  -alias workouttracker
```

Command Prompt version:

```cmd
keytool -list -v -keystore C:\Users\callu\OneDrive\Desktop\workouttracker-release.keystore -alias workouttracker
```

Copy the `SHA1` value.

### 3. Create the Android OAuth client in Google Cloud

In Google Cloud Console:

1. Open the same project that has the Drive API enabled.
2. Go to `APIs & Services` -> `Credentials`.
3. Click `Create Credentials` -> `OAuth client ID`.
4. Choose `Android`.
5. Use package name `org.callu.workouttracker`.
6. Paste the SHA-1 from the keystore.
7. Save it.

If the consent screen is still in testing, add every Google account that should be allowed to sign in.

### 4. Add the signing secrets to GitHub

Go to `Repo Settings` -> `Secrets and variables` -> `Actions` and create these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_ALIAS_PASSWORD`

To get the base64 value from Windows:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("C:\Users\callu\OneDrive\Desktop\workouttracker-release.keystore")
)
```

Use the full output as the value for `ANDROID_KEYSTORE_BASE64`.

## Build The APK

1. Commit and push your changes.
2. Open GitHub `Actions`.
3. Run `Build Android APK`, or push to `mobileApp`, `main`, or `master`.
4. In the log, look for one of these messages:
   `Building signed release APK with the repository keystore.`
   `Building debug APK because release-signing secrets are missing.`
5. Download the `workout-tracker-android-package` artifact from the completed run.
6. If the build produced an AAB, the workflow will also try to generate `Workout_Tracker_universal.apk` for direct installation.

If you want update installs and Android Google sign-in to keep working properly, you want the signed release build.

## Install On Pixel 8a From Windows

### Enable developer mode

1. Open `Settings` -> `About phone`.
2. Tap `Build number` 7 times.
3. Open `Settings` -> `System` -> `Developer options`.
4. Enable `USB debugging`.

### Install ADB

1. Download Android Platform Tools:
   https://developer.android.com/tools/releases/platform-tools
2. Extract them somewhere simple, for example `C:\platform-tools`.

### Install the APK

```powershell
cd C:\platform-tools
.\adb devices
.\adb install -r C:\Users\callu\OneDrive\Desktop\Workout_Tracker_universal.apk
```

## Updating Later

If you previously installed an older debug-signed build, uninstall it once:

```powershell
.\adb uninstall org.callu.workouttracker
```

After that one-time switch, future signed builds can be installed in place with:

```powershell
.\adb install -r C:\Users\callu\OneDrive\Desktop\Workout_Tracker_universal.apk
```

## Notes

- `credentials.json` is for the desktop app only. Do not commit it and do not bundle it into the Android APK.
- `drive_oauth_config.json` and `drive_oauth_secret.json` are no longer needed for Android sign-in.
- Android Google Drive sign-in now uses the native Google authorization flow. Each user signs into their own Google account.
- The Android Drive token is stored in the app's local data, so users should not need to sign in again after every launch unless the app data is cleared or the token is revoked.
