package com.ollie.spudgacha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }
        
        val info = TextView(this).apply {
            text = "1. Izin Overlay (Opsional, buat liat counter)\n2. Aktifkan Aksesibilitas\n3. Tempel nomor (pisah enter) & GAS"
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }

        val inputNumber = EditText(this).apply {
            hint = "628111...\n+628222...\n8..."
            setPadding(0, 16, 0, 16)
            minLines = 5
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

        val btnGas = Button(this).apply {
            text = "3. GAS BATCH GACHA"
            setOnClickListener {
                val rawText = inputNumber.text.toString().trim()
                if (rawText.isNotEmpty()) {
                    val parsedNumbers = rawText.split("\n", ",", " ").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    GachaQueue.numbers.clear()
                    GachaQueue.numbers.addAll(parsedNumbers)
                    
                    Toast.makeText(this@MainActivity, "${GachaQueue.numbers.size} nomor masuk. Buka WA...", Toast.LENGTH_SHORT).show()
                    
                    var intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (intent == null) intent = packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                    
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@MainActivity, "WA gak ketemu", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Nomor kosong", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        layout.addView(info)
        layout.addView(inputNumber)
        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        layout.addView(btnGas)
        setContentView(layout)
    }
}
