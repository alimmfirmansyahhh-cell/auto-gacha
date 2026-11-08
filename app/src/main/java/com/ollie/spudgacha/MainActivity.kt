package com.ollie.spudgacha

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }
        
        val info = TextView(this).apply {
            text = "1. Izinkan Overlay\n2. Aktifkan Accessibility\n3. Copy nomor, buka WhatsApp, tempel manual atau biarin auto."
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }
        val btnOverlay = Button(this).apply {
            text = "1. Izin Overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val btnAccess = Button(this).apply {
            text = "2. Aktifkan Aksesibilitas"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        
        layout.addView(info)
        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        setContentView(layout)
    }
}
