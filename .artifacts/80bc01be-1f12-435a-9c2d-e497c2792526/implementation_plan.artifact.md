# Fix Fatal Exception in Voicify App

The application is experiencing a `FATAL EXCEPTION: main` caused by a `java.lang.reflect.InvocationTargetException`. This is likely due to an unhandled exception during the initialization of `MainActivity`, most probably in the `TokenStore` when creating `EncryptedSharedPreferences`, or due to unsafe JSON parsing on the main thread.

## Proposed Changes

### [TokenStore](file:///D:/Voicify/app/src/main/java/com/example/voicify/TokenStore.kt)
#### [MODIFY] [TokenStore.kt](file:///D:/Voicify/app/src/main/java/com/example/voicify/TokenStore.kt)
- Wrap `EncryptedSharedPreferences` creation in a `try-catch` block.
- If creation fails (common due to KeyStore corruption or version mismatches), delete the existing shared preferences file and attempt to recreate it. This ensures the app doesn't crash, even if it means the user has to log in again.

### [MainActivity](file:///D:/Voicify/app/src/main/java/com/example/voicify/MainActivity.kt)
#### [MODIFY] [MainActivity.kt](file:///D:/Voicify/app/src/main/java/com/example/voicify/MainActivity.kt)
- Add defensive checks when parsing Spotify API responses.
- Use `optJSONObject` and `optString` instead of `getJSONObject` and `getString` to avoid `JSONException` on the UI thread.
- Ensure all UI updates from background tasks are safe and handle null/empty data gracefully.

### [Build Configuration](file:///D:/Voicify/app/build.gradle.kts)
#### [MODIFY] [build.gradle.kts](file:///D:/Voicify/app/build.gradle.kts)
- Correct the `compileSdk` version to a standard integer value (e.g., 35) to avoid potential build or runtime issues with non-existent SDK versions.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds correctly after changes.

### Manual Verification
- Deploy the app to a device/emulator.
- Verify the app no longer crashes on startup.
- Test the login flow and playback status check to ensure they are robust against unexpected API responses.
