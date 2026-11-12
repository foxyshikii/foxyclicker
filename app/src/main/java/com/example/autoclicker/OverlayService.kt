package com.example.autoclicker

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.abs

class OverlayService : Service() {
    private class Pt(val view: TextView, val lp: WindowManager.LayoutParams)

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
    private var row: View? = null
    private var dot: TextView? = null
    private var grip: TextView? = null
    private var toggle: TextView? = null
    private var hidden = false
    private var state = IDLE

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (panel == null) build()
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun gray() = Color.rgb(58, 63, 85)

    private fun circle(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
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

    private fun roundBtn(label: String, color: Int, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 18f
        t.setTextColor(Color.WHITE)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.background = circle(color)
        t.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(dp(44), dp(44))
        lp.setMargins(0, dp(3), 0, dp(3))
        t.layoutParams = lp
        return t
    }

    private fun setState(s: Int) {
        state = s
        val statusColor = when (s) {
            RUNNING -> Color.rgb(34, 197, 94)
            WAITING -> Color.rgb(250, 204, 21)
            else -> Color.rgb(239, 68, 68)
        }
        dot?.setTextColor(statusColor)
        val active = s != IDLE
        toggle?.text = if (active) "❚❚" else "▶"
        toggle?.background = circle(
            if (active) Color.rgb(249, 115, 22) else Color.rgb(34, 197, 94)
        )
    }

    private fun addPoint() {
        if (points.size >= 20) {
            Toast.makeText(this, "Maksimal 20 titik", Toast.LENGTH_SHORT).show()
            return
        }
        val n = points.size + 1
        val size = dp(44)
        val tv = TextView(this)
        tv.text = n.toString()
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.argb(210, 255, 106, 0))
        bg.setStroke(dp(2), Color.WHITE)
        tv.background = bg
        val params = makeLp(
            size, size,
            dp(110) + ((n - 1) / 8) * dp(60),
            dp(140) + ((n - 1) % 8) * dp(60)
        )
        wm.addView(tv, params)
        drag(tv, tv, params)
        points.add(Pt(tv, params))
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

    private fun toggleHide() {
        hidden = !hidden
        row?.visibility = if (hidden) View.GONE else View.VISIBLE
        grip?.visibility = if (hidden) View.GONE else View.VISIBLE
        for (p in points) p.view.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun toggleRun() {
        if (state == IDLE) startClicking() else stopClicking()
    }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addPoint()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val tg = roundBtn("▶", Color.rgb(34, 197, 94)) { toggleRun() }
        toggle = tg
        col.addView(tg)
        col.addView(roundBtn("+", gray()) { addPoint() })
        col.addView(roundBtn("−", gray()) { removePoint() })
        col.addView(roundBtn("\uD83D\uDE48", gray()) { toggleHide() })
        col.addView(roundBtn("✕", Color.rgb(239, 68, 68)) { stopSelf() })
        row = col

        val d = TextView(this)
        d.text = "●"
        d.textSize = 22f
        d.gravity = Gravity.CENTER
        dot = d
        val g = TextView(this)
        g.text = "≡"
        g.textSize = 20f
        g.setTextColor(Color.rgb(170, 175, 195))
        g.gravity = Gravity.CENTER
        grip = g

        val hb = LinearLayout(this)
        hb.orientation = LinearLayout.VERTICAL
        hb.gravity = Gravity.CENTER_HORIZONTAL
        hb.setPadding(dp(6), dp(4), dp(6), dp(2))
        hb.addView(d)
        hb.addView(g)

        val pn = LinearLayout(this)
        pn.orientation = LinearLayout.VERTICAL
        pn.gravity = Gravity.CENTER_HORIZONTAL
        pn.setPadding(dp(5), dp(2), dp(5), dp(5))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(26).toFloat()
        bg.setColor(Color.argb(235, 24, 28, 40))
        bg.setStroke(dp(1), Color.argb(60, 255, 255, 255))
        pn.background = bg
        pn.addView(hb)
        pn.addView(col)

        val panelLp = makeLp(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(8), dp(160)
        )
        wm.addView(pn, panelLp)
        drag(hb, pn, panelLp) { if (hidden) toggleHide() }
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

        val coords = points.map { pt ->
            val loc = IntArray(2)
            pt.view.getLocationOnScreen(loc)
            Pair(loc[0] + pt.view.width / 2f, loc[1] + pt.view.height / 2f)
        }

        setPointsTouchable(false)
        svc.onFinish = {
            setPointsTouchable(true)
            setState(IDLE)
        }
        svc.start(coords, ms, startAt)

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
