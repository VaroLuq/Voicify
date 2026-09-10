# Voicify

A native Android app that lets you control Spotify playback using your voice — no backend, no cloud costs, fully client-side.

Say things like *"play Doxy by Miles Davis"* or *"putar Hindia"* and Voicify searches Spotify and plays it. Built as a personal project to explore on-device voice control end-to-end: speech recognition, intent parsing, and direct integration with the Spotify Web API.

## Features

- **Search & play** any track by voice
- **Queue** tracks without interrupting playback
- **Skip / previous**, **pause / resume**, **rewind**
- **Bilingual** — supports English and Indonesian voice input via a language toggle
- **Secure auth** — OAuth 2.0 Authorization Code flow with PKCE, no client secret, no server
- **Debug mode** — a built-in panel for manually testing each Spotify command independently of voice input

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material3, custom dark theme)
- **Networking:** OkHttp
- **Voice input:** Android `SpeechRecognizer` API
- **Auth:** Spotify OAuth 2.0 (Authorization Code + PKCE), `EncryptedSharedPreferences` for token storage
- **API:** Spotify Web API

## How it works

```
Voice input → SpeechRecognizer → Intent parser → Spotify Web API → Playback
```

Speech is transcribed on-device, parsed into a structured command (song/artist extraction included) by a lightweight keyword-based parser, and dispatched directly to the Spotify Web API using a locally-stored, auto-refreshing access token. No server sits in the middle at any point.

## Setup

1. Register an app in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and note your **Client ID**.
2. Add your Spotify Client ID to `local.properties`:
```
SPOTIFY_CLIENT_ID=your_client_id_here
```
3. Set a redirect URI (e.g. `voicify://callback`) in both the dashboard and `SpotifyAuthManager.kt`.
4. Add your Spotify account as an allowed user under the app's Development Mode settings.
5. Build and run on a Premium Spotify account with an active device (phone, desktop, etc.) open.

## Notes

This is a personal project, built for single-user use — it runs entirely in Spotify's Development Mode (25-user cap) and isn't intended for distribution.