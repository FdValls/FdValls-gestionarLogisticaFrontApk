package com.example.projectgestionarmobile.ui.theme.activities

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.projectgestionarmobile.R
import com.example.projectgestionarmobile.ui.theme.handler.PermissionManager
import com.example.projectgestionarmobile.ui.theme.service.FloatingButtonService

class MainActivity : AppCompatActivity() {
    private val SCREEN_CAPTURE_REQUEST_CODE = 1
    private val OVERLAY_PERMISSION_REQUEST_CODE = 2
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var permissionManager: PermissionManager
    private var closeAppReceiver: BroadcastReceiver? = null
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)

        if (intent.getBooleanExtra("EXIT", false)) {
            isClosing = true
            closeFloatingButtonService()
            finish()
            return
        }

        if (intent.getBooleanExtra("OPEN_SETTINGS", false)) {
            showSettingsDialog()
        } else {
            checkOverlayPermission()
        }

        closeAppReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isClosing = true
                closeFloatingButtonService()
                finish()
            }
        }
        registerReceiver(closeAppReceiver, IntentFilter("com.example.projectgestionarmobile.CLOSE_APP"))
    }

    private fun closeFloatingButtonService() {
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        stopService(serviceIntent)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else {
            permissionManager.setPermissionGranted(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    permissionManager.setPermissionGranted(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    requestScreenCapturePermission()

                } else {
                    Toast.makeText(this, "Permiso de superposición denegado", Toast.LENGTH_SHORT).show()
                }
            }
            SCREEN_CAPTURE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    //permissionManager.setPermissionGranted("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
                    //permissionManager.setPermissionGranted("android.permission.FOREGROUND_SERVICE")
                    startFloatingButtonService(resultCode, data)
                    if (!isClosing) {
                        //promptEnableAccessibilityService("Bienvenido")
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Permiso de captura de pantalla denegado", Toast.LENGTH_SHORT).show()
                    finishAndRemoveTask()
                    kotlin.system.exitProcess(0)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Configuración")
            .setMessage("¿Qué acción desea realizar?")
            .setPositiveButton("Desactivar detección de números") { _, _ ->
                promptEnableAccessibilityService("Por favor, deshabilita el servicio 'PhoneNumberDetector' en la configuración de accesibilidad")

            }
            .setCancelable(false)
            .show()
    }

    private fun promptEnableAccessibilityService(msg: String) {
        if (!isClosing) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            if(msg.equals("Bienvenido")){
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startFloatingButtonService(resultCode: Int, data: Intent?) {
        val serviceIntent = Intent(this, FloatingButtonService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        closeAppReceiver?.let {
            unregisterReceiver(it)
        }
        if (isClosing) {
            closeFloatingButtonService()
        }
    }
}