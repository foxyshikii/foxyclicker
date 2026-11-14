package com.example.autoclicker

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    companion object {
        var instance: OverlayService? = null
        const val IDLE = 0
        const val WAITING = 1
        const val RUNNING = 2
    }

    private class Node(val step: Step, val v: TextView, val lp: WindowManager.LayoutParams) {
        var ev: TextView? = null
        var elp: WindowManager.LayoutParams? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var ui: Ui
    private lateinit var pal: Pal
    private val nodes = mutableListOf<Node>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lineView: LineView? = null
    private var panel: LinearLayout? = null
    private var bubble: FrameLayout? = null
    private var chip: TextView? = null
    private var playBtn: FrameLayout? = null
    private var playIcon: IconView? = null
    private var bar: View? = null
    private var dialog: AlertDialog? = null
    private var hidden = false
    private var state = IDLE
    private var startAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            refreshChip()
            uiHandler.postDelayed(this, 500L)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i != null && i.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startFg()
        if (panel == null) build()
        return START_NOT_STICKY
    }

    // ---------- notifikasi tetap ----------

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("foxy", "Foxy Clicker", NotificationManager.IMPORTANCE_LOW)
        )
        val stopI = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openI = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_fg), "Tutup panel", stopI
        ).build()
        val n = Notification.Builder(this, "foxy")
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("Foxy Clicker aktif")
            .setContentText("Panel melayang tampil. Tap untuk membuka app.")
            .setContentIntent(openI)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- util ----------

    private fun makeLp(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w, h, TYPE_APPLICATION_OVERLAY,
        FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun fullLp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        TYPE_APPLICATION_OVERLAY,
        FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun centerOf(v: View): FloatArray {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return floatArrayOf(loc[0] + v.width / 2f, loc[1] + v.height / 2f)
    }

    private fun drag(
        touchView: View,
        target: View,
        params: WindowManager.LayoutParams,
        onTap: (() -> Unit)? = null,
        onMove: (() -> Unit)? = null,
        onDrop: (() -> Unit)? = null
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
                    sx = params.x
                    sy = params.y
                    tx = e.rawX
                    ty = e.rawY
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
                        onMove?.invoke()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap?.invoke() else onDrop?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- build ----------

    private fun build() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        pal = Pal(Theme.isDark(this))
        ui = Ui(this, pal)

        val lv = LineView(this, ui.dp(20).toFloat()) { lineData() }
        wm.addView(lv, fullLp())
        lineView = lv

        loadNodes()
        buildPanel()
        uiHandler.post(ticker)
        setState(IDLE)
    }

    private fun lineData(): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        if (hidden) return out
        for (n in nodes) {
            val e = n.ev
            if (n.step.type == 2 && e != null) {
                val a = centerOf(n.v)
                val b = centerOf(e)
                out.add(floatArrayOf(a[0], a[1], b[0], b[1]))
            }
        }
        return out
    }

    // ---------- titik ----------

    private fun loadNodes() {
        for (s in Store.loadSteps(this)) addNode(s)
        if (hidden) setNodesVisible(false)
    }

    private fun persist() {
        Store.saveSteps(this, nodes.map { it.step })
    }

    private fun syncPos(n: Node) {
        val half = ui.dp(23).toFloat()
        n.step.x = n.lp.x + half
        n.step.y = n.lp.y + half
        val e = n.elp
        if (e != null) {
            val h2 = ui.dp(13).toFloat()
            n.step.x2 = e.x + h2
            n.step.y2 = e.y + h2
        }
        persist()
    }

    private fun addNode(step: Step) {
        val size = ui.dp(46)
        val tv = TextView(this)
        tv.textSize = 18f
        tv.setTextColor(Color.rgb(255, 140, 0))
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val lp = makeLp(size, size, (step.x - size / 2f).toInt(), (step.y - size / 2f).toInt())
        val node = Node(step, tv, lp)
        wm.addView(tv, lp)
        drag(tv, tv, lp, { openStep(node) }, { lineView?.invalidate() }, { syncPos(node) })
        nodes.add(node)
        if (step.type == 2) makeEnd(node)
        applyStyle(node)
        if (hidden) {
            tv.visibility = View.GONE
        }
    }

    private fun makeEnd(n: Node) {
        val size = ui.dp(26)
        if (n.step.x2 == 0f && n.step.y2 == 0f) {
            n.step.x2 = n.step.x + ui.dp(130)
            n.step.y2 = n.step.y
        }
        val ev = TextView(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.argb(220, 30, 144, 255))
        g.setStroke(ui.dp(2), Color.WHITE)
        ev.background = g
        val elp = makeLp(size, size, (n.step.x2 - size / 2f).toInt(), (n.step.y2 - size / 2f).toInt())
        wm.addView(ev, elp)
        drag(ev, ev, elp, null, { lineView?.invalidate() }, { syncPos(n) })
        n.ev = ev
        n.elp = elp
        if (hidden) ev.visibility = View.GONE
    }

    private fun removeEnd(n: Node) {
        val e = n.ev
        if (e != null) wm.removeView(e)
        n.ev = null
        n.elp = null
    }

    private fun applyStyle(n: Node) {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.argb(45, 0, 150, 255))
        if (n.step.type == 1) {
            g.setStroke(ui.dp(3), Color.rgb(0, 170, 255), ui.dp(5).toFloat(), ui.dp(4).toFloat())
        } else {
            g.setStroke(ui.dp(3), Color.rgb(0, 170, 255))
        }
        n.v.background = g
        n.v.alpha = if (n.step.enabled) 1f else 0.4f
        n.v.text = (nodes.indexOf(n) + 1).toString()
    }

    private fun renumber() {
        for (n in nodes) n.v.text = (nodes.indexOf(n) + 1).toString()
    }

    private fun addPoint() {
        if (nodes.size >= 20) {
            toast("Maksimal 20 titik")
            return
        }
        val last = nodes.lastOrNull()
        val maxY = (resources.displayMetrics.heightPixels - ui.dp(140)).toFloat()
        var x = ui.dp(170).toFloat()
        var y = ui.dp(260).toFloat()
        if (last != null) {
            x = last.step.x
            y = last.step.y + ui.dp(64)
            if (y > maxY) {
                y = ui.dp(200).toFloat()
                x += ui.dp(64)
            }
        }
        addNode(Step(x, y))
        persist()
    }

    private fun removeNode(n: Node) {
        wm.removeView(n.v)
        removeEnd(n)
        nodes.remove(n)
        renumber()
        persist()
        lineView?.invalidate()
    }

    private fun removeLast() {
        if (nodes.size <= 1) {
            toast("Minimal 1 titik")
            return
        }
        removeNode(nodes[nodes.size - 1])
    }

    private fun setNodesVisible(vis: Boolean) {
        val v = if (vis) View.VISIBLE else View.GONE
        for (n in nodes) {
            n.v.visibility = v
            n.ev?.visibility = v
        }
        lineView?.visibility = v
        lineView?.invalidate()
    }

    private fun setNodesTouchable(touchable: Boolean) {
        for (n in nodes) {
            n.lp.flags = if (touchable) n.lp.flags and FLAG_NOT_TOUCHABLE.inv()
            else n.lp.flags or FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(n.v, n.lp)
            val e = n.ev
            val el = n.elp
            if (e != null && el != null) {
                el.flags = if (touchable) el.flags and FLAG_NOT_TOUCHABLE.inv()
                else el.flags or FLAG_NOT_TOUCHABLE
                wm.updateViewLayout(e, el)
            }
        }
    }

    fun reload() {
        stopClicking(true)
        for (n in nodes) {
            wm.removeView(n.v)
            val e = n.ev
            if (e != null) wm.removeView(e)
        }
        nodes.clear()
        loadNodes()
        lineView?.invalidate()
    }

    private fun openStep(n: Node) {
        closeDialog()
        val idx = nodes.indexOf(n) + 1
        val d = Dialogs.stepDialog(this, idx, n.step, nodes.size > 1, {
            if (n.step.type == 2) {
                if (n.ev == null) makeEnd(n)
            } else {
                removeEnd(n)
            }
            applyStyle(n)
            persist()
            lineView?.invalidate()
        }, {
            removeNode(n)
        })
        dialog = d
        Dialogs.show(d, true)
    }

    private fun openSettings() {
        closeDialog()
        val d = Dialogs.settingsDialog(this) { refreshChip() }
        dialog = d
        Dialogs.show(d, true)
    }

    private fun closeDialog() {
        try {
            dialog?.dismiss()
        } catch (e: Exception) {
        }
        dialog = null
    }

    // ---------- panel ----------

    private fun btn(kind: Int, fill: Int, icon: Int, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(fill)
        f.background = g
        f.addView(IconView(this, kind, icon), FrameLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        f.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
        lp.setMargins(0, ui.dp(3), 0, ui.dp(3))
        f.layoutParams = lp
        return f
    }

    private fun buildPanel() {
        val chipTv = TextView(this)
        chipTv.textSize = 11f
        chipTv.gravity = Gravity.CENTER
        chipTv.setTextColor(pal.barIcon)
        chipTv.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
        chipTv.background = ui.rounded(pal.barBg, 14)
        chip = chipTv

        val playFrame = btn(IconView.PLAY, pal.acc, Color.WHITE) { toggleRun() }
        playBtn = playFrame
        playIcon = playFrame.getChildAt(0) as IconView

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.background = ui.rounded(pal.barBg, 26)
        col.setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4))
        col.addView(playFrame)
        col.addView(btn(IconView.PLUS, Color.TRANSPARENT, pal.barIcon) { addPoint() })
        col.addView(btn(IconView.MINUS, Color.TRANSPARENT, pal.barIcon) { removeLast() })
        col.addView(btn(IconView.GEAR, Color.TRANSPARENT, pal.barIcon) { openSettings() })
        col.addView(btn(IconView.HIDE, Color.TRANSPARENT, pal.barIcon) { setHidden(true) })
        col.addView(btn(IconView.CLOSE, Color.TRANSPARENT, pal.barIcon) { stopSelf() })
        bar = col

        val pn = LinearLayout(this)
        pn.orientation = LinearLayout.VERTICAL
        pn.gravity = Gravity.CENTER_HORIZONTAL
        val cl = LinearLayout.LayoutParams(ui.WRAP, ui.WRAP)
        cl.bottomMargin = ui.dp(6)
        pn.addView(chipTv, cl)
        pn.addView(col)

        val panelLp = makeLp(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            ui.dp(6), ui.dp(120)
        )
        wm.addView(pn, panelLp)
        drag(chipTv, pn, panelLp)
        panel = pn

        val bb = FrameLayout(this)
        bb.addView(IconView(this, IconView.CURSOR, Color.WHITE), FrameLayout.LayoutParams(ui.dp(46), ui.dp(46)))
        bb.visibility = View.GONE
        bubble = bb
        val bubbleLp = makeLp(ui.dp(46), ui.dp(46), ui.dp(6), ui.dp(120))
        wm.addView(bb, bubbleLp)
        drag(bb, bb, bubbleLp, { setHidden(false) })
        paintBubble()
    }

    private fun paintBubble() {
        val bb = bubble ?: return
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(pal.acc)
        val ring = when (state) {
            RUNNING -> Color.rgb(34, 197, 94)
            WAITING -> Color.rgb(250, 204, 21)
            else -> Color.WHITE
        }
        g.setStroke(ui.dp(3), ring)
        bb.background = g
    }

    private fun setHidden(h: Boolean) {
        hidden = h
        panel?.visibility = if (h) View.GONE else View.VISIBLE
        bubble?.visibility = if (h) View.VISIBLE else View.GONE
        setNodesVisible(!h)
    }

    // ---------- status ----------

    private fun setState(s: Int) {
        state = s
        val pb = playBtn
        val pi = playIcon
        if (pb != null && pi != null) {
            val g = pb.background as GradientDrawable
            when (s) {
                RUNNING -> {
                    g.setColor(Color.rgb(34, 160, 107))
                    pi.set(IconView.PAUSE, Color.WHITE)
                }
                WAITING -> {
                    g.setColor(Color.rgb(224, 161, 0))
                    pi.set(IconView.PAUSE, Color.WHITE)
                }
                else -> {
                    g.setColor(pal.acc)
                    pi.set(IconView.PLAY, Color.WHITE)
                }
            }
        }
        paintBubble()
        refreshChip()
    }

    private fun refreshChip() {
        val c = chip ?: return
        val now = System.currentTimeMillis()
        if (state == WAITING && now >= startAt) setState(RUNNING)
        when (state) {
            WAITING -> c.text = "Tunggu\n" + hms((startAt - now) / 1000)
            RUNNING -> {
                val svc = ClickService.instance
                val n = if (svc != null) svc.clicks else 0
                c.text = "Jalan " + n + "x\n" + hms((now - startAt) / 1000)
            }
            else -> c.text = "Siap"
        }
    }

    private fun toggleRun() {
        if (state == IDLE) startClicking() else stopClicking(false)
    }

    private fun startClicking() {
        val svc = ClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Aktifkan Accessibility dulu", Toast.LENGTH_LONG).show()
            return
        }
        val p = Store.prefs(this)
        val defIv = Store.intervalMs(this)
        val defDelay = if (defIv <= 0L) 150L else defIv
        val loop = defIv > 0L
        var at = System.currentTimeMillis()
        if (p.getBoolean("sched", false)) at = Store.nextSchedule(this)
        val limMode = p.getInt("limMode", 0)
        val limVal = p.getInt("limVal", 0)
        val maxClicks = if (limMode == 1) limVal else 0
        val maxMs = if (limMode == 2) limVal * 60_000L else 0L

        val steps = nodes.map { n ->
            val a = centerOf(n.v)
            val e = n.ev
            val b = if (e != null) centerOf(e) else a
            Step(a[0], a[1], n.step.type, b[0], b[1], n.step.durMs, n.step.delayMs, n.step.enabled)
        }

        svc.onFinish = {
            setNodesTouchable(true)
            setState(IDLE)
        }
        val ok = svc.start(ClickService.Job(steps, defDelay, loop, at, maxClicks, maxMs))
        if (!ok) {
            toast("Tidak ada titik yang aktif")
            return
        }
        startAt = at
        setNodesTouchable(false)
        val wait = at - System.currentTimeMillis()
        if (wait > 1000L) {
            setState(WAITING)
            toast("Menunggu jadwal...")
        } else {
            setState(RUNNING)
            toast("Mulai klik")
        }
    }

    private fun stopClicking(quiet: Boolean) {
        ClickService.instance?.stop()
        setNodesTouchable(true)
        setState(IDLE)
        if (!quiet) toast("Berhenti")
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(ticker)
        closeDialog()
        val svc = ClickService.instance
        if (svc != null) {
            svc.onFinish = null
            svc.stop()
        }
        if (panel != null) {
            for (n in nodes) {
                wm.removeView(n.v)
                val e = n.ev
                if (e != null) wm.removeView(e)
            }
            nodes.clear()
            val lv = lineView
            if (lv != null) wm.removeView(lv)
            val pn = panel
            if (pn != null) wm.removeView(pn)
            val bb = bubble
            if (bb != null) wm.removeView(bb)
        }
        panel = null
        bubble = null
        lineView = null
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
