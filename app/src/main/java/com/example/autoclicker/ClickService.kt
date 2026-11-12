package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class ClickService : AccessibilityService() {
    companion object { var instance: ClickService? = null }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    // intervalMs = 0 -> klik sekali saja
    fun start(x: Float, y: Float, intervalMs: Long, startAt: Long) {
        stop()
        running = true
        val delay = maxOf(0L, startAt - System.currentTimeMillis())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                tap(x, y)
                if (intervalMs > 0) handler.postDelayed(this, intervalMs)
                else running = false
            }
        }, delay)
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
