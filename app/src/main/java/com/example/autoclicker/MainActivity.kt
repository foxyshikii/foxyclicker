package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("p", MODE_PRIVATE)

        fun field(hint: String, def: String) = EditText(this).apply {
            this.hint = hint; setText(def)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val interval = field("Interval (0 = klik sekali)", p.getLong("interval", 1).toString())
        val unit = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Milidetik", "Detik", "Menit", "Jam"))
            setSelection(p.getInt("unit", 1))
        }
        val pakaiJadwal = CheckBox(this).apply {
            text = "Mulai di jam tertentu"; isChecked = p.getBoolean("sched", false)
        }
        val jam = field("Jam (0-23)", p.getInt("h", 7).toString())
        val menit = field("Menit", p.getInt("m", 55).toString())
        val detik = field("Detik", p.getInt("s", 58).toString())

        val izin = Button(this).apply { text = "1. Izinkan tampil di atas app lain" }
        val akses = Button(this).apply { text = "2. Aktifkan Accessibility" }
        val tampil = Button(this).apply { text = "3. Tampilkan Panel Melayang" }
        val tutup = Button(this).apply { text = "Tutup Panel" }

        izin.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        akses.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        tampil.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Beri izin overlay dulu (langkah 1)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            p.edit()
                .putLong("interval", interval.text.toString().toLongOrNull() ?: 0)
                .putInt("unit", unit.selectedItemPosition)
                .putBoolean("sched", pakaiJadwal.isChecked)
                .putInt("h", jam.text.toString().toIntOrNull() ?: 0)
                .putInt("m", menit.text.toString().toIntOrNull() ?: 0)
                .putInt("s", detik.text.toString().toIntOrNull() ?: 0)
                .apply()
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Panel muncul. Geser titik merah ke tempat klik.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
        tutup.setOnClickListener { stopService(Intent(this, OverlayService::class.java)) }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            listOf(interval, unit, pakaiJadwal, jam, menit, detik, izin, akses, tampil, tutup)
                .forEach { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, -2)) }
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
