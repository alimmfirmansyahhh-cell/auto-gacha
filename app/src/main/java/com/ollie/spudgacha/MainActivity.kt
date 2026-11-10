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
            text = "1. Izinkan Overlay\n2. Aktifkan Aksesibilitas\n3. Buka WA/WA Bisnis, isi nomor di OVERLAY melayang."
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

        val btnOpenWa = Button(this).apply {
            text = "3. Buka WhatsApp"
            setOnClickListener {
                var intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent == null) {
                    intent = packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                }
                if (intent != null) {
                    startActivity(intent)
                }
            }
        }
        
        layout.addView(info)
        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        layout.addView(btnOpenWa)
        setContentView(layout)
    }
}
