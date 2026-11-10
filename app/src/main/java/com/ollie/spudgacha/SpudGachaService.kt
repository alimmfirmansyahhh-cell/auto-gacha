package com.ollie.spudgacha

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
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
    private var isPaused = false
    private val handler = Handler(Looper.getMainLooper())

    // Jaring Pengaman kalo WA bener-bener ngambek
    private val watchdog = Runnable {
        if (isProcessing) {
            isProcessing = false
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1000)
            Toast.makeText(this@SpudGachaService, "Timeout! Lanjut nomor berikutnya.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isProcessing && !isPaused && GachaQueue.numbers.isNotEmpty()) {
                val root = getWhatsAppRoot()
                if (root != null) {
                    // Cari kolom input
                    var phoneInput = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull()
                    
                    // Fallback kalo ID beda
                    if (phoneInput == null) {
                        val edits = root.findAccessibilityNodeInfosByViewId("android:id/edit")
                        if (edits != null && edits.isNotEmpty()) phoneInput = edits[0]
                    }

                    if (phoneInput != null) {
                        isProcessing = true
                        handler.postDelayed(watchdog, 8000)
                        handleNumber(phoneInput)
                    }
                }
            }
            handler.postDelayed(this, 1500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
        handler.post(pollRunnable)
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

    private fun getWhatsAppRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && (active.packageName == "com.whatsapp" || active.packageName == "com.whatsapp.w4b")) return active
        for (window in windows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString()
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") return root
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    private fun handleNumber(phoneInput: AccessibilityNodeInfo) {
        if (GachaQueue.numbers.isEmpty()) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        val rawNum = GachaQueue.numbers.removeAt(0)
        val cleanNum = rawNum.replace("[^0-9]".toRegex(), "")
        updateOverlay()

        if (cleanNum.isEmpty()) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        // Kasih +
        val formattedNum = "+$cleanNum"

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("num", formattedNum))

        phoneInput.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        phoneInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        handler.postDelayed({
            val clearArgs = Bundle()
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            handler.postDelayed({
                val setArgs = Bundle()
                setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, formattedNum)
                phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

                handler.postDelayed({
                    val root = getWhatsAppRoot()
                    val nextBtn = root?.findAccessibilityNodeInfosByText("Next")?.firstOrNull()
                        ?: root?.findAccessibilityNodeInfosByText("Berikutnya")?.firstOrNull()
                        ?: root?.findAccessibilityNodeInfosByText("Lanjut")?.firstOrNull()

                    nextBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    // Tunggu hasil (loading WA)
                    handler.postDelayed({
                        val statusRoot = getWhatsAppRoot()
                        if (statusRoot != null) detectStatus(statusRoot) else failAndSkip()
                    }, 4500)
                }, 1000)
            }, 500)
        }, 500)
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        handler.removeCallbacks(watchdog)
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            // Deteksi Nomor Gak Valid (Pop-up Oke)
            allText.contains("bukan nomor yang valid", true) || allText.contains("not a valid", true) -> {
                // Klik Oke buat tutup dialog
                val okeBtn = root.findAccessibilityNodeInfosByText("Oke")?.firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("OK")?.firstOrNull()
                okeBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                skipToNext("Nomor Sampah/Invalid! Skip...")
            }
            // Deteksi Banned
            allText.contains("diblokir", true) || allText.contains("banned", true) || allText.contains("blocked", true) -> {
                banCount++; updateOverlay(); skipToNext("Banned! Skip...")
            }
            // Deteksi Cooldown
            allText.contains("terlalu banyak", true) || allText.contains("too many", true) || allText.contains("coba lagi", true) || allText.contains("cooldown", true) -> {
                cdCount++; updateOverlay(); skipToNext("Cooldown! Skip...")
            }
            // Deteksi Siap OTP
            allText.contains("verifikasi", true) || allText.contains("verify", true) || allText.contains("kode", true) || allText.contains("code", true) -> {
                winCount++; updateOverlay()
                isPaused = true; isProcessing = false
                handler.post { Toast.makeText(applicationContext, "Nomor Ready! Masukin OTP.", Toast.LENGTH_LONG).show() }
            }
            else -> {
                // Kalo gak kedeteksi apa-apa, anggap aja skip
                skipToNext("Gagal deteksi status, skip...")
            }
        }
    }

    private fun skipToNext(msg: String) {
        handler.removeCallbacks(watchdog)
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        
        // Pencet Back buat nutup keyboard / balik ke form
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            // Kadang WA perlu 2x Back (misal abis tutup popup)
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ isProcessing = false }, 1500)
        }, 1500)
    }

    private fun failAndSkip() {
        skipToNext("Error UI. Skip...")
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
