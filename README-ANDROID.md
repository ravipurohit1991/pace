# Pace Android

Pace is a native Kotlin/Jetpack Compose Android application. The existing HTML/CSS/JavaScript prototype remains in the repository as a visual and interaction reference; it is not embedded in the APK.

## Toolchain

- Android Studio 2026.1.3.7
- Android Gradle Plugin 9.2.1 with built-in Kotlin
- Gradle 9.4.1 (project wrapper)
- JDK: Android Studio JBR 25.0.2; Java 17 bytecode target
- Compile/target SDK 36; minimum SDK 26
- Android SDK Build Tools 36.0.0
- Test AVD: Pixel 8, Google APIs, API 36, x86_64
- Compose BOM 2026.06.01
- Navigation 3 1.1.0

API 37 was not present in the stable SDK repository when this environment was configured, so the project intentionally remains on the stable API 36 toolchain required by Google Play in 2026.

## Build

From the repository root in PowerShell:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install it on a connected emulator or phone with:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Debug builds are automatically debug-signed. Release signing is intentionally not configured until the owner chooses secure keystore storage and passwords.

