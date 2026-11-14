package com.example.autoclicker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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

class RingView(ctx: Context, private val acc: Int, private val track: Int) : View(ctx) {
    var progress: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val d = resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4f * d
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 5f * d
        p.color = track
        c.drawCircle(cx, cy, r, p)
        p.color = acc
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, -90f, 360f * progress, false, p)

        val rd = r - 9f * d
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#2B2B31")
        c.drawCircle(cx, cy, rd, p)

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = 1.4f * d
        p.color = Color.parseColor("#70707A")
        for (i in 0 until 60) {
            val a = (i * Math.PI / 30.0).toFloat()
            val r1 = rd - 7f * d
            val r2 = rd - 2f * d
            c.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, p)
        }

        val k = rd * 0.0036f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 22f * k
        p.color = Color.parseColor("#EDEDF0")
        path.reset()
        path.moveTo(cx + (226f - 325f) * k, cy + (205f - 324f) * k)
        path.lineTo(cx + (424f - 325f) * k, cy + (360f - 324f) * k)
        path.lineTo(cx + (318f - 325f) * k, cy + (360f - 324f) * k)
        path.lineTo(cx + (252f - 325f) * k, cy + (442f - 324f) * k)
        path.lineTo(cx + (226f - 325f) * k, cy + (420f - 324f) * k)
        path.close()
        c.drawPath(path, p)
    }
}

class IconView(ctx: Context, var kind: Int, var color: Int) : View(ctx) {
    companion object {
        const val PLAY = 0
        const val PAUSE = 1
        const val PLUS = 2
        const val MINUS = 3
        const val GEAR = 4
        const val HIDE = 5
        const val CLOSE = 6
        const val CURSOR = 7
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun set(k: Int, col: Int) {
        kind = k
        color = col
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = r * 0.16f
        when (kind) {
            PLAY -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - r * 0.22f, cy - r * 0.34f)
                path.lineTo(cx + r * 0.38f, cy)
                path.lineTo(cx - r * 0.22f, cy + r * 0.34f)
                path.close()
                c.drawPath(path, p)
            }
            PAUSE -> {
                p.style = Paint.Style.FILL
                rect.set(cx - r * 0.3f, cy - r * 0.32f, cx - r * 0.1f, cy + r * 0.32f)
                c.drawRoundRect(rect, r * 0.05f, r * 0.05f, p)
                rect.set(cx + r * 0.1f, cy - r * 0.32f, cx + r * 0.3f, cy + r * 0.32f)
                c.drawRoundRect(rect, r * 0.05f, r * 0.05f, p)
            }
            PLUS -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - r * 0.32f, cy, cx + r * 0.32f, cy, p)
                c.drawLine(cx, cy - r * 0.32f, cx, cy + r * 0.32f, p)
            }
            MINUS -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - r * 0.32f, cy, cx + r * 0.32f, cy, p)
            }
            GEAR -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, r * 0.26f, p)
                for (i in 0 until 8) {
                    val a = (i * Math.PI / 4.0).toFloat()
                    c.drawLine(
                        cx + cos(a) * r * 0.42f, cy + sin(a) * r * 0.42f,
                        cx + cos(a) * r * 0.6f, cy + sin(a) * r * 0.6f, p
                    )
                }
            }
            HIDE -> {
                p.style = Paint.Style.STROKE
                rect.set(cx - r * 0.6f, cy - r * 0.32f, cx + r * 0.6f, cy + r * 0.32f)
                c.drawOval(rect, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.11f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(cx - r * 0.5f, cy + r * 0.5f, cx + r * 0.5f, cy - r * 0.5f, p)
            }
            CLOSE -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - r * 0.28f, cy - r * 0.28f, cx + r * 0.28f, cy + r * 0.28f, p)
                c.drawLine(cx + r * 0.28f, cy - r * 0.28f, cx - r * 0.28f, cy + r * 0.28f, p)
            }
            CURSOR -> {
                val k = r * 0.0034f
                p.style = Paint.Style.FILL_AND_STROKE
                p.strokeWidth = 22f * k
                path.reset()
                path.moveTo(cx + (226f - 325f) * k, cy + (205f - 324f) * k)
                path.lineTo(cx + (424f - 325f) * k, cy + (360f - 324f) * k)
                path.lineTo(cx + (318f - 325f) * k, cy + (360f - 324f) * k)
                path.lineTo(cx + (252f - 325f) * k, cy + (442f - 324f) * k)
                path.lineTo(cx + (226f - 325f) * k, cy + (420f - 324f) * k)
                path.close()
                c.drawPath(path, p)
            }
        }
    }
}

class LineView(
    ctx: Context,
    strokePx: Float,
    private val provider: () -> List<FloatArray>
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = strokePx
        p.color = Color.argb(90, 0, 150, 255)
    }

    override fun onDraw(c: Canvas) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        for (l in provider()) {
            c.drawLine(l[0] - loc[0], l[1] - loc[1], l[2] - loc[0], l[3] - loc[1], p)
        }
    }
}
