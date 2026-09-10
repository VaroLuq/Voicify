# Fix IllegalArgumentException: method POST must have a request body

The `SpotifyApiClient` class is currently making `POST` requests (for `skipNext` and `skipPrevious`) without providing a request body. OkHttp's `Request.Builder.method(method, body)` requires a non-null `body` for `POST` and `PUT` methods, even if the body is empty.

## User Review Required

> [!NOTE]
> This change strictly addresses the OkHttp requirement and does not change the logic of the Spotify API calls themselves. Spotify's player commands like "next" and "previous" are `POST` requests that do not require data in the body, so an empty body is appropriate.

## Proposed Changes

### Spotify API Client

#### [MODIFY] [SpotifyApiClient.kt](file:///D:/Voicify/app/src/main/java/com/example/voicify/SpotifyApiClient.kt)

Modify the `playerCommand` method to provide an empty `RequestBody` for both `PUT` and `POST` methods.

```diff
-                .method(method, if (method == "PUT") emptyBody else null)
+                .method(method, if (method == "PUT" || method == "POST") emptyBody else null)
```

## Verification Plan

### Automated Tests
- I will attempt to run the application and trigger the "skip next" or "skip previous" commands to verify that the crash no longer occurs.
- Since I cannot easily run unit tests that require the device interaction here without more setup, I will rely on the code fix being correct according to OkHttp's documentation.

### Manual Verification
- Deploy the app to the device.
- Navigate to the music player screen.
- Press "Next" and "Previous" buttons.
- Check Logcat for any `IllegalArgumentException`.
