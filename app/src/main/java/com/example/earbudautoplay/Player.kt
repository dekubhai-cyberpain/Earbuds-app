package com.example.earbudautoplay

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

object Player {
    fun prefs(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

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
            Intent(Intent.ACTION_VIEW, Uri.parse(song)).setPackage(pkg)
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
