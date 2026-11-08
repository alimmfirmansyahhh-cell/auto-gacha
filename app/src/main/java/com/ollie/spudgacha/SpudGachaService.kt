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

class SpudGachaService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private var winCount = 0
    private var cdCount = 0
    private var banCount = 0
    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        overlayView = TextView(this).apply {
            text = "0 Win · 0 CD · 0 Ban"
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
        if (event.packageName != "com.whatsapp" || isProcessing) return

        // Deteksi layar input nomor
        val phoneInput = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull()
        if (phoneInput != null) {
            isProcessing = true
            handleNumberInput(root, phoneInput)
        }
    }

    private fun handleNumberInput(root: AccessibilityNodeInfo, phoneInput: AccessibilityNodeInfo) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val number = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        if (number.isNotEmpty()) {
            // Tempel nomor
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, number)
            phoneInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Cari tombol Next
            val nextBtn = root.findAccessibilityNodeInfosByText("Next").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("Berikutnya").firstOrNull()
            
            nextBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // Beri waktu WhatsApp loading cek nomor (3 detik)
            handler.postDelayed({
                detectStatus(root)
                isProcessing = false
            }, 3000)
        } else {
            isProcessing = false
        }
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            allText.contains("banned", ignoreCase = true) || allText.contains("blocked", ignoreCase = true) -> {
                banCount++
                updateOverlay()
            }
            allText.contains("too many", ignoreCase = true) || allText.contains("try again later", ignoreCase = true) || allText.contains("cooldown", ignoreCase = true) -> {
                cdCount++
                updateOverlay()
            }
            allText.contains("verification", ignoreCase = true) || allText.contains("verify", ignoreCase = true) || allText.contains("code", ignoreCase = true) -> {
                winCount++
                updateOverlay()
            }
        }
    }

    private fun updateOverlay() {
        overlayView.text = "$winCount Win · $cdCount CD · $banCount Ban"
    }

    override fun onInterrupt() {
        if (this::windowManager.isInitialized && this::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        handler.removeCallbacksAndMessages(null)
    }
}
