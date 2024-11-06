package com.example.projectgestionarmobile.ui.theme.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.example.projectgestionarmobile.ui.theme.util.FloatingMessageWindow
import com.example.projectgestionarmobile.ui.theme.util.SimpleDataManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PhoneNumberDetectorService : AccessibilityService() {
    private var lastDetectedNumber: String = ""
    private var lastProcessedTime: Long = 0
    private val processingDelay = 1000
    private lateinit var floatingMessageWindow: FloatingMessageWindow
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 5
    private var isNewSession = true
    private var sessionId: String = ""
    private val processedSessions = mutableSetOf<String>()
    private var isServiceConnected = false

    override fun onCreate() {
        super.onCreate()
        floatingMessageWindow = FloatingMessageWindow(this)

    }

    private fun generateSessionId(): String {
        return System.currentTimeMillis().toString() + "-" + (0..9999).random().toString().padStart(4, '0')
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        floatingMessageWindow = FloatingMessageWindow(this)
        if (!isServiceConnected) {
            isServiceConnected = true
            Log.d(TAG, "Servicio de accesibilidad conectado por primera vez")
        } else {
            Log.d(TAG, "Servicio de accesibilidad reconectado")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        if (DIALER_PACKAGES.contains(packageName)) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Reiniciar variables para una nueva sesión
                    resetSessionVariables()
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProcessedTime > processingDelay) {
                        processAccessibilityNodeInfo(rootInActiveWindow)
                        lastProcessedTime = currentTime
                    }
                }
            }
        }
    }

    private fun resetSessionVariables() {
        lastDetectedNumber = ""
        retryCount = 0
        isNewSession = true
        sessionId = generateSessionId()
    }

    private fun processAccessibilityNodeInfo(root: AccessibilityNodeInfo?) {
        if (root == null) return

        val number = findPhoneNumber(root)
        if (number != null) {
            handleValidPhoneNumber(number)
        } else if (retryCount < maxRetries) {
            retryCount++
            handler.postDelayed({ processAccessibilityNodeInfo(rootInActiveWindow) }, 500)
        } else {
            notifyNumberNotDetected()
            retryCount = 0
        }
    }

    private fun findPhoneNumber(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        for (id in POSSIBLE_IDS) {
            val nodes = node.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val number = nodes[0].text?.toString()?.trim() ?: ""
                if (isValidPhoneNumber(number)) {
                    return number
                }
            }
        }

        if (node.className == "android.widget.EditText" || node.className == "android.widget.TextView") {
            val text = node.text?.toString()?.trim() ?: ""
            if (isValidPhoneNumber(text)) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val result = findPhoneNumber(node.getChild(i))
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun cleanPhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }

    private fun handleValidPhoneNumber(number: String) {
        if (number != lastDetectedNumber || isNewSession) {
            lastDetectedNumber = number
            val cleanNumber = cleanPhoneNumber(number)

            if (!processedSessions.contains(sessionId)) {
                processedSessions.add(sessionId)
                SimpleDataManager.setPhoneNumber(cleanNumber)
                notifyNumberCaptured("Numero detectado: ", cleanNumber, "#98CE68")
                logAndPrepareForApiCall(cleanNumber)

                handler.postDelayed({
                    closeDialerApp()
                }, 1000)
            }

            isNewSession = false
        }
        retryCount = 0
    }

    private fun logAndPrepareForApiCall(number: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("phone_number", number)
            put("order_id", SimpleDataManager.getScreenShot())
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("http://asd/api/create_order/")
            .post(requestBody)
            .header("Authorization", "Bearer 16d8938ae0dfeaf76d7ee6fec2be56cd5c51fd9e")
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val jsonObject = JSONObject(responseBody)
                        Log.d("Ultima vista", "API call ORDER_ID: ${jsonObject.getString("order_id")}")
                        Log.d("Ultima vista", "API call PHONE_NUMBER: ${jsonObject.getString("phone_number")}")
                    }
                } else {
                    Log.e("Ultima vista", "API call failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("ScreenshotUtil", "Error sending image to API", e)
            }
        }.start()
    }


    private fun closeDialerApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        //Log.d("Objeto a enviar BIS: ", SimpleDataManager.toString())
        SimpleDataManager.setScreenshotText("")
        SimpleDataManager.setPhoneNumber("")
        Log.d("Luego de cerrar todo: ", SimpleDataManager.toString())
    }

    private fun notifyNumberNotDetected() {
        notifyNumberCaptured("Numero NO detectado: ", "N/A", "#F14132")
    }

    private fun isValidPhoneNumber(text: String): Boolean {
        val digitsOnly = text.replace(Regex("[\\s()-]"), "")
        return digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() || it == '+' }
    }

    private fun notifyNumberCaptured(text: String, number: String, color: String) {
        val colorHex = Color.parseColor(color)
        floatingMessageWindow.showMessage("$text: $number", colorHex)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingMessageWindow.destroy()
        isServiceConnected = false
    }

    companion object {
        private const val TAG = "PhoneNumberDetector"
        private val DIALER_PACKAGES = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.xiaomi.miui.dialer",
            "com.samsung.android.dialer"
        )
        private val POSSIBLE_IDS = listOf(
            "com.android.dialer:id/digits",
            "com.android.dialer:id/phone_number_edit_text",
            "com.google.android.dialer:id/digits",
            "com.miui.voiceassist:id/digits",
            "com.xiaomi.voiceassist:id/digits",
            "com.samsung.android.dialer:id/digits"
        )
    }
}