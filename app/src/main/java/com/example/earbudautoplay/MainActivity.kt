package com.example.earbudautoplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {

    private lateinit var deviceLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Player.prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val enabled = Switch(this).apply {
            text = "Auto-play when earbuds connect"
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("enabled", on).apply() }
        }

        deviceLabel = TextView(this).apply {
            text = "Earbuds: " + (prefs.getString("deviceName", null) ?: "not chosen")
            setPadding(0, pad, 0, 0)
        }
        val chooseBtn = Button(this).apply {
            text = "Choose earbuds"
            setOnClickListener { chooseDevice() }
        }

        val pkgInput = EditText(this).apply {
            hint = "Music app package"
            setText(prefs.getString("pkg", "com.spotify.music"))
        }
        val songInput = EditText(this).apply {
            hint = "Song name + artist, or a link like spotify:track:..."
            setText(prefs.getString("song", ""))
        }

        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit()
                    .putString("pkg", pkgInput.text.toString().trim())
                    .putString("song", songInput.text.toString().trim())
                    .apply()
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        val testBtn = Button(this).apply {
            text = "Test play now"
            setOnClickListener { saveBtn.performClick(); Player.play(context) }
        }
        val overlayBtn = Button(this).apply {
            text = "Allow display over other apps (required)"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        }

        listOf(enabled, deviceLabel, chooseBtn, pkgInput, songInput, saveBtn, testBtn, overlayBtn)
            .forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val devices = adapter?.bondedDevices?.toList().orEmpty()
        if (devices.isEmpty()) {
            Toast.makeText(this, "Pair your earbuds in Bluetooth settings first", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pick your earbuds")
            .setItems(devices.map { it.name ?: it.address }.toTypedArray()) { _, i ->
                val d = devices[i]
                Player.prefs(this).edit()
                    .putString("device", d.address)
                    .putString("deviceName", d.name ?: d.address)
                    .apply()
                deviceLabel.text = "Earbuds: " + (d.name ?: d.address)
            }
            .show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseDevice()
    }
}
