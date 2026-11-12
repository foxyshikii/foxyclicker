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
    var onFinish: (() -> Unit)? = null

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    // intervalMs = 0 -> semua titik diklik sekali saja, lalu berhenti
    fun start(points: List<Pair<Float, Float>>, intervalMs: Long, startAt: Long) {
        stop()
        if (points.isEmpty()) return
        running = true
        var i = 0
        val delay = maxOf(0L, startAt - System.currentTimeMillis())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                val p = points[i]
                tap(p.first, p.second)
                i++
                if (i >= points.size) {
                    if (intervalMs <= 0) {
                        running = false
                        onFinish?.invoke()
                        return
                    }
                    i = 0
                }
                val gap = if (intervalMs <= 0) 150L else maxOf(intervalMs, 60L)
                handler.postDelayed(this,
