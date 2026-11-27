package com.example.earbudautoplay

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import android.provider.MediaStore
import kotlin.math.roundToInt

object Player {
    fun prefs(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private const val SIMPMUSIC = "com.maxrave.simpmusic"
    private val videoId = Regex("(?:[?&]v=|youtu\\.be/|/embed/|/v/)([A-Za-z0-9_-]{11})")

    private val listId = Regex("[?&]list=([A-Za-z0-9_-]+)")

    /**
     * For SimpMusic, turn a YouTube / YouTube Music link into its own simpmusic:// link.
     * Handles single songs, playlists, and songs opened from within a playlist.
     */
    private fun toDeepLink(pkg: String, link: String): String {
        if (pkg != SIMPMUSIC) return link
        val vid = videoId.find(link)?.groupValues?.get(1)
        val list = listId.find(link)?.groupValues?.get(1)
        return when {
            vid != null && list != null -> "simpmusic://watch?v=$vid&list=$list"
            vid != null -> "simpmusic://watch?v=$vid"
            list != null -> "simpmusic://playlist?list=$list"
            else -> link
        }
    }

    /** Sends a media "play" key press (does nothing if already playing). */
    fun nudgePlay(c: Context) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    /** Sets media volume to the saved percentage, if the feature is enabled. */
    fun applyVolume(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("volEnabled", false)) return
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (p.getInt("volPercent", 50) / 100f * max).roundToInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    /**
     * Song field can be either:
     *  - a search phrase, e.g. "Blinding Lights The Weeknd" (uses the standard
     *    "play from search" intent most music apps support), or
     *  - a link/URI, e.g. "spotify:track:xxxx" or "https://..." (opened directly).
     */
    fun play(c: Context) {
        val p = prefs(c)
        val pkg = p.getString("pkg", "com.spotify.music") ?: return
        val song = p.getString("song", "") ?: ""
        if (song.isBlank()) return

        val isLink = song.startsWith("spotify:") || song.contains("://")
        val intent = if (isLink) {
            Intent(Intent.ACTION_VIEW, Uri.parse(toDeepLink(pkg, song))).setPackage(pkg)
        } else {
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, song)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            c.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: just open the app
            c.packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(it)
            }
        }
    }
}
