package com.example.autoclicker

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Dialogs {
    private fun themed(c: Context): Context =
        ContextThemeWrapper(c, android.R.style.Theme_Material_Light_Dialog_Alert)

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    private fun field(c: Context, text: String, hint: String, widthDp: Int): EditText {
        val e = EditText(c)
        e.setText(text)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.gravity = Gravity.CENTER
        e.layoutParams = LinearLayout.LayoutParams(
            dp(c, widthDp),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return e
    }

    private fun label(c: Context, s: String): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = 13f
        t.setTextColor(Color.DKGRAY)
        t.setPadding(0, dp(c, 10), 0, dp(c, 2))
        return t
    }

    private fun chips(
        c: Context,
        labels: Array<String>,
        initial: Int,
        onSel: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val tvs = mutableListOf<TextView>()
        fun paint(sel: Int) {
            for (i in tvs.indices) {
                val on = i == sel
                tvs[i].setTextColor(if (on) Color.WHITE else Color.DKGRAY)
                val g = GradientDrawable()
                g.cornerRadius = dp(c, 18).toFloat()
                g.setColor(if (on) Color.parseColor("#E5384F") else Color.rgb(228, 230, 236))
                tvs[i].background = g
            }
        }
        for (i in labels.indices) {
            val t = TextView(c)
            t.text = labels[i]
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(c, 3), dp(c, 6), dp(c, 3), 0)
            t.layoutParams = lp
            t.setOnClickListener {
                paint(i)
                onSel(i)
            }
            tvs.add(t)
            row.addView(t)
        }
        paint(initial)
        return row
    }

    fun show(d: AlertDialog, overlay: Boolean) {
        val w = d.window
        if (overlay && w != null) {
            w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            w.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            )
        }
        d.show()
    }

    fun stepDialog(
        base: Context,
        number: Int,
        step: Step,
        canDelete: Boolean,
        onSave: () -> Unit,
        onDelete: () -> Unit
    ): AlertDialog {
        val c = themed(base)
        var type = step.type
        var unit = 0
        var text = ""
        if (step.delayMs >= 0) {
            val d = step.delayMs
            if (d > 0 && d % 60_000L == 0L) {
                unit = 2
                text = (d / 60_000L).toString()
            } else if (d > 0 && d % 1000L == 0L) {
                unit = 1
                text = (d / 1000L).toString()
            } else {
                unit = 0
                text = d.toString()
            }
        }
        val mults = longArrayOf(1L, 1000L, 60_000L)

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(c, 24), dp(c, 8), dp(c, 24), 0)

        val durField = field(c, step.durMs.toString(), "md", 110)
        val durLabel = label(c, "")
        val durBox = LinearLayout(c)
        durBox.orientation = LinearLayout.VERTICAL
        durBox.addView(durLabel)
        durBox.addView(durField)

        fun refresh() {
            durBox.visibility = if (type == 0) View.GONE else View.VISIBLE
            durLabel.text = if (type == 1) "Lama menahan (md)" else "Lama menggeser (md)"
        }

        body.addView(label(c, "Jenis klik"))
        body.addView(chips(c, arrayOf("Ketuk", "Tahan", "Geser"), type) {
            type = it
            val cur = durField.text.toString()
            if (it == 1 && (cur.isEmpty() || cur == "300")) durField.setText("800")
            if (it == 2 && (cur.isEmpty() || cur == "800")) durField.setText("300")
            refresh()
        })
        body.addView(durBox)
        body.addView(label(c, "Jeda sebelum klik berikutnya (kosong = jeda umum)"))
        val delayField = field(c, text, "angka", 110)
        body.addView(delayField)
        body.addView(chips(c, arrayOf("md", "dtk", "mnt"), unit) { unit = it })
        val en = CheckBox(c)
        en.text = "Titik aktif"
        en.isChecked = step.enabled
        body.addView(en)
        refresh()

        val b = AlertDialog.Builder(c)
        b.setTitle("Titik " + number)
        b.setView(body)
        b.setPositiveButton("OK") { _, _ ->
            step.type = type
            val dv = delayField.text.toString().toLongOrNull()
            step.delayMs = if (dv == null) -1L else dv * mults[unit]
            step.enabled = en.isChecked
            if (type != 0) {
                val du = durField.text.toString().toLongOrNull()
                val def = if (type == 1) 800L else 300L
                step.durMs = (du ?: def).coerceIn(50L, 60_000L)
            }
            onSave()
        }
        b.setNegativeButton("Batal", null)
        if (canDelete) b.setNeutralButton("Hapus") { _, _ -> onDelete() }
        return b.create()
    }

    fun settingsDialog(base: Context, onSave: () -> Unit): AlertDialog {
        val c = themed(base)
        val prefs = Store.prefs(base)
        var unit = prefs.getInt("unit", 1)
        var limMode = prefs.getInt("limMode", 0)

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(c, 24), dp(c, 8), dp(c, 24), 0)

        body.addView(label(c, "Jeda umum antar klik (0 = klik sekali saja)"))
        val interval = field(c, prefs.getLong("interval", 1L).toString(), "angka", 110)
        body.addView(interval)
        body.addView(chips(c, arrayOf("md", "dtk", "mnt", "jam"), unit) { unit = it })

        val sched = CheckBox(c)
        sched.text = "Mulai di jam tertentu"
        sched.isChecked = prefs.getBoolean("sched", false)
        body.addView(sched)
        val hh = field(c, prefs.getInt("h", 7).toString(), "jam", 70)
        val mm = field(c, prefs.getInt("m", 55).toString(), "mnt", 70)
        val ss = field(c, prefs.getInt("s", 58).toString(), "dtk", 70)
        val tRow = LinearLayout(c)
        tRow.orientation = LinearLayout.HORIZONTAL
        tRow.addView(hh)
        tRow.addView(mm)
        tRow.addView(ss)
        body.addView(tRow)

        body.addView(label(c, "Berhenti otomatis"))
        val limField = field(c, prefs.getInt("limVal", 100).toString(), "angka", 110)
        fun refreshLim() {
            limField.visibility = if (limMode == 0) View.GONE else View.VISIBLE
        }
        body.addView(chips(c, arrayOf("Tanpa batas", "Jumlah klik", "Menit"), limMode) {
            limMode = it
            refreshLim()
        })
        body.addView(limField)
        refreshLim()

        val b = AlertDialog.Builder(c)
        b.setTitle("Pengaturan")
        b.setView(body)
        b.setPositiveButton("Simpan") { _, _ ->
            prefs.edit()
                .putLong("interval", interval.text.toString().toLongOrNull() ?: 0L)
                .putInt("unit", unit)
                .putBoolean("sched", sched.isChecked)
                .putInt("h", (hh.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23))
                .putInt("m", (mm.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59))
                .putInt("s", (ss.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59))
                .putInt("limMode", limMode)
                .putInt("limVal", (limField.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0))
                .apply()
            onSave()
        }
        b.setNegativeButton("Batal", null)
        return b.create()
    }
}
