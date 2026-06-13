# Project Bug Analysis & Tracking Registry

This document records the results of a detailed static code analysis and security/compatibility review for the IPTVMine Pro project. It outlines potential bug vectors, architectural risks, and missing features/TODOs, and provides a structured registry for tracking active issues.

---

## 📊 Project Health Overview

- **Build Status**: 🟢 **SUCCESSFUL**
  - Checked via local Gradle compilation (`.\gradlew.bat compileDebugKotlin`). The project compiles successfully with zero compile-time Kotlin errors.
- **Min SDK Version**: `24` (Android 7.0)
- **Target SDK Version**: `36`
- **Core Architectures**: MVVM, Jetpack Compose, Kotlin Coroutines, AndroidX Media3 (ExoPlayer), Headless WebView JS Bridge.

---

## 🔍 Detailed Code Analysis & Potential Bug Vectors

During the codebase review, several architectural risks, potential memory leaks, and runtime compatibility issues were identified:

### 1. ExoPlayer/Media3 & PiP Lifecycle Risks
*File location: [PlayerActivity.kt](file:///c:/Users/ADMIN/AndroidStudioProjects/IPTVMine-Pro/Player/src/main/java/com/samyak/player/PlayerActivity.kt)*

* **API Compatibility Crash in Broadcast Registration**:
  On line 1265, `registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)` is called when Picture-in-Picture receiver is registered.
  * **Risk**: The `RECEIVER_NOT_EXPORTED` flag was introduced in API level 33 (Android 13). Since the app's `minSdk` is `24`, calling this on devices running Android 8.0 to Android 12 (API levels 26–32) will result in a runtime exception (`NoSuchMethodError` or `IllegalArgumentException`), crashing the player activity when initiating PiP.
  * **Remedy**: Wrap with an SDK check or use `ContextCompat.registerReceiver` from the AndroidX Core library, which automatically manages receiver exporting flags on compatible OS versions:
    ```kotlin
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(pipReceiver, filter)
    }
    ```

* **Leaked Suspended Coroutines on WebView Destruction**:
  *File location: [VegaProviderRunner.kt](file:///c:/Users/ADMIN/AndroidStudioProjects/IPTVMine-Pro/app/src/main/java/com/samyak/iptvminepro/provider/VegaProviderRunner.kt)*
  * **Risk**: The runner evaluates JS asynchronously via `evalJsAsync` and suspends waiting for `deferred.await()` with a 45-second timeout. If the runner's `destroy()` method is called while a JS scraper request is actively pending, the WebView is destroyed, but the suspended coroutines in `evalJsAsync` are left waiting for the full 45-second timeout before throwing.
  * **Remedy**: Introduce a `clearPendingCallbacks` or cancel method inside `AndroidBridge` to immediately cancel all active Deferred promises in the callback map upon runner destruction.

---

## 🛠 Missing Features & Functional Gaps (TODOs)

Several files contain placeholders and unimplemented functions that are currently marked with `TODO`:

### 1. Settings Menu Navigation
*File location: [SettingsScreen.kt](file:///c:/Users/ADMIN/AndroidStudioProjects/IPTVMine-Pro/app/src/main/java/com/samyak/iptvminepro/ui/screens/SettingsScreen.kt)*

* **Bug Report Option** (Line 70):
  ```kotlin
  onClick = { /* TODO */ }
  ```
  * **Impact**: Tapping "Report Bug" does nothing. Needs implementation (e.g., launching an email intent or redirecting to a GitHub issues page).
* **Legal and Disclaimers** (Lines 77, 82, 87):
  ```kotlin
  // Privacy Policy, Terms & Conditions, Disclaimer
  onClick = { /* TODO */ }
  ```
  * **Impact**: Unimplemented links for app legal requirements.

### 2. Network State Adaptability
* **ExoPlayer Auto-Pause/Resume**:
  The player currently handles `AudioBecomingNoisy` but does not listen for network state updates. If a user transitions from Wi-Fi to cellular or drops connection entirely, player streams will stall without a user-friendly message or automatic retry attempt once the connection is restored.

---

## 📋 Active Bug & Defect Registry

This table is reserved for logging active runtime errors, UI bugs, or customer-reported issues. 

| Bug ID | Description | Component | Severity | Status | Target Fix Version |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-001 | Clicking movie cards from HDHub4u extension opens empty details screen. | Scraper / VegaProviderRunner | High | Resolved | v1.0.1 |


---

## 📝 Bug Reporting Guidelines

To add a new bug to this registry:
1. Assign a sequential **Bug ID** (e.g., `BUG-001`).
2. Provide a descriptive summary, including specific steps to reproduce and the target device context.
3. Classify **Severity** (`Low`, `Medium`, `High`, `Critical`).
4. Set **Status** to `New`, `In Progress`, or `Resolved`.
5. Link relevant source code files or log traces where applicable.
