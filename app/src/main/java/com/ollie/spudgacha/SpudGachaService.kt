package com.ollie.spudgacha

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast

class SpudGachaService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private var winCount = 0
    private var cdCount = 0
    private var banCount = 0
    private var isProcessing = false
    private var isPaused = false // Berhenti kalo dapet OTP
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        overlayView = TextView(this).apply {
            text = "0 Ready · 0 CD · 0 Ban\nSisa: 0"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(32, 16, 32, 16)
            textSize = 14f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        windowManager.addView(overlayView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        if (event.packageName != "com.whatsapp" || isProcessing || isPaused) return

        // Antrean kosong? Berhenti.
        if (GachaQueue.numbers.isEmpty()) return

        // Cari kolom input nomor
        val phoneInput = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull()
        
        // Kadang WA ngehapus teks lama, kita paksa masuk aja kalo field-nya ada
        if (phoneInput != null) {
            isProcessing = true
            handleNumberInput(root, phoneInput)
        }
    }

    private fun handleNumberInput(root: AccessibilityNodeInfo, phoneInput: AccessibilityNodeInfo) {
        if (GachaQueue.numbers.isEmpty()) {
            isProcessing = false
            return
        }

        val currentNumber = GachaQueue.numbers.removeAt(0) // Ambil antrean terdepan
        updateOverlay()

        if (currentNumber.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Number", currentNumber))

            // Set teks ke kolom input
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentNumber)
            phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Klik Next setelah 500ms
            handler.postDelayed({
                val nextBtn = root.findAccessibilityNodeInfosByText("Next").firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("Berikutnya").firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("Lanjut").firstOrNull()
                
                nextBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                // Tunggu loading cek status (4 detik)
                handler.postDelayed({
                    detectStatus(root)
                }, 4000)
            }, 500)
        } else {
            isProcessing = false
        }
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            // 1. BANNED
            allText.contains("diblokir", ignoreCase = true) || allText.contains("banned", ignoreCase = true) || allText.contains("blocked", ignoreCase = true) -> {
                banCount++
                updateOverlay()
                skipToNextNumber("Nomor Banned! Skip...")
            }
            // 2. COOLDOWN
            allText.contains("terlalu banyak", ignoreCase = true) || allText.contains("too many", ignoreCase = true) || allText.contains("coba lagi", ignoreCase = true) || allText.contains("cooldown", ignoreCase = true) -> {
                cdCount++
                updateOverlay()
                skipToNextNumber("Nomor Cooldown! Skip...")
            }
            // 3. READY (OTP)
            allText.contains("verifikasi", ignoreCase = true) || allText.contains("verify", ignoreCase = true) || allText.contains("kode", ignoreCase = true) || allText.contains("code", ignoreCase = true) -> {
                winCount++
                updateOverlay()
                
                // PAUSE AUTOMATION
                isPaused = true
                isProcessing = false
                
                handler.post {
                    Toast.makeText(applicationContext, "Nomor Ready! Silakan ketik OTP manual, lalu buka SpudGacha buat lanjut.", Toast.LENGTH_LONG).show()
                }
            }
            // 4. Gagal deteksi (UI berubah / Blank)
            else -> {
                // Kalo 4 detik gak ada apa-apa, anggap skip
                skipToNextNumber("Gagal deteksi status, lanjut nomor berikutnya...")
            }
        }
    }

    private fun skipToNextNumber(msg: String) {
        handler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
        
        // Pencet tombol Back buat nutup dialog error (kalo ada)
        performGlobalAction(GLOBAL_ACTION_BACK)
        
        // Jeda bentar biar UI WA balik ke form input
        handler.postDelayed({
            // Pencet Back lagi buat mastiin form input kosong (kalo perlu)
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                isProcessing = false
                // onAccessibilityEvent bakal otomatis nangkep event berikutnya dan masang nomor baru
            }, 1000)
        }, 1000)
    }

    private fun updateOverlay() {
        overlayView.text = "$winCount Ready · $cdCount CD · $banCount Ban\nSisa: ${GachaQueue.numbers.size}"
    }

    override fun onInterrupt() {
        if (this::windowManager.isInitialized && this::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        handler.removeCallbacksAndMessages(null)
    }
}
