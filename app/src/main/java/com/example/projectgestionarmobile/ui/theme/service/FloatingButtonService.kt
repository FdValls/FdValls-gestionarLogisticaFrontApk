package com.example.projectgestionarmobile.ui.theme.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.projectgestionarmobile.R
import com.example.projectgestionarmobile.ui.theme.activities.MainActivity
import com.example.projectgestionarmobile.ui.theme.util.FloatingMessageWindow
import com.example.projectgestionarmobile.ui.theme.util.ScreenshotUtil
import kotlin.system.exitProcess

class FloatingButtonService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingMessageWindow: FloatingMessageWindow
    private lateinit var floatingButton: View
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isLongPress = false
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val longPressRunnable = Runnable {
        isLongPress = true
        // Programamos el cierre de la app 2 segundos después del mensaje
        handler.postDelayed({
            stopSelf() // Si es un servicio
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }, 2000)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data: Intent? = it.getParcelableExtra("data")

            if (resultCode != Activity.RESULT_CANCELED && data != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                setupVirtualDisplay()
            }
        }
        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        mediaProjection?.let { projection ->
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingButton() {
        if (::floatingButton.isInitialized && floatingButton.parent != null) {
            Log.d("FloatingButtonService", "El botón flotante ya está inicializado y agregado a la vista.")
            return
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingButton = inflater.inflate(R.layout.floating_button_layout, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        // Establecer la gravedad para que esté en la esquina inferior derecha
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END

        windowManager.addView(floatingButton, layoutParams)

        val button = floatingButton.findViewById<Button>(R.id.floating_button)


        button.setOnClickListener {
            if (!isLongPress) {
                takeScreenshot()
            }
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    handler.postDelayed(longPressRunnable, 3000)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isLongPress) {
                        takeScreenshot()
                    }
                }
            }
            true
        }
    }

    /*
    private fun showExitDialog() {
        val inflater = this.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_exit, null)

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setView(dialogView)
            .setCancelable(false)

        val alert = dialogBuilder.create()
        alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        val positiveButton = dialogView.findViewById<Button>(R.id.positive_button)
        val negativeButton = dialogView.findViewById<Button>(R.id.negative_button)


        positiveButton.setOnClickListener {
            stopSelf() // Detén el servicio
            android.os.Process.killProcess(android.os.Process.myPid()) // Mata el proceso de la aplicación
            exitProcess(0)
        }

        negativeButton.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }
     */


    private fun takeScreenshot() {
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            ScreenshotUtil.processImage(image, this, windowManager.defaultDisplay)
        } else {
            Log.e("FloatingButtonService", "Failed to acquire image")
            // Mostrar un mensaje de error al usuario
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Floating Button Service"
            val descriptionText = "Enables screenshot capture"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Service")
            .setContentText("Tap to capture screenshot")
            .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este icono en tus recursos
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(floatingButton)
        } catch (e: IllegalArgumentException) {
            Log.e("FloatingButtonService", "Floating button view already removed", e)
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FloatingButtonChannel"
    }


}