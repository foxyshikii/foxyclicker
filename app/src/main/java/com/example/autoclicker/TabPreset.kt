package com.example.autoclicker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

fun buildPreset(ui: Ui, act: Activity): TabView {
    val pal = ui.pal
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL

    val list = LinearLayout(act)
    list.orientation = LinearLayout.VERTICAL

    fun fill() {
        list.removeAllViews()
        val ps = Store.loadPresets(act)
        if (ps.isEmpty()) {
            val e = ui.text("Belum ada preset. Atur titik dan jadwal, lalu simpan di sini.", 13f, pal.sub)
            e.setPadding(0, ui.dp(14), 0, ui.dp(4))
            list.addView(e)
        }
        for (i in ps.indices) {
            val p = ps[i]
            val row = LinearLayout(act)
            row.orientation = LinearLayout.VERTICAL
            row.background = ui.rounded(pal.card, 14)
            row.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            row.addView(ui.text(p.name, 15f, pal.tx, true))
            val n = Store.parseSteps(p.steps).size
            val ms = p.interval * Store.unitMult(p.unit)
            var sum = n.toString() + " titik"
            if (p.sched) sum += " · jam " + two(p.h) + ":" + two(p.m) + ":" + two(p.s)
            sum += if (ms <= 0L) " · klik sekali" else " · jeda " + fmtMs(ms)
            row.addView(ui.text(sum, 12f, pal.sub))
            row.setOnClickListener {
                Store.applyPreset(act, p)
                OverlayService.instance?.reload()
                Toast.makeText(act, "Dimuat: " + p.name, Toast.LENGTH_SHORT).show()
            }
            row.setOnLongClickListener {
                AlertDialog.Builder(act)
                    .setTitle("Hapus preset?")
                    .setMessage(p.name)
                    .setPositiveButton("Hapus") { _, _ ->
                        ps.removeAt(i)
                        Store.savePresets(act, ps)
                        fill()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
                true
            }
            list.addView(row, ui.lp(ui.MATCH, ui.WRAP, ui.dp(8)))
        }
    }
    fill()

    val head = ui.text("Preset", 12f, pal.sub)
    head.setPadding(0, ui.dp(14), 0, 0)
    col.addView(head)
    col.addView(list)

    val save = ui.text("Simpan susunan sekarang", 15f, Color.WHITE, true)
    save.gravity = Gravity.CENTER
    save.background = ui.rounded(pal.acc, 14)
    save.setOnClickListener {
        val e = EditText(act)
        e.hint = "Nama preset"
        e.setText("Preset " + (Store.loadPresets(act).size + 1))
        val box = LinearLayout(act)
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        box.addView(e, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP))
        AlertDialog.Builder(act)
            .setTitle("Simpan preset")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                val ps = Store.loadPresets(act)
                val nm = e.text.toString().trim()
                ps.add(Store.snapshot(act, if (nm.isEmpty()) "Preset" else nm))
                Store.savePresets(act, ps)
                fill()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    col.addView(save, ui.lp(ui.MATCH, ui.dp(48), ui.dp(14)))
    val hint = ui.text(
        "Tap preset untuk memuat titik, jeda, dan jadwalnya. Tekan lama untuk menghapus.",
        12f, pal.sub
    )
    hint.setPadding(0, ui.dp(10), 0, 0)
    col.addView(hint)

    return TabView(col)
}
