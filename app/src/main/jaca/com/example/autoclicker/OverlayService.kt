package com.example.autoclicker

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var dot: View? = null
    private var panel: View? = null
    private lateinit var dotLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (dot == null) build()
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h, TYPE_APPLICATION_OVERLAY,
        FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun drag(handle: View, target: View, params: WindowManager.LayoutParams) {
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - tx).toInt()
                    params.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(target, params); true
                }
                else -> false
            }
        }
    }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val size = dp(48)
        val d = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(170, 255, 0, 0))
                setStroke(dp(2), Color.WHITE)
            }
        }
        dotLp = lp(size, size, dp(150), dp(300))
        wm.addView(d, dotLp); drag(d, d, dotLp); dot = d

        val handle = TextView(this).apply {
            text = " ≡ "; textSize = 22f; setTextColor(Color.WHITE)
        }
        val bStart = Button(this).apply { text = "▶" }
        val bStop = Button(this).apply { text = "■" }
        val bClose = Button(this).apply { text = "✕" }
        val pn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(200, 30, 30, 30))
            gravity = Gravity.CENTER_VERTICAL
            addView(handle)
            listOf(bStart, bStop, bClose).forEach {
                addView(it, LinearLayout.LayoutParams(dp(52), dp(44)))
            }
        }
        val panelLp = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, dp(10), dp(120))
        wm.addView(pn, panelLp); drag(handle, pn, panelLp); panel = pn

        bStart.setOnClickListener { startClicking() }
        bStop.setOnClickListener { stopClicking() }
        bClose.setOnClickListener { stopSelf() }
    }

    private fun setDotTouchable(touchable: Boolean) {
        dotLp.flags = if (touchable) dotLp.flags and FLAG_NOT_TOUCHABLE.inv()
        else dotLp.flags or FLAG_NOT_TOUCHABLE
        dot?.let { wm.updateViewLayout(it, dotLp) }
    }

    private fun startClicking() {
        val svc = ClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Aktifkan Accessibility dulu", Toast.LENGTH_LONG).show(); return
        }
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val mult = longArrayOf(1, 1000, 60_000, 3_600_000)[p.getInt("unit", 1)]
        val ms = p.getLong("interval", 1) * mult

        var startAt = System.currentTimeMillis()
        if (p.getBoolean("sched", false)) {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, p.getInt("h", 0))
                set(Calendar.MINUTE, p.getInt("m", 0))
                set(Calendar.SECOND, p.getInt("s", 0))
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            startAt = c.timeInMillis
        }

        val loc = IntArray(2)
        dot!!.getLocationOnScreen(loc)
        val cx = loc[0] + dot!!.width / 2f
        val cy = loc[1] + dot!!.height / 2f

        setDotTouchable(false)
        svc.start(cx, cy, ms, startAt)
        Toast.makeText(this, if (startAt > System.currentTimeMillis() + 1000)
            "Menunggu jadwal..." else "Mulai klik", Toast.LENGTH_SHORT).show()
    }

    private fun stopClicking() {
        ClickService.instance?.stop()
        setDotTouchable(true)
        Toast.makeText(this, "Berhenti", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        ClickService.instance?.stop()
        dot?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        dot = null; panel = null
        super.onDestroy()
    }
}
