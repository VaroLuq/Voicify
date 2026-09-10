package com.example.voicify

sealed class VoiceCommand {
    data class Play(val song: String, val artist: String?) : VoiceCommand()
    data class Queue(val song: String, val artist: String?) : VoiceCommand()
    object Skip : VoiceCommand()
    object Previous : VoiceCommand()
    object Pause : VoiceCommand()
    object Resume : VoiceCommand()
    data class Rewind(val seconds: Int) : VoiceCommand()
    object Unknown : VoiceCommand()
}

object CommandParser {
    // English + Indonesian trigger words, since your toggle can produce either
    private val queueWords = listOf("Q", "cue", "add", "add song", "add a song", "stack", "line up", "put", "queue", "antre", "antrian", "tambahkan")
    private val playWords = listOf("play", "putar", "mainkan")
    private val skipWords = listOf("skip", "next", "lewati", "berikutnya")
    private val previousWords = listOf("previous", "back", "sebelumnya", "kembali")
    private val pauseWords = listOf("pause", "stop", "jeda", "berhenti")
    private val resumeWords = listOf("resume", "continue", "lanjutkan")
    private val rewindWords = listOf("rewind", "mundur")

    fun parse(text: String): VoiceCommand {
        val lower = text.lowercase().trim()

        // Order matters: check queue before play, since "queue" is more specific
        return when {
            queueWords.any { lower.contains(it) } -> parseSongCommand(lower, isQueue = true)
            playWords.any { lower.contains(it) } -> parseSongCommand(lower, isQueue = false)
            rewindWords.any { lower.contains(it) } -> parseRewind(lower)
            skipWords.any { lower.contains(it) } -> VoiceCommand.Skip
            previousWords.any { lower.contains(it) } -> VoiceCommand.Previous
            pauseWords.any { lower.contains(it) } -> VoiceCommand.Pause
            resumeWords.any { lower.contains(it) } -> VoiceCommand.Resume
            else -> VoiceCommand.Unknown
        }
    }

    private fun parseSongCommand(text: String, isQueue: Boolean): VoiceCommand {
        var remainder = text
        (queueWords + playWords).forEach { remainder = remainder.replaceFirst(it, "") }
        remainder = remainder.trim()

        // Split on "by" (English) or "dari"/"oleh" (Indonesian) for song/artist
        val separators = listOf(" by ", " dari ", " oleh ")
        var song = remainder
        var artist: String? = null
        for (sep in separators) {
            if (remainder.contains(sep)) {
                val parts = remainder.split(sep, limit = 2)
                song = parts[0].trim()
                artist = parts.getOrNull(1)?.trim()
                break
            }
        }

        return if (isQueue) VoiceCommand.Queue(song, artist) else VoiceCommand.Play(song, artist)
    }

    private fun parseRewind(text: String): VoiceCommand.Rewind {
        val seconds = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: 15
        return VoiceCommand.Rewind(seconds)
    }
}
