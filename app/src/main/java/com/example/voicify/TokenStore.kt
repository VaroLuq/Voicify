package com.example.voicify

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


class TokenStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "spotify_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        val expiryTimestamp = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expiry", expiryTimestamp)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun isAccessTokenExpired(): Boolean {
        val expiry = prefs.getLong("expiry", 0L)
        return System.currentTimeMillis() >= expiry
    }
    fun hasTokens(): Boolean = getRefreshToken() != null
    fun clear() {
        prefs.edit().clear().apply()
    }
}