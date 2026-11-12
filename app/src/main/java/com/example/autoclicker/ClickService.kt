package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class ClickService : AccessibilityService() {
    companion object { var instance: ClickService? = null }

    // delayMs = jeda setelah klik titik ini, sebelum klik titik berikutnya
    class Target(val x: Float, val y: Float, val delayMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    var onFinish: (() -> Unit)? = null

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    // loop = false -> semua titik diklik sekali saja, lalu berhenti
    fun start(targets: List<Target>, loop: Boolean, startAt: Long) {
        stop()
        if (targets.isEmpty()) return
        running = true
        var i = 0
        val first = maxOf(0L, startAt - System.currentTimeMillis())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                val t = targets[i]
                tap(t.x, t.y)
                i++
                if (i >= targets.size) {
                    if (!loop) {
                        running = false
                        onFinish?.invoke()
                        return
                    }
                    i = 0
                }
                handler.postDelayed(this, maxOf(t.delayMs, 60L))
            }
        }, first)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(g, null, null)
    }
}
