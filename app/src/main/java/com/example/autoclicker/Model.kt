package com.example.autoclicker

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

fun two(n: Int): String = (if (n < 10) "0" else "") + n

fun hms(totalSec: Long): String {
    val t = if (totalSec < 0) 0L else totalSec
    return two((t / 3600).toInt()) + ":" + two(((t % 3600) / 60).toInt()) + ":" + two((t % 60).toInt())
}

fun fmtMs(ms: Long): String {
    if (ms >= 60_000L && ms % 60_000L == 0L) return (ms / 60_000L).toString() + " mnt"
    if (ms >= 1000L && ms % 1000L == 0L) return (ms / 1000L).toString() + " dtk"
    return ms.toString() + " md"
}

class Step(
    var x: Float,
    var y: Float,
    var type: Int = 0,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var durMs: Long = 300L,
    var delayMs: Long = -1L,
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("x", x.toDouble())
        o.put("y", y.toDouble())
        o.put("t", type)
        o.put("x2", x2.toDouble())
        o.put("y2", y2.toDouble())
        o.put("d", durMs)
        o.put("dl", delayMs)
        o.put("en", enabled)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Step = Step(
            o.optDouble("x", 300.0).toFloat(),
            o.optDouble("y", 700.0).toFloat(),
            o.optInt("t", 0),
            o.optDouble("x2", 0.0).toFloat(),
            o.optDouble("y2", 0.0).toFloat(),
            o.optLong("d", 300L),
            o.optLong("dl", -1L),
            o.optBoolean("en", true)
        )
    }
}

class Preset(
    val name: String,
    val steps: String,
    val interval: Long,
    val unit: Int,
    val sched: Boolean,
    val h: Int,
    val m: Int,
    val s: Int
)

object Store {
    fun prefs(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun parseSteps(s: String?): MutableList<Step> {
        val out = mutableListOf<Step>()
        if (s != null) {
            try {
                val a = JSONArray(s)
                for (i in 0 until a.length()) out.add(Step.fromJson(a.getJSONObject(i)))
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun stepsToJson(list: List<Step>): String {
        val a = JSONArray()
        for (st in list) a.put(st.toJson())
        return a.toString()
    }

    fun loadSteps(c: Context): MutableList<Step> {
        val out = parseSteps(prefs(c).getString("steps", null))
        if (out.isEmpty()) out.add(Step(300f, 700f))
        return out
    }

    fun saveSteps(c: Context, list: List<Step>) {
        prefs(c).edit().putString("steps", stepsToJson(list)).apply()
    }

    fun loadPresets(c: Context): MutableList<Preset> {
        val out = mutableListOf<Preset>()
        val s = prefs(c).getString("presets", null)
        if (s == null) return out
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Preset(
                        o.optString("n", "Preset"),
                        o.optString("st", "[]"),
                        o.optLong("iv", 1L),
                        o.optInt("un", 1),
                        o.optBoolean("sc", false),
                        o.optInt("h", 7),
                        o.optInt("m", 55),
                        o.optInt("s", 58)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun savePresets(c: Context, list: List<Preset>) {
        val a = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("n", p.name)
            o.put("st", p.steps)
            o.put("iv", p.interval)
            o.put("un", p.unit)
            o.put("sc", p.sched)
            o.put("h", p.h)
            o.put("m", p.m)
            o.put("s", p.s)
            a.put(o)
        }
        prefs(c).edit().putString("presets", a.toString()).apply()
    }

    fun snapshot(c: Context, name: String): Preset {
        val p = prefs(c)
        return Preset(
            name,
            stepsToJson(loadSteps(c)),
            p.getLong("interval", 1L),
            p.getInt("unit", 1),
            p.getBoolean("sched", false),
            p.getInt("h", 7),
            p.getInt("m", 55),
            p.getInt("s", 58)
        )
    }

    fun applyPreset(c: Context, pr: Preset) {
        prefs(c).edit()
            .putString("steps", pr.steps)
            .putLong("interval", pr.interval)
            .putInt("unit", pr.unit)
            .putBoolean("sched", pr.sched)
            .putInt("h", pr.h)
            .putInt("m", pr.m)
            .putInt("s", pr.s)
            .apply()
    }

    fun unitMult(unit: Int): Long =
        longArrayOf(1L, 1000L, 60_000L, 3_600_000L)[unit.coerceIn(0, 3)]

    fun intervalMs(c: Context): Long {
        val p = prefs(c)
        return p.getLong("interval", 1L) * unitMult(p.getInt("unit", 1))
    }

    fun schedOn(c: Context): Boolean = prefs(c).getBoolean("sched", false)

    fun nextSchedule(c: Context): Long {
        val p = prefs(c)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, p.getInt("h", 7))
        cal.set(Calendar.MINUTE, p.getInt("m", 55))
        cal.set(Calendar.SECOND, p.getInt("s", 58))
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

object Theme {
    fun isDark(c: Context): Boolean {
        val t = Store.prefs(c).getInt("theme", 0)
        if (t == 1) return false
        if (t == 2) return true
        val night = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }
}

class Pal(val dark: Boolean) {
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
    val warn = Color.parseColor("#E0A100")
    val barBg = Color.parseColor(if (dark) "#2A1A1E" else "#FFE3E7")
    val barIcon = Color.parseColor(if (dark) "#FF8A9A" else "#B3263B")
}

class TabView(val view: View, val tick: (() -> Unit)? = null)

class Ui(val ctx: Context, val pal: Pal) {
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, r: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(r).toFloat()
        d.setColor(color)
        return d
    }

    fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun lp(w: Int, h: Int, top: Int = 0, left: Int = 0): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(w, h)
        l.topMargin = top
        l.leftMargin = left
        return l
    }

    fun card(title: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(pal.card, 18)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.layoutParams = lp(MATCH, WRAP, dp(14))
        if (title.isNotEmpty()) c.addView(text(title, 12f, pal.sub))
        return c
    }

    fun chip(label: String): TextView {
        val t = text(label, 13f, pal.sub)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        return t
    }

    fun paintChip(t: TextView, sel: Boolean) {
        t.setTextColor(if (sel) pal.acc else pal.sub)
        t.background = rounded(if (sel) pal.tint else pal.chip, 20)
        t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun permRow(label: String, ok: Boolean, onFix: () -> Unit): LinearLayout {
        val r = LinearLayout(ctx)
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
}
