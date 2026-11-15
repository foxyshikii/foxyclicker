package com.example.autoclicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun buildJadwal(ui: Ui, act: Activity): TabView {
    val pal = ui.pal
    val prefs = Store.prefs(act)
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL

    val pm = act.getSystemService(Context.POWER_SERVICE) as PowerManager
    val c1 = ui.card("Izin")
    c1.addView(ui.permRow("Tampil di atas app lain", Settings.canDrawOverlays(act)) {
        act.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + act.packageName))
        )
    })
    c1.addView(ui.permRow("Accessibility", ClickService.instance != null) {
        act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    })
    c1.addView(ui.permRow("Tanpa penghemat baterai", pm.isIgnoringBatteryOptimizations(act.packageName)) {
        act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    })
    col.addView(c1)

    val c2 = ui.card("Jadwal")
    val head = LinearLayout(act)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    head.setPadding(0, ui.dp(6), 0, ui.dp(2))
    val hl = ui.text("Mulai di jam tertentu", 14f, pal.tx)
    hl.layoutParams = LinearLayout.LayoutParams(0, ui.WRAP, 1f)
    val tg = Toggle(act, pal.acc, pal.off)
    tg.checked = prefs.getBoolean("sched", false)
    head.addView(hl)
    head.addView(tg, LinearLayout.LayoutParams(ui.dp(44), ui.dp(24)))
    c2.addView(head)

    val cd: TextView = ui.text("", 15f, pal.acc, true)
    cd.gravity = Gravity.CENTER

    fun upd() {
        if (prefs.getBoolean("sched", false)) {
            val d = (Store.nextSchedule(act) - System.currentTimeMillis()) / 1000L
            cd.text = "Mulai dalam " + hms(d)
        } else {
            cd.text = "Klik langsung saat tombol ▶ ditekan"
        }
    }

    // Jam berjalan (waktu sekarang)
    val clock: TextView = ui.text("", 13f, pal.sub)
    clock.gravity = Gravity.CENTER
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    c2.addView(clock, ui.lp(ui.MATCH, ui.WRAP, ui.dp(4)))

    val wh = WheelView(act, 24, "jam", pal.tx, pal.sub)
    val wm = WheelView(act, 60, "mnt", pal.tx, pal.sub)
    val ws = WheelView(act, 60, "dtk", pal.tx, pal.sub)
    wh.setValue(prefs.getInt("h", 7))
    wm.setValue(prefs.getInt("m", 55))
    ws.setValue(prefs.getInt("s", 58))
    wh.onChange = {
        prefs.edit().putInt("h", it).apply()
        upd()
    }
    wm.onChange = {
        prefs.edit().putInt("m", it).apply()
        upd()
    }
    ws.onChange = {
        prefs.edit().putInt("s", it).apply()
        upd()
    }

    // Isi roda dengan jam sekarang, hanya kalau jadwal sedang mati.
    // Kalau jadwal aktif, roda tetap menampilkan jam jadwal yang tersimpan.
    fun fillNow() {
        if (prefs.getBoolean("sched", false)) return
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        wh.setValue(h)
        wm.setValue(m)
        ws.setValue(s)
        prefs.edit().putInt("h", h).putInt("m", m).putInt("s", s).apply()
    }

    val wheels = LinearLayout(act)
    wheels.orientation = LinearLayout.HORIZONTAL
    for (w in listOf(wh, wm, ws)) {
        wheels.addView(w, LinearLayout.LayoutParams(0, ui.dp(132), 1f))
    }
    fun wheelsOn(on: Boolean) {
        for (w in listOf(wh, wm, ws)) {
            w.isEnabled = on
            w.alpha = if (on) 1f else 0.35f
        }
    }
    wheelsOn(tg.checked)
    tg.onChange = {
        prefs.edit().putBoolean("sched", it).apply()
        wheelsOn(it)
        upd()
    }
    c2.addView(wheels)
    c2.addView(cd, ui.lp(ui.MATCH, ui.WRAP, ui.dp(4)))
    col.addView(c2)

    // Ticker 1 detik: jam berjalan + hitung mundur.
    // Jalan hanya selama tampilan menempel di layar, berhenti saat dilepas.
    val handler = Handler(Looper.getMainLooper())
    var running = false
    var wasShown = false
    val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            clock.text = "Sekarang " + fmt.format(Date())
            upd()
            // sejajarkan dengan pergantian detik
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }
    fun startTicker() {
        if (running) return
        running = true
        handler.post(ticker)
    }
    fun stopTicker() {
        running = false
        handler.removeCallbacks(ticker)
    }
    col.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            fillNow()
            wasShown = col.isShown
            startTicker()
        }

        override fun onViewDetachedFromWindow(v: View) {
            stopTicker()
            wasShown = false
        }
    })
    if (col.isAttachedToWindow) startTicker()

    clock.text = "Sekarang " + fmt.format(Date())
    fillNow()
    upd()

    return TabView(col) {
        // Saat tab baru ditampilkan (dari tersembunyi), roda diisi jam sekarang lagi.
        val sh = col.isShown
        if (sh && !wasShown) fillNow()
        wasShown = sh
        upd()
    }
}
