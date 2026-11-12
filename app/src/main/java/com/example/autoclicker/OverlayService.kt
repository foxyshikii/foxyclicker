package com.example.autoclicker

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.abs

class OverlayService : Service() {
    private class Pt(val view: TextView, val lp: WindowManager.LayoutParams) {
        var delayMs = -1L
        var cx = 0f
        var cy = 0f
    }

    private companion object {
        const val IDLE = 0
        const val WAITING = 1
        const val RUNNING = 2
    }

    private lateinit var wm: WindowManager
    private val points = mutableListOf<Pt>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val toRunning = Runnable { if (state == WAITING) setState(RUNNING) }

    private var panel: View? = null
    private var toggleBtn: TextView? = null
    private var plusBtn: View? = null
    private var minusBtn: View? = null
    private var gearBtn: View? = null
    private var closeBtn: View? = null
    private var collapsed = false
    private var state = IDLE
    private var dialog: AlertDialog? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (panel == null) build()
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radiusDp: Int = 16): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(color)
        return d
    }

    private fun makeLp(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h, TYPE_APPLICATION_OVERLAY,
        FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun drag(
        touchView: View,
        target: View,
        params: WindowManager.LayoutParams,
        onTap: (() -> Unit)? = null
    ) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        touchView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = params.x; sy = params.y
                    tx = e.rawX; ty = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = sx + dx
                        params.y = sy + dy
                        wm.updateViewLayout(target, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun glyph(text: String, color: Int, size: Float, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setOnClickListener { onClick() }
        t.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        return t
    }

    private fun setState(s: Int) {
        state = s
        val t = toggleBtn ?: return
        when (s) {
            RUNNING -> { t.text = "❚❚"; t.setTextColor(Color.rgb(34, 197, 94)) }
            WAITING -> { t.text = "❚❚"; t.setTextColor(Color.rgb(250, 204, 21)) }
            else -> { t.text = "▶\uFE0E"; t.setTextColor(Color.rgb(33, 150, 243)) }
        }
    }

    private fun addPoint() {
        if (points.size >= 20) {
            Toast.makeText(this, "Maksimal 20 titik", Toast.LENGTH_SHORT).show()
            return
        }
        val n = points.size + 1
        val size = dp(48)
        val tv = TextView(this)
        tv.text = n.toString()
        tv.textSize = 18f
        tv.setTextColor(Color.rgb(255, 140, 0))
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setColor(Color.argb(60, 0, 150, 255))
        ring.setStroke(dp(3), Color.rgb(0, 170, 255))
        tv.background = ring
        val params = makeLp(
            size, size,
            dp(90) + ((n - 1) / 8) * dp(60),
            dp(140) + ((n - 1) % 8) * dp(60)
        )
        val pt = Pt(tv, params)
        wm.addView(tv, params)
        drag(tv, tv, params) { showDelayDialog(pt) }
        points.add(pt)
    }

    private fun removePoint() {
        if (points.size <= 1) {
            Toast.makeText(this, "Minimal 1 titik", Toast.LENGTH_SHORT).show()
            return
        }
        val p = points.removeAt(points.size - 1)
        wm.removeView(p.view)
    }

    private fun setPointsTouchable(touchable: Boolean) {
        for (p in points) {
            p.lp.flags = if (touchable) p.lp.flags and FLAG_NOT_TOUCHABLE.inv()
            else p.lp.flags or FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(p.view, p.lp)
        }
    }

    private fun refreshCenter(pt: Pt) {
        if (pt.view.visibility == View.VISIBLE && pt.view.width > 0) {
            val loc = IntArray(2)
            pt.view.getLocationOnScreen(loc)
            pt.cx = loc[0] + pt.view.width / 2f
            pt.cy = loc[1] + pt.view.height / 2f
        }
    }

    private fun toggleCollapse() {
        if (!collapsed) {
            for (p in points) refreshCenter(p)
        }
        collapsed = !collapsed
        val full = if (collapsed) View.GONE else View.VISIBLE
        plusBtn?.visibility = full
        minusBtn?.visibility = full
        gearBtn?.visibility = full
        closeBtn?.visibility = if (collapsed) View.VISIBLE else View.GONE
        for (p in points) p.view.visibility = full
    }

    private fun toggleRun() {
        if (state == IDLE) startClicking() else stopClicking()
    }

    // ---------- kotak dialog ----------

    private fun dc() = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert)

    private fun numField(c: Context, text: String, hint: String, widthDp: Int = 70): EditText {
        val e = EditText(c)
        e.setText(text)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.gravity = Gravity.CENTER
        e.layoutParams = LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
        return e
    }

    private fun unitRow(c: Context, labels: Array<String>, initial: Int, onSel: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val tvs = mutableListOf<TextView>()
        fun paint(sel: Int) {
            for (i in tvs.indices) {
                tvs[i].setTextColor(if (i == sel) Color.WHITE else Color.DKGRAY)
                tvs[i].background = pill(
                    if (i == sel) Color.rgb(0, 150, 200) else Color.rgb(225, 228, 235)
                )
            }
        }
        for (i in labels.indices) {
            val t = TextView(c)
            t.text = labels[i]
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(12), dp(8), dp(12), dp(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(3), dp(8), dp(3), dp(0))
            t.layoutParams = lp
            t.setOnClickListener { paint(i); onSel(i) }
            tvs.add(t)
            row.addView(t)
        }
        paint(initial)
        return row
    }

    private fun showOverlayDialog(d: AlertDialog) {
        val w = d.window
        if (w != null) {
            w.setType(TYPE_APPLICATION_OVERLAY)
            w.clearFlags(FLAG_NOT_FOCUSABLE or FLAG_ALT_FOCUSABLE_IM)
        }
        dialog = d
        d.show()
    }

    private fun closeDialog() {
        try {
            dialog?.dismiss()
        } catch (e: Exception) {
        }
        dialog = null
    }

    private fun showDelayDialog(pt: Pt) {
        closeDialog()
        val c = dc()
        val n = points.indexOf(pt) + 1
        val mults = longArrayOf(1L, 1000L, 60_000L)
        var unit = 0
        var text = ""
        if (pt.delayMs >= 0) {
            val d = pt.delayMs
            if (d > 0 && d % 60_000L == 0L) {
                unit = 2
                text = (d / 60_000L).toString()
            } else if (d > 0 && d % 1000L == 0L) {
                unit = 1
                text = (d / 1000L).toString()
            } else {
                unit = 0
                text = d.toString()
            }
        }
        val input = numField(c, text, "angka", 120)
        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(24), dp(8), dp(24), dp(0))
        val note = TextView(c)
        note.text = "Jeda sebelum klik titik berikutnya. Kosongkan untuk pakai jeda umum."
        note.textSize = 13f
        body.addView(note)
        body.addView(input)
        body.addView(unitRow(c, arrayOf("md", "dtk", "mnt"), unit) { unit = it })

        val dlg = AlertDialog.Builder(c)
            .setTitle("Titik $n")
            .setView(body)
            .setPositiveButton("OK") { _, _ ->
                val v = input.text.toString().toLongOrNull()
                pt.delayMs = if (v == null) -1L else v * mults[unit]
            }
            .setNegativeButton("Batal", null)
            .create()
        showOverlayDialog(dlg)
    }

    private fun showSettingsDialog() {
        closeDialog()
        val c = dc()
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        var unit = prefs.getInt("unit", 1)

        val interval = numField(c, prefs.getLong("interval", 1).toString(), "angka", 120)
        val sched = CheckBox(c)
        sched.text = "Mulai di jam tertentu"
        sched.isChecked = prefs.getBoolean("sched", false)
        val hh = numField(c, prefs.getInt("h", 7).toString(), "jam")
        val mm = numField(c, prefs.getInt("m", 55).toString(), "mnt")
        val ss = numField(c, prefs.getInt("s", 58).toString(), "dtk")
        val timeRow = LinearLayout(c)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.addView(hh)
        timeRow.addView(mm)
        timeRow.addView(ss)

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(24), dp(8), dp(24), dp(0))
        val lbl = TextView(c)
        lbl.text = "Jeda umum antar klik (0 = klik sekali saja)"
        lbl.textSize = 13f
        body.addView(lbl)
        body.addView(interval)
        body.addView(unitRow(c, arrayOf("md", "dtk", "mnt", "jam"), unit) { unit = it })
        body.addView(sched)
        body.addView(timeRow)

        val dlg = AlertDialog.Builder(c)
            .setTitle("Pengaturan")
            .setView(body)
            .setPositiveButton("Simpan") { _, _ ->
                prefs.edit()
                    .putLong("interval", interval.text.toString().toLongOrNull() ?: 0L)
                    .putInt("unit", unit)
                    .putBoolean("sched", sched.isChecked)
                    .putInt("h", (hh.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23))
                    .putInt("m", (mm.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59))
                    .putInt("s", (ss.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59))
                    .apply()
                Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .create()
        showOverlayDialog(dlg)
    }

    // ---------- panel ----------

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addPoint()

        val tg = glyph("▶\uFE0E", Color.rgb(33, 150, 243), 26f) { toggleRun() }
        val pl = glyph("+", Color.rgb(76, 175, 80), 30f) { addPoint() }
        val mi = glyph("−", Color.rgb(244, 67, 54), 30f) { removePoint() }
        val ge = glyph("\u2699\uFE0E", Color.rgb(160, 168, 190), 24f) { showSettingsDialog() }
        val cl = glyph("✕", Color.rgb(244, 67, 54), 22f) { stopSelf() }
        val hd = glyph("✥", Color.rgb(160, 168, 190), 24f) { }
        cl.visibility = View.GONE
        toggleBtn = tg
        plusBtn = pl
        minusBtn = mi
        gearBtn = ge
        closeBtn = cl

        val pn = LinearLayout(this)
        pn.orientation = LinearLayout.VERTICAL
        pn.gravity = Gravity.CENTER_HORIZONTAL
        pn.setPadding(dp(4), dp(6), dp(4), dp(6))
        pn.background = pill(Color.argb(235, 22, 25, 36), 14)
        pn.addView(tg)
        pn.addView(pl)
        pn.addView(mi)
        pn.addView(ge)
        pn.addView(cl)
        pn.addView(hd)

        val panelLp = makeLp(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            0, dp(160)
        )
        wm.addView(pn, panelLp)
        drag(hd, pn, panelLp) { toggleCollapse() }
        panel = pn
        setState(IDLE)
    }

    private fun startClicking() {
        val svc = ClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Aktifkan Accessibility dulu", Toast.LENGTH_LONG).show()
            return
        }
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val mult = longArrayOf(1, 1000, 60_000, 3_600_000)[prefs.getInt("unit", 1)]
        val ms = prefs.getLong("interval", 1) * mult
        val defDelay = if (ms <= 0) 150L else ms

        var startAt = System.currentTimeMillis()
        if (prefs.getBoolean("sched", false)) {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, prefs.getInt("h", 0))
            c.set(Calendar.MINUTE, prefs.getInt("m", 0))
            c.set(Calendar.SECOND, prefs.getInt("s", 0))
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            startAt = c.timeInMillis
        }

        val targets = points.map { pt ->
            refreshCenter(pt)
            ClickService.Target(pt.cx, pt.cy, if (pt.delayMs >= 0) pt.delayMs else defDelay)
        }

        setPointsTouchable(false)
        svc.onFinish = {
            setPointsTouchable(true)
            setState(IDLE)
        }
        svc.start(targets, ms > 0, startAt)

        val wait = startAt - System.currentTimeMillis()
        if (wait > 1000) {
            setState(WAITING)
            uiHandler.postDelayed(toRunning, wait)
            Toast.makeText(this, "Menunggu jadwal...", Toast.LENGTH_SHORT).show()
        } else {
            setState(RUNNING)
            Toast.makeText(this, "Mulai klik", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopClicking() {
        uiHandler.removeCallbacks(toRunning)
        ClickService.instance?.stop()
        setPointsTouchable(true)
        setState(IDLE)
        Toast.makeText(this, "Berhenti", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(toRunning)
        closeDialog()
        val svc = ClickService.instance
        if (svc != null) {
            svc.onFinish = null
            svc.stop()
        }
        val pn = panel
        if (pn != null) {
            for (p in points) wm.removeView(p.view)
            points.clear()
            wm.removeView(pn)
        }
        panel = null
        super.onDestroy()
    }
}
