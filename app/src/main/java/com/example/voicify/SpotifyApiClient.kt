package com.example.voicify

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType

sealed class CommandResult {
    object Success : CommandResult()
    object NoActiveDevice : CommandResult()
    object AuthError : CommandResult()
    object Failure : CommandResult()
}
class SpotifyApiClient(private val authManager: SpotifyAuthManager) {
    private val TAG = "MainActivityDebug"
    private val client = OkHttpClient()
    fun pause(onResult: (CommandResult) -> Unit) = playerCommand("PUT", "https://api.spotify.com/v1/me/player/pause", onResult = onResult)
    fun resume(onResult: (CommandResult) -> Unit) = playerCommand("PUT", "https://api.spotify.com/v1/me/player/play", onResult = onResult)
    fun skipNext(onResult: (CommandResult) -> Unit) = playerCommand("POST", "https://api.spotify.com/v1/me/player/next", onResult = onResult)
    fun skipPrevious(onResult: (CommandResult) -> Unit) = playerCommand("POST", "https://api.spotify.com/v1/me/player/previous", onResult = onResult)

    fun searchTrack(songName: String, artistName: String? = null, onResult: (uri: String?, name: String?, artist: String?) -> Unit) {
        authManager.getValidAccessToken { token ->
            if (token == null) {
                onResult(null, null, null)
                return@getValidAccessToken
            }

            val query = if (!artistName.isNullOrBlank()) {
                "track:$songName artist:$artistName"
            } else {
                songName
            }

            val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query) // raw string in, encoding handled once, here
                .addQueryParameter("type", "track")
                .addQueryParameter("limit", "10")
                .build()
            Log.d(TAG, url.toString())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string()
                    if (response.isSuccessful && json != null) {
                        val items = JSONObject(json).getJSONObject("tracks").getJSONArray("items")
                        if (items.length() > 0) {
                            val result = (0 until items.length()).joinToString("\n") { i ->
                                val track = items.getJSONObject(i)
                                val artists = track.getJSONArray("artists")

                                "${i + 1}. ${track.getString("name")} — " +
                                        "${(0 until artists.length()).joinToString(", ") { artists.getJSONObject(it).getString("name") }} " +
                                        "(${track.getJSONObject("album").getString("name")})"
                            }
                            Log.d(TAG, result)
                            val track = items.getJSONObject(0)
                            onResult(
                                track.getString("uri"),
                                track.getString("name"),
                                track.getJSONArray("artists").getJSONObject(0).getString("name")
                            )
                        } else {
                            onResult(null, null, null)
                        }
                    } else {
                        onResult(null, null, null)
                    }
                }
                override fun onFailure(call: Call, e: IOException) {
                    onResult(null, null, null)
                }
            })
        }
    }

    fun playTrack(uri: String, onResult: (CommandResult) -> Unit) {
        val jsonBody = JSONObject().apply { put("uris", org.json.JSONArray().apply { put(uri) }) }.toString()
        playerCommand("PUT", "https://api.spotify.com/v1/me/player/play", jsonBody, onResult)
    }

    fun searchAndPlay(songName: String, artistName: String? = null, onResult: (CommandResult, String?, String?) -> Unit) {
        searchTrack(songName, artistName) { uri, name, artist ->
            if (uri == null) onResult(CommandResult.Failure, null, null)
            else playTrack(uri) { result -> onResult(result, name, artist) }
        }
    }

    fun getCurrentPlayback(onResult: (JSONObject?) -> Unit) {
        authManager.getValidAccessToken { token ->
            if (token == null) {
                onResult(null)
                return@getValidAccessToken
            }

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/player")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, response.code.toString())
                    when (response.code) {
                        200 -> {
                            val json = response.body?.string()
                            Log.d(TAG, "playback json: $json")
                            onResult(if (json != null) JSONObject(json) else null)
                        }
                        204 -> onResult(null) // no active device — nothing is playing anywhere
                        else -> onResult(null)
                    }
                }
                override fun onFailure(call: Call, e: IOException) {
                    onResult(null)
                }
            })
        }
    }

    fun addToQueue(uri: String, onResult: (CommandResult) -> Unit) {
        val url = "https://api.spotify.com/v1/me/player/queue".toHttpUrl().newBuilder()
            .addQueryParameter("uri", uri).build().toString()
        playerCommand("POST", url, onResult = onResult)
    }

    fun searchAndQueue(songName: String, artistName: String? = null, onResult: (CommandResult, String?, String?) -> Unit) {
        searchTrack(songName, artistName) { uri, name, artist ->
            if (uri == null) onResult(CommandResult.Failure, null, null)
            else addToQueue(uri) { result -> onResult(result, name, artist) }
        }
    }

    fun rewind(seconds: Int = 15, onResult: (CommandResult) -> Unit) {
        getCurrentPlayback { json ->
            if (json == null) {
                onResult(CommandResult.NoActiveDevice)
                return@getCurrentPlayback
            }
            val progressMs = json.optInt("progress_ms", -1)
            if (progressMs < 0) {
                onResult(CommandResult.Failure) // e.g. private session hiding progress
                return@getCurrentPlayback
            }
            seekToPosition(maxOf(0, progressMs - (seconds * 1000)), onResult)
        }
    }

    fun seekToPosition(positionMs: Int, onResult: (CommandResult) -> Unit) {
        val url = "https://api.spotify.com/v1/me/player/seek".toHttpUrl().newBuilder()
            .addQueryParameter("position_ms", positionMs.toString()).build().toString()
        playerCommand("PUT", url, onResult = onResult)
    }

    private fun playerCommand(method: String, url: String, jsonBody: String? = null, onResult: (CommandResult) -> Unit) {
        authManager.getValidAccessToken { token ->
            if (token == null) {
                onResult(CommandResult.AuthError)
                return@getValidAccessToken
            }

            val body = if (jsonBody != null) jsonBody.toRequestBody("application/json".toMediaType())
            else "".toRequestBody(null)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .method(method, if (method == "PUT" || method == "POST") body else null)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    Log.d("SpotifyApiClient", "HTTP ${response.code} for ${call.request().url}")
                    onResult(when (response.code) {
                        204 -> CommandResult.Success
                        200 -> CommandResult.Success
                        404 -> CommandResult.NoActiveDevice
                        else -> CommandResult.Failure
                    })
                }
                override fun onFailure(call: Call, e: IOException) {
                    onResult(CommandResult.Failure)
                }
            })
        }
    }



}