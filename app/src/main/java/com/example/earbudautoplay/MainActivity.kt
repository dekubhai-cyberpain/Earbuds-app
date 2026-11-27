package com.example.earbudautoplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F1115")
    private val cardColor = Color.parseColor("#181B22")
    private val stroke = Color.parseColor("#2A2E37")
    private val accent = Color.parseColor("#7C5CFF")
    private val textColor = Color.parseColor("#F2F3F5")
    private val muted = Color.parseColor("#8A8F98")

    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var deviceName: TextView
    private lateinit var overlayStatus: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius * resources.displayMetrics.density
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(title: String, body: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardColor, 20, stroke)
        setPadding(dp(18), dp(16), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply { topMargin = dp(14) }
        addView(label(title.uppercase(), 12f, muted, true).apply { letterSpacing = 0.1f })
        body()
    }

    private fun input(hintText: String, value: String?) = EditText(this).apply {
        hint = hintText
        setText(value)
        setHintTextColor(muted)
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setSingleLine(true)
        background = rounded(bg, 12, stroke)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply { topMargin = dp(10) }
    }

    private fun pill(t: String, filled: Boolean, small: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(if (filled) Color.WHITE else accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null
        val shape = if (filled) rounded(accent, 14) else rounded(Color.TRANSPARENT, 14, accent)
        background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), shape, null)
        if (small) {
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(wrapContent, dp(38))
        } else {
            layoutParams = LinearLayout.LayoutParams(matchParent, dp(50)).apply { topMargin = dp(12) }
        }
        setOnClickListener { onClick() }
    }

    private fun styledSwitch(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        isChecked = checked
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, muted))
        trackTintList = ColorStateList(states, intArrayOf(accent, stroke))
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val prefs = Player.prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        root.addView(label("Earbud AutoPlay", 28f, textColor, true))
        root.addView(label("Your music, the moment your earbuds connect.", 14f, muted).apply {
            setPadding(0, dp(4), 0, dp(6))
        })

        // --- Trigger ---
        deviceName = label(prefs.getString("deviceName", null) ?: "Not chosen yet", 16f, textColor, true)
        root.addView(card("Trigger") {
            addView(styledSwitch("Auto-play when earbuds connect", prefs.getBoolean("enabled", true)) {
                prefs.edit().putBoolean("enabled", it).apply()
            })
            val deviceRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            val names = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
                addView(label("Earbuds", 12f, muted))
                addView(deviceName)
            }
            deviceRow.addView(names)
            deviceRow.addView(pill("Choose", false, true) { chooseDevice() })
            addView(deviceRow)
        })

        // --- Music ---
        val pkgInput = input("Music app package", prefs.getString("pkg", "com.spotify.music"))
        val songInput = input("Song or playlist link (or song name + artist)", prefs.getString("song", ""))
        root.addView(card("Music") {
            addView(pkgInput)
            addView(songInput)
            addView(label("Paste a share link from your music app. SimpMusic package: com.maxrave.simpmusic", 12f, muted).apply {
                setPadding(0, dp(10), 0, 0)
            })
        })

        // --- Volume ---
        val volLabel = label("", 22f, accent, true)
        val volBar = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("volPercent", 50)
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(stroke)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(4), dp(8), dp(4), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    volLabel.text = "$p%"
                    if (fromUser) prefs.edit().putInt("volPercent", p).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        volLabel.text = "${volBar.progress}%"
        root.addView(card("Volume") {
            addView(styledSwitch("Set volume when earbuds connect", prefs.getBoolean("volEnabled", false)) {
                prefs.edit().putBoolean("volEnabled", it).apply()
            })
            addView(volLabel)
            addView(volBar)
        })

        // --- Setup ---
        overlayStatus = label("", 15f, textColor, true)
        root.addView(card("Setup") {
            addView(label("Display over other apps", 12f, muted))
            addView(overlayStatus)
            addView(pill("Open settings", false, false) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            })
        })

        // --- Actions ---
        fun save() {
            prefs.edit()
                .putString("pkg", pkgInput.text.toString().trim())
                .putString("song", songInput.text.toString().trim())
                .apply()
        }
        root.addView(pill("Save", true, false) {
            save()
            Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(22) })
        root.addView(pill("Test play now", false, false) {
            save()
            Player.applyVolume(this@MainActivity)
            Player.play(this@MainActivity)
            Handler(Looper.getMainLooper()).postDelayed({ Player.nudgePlay(this@MainActivity) }, 3500)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        overlayStatus.text = if (ok) "Granted \u2713" else "Required - tap below to enable"
        overlayStatus.setTextColor(Color.parseColor(if (ok) "#4ADE80" else "#FBBF24"))
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
                deviceName.text = d.name ?: d.address
            }
            .show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseDevice()
    }
}
