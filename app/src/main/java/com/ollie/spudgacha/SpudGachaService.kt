package com.ollie.spudgacha

import android.accessibilityservice.AccessibilityService
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
    private val handler = Handler(Looper.getMainLooper())

    // Jaring pengaman
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
            if (GachaQueue.isStopped || GachaQueue.numbers.isEmpty() || GachaQueue.isPaused) {
                handler.postDelayed(this, 2000)
                return
            }

            if (!isProcessing) {
                val root = getWhatsAppRoot()
                if (root != null) {
                    var phoneInput = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull()
                    if (phoneInput == null) {
                        val edits = root.findAccessibilityNodeInfosByViewId("android:id/edit")
                        if (edits != null && edits.isNotEmpty()) phoneInput = edits[0]
                    }

                    // Kalo nemu kolom input, hajar!
                    if (phoneInput != null) {
                        isProcessing = true
                        handler.postDelayed(watchdog, 12000)
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
        if (GachaQueue.numbers.isEmpty() || GachaQueue.isStopped || GachaQueue.isPaused) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        val rawNum = GachaQueue.numbers.removeAt(0)
        val cleanNum = rawNum.replace(" ", "").replace("-", "")
        updateOverlay()

        if (cleanNum.isEmpty()) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        // AUTO PASTE TANPA CLIPBOARD
        phoneInput.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        phoneInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        handler.postDelayed({
            // Bersihin teks lama
            val clearArgs = Bundle()
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            handler.postDelayed({
                // Suntik nomor baru
                val setArgs = Bundle()
                setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cleanNum)
                phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

                handler.postDelayed({
                    val root = getWhatsAppRoot()
                    val nextBtn = root?.findAccessibilityNodeInfosByText("Next")?.firstOrNull()
                        ?: root?.findAccessibilityNodeInfosByText("Berikutnya")?.firstOrNull()
                        ?: root?.findAccessibilityNodeInfosByText("Lanjut")?.firstOrNull()

                    if (nextBtn != null) {
                        nextBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        handler.postDelayed({
                            val statusRoot = getWhatsAppRoot()
                            if (statusRoot != null) detectStatus(statusRoot) else failAndSkip()
                        }, 2500)
                    } else {
                        failAndSkip()
                    }
                }, 800)
            }, 500)
        }, 500)
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            // 1. BANNED (Foto ke 3)
            allText.contains("Akun ini tidak bisa menggunakan WhatsApp", true) || 
            allText.contains("Daftarkan nomor telepon baru", true) -> {
                
                val regBtn = root.findAccessibilityNodeInfosByText("Daftarkan nomor telepon baru")?.firstOrNull()
                if (regBtn != null) {
                    regBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    banCount++
                    updateOverlay()
                    skipToNext("Nomor Banned! Skip...")
                } else {
                    failAndSkip()
                }
            }

            // 2. COOLDOWN / Limit SMS (Foto ke 1)
            allText.contains("Tidak bisa mengirim SMS", true) || 
            allText.contains("baru-baru ini mendaftarkan", true) -> {
                
                val wrongNumBtn = root.findAccessibilityNodeInfosByText("Nomor telepon salah?")?.firstOrNull()
                if (wrongNumBtn != null) {
                    wrongNumBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    cdCount++
                    updateOverlay()
                    skipToNext("Nomor Cooldown! Skip...")
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    cdCount++
                    updateOverlay()
                    handler.postDelayed({ isProcessing = false }, 2000)
                }
            }

            // 3. KONFIRMASI NOMOR (Foto ke 2)
            allText.contains("Apakah ini nomor yang benar", true) -> {
                val yaBtn = root.findAccessibilityNodeInfosByText("Ya")?.firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("Yes")?.firstOrNull()

                if (yaBtn != null) {
                    yaBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val nextRoot = getWhatsAppRoot()
                        if (nextRoot != null) detectStatus(nextRoot) else failAndSkip()
                    }, 3500)
                } else {
                    failAndSkip()
                }
            }

            // 4. SIAP OTP
            allText.contains("Memverifikasi nomor Anda", true) || 
            allText.contains("Verifikasi nomor telepon", true) || 
            allText.contains("kode 6 digit", true) || 
            allText.contains("Memverifikasi nomor", true) -> {
                
                winCount++
                updateOverlay()
                
                GachaQueue.isPaused = true
                isProcessing = false
                handler.removeCallbacks(watchdog) 
                
                handler.post { 
                    Toast.makeText(applicationContext, "Nomor Ready! Masukin OTP manual, lalu balik ke app & pencet LANJUT GACHA.", Toast.LENGTH_LONG).show() 
                }
            }

            // 5. NOMOR INVALID
            allText.contains("bukan nomor yang valid", true) || allText.contains("not a valid", true) -> {
                val okeBtn = root.findAccessibilityNodeInfosByText("Oke")?.firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("OK")?.firstOrNull()
                okeBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                banCount++
                updateOverlay()
                skipToNext("Nomor Invalid! Skip...")
            }

            // 6. LOADING / TIDAK KEDETEKSI
            else -> {
                handler.postDelayed({
                    if (GachaQueue.isPaused) return@postDelayed
                    val nextRoot = getWhatsAppRoot()
                    if (nextRoot != null) detectStatus(nextRoot) else failAndSkip()
                }, 2500)
            }
        }
    }

    private fun skipToNext(msg: String) {
        handler.removeCallbacks(watchdog)
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        
        handler.postDelayed({
            isProcessing = false
        }, 2500)
    }

    private fun failAndSkip() {
        handler.removeCallbacks(watchdog)
        isProcessing = false
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1000)
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
