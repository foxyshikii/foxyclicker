package com.example.autoclicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var tab = 0
    private var cur: TabView? = null
    private val tabNames = arrayOf("Jadwal", "Urutan", "Preset", "Atur")

    private val tickR = object : Runnable {
        override fun run() {
            cur?.tick?.invoke()
            h.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        actionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
        h.removeCallbacks(tickR)
        h.post(tickR)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tickR)
    }

    fun rebuild() {
        val pal = Pal(Theme.isDark(this))
        val ui = Ui(this, pal)

        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pal.top, pal.mid, pal.bottom)
        )
        val glow = GradientDrawable()
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        glow.setGradientRadius(ui.dp(460).toFloat())
        glow.setGradientCenter(0.5f, 1.1f)
        glow.setColors(intArrayOf(pal.glow, Color.argb(0, 255, 100, 120)))
        val root = FrameLayout(this)
        root.background = LayerDrawable(arrayOf<Drawable>(base, glow))

        window.statusBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (!pal.dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = flags
        val sid = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusH = if (sid > 0) resources.getDimensionPixelSize(sid) else ui.dp(24)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(18), statusH + ui.dp(14), ui.dp(18), ui.dp(12))
        col.addView(ui.text("Foxy Clicker", 26f, pal.tx, true))
        col.addView(ui.text("Auto clicker multi target", 13f, pal.sub))

        // ----- tab -----
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for (i in tabNames.indices) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val t = ui.text(tabNames[i], 14f, if (i == tab) pal.acc else pal.sub, i == tab)
            t.gravity = Gravity.CENTER
            t.setPadding(0, ui.dp(10), 0, ui.dp(6))
            val ind = View(this)
            ind.setBackgroundColor(if (i == tab) pal.acc else Color.TRANSPARENT)
            cell.addView(t, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP))
            cell.addView(ind, LinearLayout.LayoutParams(ui.dp(28), ui.dp(2)))
            cell.setOnClickListener {
                tab = i
                rebuild()
            }
            tabs.addView(cell, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
        }
        col.addView(tabs, ui.lp(ui.MATCH, ui.WRAP, ui.dp(10)))

        // ----- isi tab -----
        val tv = when (tab) {
            0 -> buildJadwal(ui, this)
            1 -> buildUrutan(ui, this)
            2 -> buildPreset(ui, this)
            else -> buildAtur(ui, this)
        }
        cur = tv
        val content = FrameLayout(this)
        content.setPadding(0, 0, 0, ui.dp(12))
        content.addView(tv.view)
        val sv = ScrollView(this)
        sv.addView(content)
        col.addView(sv, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))

        // ----- tombol bawah -----
        val go = ui.text("Tampilkan panel", 16f, Color.WHITE, true)
        go.gravity = Gravity.CENTER
        go.background = ui.rounded(pal.acc, 14)
        go.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Beri izin tampil di atas app lain dulu", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            moveTaskToBack(true)
        }
        col.addView(go, ui.lp(ui.MATCH, ui.dp(52), ui.dp(8)))

        val close = ui.text("Tutup panel", 14f, pal.sub)
        close.gravity = Gravity.CENTER
        close.setPadding(0, ui.dp(12), 0, ui.dp(4))
        close.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
        col.addView(close, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP))

        root.addView(col)
        setContentView(root)
        tv.tick?.invoke()
    }
}
