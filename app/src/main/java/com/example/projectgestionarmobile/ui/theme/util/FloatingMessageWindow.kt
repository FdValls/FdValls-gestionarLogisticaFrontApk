package com.example.projectgestionarmobile.ui.theme.util

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.projectgestionarmobile.R

class FloatingMessageWindow(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_message, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params?.gravity = Gravity.CENTER
    }

    fun setBackgroundColor(color: Int) {
        floatingView?.findViewById<TextView>(R.id.messageText)?.setBackgroundColor(color)
    }

    fun showMessage(message: String, backgroundColor: Int, durationMillis: Long = 2000) {
        handler.removeCallbacksAndMessages(null)

        handler.post {
            val textView = floatingView?.findViewById<TextView>(R.id.messageText)
            textView?.text = message
            textView?.setBackgroundColor(backgroundColor)
            if (!isShowing) {
                try {
                    windowManager?.addView(floatingView, params)
                    isShowing = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            handler.postDelayed({
                hideMessage()
            }, durationMillis)
        }
    }

    private fun hideMessage() {
        handler.post {
            if (isShowing) {
                try {
                    windowManager?.removeView(floatingView)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isShowing = false
                }
            }
        }
    }

    fun destroy() {
        hideMessage()
        windowManager = null
        floatingView = null
    }
}