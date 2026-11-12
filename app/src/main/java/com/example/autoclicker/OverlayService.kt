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
