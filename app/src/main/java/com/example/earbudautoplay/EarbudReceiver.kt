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
        Handler(Looper.getMainLooper()).postDelayed({
            try { Player.play(context) } finally { pending.finish() }
        }, 2500)
    }
}
