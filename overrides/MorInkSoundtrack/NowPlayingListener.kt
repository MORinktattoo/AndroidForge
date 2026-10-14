package com.morink.soundtrack

import android.app.Notification
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors

class NowPlayingListener : NotificationListenerService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        executor.execute { flushQueue() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        executor.execute { flushQueue() }
        if (!MainActivity.captureAllowed(this)) return
        val extras = sbn.notification.extras ?: return
        val packageName = sbn.packageName.lowercase(Locale.ROOT)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()
        val combined = listOf(title, titleBig, text, bigText, subText, infoText)
            .filter { it.isNotBlank() }.joinToString(" — ")

        // Do not accept arbitrary notifications just because their text says "Now playing".
        val looksLikeMusic = packageName == "com.google.intelligence.sense" ||
            packageName == "com.google.android.as" || packageName.contains("ambient") ||
            packageName.contains("music") ||
            (packageName.contains("google") &&
                (combined.contains("now playing", true) || combined.contains("identified", true)))
        if (!looksLikeMusic || combined.length < 3) return

        // Prefer real media-session metadata if Android exposes it. On some devices the
        // visible notification title is only the generic label "Now playing…".
        val pair = mediaSessionSong(extras)
            ?: parseSong(title, titleBig, text, bigText, subText, infoText)
            ?: return

        val playedAt = Instant.ofEpochMilli(sbn.postTime).toString()
        val key = "${pair.first.lowercase(Locale.ROOT)}|${pair.second.lowercase(Locale.ROOT)}"
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong("last_song_seen_at", 0L)
        // Notification refreshes for the same track are not new plays.
        if (prefs.getString(MainActivity.KEY_LAST_KEY, "") == key &&
            sbn.postTime >= lastAt && sbn.postTime - lastAt < 120_000L) return
        prefs.edit().putString(MainActivity.KEY_LAST_KEY, key)
            .putLong("last_song_seen_at", sbn.postTime)
            .putString(MainActivity.KEY_LAST_SONG, "${pair.first} — ${pair.second} · waiting to sync")
            .apply()

        val payload = JSONObject().put("title", pair.first).put("artist", pair.second)
            .put("playedAt", playedAt).put("eventId", "${sbn.postTime}-${key.hashCode()}")
            .put("studio", prefs.getString(MainActivity.KEY_STUDIO, "Mor Ink Shop"))
            .put("source", "pixel-notification-listener")
        synchronized(this) {
            val h = try { org.json.JSONArray(prefs.getString(MainActivity.KEY_HISTORY, "[]") ?: "[]") }
                catch (_: Exception) { org.json.JSONArray() }
            h.put(JSONObject(payload.toString()))
            val trimmed = org.json.JSONArray()
            for (i in maxOf(0, h.length() - 2000) until h.length()) trimmed.put(h.get(i))
            prefs.edit().putString(MainActivity.KEY_HISTORY, trimmed.toString()).apply()
        }
        executor.execute { sendOrQueue(payload) }
    }

    private fun mediaSessionSong(extras: android.os.Bundle): Pair<String, String>? {
        return try {
            @Suppress("DEPRECATION")
            val token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
                ?: return null
            val metadata = MediaController(this, token).metadata ?: return null
            val track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = (metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim().orEmpty()
            validTrackPair(track, artist)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSong(vararg rawFields: String): Pair<String, String>? {
        val fields = rawFields.map { it.trim() }.filter { it.isNotBlank() }
        // Handle formats such as "Track — Artist", but never accept a status label as a title.
        for (candidate in fields) {
            for (separator in listOf(" — ", " - ", " • ", " by ")) {
                if (!candidate.contains(separator)) continue
                val parts = candidate.split(separator, limit = 2).map { it.trim() }
                if (parts.size == 2) validTrackPair(parts[0], parts[1])?.let { return it }
            }
        }

        // Standard media notification: actual track title in title, artist in text.
        for (track in fields) {
            if (isPlaceholderTitle(track)) continue
            for (artist in fields) {
                if (artist == track || isPlaceholderTitle(artist)) continue
                validTrackPair(track, artist)?.let { return it }
            }
        }
        return null
    }

    private fun validTrackPair(trackRaw: String, artistRaw: String): Pair<String, String>? {
        val track = trackRaw.trim().trim('—', '-', '•', ':', ' ')
        val artist = artistRaw.trim().trim('—', '-', '•', ':', ' ')
        if (track.isBlank() || artist.isBlank() || track.equals(artist, true)) return null
        if (track.length > 120 || artist.length > 120) return null
        if (isPlaceholderTitle(track) || isPlaceholderTitle(artist)) return null
        return track to artist
    }

    private fun isPlaceholderTitle(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.…]+$"), "").trim()
        return normalized.isBlank() ||
            normalized in setOf("now playing", "identified", "song identified", "music identified",
                "song detected", "listening", "listening now", "unknown", "unknown song",
                "no song title", "playing music") ||
            normalized.startsWith("now playing ") ||
            normalized.startsWith("listening to ")
    }

    private fun sendOrQueue(payload: JSONObject) {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(MainActivity.KEY_ENDPOINT, "").orEmpty()
        val secret = prefs.getString(MainActivity.KEY_SECRET, "").orEmpty()
        if (endpoint.isBlank() || secret.isBlank()) {
            appendQueue(payload)
            return
        }
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-shop-secret", secret)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) appendQueue(payload)
            else prefs.edit().putString(MainActivity.KEY_LAST_SONG,
                "${payload.optString("title")} — ${payload.optString("artist")} · synced").apply()
        } catch (_: Exception) { appendQueue(payload) }
    }

    private fun flushQueue() {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(MainActivity.KEY_ENDPOINT, "").orEmpty()
        val secret = prefs.getString(MainActivity.KEY_SECRET, "").orEmpty()
        if (endpoint.isBlank() || secret.isBlank()) return
        val oldQueue = org.json.JSONArray(prefs.getString("pending_queue", "[]"))
        if (oldQueue.length() == 0) return
        val remaining = org.json.JSONArray()
        for (i in 0 until oldQueue.length()) {
            val item = oldQueue.optJSONObject(i) ?: continue
            if (!postPayload(endpoint, secret, item)) remaining.put(item)
        }
        prefs.edit().putString("pending_queue", remaining.toString()).apply()
    }

    private fun postPayload(endpoint: String, secret: String, payload: JSONObject): Boolean {
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-shop-secret", secret)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (_: Exception) { false }
    }

    private fun appendQueue(payload: JSONObject) {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            val queue = org.json.JSONArray(prefs.getString("pending_queue", "[]"))
            val id = payload.optString("eventId")
            val alreadyQueued = (0 until queue.length()).any { queue.optJSONObject(it)?.optString("eventId") == id }
            if (!alreadyQueued && queue.length() < 3000) queue.put(payload)
            prefs.edit().putString("pending_queue", queue.toString())
                .putString(MainActivity.KEY_LAST_SONG,
                    "${payload.optString("title")} — ${payload.optString("artist")} · queued").apply()
        }
    }
}
