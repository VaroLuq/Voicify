package com.example.voicify

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.voicify.ui.theme.*

private enum class Screen { LOGIN, VOICE, DEBUG }

class MainActivity : ComponentActivity() {
    private val authManager by lazy { SpotifyAuthManager(this) }
    private val apiClient by lazy { SpotifyApiClient(authManager) }
    private val voiceRecognizer by lazy { VoiceRecognizer(this) }
    private var isLoggedIn by mutableStateOf(false)
    private var currentScreen by mutableStateOf(Screen.VOICE)
    private var voiceStatus by mutableStateOf("Tap to speak")
    private var playbackStatus by mutableStateOf("")
    private var isListening by mutableStateOf(false)
    private var isIndonesian by mutableStateOf(false)
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startListening(if (isIndonesian) "id-ID" else "en-US") else voiceStatus = "Mic permission denied" }

    @Composable
    fun LoginScreen(onLoginClick: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Voicify", style = MaterialTheme.typography.headlineMedium, color = SpotifyGreen)
            Spacer(Modifier.height(8.dp))
            Text("Voice control for your Spotify", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = BackgroundBlack),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Log in with Spotify", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    @Composable
    fun VoiceScreen(
        status: String,
        playbackStatus: String,
        isListening: Boolean,
        isIndonesian: Boolean,
        onToggleLanguage: (Boolean) -> Unit,
        onMicClick: () -> Unit,
        onDebugClick: () -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "mic-pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.15f else 1f,
            animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
            label = "scale"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onDebugClick, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                // Debug gear icon, in VoiceScreen
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Debug tools",
                    tint = TextSecondary
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("EN", color = if (!isIndonesian) SpotifyGreen else TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EN", color = if (!isIndonesian) SpotifyGreen else TextSecondary, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = isIndonesian,
                        onCheckedChange = onToggleLanguage,
                        colors = SwitchDefaults.colors(checkedTrackColor = SpotifyGreen)
                    )
                    Text("ID", color = if (isIndonesian) SpotifyGreen else TextSecondary, modifier = Modifier.padding(start = 8.dp))
                }

                Spacer(Modifier.height(64.dp))

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (isListening) SpotifyGreen else SurfaceElevated)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    // Mic icon, in VoiceScreen
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Speak",
                        tint = if (isListening) BackgroundBlack else SpotifyGreen,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))
                Text(status, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                if (playbackStatus.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(playbackStatus, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    @Composable
    fun DebugScreen(
        apiClient: SpotifyApiClient,
        playbackStatus: String,
        onStatusChange: (String) -> Unit,
        onBack: () -> Unit,
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var artistQuery by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("←", color = SpotifyGreen, style = MaterialTheme.typography.headlineMedium) }
                Text("DEBUG TOOLS", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(playbackStatus, style = MaterialTheme.typography.labelSmall, color = SpotifyGreen)
            Spacer(Modifier.height(16.dp))

            TextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Song") }, modifier = Modifier.fillMaxWidth())
            TextField(value = artistQuery, onValueChange = { artistQuery = it }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth())

            OutlinedButton(onClick = { apiClient.pause { result -> runOnUiThread { handleResult(result, "Paused") } }}, modifier = Modifier.fillMaxWidth()) { Text("Pause") }
            OutlinedButton(onClick = { apiClient.resume { result -> runOnUiThread { handleResult(result, "Resumed") } } }, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
            OutlinedButton(onClick = { apiClient.skipNext { result -> runOnUiThread { handleResult(result, "Skipped") } } }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            OutlinedButton(onClick = { apiClient.searchAndPlay(searchQuery, artistQuery.ifBlank { null }) { result, name, artist ->
                runOnUiThread { handleResult(result, "Playing: $name by $artist") }
            } }, modifier = Modifier.fillMaxWidth()) { Text("Search & Play") }
            OutlinedButton(onClick = { apiClient.searchAndQueue(searchQuery, artistQuery.ifBlank { null }) { result, name, artist ->
                runOnUiThread { handleResult(result, "Queued: $name by $artist") }
            } }, modifier = Modifier.fillMaxWidth()) { Text("Queue") }
            OutlinedButton(onClick = { apiClient.rewind(15) { result -> runOnUiThread { handleResult(result, "Rewound 15s") } } }, modifier = Modifier.fillMaxWidth()) { Text("Rewind 15s") }
            OutlinedButton(onClick = { apiClient.getCurrentPlayback { json ->
                runOnUiThread {
                    this@MainActivity.playbackStatus = if (json != null) {
                        val item = json.optJSONObject("item")
                        val isPlaying = json.optBoolean("is_playing", false)
                        if (item != null) {
                            val trackName = item.optString("name", "Unknown track")
                            "Now playing: $trackName (playing=$isPlaying)"
                        } else {
                            "Session active, but no track info available"
                        }
                    } else {
                        "No active device / nothing playing"
                    }
                }
            } }, modifier = Modifier.fillMaxWidth()) { Text("Check Playback") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLoggedIn = authManager.hasValidSession()

        setContent {
            VoicifyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !isLoggedIn -> LoginScreen(onLoginClick = {
                            CustomTabsIntent.Builder().build().launchUrl(this, authManager.buildAuthUrl())
                        })
                        currentScreen == Screen.VOICE -> VoiceScreen(
                            status = voiceStatus,
                            playbackStatus = playbackStatus,
                            isListening = isListening,
                            isIndonesian = isIndonesian,
                            onToggleLanguage = { isIndonesian = it },
                            onMicClick = { onMicTapped() },
                            onDebugClick = { currentScreen = Screen.DEBUG }
                        )
                        else -> DebugScreen(
                            apiClient = apiClient,
                            playbackStatus = playbackStatus,
                            onStatusChange = { playbackStatus = it },
                        ) { currentScreen = Screen.VOICE }
                    }
                }
            }
        }
    }

    private fun onMicTapped() {
        if (isListening) return
        val lang = if (isIndonesian) "id-ID" else "en-US"
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startListening(lang)
        } else {
            requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening(languageCode: String) {
        isListening = true
        voiceStatus = "Listening..."
        voiceRecognizer.listen(languageCode) { spoken, errorCode ->
            runOnUiThread {
                isListening = false
                if (spoken != null) {
                    voiceStatus = "\"$spoken\""
                    executeCommand(CommandParser.parse(spoken))
                } else {
                    voiceStatus = errorCode?.let { speechErrorMessage(it) } ?: "Didn't catch that"
                }
            }
        }
    }

    private fun executeCommand(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.Play -> apiClient.searchAndPlay(command.song, command.artist) { result, name, artist ->
                runOnUiThread { handleResult(result, "Playing: $name by $artist") }
            }
            is VoiceCommand.Queue -> apiClient.searchAndQueue(command.song, command.artist) { result, name, artist ->
                runOnUiThread { handleResult(result, "Queued: $name by $artist") }
            }
            VoiceCommand.Skip -> apiClient.skipNext { result -> runOnUiThread { handleResult(result, "Skipped") } }
            VoiceCommand.Previous -> apiClient.skipPrevious { result -> runOnUiThread { handleResult(result, "Went back") } }
            VoiceCommand.Pause -> apiClient.pause { result -> runOnUiThread { handleResult(result, "Paused") } }
            VoiceCommand.Resume -> apiClient.resume { result -> runOnUiThread { handleResult(result, "Resumed") } }
            is VoiceCommand.Rewind -> apiClient.rewind(command.seconds) { result -> runOnUiThread { handleResult(result, "Rewound ${command.seconds}s") } }
            VoiceCommand.Unknown -> playbackStatus = "Didn't understand that command"
        }
    }

    private fun handleResult(result: CommandResult, successMsg: String) {
        Log.d("TAG", result.toString() )
        when (result) {
            CommandResult.Success -> playbackStatus = successMsg
            CommandResult.NoActiveDevice -> playbackStatus = "No active device — open Spotify first"
            CommandResult.AuthError -> { isLoggedIn = false; playbackStatus = "Session expired" }
            CommandResult.Failure -> playbackStatus = "Something went wrong"
        }
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        android.speech.SpeechRecognizer.ERROR_NETWORK,
        android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue"
        android.speech.SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        else -> "Voice recognition error"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (code != null) authManager.exchangeCodeForToken(code) { success -> runOnUiThread { if (success) isLoggedIn = true } }
    }



    override fun onDestroy() {
        super.onDestroy()
        voiceRecognizer.destroy()
    }
}