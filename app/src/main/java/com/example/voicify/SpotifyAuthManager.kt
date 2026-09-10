package com.example.voicify

import android.content.Context
import android.net.Uri
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Callback
import java.io.IOException

class SpotifyAuthManager(private val context: Context) {
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "voicify://callback"
    private val tokenStore = TokenStore(context)
    private var codeVerifier: String = ""

    fun buildAuthUrl(): Uri {
        codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", "user-modify-playback-state user-read-playback-state")
            .build()
    }

    fun exchangeCodeForToken(code: String, onResult: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (response.isSuccessful && json != null) {
                    val obj = org.json.JSONObject(json)
                    tokenStore.save(
                        obj.getString("access_token"),
                        obj.getString("refresh_token"),
                        obj.getInt("expires_in")
                    )
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
        })
    }

    fun hasValidSession(): Boolean = tokenStore.hasTokens()

    fun refreshAccessToken(onResult: (Boolean) -> Unit) {
        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken == null) {
            onResult(false)
            return
        }

        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (response.isSuccessful && json != null) {
                    val obj = org.json.JSONObject(json)
                    val newAccessToken = obj.getString("access_token")
                    // Spotify may or may not rotate the refresh token — keep the old one if absent
                    val newRefreshToken = if (obj.has("refresh_token")) obj.getString("refresh_token") else refreshToken
                    val expiresIn = obj.getInt("expires_in")
                    tokenStore.save(newAccessToken, newRefreshToken, expiresIn)
                    onResult(true)
                } else {
                    // A failed refresh usually means the refresh token was revoked — caller should force re-login
                    onResult(false)
                }
            }
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
        })
    }

    fun getValidAccessToken(onResult: (String?) -> Unit) {
        if (!tokenStore.hasTokens()) {
            onResult(null)
            return
        }
        if (!tokenStore.isAccessTokenExpired()) {
            onResult(tokenStore.getAccessToken())
            return
        }
        refreshAccessToken { success ->
            if (success) {
                onResult(tokenStore.getAccessToken())
            } else {
                tokenStore.clear() // refresh token is dead — don't keep retrying forever
                onResult(null)
            }
        }
    }


}

