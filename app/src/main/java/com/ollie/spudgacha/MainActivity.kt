package com.ollie.spudgacha

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }
        
        val info = TextView(this).apply {
            text = "1. Izin Overlay & Aksesibilitas\n2. Tempel nomor & GAS"
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }

        // ScrollView biar tombol di bawah gak kedorong keluar layar
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val inputNumber = EditText(this).apply {
            hint = "+628111...\n+224610...\n8..."
            setPadding(0, 16, 0, 16)
            minLines = 10
        }
        scrollView.addView(inputNumber)

        val btnOverlay = Button(this).apply {
            text = "Izin Overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        
        val btnAccess = Button(this).apply {
            text = "Aktifkan Aksesibilitas"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnGas = Button(this).apply {
            text = "GAS BATCH GACHA"
            setBackgroundColor(Color.parseColor("#388E3C")) // Ijo
            setTextColor(Color.WHITE)
            setOnClickListener {
                val rawText = inputNumber.text.toString().trim()
                if (rawText.isNotEmpty()) {
                    val parsedNumbers = rawText.split("\n", ",", " ").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    GachaQueue.numbers.clear()
                    GachaQueue.numbers.addAll(parsedNumbers)
                    GachaQueue.isStopped = false // Reset status stop
                    
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

        val btnStop = Button(this).apply {
            text = "STOP GACHA"
            setBackgroundColor(Color.parseColor("#D32F2F")) // Merah
            setTextColor(Color.WHITE)
            setOnClickListener {
                GachaQueue.isStopped = true // Sinyal ke service buat berhenti
                GachaQueue.numbers.clear()
                Toast.makeText(this@MainActivity, "Gacha dihentikan.", Toast.LENGTH_SHORT).show()
            }
        }
        
        rootLayout.addView(info)
        rootLayout.addView(scrollView)
        rootLayout.addView(btnOverlay)
        rootLayout.addView(btnAccess)
        rootLayout.addView(btnGas)
        rootLayout.addView(btnStop)
        
        setContentView(rootLayout)
    }
}
