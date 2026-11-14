package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

class ClickService : AccessibilityService() {
    companion object {
        var instance: ClickService? = null
    }

    class Job(
        val steps: List<Step>,
        val defDelay: Long,
        val loop: Boolean,
        val startAt: Long,
        val maxClicks: Int,
        val maxMs: Long
    )

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var wl: PowerManager.WakeLock? = null
    var onFinish: (() -> Unit)? = null
    var clicks = 0
        private set
    val isRunning: Boolean
        get() = running

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        stop()
        instance = null
        super.onDestroy()
    }

    fun start(job: Job): Boolean {
        stop()
        val list = job.steps.filter { it.enabled }
        if (list.isEmpty()) return false
        running = true
        clicks = 0
        grab()
        var i = 0
        var runStart = 0L
        val first = maxOf(0L, job.startAt - System.currentTimeMillis())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                val now = System.currentTimeMillis()
                if (runStart == 0L) runStart = now
                if (job.maxClicks > 0 && clicks >= job.maxClicks) {
                    finish()
                    return
                }
                if (job.maxMs > 0 && now - runStart >= job.maxMs) {
                    finish()
                    return
                }
                val s = list[i]
                perform(s)
                clicks++
                i++
                var last = false
                if (i >= list.size) {
                    if (!job.loop) last = true
                    i = 0
                }
                if (last) {
                    handler.postDelayed({ finish() }, 150L)
                    return
                }
                val d = if (s.delayMs >= 0) s.delayMs else job.defDelay
                val busy = if (s.type == 0) 60L else s.durMs + 40L
                handler.postDelayed(this, maxOf(d, busy, 60L))
            }
        }, first)
        return true
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        release()
    }

    private fun finish() {
        running = false
        handler.removeCallbacksAndMessages(null)
        release()
        onFinish?.invoke()
    }

    private fun perform(s: Step) {
        val path = Path()
        path.moveTo(s.x, s.y)
        var dur = 50L
        if (s.type == 1) {
            dur = s.durMs
        } else if (s.type == 2) {
            path.lineTo(s.x2, s.y2)
            dur = s.durMs
        }
        dur = dur.coerceIn(50L, 60_000L)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, dur))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun grab() {
        release()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val w = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foxy:click")
        w.acquire(12L * 60L * 60L * 1000L)
        wl = w
    }

    private fun release() {
        val w = wl
        if (w != null && w.isHeld) w.release()
        wl = null
    }
}
