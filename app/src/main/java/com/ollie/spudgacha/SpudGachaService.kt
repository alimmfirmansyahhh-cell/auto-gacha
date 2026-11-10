package com.ollie.spudgacha

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SpudGachaService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: LinearLayout
    private lateinit var statsView: TextView
    private lateinit var inputView: EditText
    private var winCount = 0
    private var cdCount = 0
    private var banCount = 0
    private var isProcessing = false
    private var isPaused = false
    private val handler = Handler(Looper.getMainLooper())

    // Jaring Pengaman: Kalo 8 detik gak kelar-kelar, paksa skip
    private val forceResetRunnable = Runnable {
        if (isProcessing) {
            isProcessing = false
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this@SpudGachaService, "Stuck! Skip ke nomor berikutnya.", Toast.LENGTH_SHORT).show()
        }
    }

    private val countryMap = mapOf(
        "62" to "Indonesia", "60" to "Malaysia", "65" to "Singapore",
        "66" to "Thailand", "63" to "Philippines", "84" to "Vietnam",
        "91" to "India", "1" to "United States", "44" to "United Kingdom",
        "7" to "Russia", "86" to "China", "81" to "Japan", "82" to "South Korea",
        "90" to "Turkey", "92" to "Pakistan", "880" to "Bangladesh",
        "234" to "Nigeria", "27" to "South Africa", "94" to "Sri Lanka"
    )

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isProcessing && !isPaused && GachaQueue.numbers.isNotEmpty()) {
                val root = getWhatsAppRoot()
                if (root != null) {
                    val phoneInput = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull()
                    if (phoneInput != null && phoneInput.text.isNullOrEmpty()) {
                        isProcessing = true
                        // Pasang timer pengaman
                        handler.postDelayed(forceResetRunnable, 8000)
                        
                        val nextNum = GachaQueue.numbers.removeAt(0).replace("[^0-9]".toRegex(), "")
                        updateOverlay()
                        if (nextNum.isNotEmpty()) handleNumberInput(root, nextNum)
                    }
                }
            }
            handler.postDelayed(this, 1200)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
        handler.post(pollRunnable)
    }

    private fun setupOverlay() {
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(32, 32, 32, 32)
        }

        statsView = TextView(this).apply {
            text = "0 Ready · 0 CD · 0 Ban\nSisa: 0"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        inputView = EditText(this).apply {
            hint = "Tempel nomor (+62 / 62 / 8...)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 12f
            minLines = 2
            maxLines = 5
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val btnRun = Button(this).apply {
            text = "GAS RUN"
            setOnClickListener {
                val rawText = inputView.text.toString().trim()
                if (rawText.isNotEmpty()) {
                    val parsedNumbers = rawText.split("\n", ",", " ").map { it.trim() }.filter { it.isNotEmpty() }
                    GachaQueue.numbers.clear()
                    GachaQueue.numbers.addAll(parsedNumbers)
                    updateOverlay()
                    
                    isPaused = false
                    isProcessing = false
                    inputView.setText("") 
                    
                    inputView.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(inputView.windowToken, 0)

                    Toast.makeText(this@SpudGachaService, "${GachaQueue.numbers.size} nomor masuk. Buka WA.", Toast.LENGTH_SHORT).show()
                    
                    var intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (intent == null) intent = packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
            }
        }

        val btnStop = Button(this).apply {
            text = "MATIKAN"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                GachaQueue.numbers.clear()
                isPaused = true
                isProcessing = false
                handler.removeCallbacksAndMessages(null)
                
                if (this@SpudGachaService::windowManager.isInitialized && this@SpudGachaService::overlayView.isInitialized) {
                    windowManager.removeView(overlayView)
                }
                
                Toast.makeText(this@SpudGachaService, "Robot OFF.", Toast.LENGTH_SHORT).show()
                disableSelf()
            }
        }

        val paramsRun = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
        val paramsStop = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) }
        
        buttonLayout.addView(btnRun, paramsRun)
        buttonLayout.addView(btnStop, paramsStop)

        overlayView.addView(statsView)
        overlayView.addView(inputView)
        overlayView.addView(buttonLayout)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        windowManager.addView(overlayView, params)
    }

    // PINTER NYARI WINDOW (Bisa nembus overlay)
    private fun getWhatsAppRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && (active.packageName == "com.whatsapp" || active.packageName == "com.whatsapp.w4b")) {
            return active
        }
        for (window in windows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString()
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") return root
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) { }

    private fun handleNumberInput(root: AccessibilityNodeInfo, cleanNumber: String) {
        val countryCode = extractCountryCode(cleanNumber)
        val countryName = if (countryCode != null) countryMap[countryCode] else null
        val localNumber = if (countryCode != null) cleanNumber.removePrefix(countryCode) else cleanNumber

        if (countryName != null) {
            val countryBtn = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_country")?.firstOrNull()
            if (countryBtn != null) {
                countryBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                handler.postDelayed({
                    val searchRoot = getWhatsAppRoot()
                    val searchNode = searchRoot?.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_input")?.firstOrNull()
                        ?: searchRoot?.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_src_text")?.firstOrNull()

                    if (searchNode != null) {
                        val args = Bundle()
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, countryName)
                        searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                        handler.postDelayed({
                            val listRoot = getWhatsAppRoot()
                            val resultList = listRoot?.findAccessibilityNodeInfosByText(countryName)
                            val firstItem = resultList?.firstOrNull { it.isClickable || it.parent?.isClickable == true }
                            
                            if (firstItem != null) {
                                if (firstItem.isClickable) firstItem.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                else firstItem.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            
                            handler.postDelayed({ pasteNumber(localNumber) }, 1500)
                        }, 1200)
                    } else {
                        pasteNumber(cleanNumber)
                    }
                }, 1000)
            } else {
                pasteNumber(cleanNumber)
            }
        } else {
            pasteNumber(cleanNumber)
        }
    }

    private fun pasteNumber(number: String) {
        // Ambil root fresh (jangan pake node usang)
        val freshRoot = getWhatsAppRoot() ?: return failAndSkip()
        val freshInput = freshRoot.findAccessibilityNodeInfosByViewId("com.whatsapp:id/registration_phone")?.firstOrNull() ?: return failAndSkip()

        freshInput.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        freshInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        handler.postDelayed({
            // Bersihin field
            val clearArgs = Bundle()
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            freshInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            handler.postDelayed({
                // Tempel Nomor
                val setArgs = Bundle()
                setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, number)
                freshInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

                // Klik Next
                handler.postDelayed({
                    val nextRoot = getWhatsAppRoot()
                    val nextBtn = nextRoot?.findAccessibilityNodeInfosByText("Next")?.firstOrNull()
                        ?: nextRoot?.findAccessibilityNodeInfosByText("Berikutnya")?.firstOrNull()
                        ?: nextRoot?.findAccessibilityNodeInfosByText("Lanjut")?.firstOrNull()

                    nextBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    handler.postDelayed({
                        val statusRoot = getWhatsAppRoot()
                        if (statusRoot != null) detectStatus(statusRoot) else failAndSkip()
                    }, 5000) // 5 detik nunggu WA ngecek
                }, 1000)
            }, 500)
        }, 500)
    }

    private fun failAndSkip() {
        handler.removeCallbacks(forceResetRunnable)
        isProcessing = false
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1000)
    }

    private fun extractCountryCode(number: String): String? {
        for (len in 3 downTo 1) {
            if (number.length >= len) {
                val prefix = number.substring(0, len)
                if (countryMap.containsKey(prefix)) return prefix
            }
        }
        return null
    }

    private fun detectStatus(root: AccessibilityNodeInfo) {
        handler.removeCallbacks(forceResetRunnable) // Matiin alarm
        val allText = root.findAccessibilityNodeInfosByText("").mapNotNull { it.text?.toString() }.joinToString(" ")

        when {
            allText.contains("diblokir", ignoreCase = true) || allText.contains("banned", ignoreCase = true) || allText.contains("blocked", ignoreCase = true) -> {
                banCount++; updateOverlay(); skipToNextNumber("Banned! Skip...")
            }
            allText.contains("terlalu banyak", ignoreCase = true) || allText.contains("too many", ignoreCase = true) || allText.contains("coba lagi", ignoreCase = true) || allText.contains("cooldown", ignoreCase = true) -> {
                cdCount++; updateOverlay(); skipToNextNumber("Cooldown! Skip...")
            }
            allText.contains("verifikasi", ignoreCase = true) || allText.contains("verify", ignoreCase = true) || allText.contains("kode", ignoreCase = true) || allText.contains("code", ignoreCase = true) -> {
                winCount++; updateOverlay()
                isPaused = true; isProcessing = false
                handler.post { Toast.makeText(applicationContext, "Nomor Ready! Masukin OTP.", Toast.LENGTH_LONG).show() }
            }
            else -> {
                skipToNextNumber("Gagal deteksi, skip...")
            }
        }
    }

    private fun skipToNextNumber(msg: String) {
        handler.removeCallbacks(forceResetRunnable)
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ isProcessing = false }, 1500)
        }, 1500)
    }

    private fun updateOverlay() {
        statsView.text = "$winCount Ready · $cdCount CD · $banCount Ban\nSisa: ${GachaQueue.numbers.size}"
    }

    override fun onInterrupt() {
        if (this::windowManager.isInitialized && this::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        handler.removeCallbacksAndMessages(null)
    }
}
