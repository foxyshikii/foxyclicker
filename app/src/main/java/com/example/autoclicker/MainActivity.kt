package com.example.autoclicker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun two(n: Int): String = (if (n < 10) "0" else "") + n

private class Pal(val dark: Boolean) {
    val top = Color.parseColor(if (dark) "#151013" else "#FFF8F9")
    val mid = Color.parseColor(if (dark) "#1E1317" else "#FFE3E7")
    val bottom = Color.parseColor(if (dark) "#2D151B" else "#FFC4CB")
    val glow = if (dark) Color.argb(100, 255, 70, 100) else Color.argb(140, 255, 110, 130)
    val card = if (dark) Color.argb(220, 32, 24, 27) else Color.argb(220, 255, 255, 255)
    val tx = Color.parseColor(if (dark) "#F4EEF0" else "#1B1B1F")
    val sub = Color.parseColor(if (dark) "#A5969A" else "#8C777B")
    val acc = Color.parseColor(if (dark) "#FF5A70" else "#E5384F")
    val tint = Color.parseColor(if (dark) "#3F1E25" else "#FCE1E4")
    val chip = if (dark) Color.argb(20, 255, 255, 255) else Color.argb(180, 255, 255, 255)
    val off = if (dark) Color.argb(51, 255, 255, 255) else Color.argb(36, 0, 0, 0)
    val ok = Color.parseColor("#22A06B")
}

class WheelView(
    ctx: Context,
    private val count: Int,
    private val unit: String,
    private val txColor: Int,
    private val subColor: Int
) : View(ctx) {
    var onChange: ((Int) -> Unit)? = null
    private var pos = 0f
    private val density = resources.displayMetrics.density
    private val rowH = 44f * density
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unitP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    private var vt: VelocityTracker? = null
    private var lastY = 0f
    private var moved = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    init {
        textP.textAlign = Paint.Align.CENTER
        textP.color = txColor
        textP.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        unitP.color = subColor
        unitP.textSize = 12f * density
        unitP.textAlign = Paint.Align.LEFT
    }

    private fun mod(a: Int): Int = ((a % count) + count) % count

    fun setValue(v: Int) {
        anim?.cancel()
        pos = v.toFloat()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = pos.roundToInt()
        val baseSize = 38f * density
        for (k in -3..3) {
            val idx = base + k
            val d = abs(idx - pos)
            val a = max(0f, 1f - d * 0.65f)
            if (a <= 0f) continue
            val s = 1f - min(d, 1f) * 0.4f
            textP.textSize = baseSize * s
            textP.alpha = (a * 255).toInt()
            val y = cy + (idx - pos) * rowH
            val fm = textP.fontMetrics
            val baseline = y - (fm.ascent + fm.descent) / 2f
            c.drawText(two(mod(idx)), cx, baseline, textP)
        }
        c.drawText(unit, cx + 30f * density, cy + 12f * density, unitP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                lastY = e.y
                moved = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = lastY - e.y
                pos += dy / rowH
                moved += abs(dy)
                lastY = e.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (e.actionMasked == MotionEvent.ACTION_UP && moved < slop) {
                    val delta = ((e.y - height / 2f) / rowH).roundToInt()
                    animateTo(pos.roundToInt() + delta, 300L)
                } else {
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    val target = (pos + (-vy / rowH) * 0.26f).roundToInt()
                    animateTo(target, null)
                }
                vt?.recycle()
                vt = null
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun animateTo(target: Int, duration: Long?) {
        anim?.cancel()
        val from = pos
        val to = target.toFloat()
        val dur = duration ?: min(700L, 260L + (abs(to - from) * 80f).toLong())
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = dur
        a.interpolator = DecelerateInterpolator(2f)
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    pos = to
                    invalidate()
                    onChange?.invoke(mod(to.roundToInt()))
                }
            }
        })
        anim = a
        a.start()
    }
}

class Toggle(ctx: Context, private val onC: Int, private val offC: Int) : View(ctx) {
    var onChange: ((Boolean) -> Unit)? = null
    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        setOnClickListener {
            checked = !checked
            onChange?.invoke(checked)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = if (checked) onC else offC
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        p.color = Color.WHITE
        val pad = 3f * resources.displayMetrics.density
        val cx = if (checked) w - h / 2f else h / 2f
        c.drawCircle(cx, h / 2f, h / 2f - pad, p)
    }
}

class MainActivity : Activity() {
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private lateinit var pal: Pal
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        actionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun isDark(): Boolean {
        val t = prefs.getInt("theme", 0)
        if (t == 1) return false
        if (t == 2) return true
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(r).toFloat()
        d.setColor(color)
        return d
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(pal.card, 18)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(14)
        c.layoutParams = lp
        c.addView(text(title, 12f, pal.sub))
        return c
    }

    private fun chip(label: String): TextView {
        val t = text(label, 13f, pal.sub)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        return t
    }

    private fun paintChip(t: TextView, sel: Boolean) {
        t.setTextColor(if (sel) pal.acc else pal.sub)
        t.background = rounded(if (sel) pal.tint else pal.chip, 20)
        t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun permRow(label: String, ok: Boolean, onFix: () -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(8), 0, dp(8))
        val l = text(label, 14f, pal.tx)
        l.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val s = text(if (ok) "Aktif" else "Aktifkan", 14f, if (ok) pal.ok else pal.acc, !ok)
        if (!ok) s.setOnClickListener { onFix() }
        r.addView(l)
        r.addView(s)
        return r
    }

    private fun build() {
        pal = Pal(isDark())

        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pal.top, pal.mid, pal.bottom)
        )
        val glow = GradientDrawable()
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        glow.setGradientRadius(dp(460).toFloat())
        glow.setGradientCenter(0.5f, 1.1f)
        glow.setColors(intArrayOf(pal.glow, Color.argb(0, 255, 100, 120)))
        val root = FrameLayout(this)
        root.background = LayerDrawable(arrayOf<Drawable>(base, glow))

        window.statusBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULL_SCREEN
        if (!pal.dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = flags
        val sid = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusH = if (sid > 0) resources.getDimensionPixelSize(sid) else dp(24)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), statusH + dp(14), dp(18), dp(28))
        col.addView(text("Foxy Clicker", 26f, pal.tx, true))
        col.addView(text("Auto clicker multi target", 13f, pal.sub))

        // ----- izin -----
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = ClickService.instance != null
        val c1 = card("Izin")
        c1.addView(permRow("Tampil di atas app lain", overlayOk) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })
        c1.addView(permRow("Accessibility", accOk) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(c1)

        // ----- jadwal -----
        val c2 = card("Jadwal")
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, dp(6), 0, dp(2))
        val hl = text("Mulai di jam tertentu", 14f, pal.tx)
        hl.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val tg = Toggle(this, pal.acc, pal.off)
        tg.checked = prefs.getBoolean("sched", false)
        head.addView(hl)
        head.addView(tg, LinearLayout.LayoutParams(dp(44), dp(24)))
        c2.addView(head)

        val summary = text("", 13f, pal.sub)
        summary.gravity = Gravity.CENTER
        fun upd() {
            val on = prefs.getBoolean("sched", false)
            summary.text = if (on) {
                "Klik pertama tepat jam " + two(prefs.getInt("h", 7)) + ":" +
                    two(prefs.getInt("m", 55)) + ":" + two(prefs.getInt("s", 58))
            } else {
                "Klik langsung saat tombol ▶ ditekan"
            }
        }

        val wh = WheelView(this, 24, "jam", pal.tx, pal.sub)
        val wm = WheelView(this, 60, "mnt", pal.tx, pal.sub)
        val ws = WheelView(this, 60, "dtk", pal.tx, pal.sub)
        wh.setValue(prefs.getInt("h", 7))
        wm.setValue(prefs.getInt("m", 55))
        ws.setValue(prefs.getInt("s", 58))
        wh.onChange = { prefs.edit().putInt("h", it).apply(); upd() }
        wm.onChange = { prefs.edit().putInt("m", it).apply(); upd() }
        ws.onChange = { prefs.edit().putInt("s", it).apply(); upd() }
        val wheels = LinearLayout(this)
        wheels.orientation = LinearLayout.HORIZONTAL
        for (w in listOf(wh, wm, ws)) wheels.addView(w, LinearLayout.LayoutParams(0, dp(132), 1f))
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
        upd()
        c2.addView(wheels)
        c2.addView(summary)
        col.addView(c2)

        // ----- jeda umum -----
        val c3 = card("Jeda antar klik")
        val unitChips = mutableListOf<TextView>()
        val presetChips = mutableListOf<TextView>()
        val presetVals = listOf(Pair(100L, 0), Pair(1L, 1), Pair(5L, 1))
        val presetNames = listOf("100 ms", "1 dtk", "5 dtk")
        val et = EditText(this)

        fun paintAll() {
            val u = prefs.getInt("unit", 1)
            val iv = prefs.getLong("interval", 1)
            for (i in unitChips.indices) paintChip(unitChips[i], i == u)
            for (i in presetChips.indices) {
                paintChip(presetChips[i], presetVals[i].first == iv && presetVals[i].second == u)
            }
        }

        et.setText(prefs.getLong("interval", 1).toString())
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.gravity = Gravity.CENTER
        et.textSize = 20f
        et.setTextColor(pal.tx)
        et.background = rounded(pal.chip, 12)
        et.setPadding(dp(10), dp(8), dp(10), dp(8))
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putLong("interval", s.toString().toLongOrNull() ?: 0L).apply()
                paintAll()
            }
        })

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = Gravity.CENTER_VERTICAL
        row1.setPadding(0, dp(8), 0, 0)
        row1.addView(et, LinearLayout.LayoutParams(dp(84), WRAP))
        val unitNames = listOf("ms", "dtk", "mnt", "jam")
        for (i in unitNames.indices) {
            val t = chip(unitNames[i])
            t.setOnClickListener {
                prefs.edit().putInt("unit", i).apply()
                paintAll()
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.leftMargin = dp(8)
            row1.addView(t, lp)
            unitChips.add(t)
        }
        c3.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.setPadding(0, dp(12), 0, 0)
        for (i in presetNames.indices) {
            val t = chip(presetNames[i])
            t.setOnClickListener {
                prefs.edit()
                    .putLong("interval", presetVals[i].first)
                    .putInt("unit", presetVals[i].second)
                    .apply()
                et.setText(presetVals[i].first.toString())
                paintAll()
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.rightMargin = dp(8)
            row2.addView(t, lp)
            presetChips.add(t)
        }
        c3.addView(row2)
        val hint = text("Isi 0 untuk klik sekali saja. Jeda tiap titik diatur dari panel melayang.", 12f, pal.sub)
        hint.setPadding(0, dp(12), 0, 0)
        c3.addView(hint)
        paintAll()
        col.addView(c3)

        // ----- tombol -----
        val go = text("Tampilkan panel", 16f, Color.WHITE, true)
        go.gravity = Gravity.CENTER
        go.background = rounded(pal.acc, 14)
        go.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Beri izin tampil di atas app lain dulu", Toast.LENGTH_LONG).show()
            } else {
                startService(Intent(this, OverlayService::class.java))
                moveTaskToBack(true)
            }
        }
        val glp = LinearLayout.LayoutParams(MATCH, dp(52))
        glp.topMargin = dp(20)
        col.addView(go, glp)

        val close = text("Tutup panel", 14f, pal.sub)
        close.gravity = Gravity.CENTER
        close.setPadding(0, dp(14), 0, dp(14))
        close.setOnClickListener { stopService(Intent(this, OverlayService::class.java)) }
        col.addView(close, LinearLayout.LayoutParams(MATCH, WRAP))

        // ----- tema -----
        val themeRow = LinearLayout(this)
        themeRow.orientation = LinearLayout.HORIZONTAL
        themeRow.gravity = Gravity.CENTER_VERTICAL
        val tl = text("Tema", 13f, pal.sub)
        tl.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        themeRow.addView(tl)
        val names = listOf("Sistem", "Terang", "Gelap")
        val cur = prefs.getInt("theme", 0)
        for (i in names.indices) {
            val t = chip(names[i])
            paintChip(t, i == cur)
            t.setOnClickListener {
                prefs.edit().putInt("theme", i).apply()
                build()
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.leftMargin = dp(6)
            themeRow.addView(t, lp)
        }
        col.addView(themeRow)

        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.addView(col)
        root.addView(sv)
        setContentView(root)
    }
}
