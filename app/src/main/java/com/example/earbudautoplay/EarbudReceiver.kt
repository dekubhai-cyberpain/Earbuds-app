package com.example.earbudautoplay

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper

class EarbudReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val prefs = Player.prefs(context)
        if (!prefs.getBoolean("enabled", true)) return
        val saved = prefs.getString("device", null) ?: return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device?.address != saved) return

        // Give the audio route a moment to switch to the earbuds
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            try {
                Player.applyVolume(context)
                Player.play(context)
            } catch (e: Exception) {
            }
            // Bluetooth volume can re-sync after connecting, so apply it once more
            handler.postDelayed({
                try { Player.applyVolume(context) } catch (e: Exception) {}
                // Finally, nudge the music app's player to start if it only loaded the song
                handler.postDelayed({
                    try { Player.nudgePlay(context) } finally { pending.finish() }
                }, 2500)
            }, 2000)
        }, 2500)
    }
}
