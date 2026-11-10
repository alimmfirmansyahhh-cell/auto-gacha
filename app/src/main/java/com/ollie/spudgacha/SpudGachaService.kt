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
    private val handler = Handler(Looper.getMainLooper())

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
            // Cek kalo digas atau distop
            if (GachaQueue.isStopped || GachaQueue.numbers.isEmpty()) {
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

                    if (phoneInput != null) {
                        isProcessing = true
                        handler.postDelayed(watchdog, 10000) // 10 detik timeout
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
        if (GachaQueue.numbers.isEmpty() || GachaQueue.isStopped) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        val rawNum = GachaQueue.numbers.removeAt(0)
        // Biarin plusnya, cuma hapus spasi sama strip
        val cleanNum = rawNum.replace(" ", "").replace("-", "")
        updateOverlay()

        if (cleanNum.isEmpty()) {
            isProcessing = false
            handler.removeCallbacks(watchdog)
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("num", cleanNum))

        phoneInput.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        phoneInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        handler.postDelayed({
            val clearArgs = Bundle()
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            handler.postDelayed({
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
                        // Tunggu hasil
                        handler.postDelayed({
                            val statusRoot = getWhatsAppRoot()
                            if (statusRoot != null) detectStatus(statusRoot) else failAndSkip()
                        }, 5000)
                    } else {
                        failAndSkip()
                    }
                }, 1000)
            }, 500)
        }, 500)
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        handler.removeCallbacks(watchdog)
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            allText.contains("bukan nomor yang valid", true) || allText.contains("not a valid", true) -> {
                val okeBtn = root.findAccessibilityNodeInfosByText("Oke")?.firstOrNull()
                    ?: root.findAccessibilityNodeInfosByText("OK")?.firstOrNull()
                okeBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                skipToNext("Nomor Sampah/Invalid! Skip...")
            }
            allText.contains("diblokir", true) || allText.contains("banned", true) || allText.contains("blocked", true) -> {
                banCount++; updateOverlay(); skipToNext("Banned! Skip...")
            }
            allText.contains("terlalu banyak", true) || allText.contains("too many", true) || allText.contains("coba lagi", true) || allText.contains("cooldown", true) -> {
                cdCount++; updateOverlay(); skipToNext("Cooldown! Skip...")
            }
            allText.contains("verifikasi", true) || allText.contains("verify", true) || allText.contains("kode", true) || allText.contains("code", true) -> {
                winCount++; updateOverlay()
                isProcessing = false
                handler.post { Toast.makeText(applicationContext, "Nomor Ready! Masukin OTP.", Toast.LENGTH_LONG).show() }
            }
            else -> {
                skipToNext("Gagal deteksi, skip...")
            }
        }
    }

    private fun skipToNext(msg: String) {
        handler.removeCallbacks(watchdog)
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
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
