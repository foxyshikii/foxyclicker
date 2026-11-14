package com.example.autoclicker

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout

private fun describe(s: Step): String {
    val t = when (s.type) {
        1 -> "Tahan " + s.durMs + " md"
        2 -> "Geser " + s.durMs + " md"
        else -> "Ketuk"
    }
    val d = if (s.delayMs >= 0) "jeda " + fmtMs(s.delayMs) else "jeda umum"
    return t + " · " + d
}

fun buildUrutan(ui: Ui, act: Activity): TabView {
    val pal = ui.pal
    val steps = Store.loadSteps(act)
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL

    val dial = ui.card("")
    dial.gravity = Gravity.CENTER_HORIZONTAL
    val ring = RingView(act, pal.acc, pal.off)
    dial.addView(ring, LinearLayout.LayoutParams(ui.dp(156), ui.dp(156)))
    val big = ui.text("", 17f, pal.acc, true)
    big.gravity = Gravity.CENTER
    val small = ui.text("", 11f, pal.sub)
    small.gravity = Gravity.CENTER
    dial.addView(big, ui.lp(ui.WRAP, ui.WRAP, ui.dp(6)))
    dial.addView(small)
    col.addView(dial)

    val list = LinearLayout(act)
    list.orientation = LinearLayout.VERTICAL
    col.addView(list, ui.lp(ui.MATCH, ui.WRAP, ui.dp(6)))

    fun commit() {
        Store.saveSteps(act, steps)
        OverlayService.instance?.reload()
    }

    fun fill() {
        list.removeAllViews()
        for (i in steps.indices) {
            val s = steps[i]
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = ui.rounded(pal.card, 14)
            row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))

            val badge = ui.text((i + 1).toString(), 14f, pal.acc, true)
            badge.gravity = Gravity.CENTER
            badge.background = ui.rounded(pal.tint, 17)
            row.addView(badge, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)))

            val info = LinearLayout(act)
            info.orientation = LinearLayout.VERTICAL
            info.addView(ui.text("Titik " + (i + 1), 14f, pal.tx, true))
            info.addView(ui.text(describe(s), 11f, pal.sub))
            val ilp = LinearLayout.LayoutParams(0, ui.WRAP, 1f)
            ilp.leftMargin = ui.dp(12)
            row.addView(info, ilp)

            val tg = Toggle(act, pal.acc, pal.off)
            tg.checked = s.enabled
            tg.onChange = {
                s.enabled = it
                commit()
            }
            row.addView(tg, LinearLayout.LayoutParams(ui.dp(44), ui.dp(24)))

            row.setOnClickListener {
                val d = Dialogs.stepDialog(act, i + 1, s, steps.size > 1, {
                    commit()
                    fill()
                }, {
                    steps.removeAt(i)
                    commit()
                    fill()
                })
                Dialogs.show(d, false)
            }
            list.addView(row, ui.lp(ui.MATCH, ui.WRAP, ui.dp(8)))
        }
    }
    fill()

    val add = ui.text("Tambah titik", 15f, Color.WHITE, true)
    add.gravity = Gravity.CENTER
    add.background = ui.rounded(pal.acc, 14)
    add.setOnClickListener {
        if (steps.size < 20) {
            val l = steps[steps.size - 1]
            steps.add(Step(l.x, l.y + ui.dp(64)))
            commit()
            fill()
        }
    }
    col.addView(add, ui.lp(ui.MATCH, ui.dp(48), ui.dp(14)))
    val hint = ui.text(
        "Tap baris untuk mengubah jenis klik dan jeda. Posisi titik digeser lewat panel melayang.",
        12f, pal.sub
    )
    hint.setPadding(0, ui.dp(10), 0, 0)
    col.addView(hint)

    fun tick() {
        if (Store.schedOn(act)) {
            val rem = Store.nextSchedule(act) - System.currentTimeMillis()
            ring.progress = 1f - rem / 86_400_000f
            val sec = rem / 1000L
            big.text = if (sec >= 3600L) {
                "-" + two((sec / 3600L).toInt()) + ":" + two(((sec % 3600L) / 60L).toInt()) + " jam"
            } else {
                "-" + two((sec / 60L).toInt()) + ":" + two((sec % 60L).toInt()) + " mnt"
            }
            val p = Store.prefs(act)
            small.text = "menuju " + two(p.getInt("h", 7)) + ":" + two(p.getInt("m", 55)) + ":" +
                two(p.getInt("s", 58))
        } else {
            ring.progress = 0f
            big.text = "Tanpa jadwal"
            small.text = "Klik mulai saat tombol ▶ ditekan"
        }
    }
    tick()

    return TabView(col) { tick() }
}
