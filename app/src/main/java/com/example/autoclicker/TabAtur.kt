package com.example.autoclicker

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

fun buildAtur(ui: Ui, act: MainActivity): TabView {
    val pal = ui.pal
    val prefs = Store.prefs(act)
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL

    // ----- tema -----
    val c0 = ui.card("Tema")
    val themeRow = LinearLayout(act)
    themeRow.orientation = LinearLayout.HORIZONTAL
    themeRow.setPadding(0, ui.dp(8), 0, 0)
    val themeNames = listOf("Sistem", "Terang", "Gelap")
    val curTheme = prefs.getInt("theme", 0)
    for (i in themeNames.indices) {
        val t = ui.chip(themeNames[i])
        ui.paintChip(t, i == curTheme)
        t.setOnClickListener {
            prefs.edit().putInt("theme", i).apply()
            act.rebuild()
        }
        themeRow.addView(t, ui.lp(ui.WRAP, ui.WRAP, 0, if (i == 0) 0 else ui.dp(8)))
    }
    c0.addView(themeRow)
    col.addView(c0)

    // ----- jeda umum -----
    val c1 = ui.card("Jeda umum antar klik")
    val unitChips = mutableListOf<TextView>()
    val presetChips = mutableListOf<TextView>()
    val presetVals = listOf(Pair(100L, 0), Pair(1L, 1), Pair(5L, 1))
    val presetNames = listOf("100 ms", "1 dtk", "5 dtk")
    val et = EditText(act)

    fun paintAll() {
        val u = prefs.getInt("unit", 1)
        val iv = prefs.getLong("interval", 1L)
        for (i in unitChips.indices) ui.paintChip(unitChips[i], i == u)
        for (i in presetChips.indices) {
            ui.paintChip(presetChips[i], presetVals[i].first == iv && presetVals[i].second == u)
        }
    }

    et.setText(prefs.getLong("interval", 1L).toString())
    et.inputType = InputType.TYPE_CLASS_NUMBER
    et.gravity = Gravity.CENTER
    et.textSize = 20f
    et.setTextColor(pal.tx)
    et.background = ui.rounded(pal.chip, 12)
    et.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
    et.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            prefs.edit().putLong("interval", s.toString().toLongOrNull() ?: 0L).apply()
            paintAll()
        }
    })

    val row1 = LinearLayout(act)
    row1.orientation = LinearLayout.HORIZONTAL
    row1.gravity = Gravity.CENTER_VERTICAL
    row1.setPadding(0, ui.dp(8), 0, 0)
    row1.addView(et, LinearLayout.LayoutParams(ui.dp(84), ui.WRAP))
    val unitNames = listOf("ms", "dtk", "mnt", "jam")
    for (i in unitNames.indices) {
        val t = ui.chip(unitNames[i])
        t.setOnClickListener {
            prefs.edit().putInt("unit", i).apply()
            paintAll()
        }
        row1.addView(t, ui.lp(ui.WRAP, ui.WRAP, 0, ui.dp(8)))
        unitChips.add(t)
    }
    c1.addView(row1)

    val row2 = LinearLayout(act)
    row2.orientation = LinearLayout.HORIZONTAL
    row2.setPadding(0, ui.dp(12), 0, 0)
    for (i in presetNames.indices) {
        val t = ui.chip(presetNames[i])
        t.setOnClickListener {
            prefs.edit()
                .putLong("interval", presetVals[i].first)
                .putInt("unit", presetVals[i].second)
                .apply()
            et.setText(presetVals[i].first.toString())
            paintAll()
        }
        row2.addView(t, ui.lp(ui.WRAP, ui.WRAP, 0, if (i == 0) 0 else ui.dp(8)))
        presetChips.add(t)
    }
    c1.addView(row2)
    val h1 = ui.text("Isi 0 untuk klik sekali saja. Jeda tiap titik diatur dari tab Urutan.", 12f, pal.sub)
    h1.setPadding(0, ui.dp(12), 0, 0)
    c1.addView(h1)
    paintAll()
    col.addView(c1)

    // ----- batas -----
    val c2 = ui.card("Berhenti otomatis")
    val limChips = mutableListOf<TextView>()
    val limField = EditText(act)
    limField.setText(prefs.getInt("limVal", 100).toString())
    limField.inputType = InputType.TYPE_CLASS_NUMBER
    limField.gravity = Gravity.CENTER
    limField.textSize = 20f
    limField.setTextColor(pal.tx)
    limField.background = ui.rounded(pal.chip, 12)
    limField.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
    limField.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            prefs.edit().putInt("limVal", s.toString().toIntOrNull() ?: 0).apply()
        }
    })
    val limHint = ui.text("", 12f, pal.sub)
    limHint.setPadding(0, ui.dp(10), 0, 0)

    fun paintLim() {
        val m = prefs.getInt("limMode", 0)
        for (i in limChips.indices) ui.paintChip(limChips[i], i == m)
        limField.visibility = if (m == 0) View.GONE else View.VISIBLE
        limHint.text = when (m) {
            1 -> "Berhenti setelah sekian kali klik."
            2 -> "Berhenti setelah sekian menit berjalan."
            else -> "Klik terus sampai kamu menekan tombol berhenti."
        }
    }

    val limRow = LinearLayout(act)
    limRow.orientation = LinearLayout.HORIZONTAL
    limRow.setPadding(0, ui.dp(8), 0, 0)
    val limNames = listOf("Tanpa batas", "Jumlah klik", "Menit")
    for (i in limNames.indices) {
        val t = ui.chip(limNames[i])
        t.setOnClickListener {
            prefs.edit().putInt("limMode", i).apply()
            paintLim()
        }
        limRow.addView(t, ui.lp(ui.WRAP, ui.WRAP, 0, if (i == 0) 0 else ui.dp(8)))
        limChips.add(t)
    }
    c2.addView(limRow)
    c2.addView(limField, ui.lp(ui.dp(110), ui.WRAP, ui.dp(10)))
    c2.addView(limHint)
    paintLim()
    col.addView(c2)

    return TabView(col)
}
