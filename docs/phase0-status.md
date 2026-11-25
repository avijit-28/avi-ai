# Avi AI — Phase 0 Environment Status

**Date:** 25 November 2026  
**Status:** In Progress — Repository Scaffolded; Awaiting Android Studio & SDK Installation  

---

## Environment Diagnostic

| Component | Status | Details |
| :--- | :--- | :--- |
| **Operating System** | Ready | Windows 10/11 |
| **Python** | Ready | Python 3.12.10 |
| **Git** | Ready | Git 2.55.0.windows.5 |
| **Repository Root** | Initialized | `D:\avi\AVI AI` (Git initialized, structure scaffolded) |
| **Java / JDK** | Not Detected | `java` not found on PATH |
| **Android SDK / ADB** | Not Detected | `adb` not found on PATH |
| **Android Studio** | Not Detected | Not found in standard Program Files |

---

## Action Plan for Phase 0 Completion

1. **Install Android Studio:**
   - Download the official installer from [developer.android.com/studio](https://developer.android.com/studio).
   - During setup, ensure the following components are selected:
     - Android SDK
     - Android SDK Platform-Tools (provides `adb`)
     - Android SDK Command-line Tools
     - Android SDK Build-Tools (API 34/35/36)
2. **Configure Environment Variables (User PATH):**
   - Add Android SDK Platform-Tools to PATH:  
     `%LOCALAPPDATA%\Android\Sdk\platform-tools`
   - Add Android Studio embedded JDK to `JAVA_HOME`:  
     `C:\Program Files\Android\Android Studio\jbr` (or set custom JDK 17/21)
3. **Verify Device Connection:**
   - Connect the Realme P1 5G via USB cable.
   - Enable **Developer Options** and **USB Debugging** on the phone.
   - Run `adb devices` in PowerShell to confirm device authorization (`device` status).
4. **Scaffold Minimal Kotlin Diagnostic App (Phase 1):**
   - Create Android Gradle project inside `D:\avi\AVI AI\android`.
   - Implement `InCallService` diagnostic to run TEL-01 to TEL-12.
