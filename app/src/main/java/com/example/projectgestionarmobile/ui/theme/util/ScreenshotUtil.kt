package com.example.projectgestionarmobile.ui.theme.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Display
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import com.example.projectgestionarmobile.R
import com.example.projectgestionarmobile.ui.theme.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*

object ScreenshotUtil {
    private lateinit var floatingMessageWindow: FloatingMessageWindow
    private var base64Image: String? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var flagCapturePhone: Boolean = false

    private suspend fun sendImageToApi(base64Image: String?, context: Context): Pair<Boolean, String?> {
        if (base64Image == null) {
            return Pair(false, "Base64 image is null")
        }

        val client = OkHttpClient()
        val request: Request

        try {
            // Modularizamos el mensaje dependiendo del endpoint
            val (url, json) = prepareRequestData(base64Image)

            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer 16d8938ae0dfeaf76d7ee6fec2be56cd5c51fd9e")
                .post(requestBody)
                .build()

            return withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string())
                    if(!flagCapturePhone){
                        flagCapturePhone = true
                        SimpleDataManager.setScreenshotText(jsonObject.getString("id_number"))
                        Log.d("ScreenshotID", "Mercado libre response: ${jsonObject.getString("id_number")}")
                        Log.d("Flag luego de capturar", "flagCapturePhone: $flagCapturePhone")
                    }
                    else{
                        Log.d("Capturo al menos una vez...", "Capturo al menos una vez...")
                        SimpleDataManager.setPhoneNumber(jsonObject.getString("message"))
                        Log.d("ScreenshotPhone", "${jsonObject.getString("message")}")
                        // Cerrar pantalla de llamada
                        //closeCallScreen(context)
                    }

                    //Log.dm
                    if (bothEndpointsSuccessful()) {
                        // Cerrar pantalla de llamada
                        closeCallScreen(context)
                        Log.d("flagCapturePhone", flagCapturePhone.toString())
                        Log.d("SimpleDataManager.getPhone()", if (SimpleDataManager.getPhone().isEmpty()) "VACIO" else SimpleDataManager.getPhone())
                        Log.d("SimpleDataManager.getScreenShot()", if (SimpleDataManager.getScreenShot().isEmpty()) "VACIO" else SimpleDataManager.getScreenShot())
                        resetVariables()
                    }
                    Pair(true, null)  // Éxito, sin error

                } else {
                    val responseBody = response.body?.string()
                    try {
                        val jsonObject = JSONObject(responseBody) // Cambiado de JSONArray a JSONObject

                        // Acceder a los campos del objeto
                        val errorMessage = jsonObject.getString("error")
                        val id = jsonObject.getString("id")
                        val exists = jsonObject.getString("exists")

                        Log.d("MENSAJE DE ERROR...", errorMessage)
                        val errorFinal = handleApiError(errorMessage, response.code)
                        Pair(false, errorFinal)

                    } catch (e: JSONException) {
                        Log.e("JSON Error", "Error al parsear JSON: ${e.message}")
                        val errorFinal = handleApiError(e.message.toString(), response.code)
                        Pair(false, errorFinal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotUtil", "Error sending request to API", e)
            return Pair(false, "Error sending request to API: ${e.message}")
        }
    }

    // Función modular para preparar la URL y el cuerpo del JSON
    private fun prepareRequestData(base64Image: String): Pair<String, JSONObject> {
        val url: String
        val json = JSONObject()

        if (!flagCapturePhone) {
            url = "http://192.168.0.193:8000/api/procesar_base64/"
            //url = "http://3.20.167.191/api/procesar_base64/"
            json.put("base64", base64Image)
        } else {
            url = "http://192.168.0.193:8000/api/process-phone-number/"
            //url = "http://3.20.167.191/api/process-phone-number/"
            json.put("base64", base64Image)
        }

        return Pair(url, json)
    }
    private fun handleApiError(url: String, errorCode: Int): String {
        val urlTrimmed = url.trim()
        return when {
            urlTrimmed.contains("No se pudo extraer un ID válido de la imagen") -> "Error al procesar la imagen, no se detecto ID de ML. "
            urlTrimmed == "El ID ya existe en la base de datos" -> "El ID de MELI, ya existe en la base de datos. "
            //
            urlTrimmed.contains("\"Error al procesar la imagen completa: ") -> "El ID de MELI, ya fue escaneado, por favor captura el teléfono."
            urlTrimmed == "No value for id" -> "Primero debes escanear un ID de MELI."
            urlTrimmed.contains("No se pudo extraer un número de teléfono válido de la imagen") -> "Error al procesar la imagen, no se detecto de teléfono."
            else -> "Error desconocido en el API. Código de error: $errorCode"
        }
    }

    private fun bothEndpointsSuccessful(): Boolean {
        return flagCapturePhone && SimpleDataManager.getScreenShot().isNotEmpty() && SimpleDataManager.getPhone().isNotEmpty()
    }
    private fun resetVariables() {
        SimpleDataManager.setScreenshotText("")
        SimpleDataManager.setPhoneNumber("")
        flagCapturePhone = false
        Log.d("ScreenshotUtil", "Variables reseteadas.")
        cleanup()
    }

    fun processImage(image: Image, context: Context, display: Display) {
        Log.d("ScreenshotUtil", "Processing captured image")
        floatingMessageWindow = FloatingMessageWindow(context)

        floatingMessageWindow.showMessage("Captura de pantalla iniciada", ContextCompat.getColor(context, R.color.green))

        try {
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val roiHeight = (screenHeight * 0.80).toInt()
            val roiStartY = screenHeight - roiHeight

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            val leftMargin = 85
            val rightMargin = 85
            val newWidth = screenWidth - leftMargin - rightMargin

            val roiBitmap: Bitmap = if (!flagCapturePhone) {
                Bitmap.createBitmap(bitmap, 0, roiStartY, screenWidth, roiHeight)
            } else {
                Bitmap.createBitmap(bitmap, leftMargin, roiStartY, newWidth, roiHeight)
            }

            base64Image = encodeToBase64(roiBitmap)

            GlobalScope.launch {
                val (success, errorMessage) = sendImageToApi(base64Image, context)
                if (success) {
                    val message = if (flagCapturePhone) "ID CAPTURADO" else "NUMERO DE TELÉFONO CAPTURADO"
                    Log.d("TEST ID MSG", success.toString())
                    floatingMessageWindow.showMessage(message, ContextCompat.getColor(context, R.color.green))
                } else {
                    val messageToShow = errorMessage ?: "Error, no se realizó captura"
                    floatingMessageWindow.showMessage(messageToShow, ContextCompat.getColor(context, R.color.red))
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotUtil", "Error processing image", e)
            floatingMessageWindow.showMessage("Error al procesar la imagen", ContextCompat.getColor(context, R.color.red))
        } finally {
            image.close()
        }
    }

    private fun closeCallScreen(context: Context) {
        // Intent para volver a la pantalla de inicio o cerrar la pantalla de llamada
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun cleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null

    }

    private fun encodeToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }


}