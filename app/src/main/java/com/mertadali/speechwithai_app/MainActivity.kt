package com.mertadali.speechwithai_app

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mertadali.speechwithai_app.repository.FirebaseRepository
import com.mertadali.speechwithai_app.service.ChatGPTService
import com.mertadali.speechwithai_app.ui.theme.SpeechWithAI_AppTheme
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.content.Intent
import androidx.core.app.ActivityCompat
import com.mertadali.speechwithai_app.repository.ConversationData
import com.mertadali.speechwithai_app.service.AssistantMessage
import com.mertadali.speechwithai_app.service.RunRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.mertadali.speechwithai_app.service.EventHandler
import com.mertadali.speechwithai_app.service.AssistantEvent


class MainActivity : ComponentActivity() {

    private val audioRecorder = AudioRecorder()
    private val firebaseRepository = FirebaseRepository()
    private var currentRecordingFile: File? = null
    private val chatGPTService by lazy { ChatGPTService.create() }
    private lateinit var textToSpeech: TextToSpeech
    private var isProcessing = false

    private var currentThreadId: String? = null
    private val threadLock = Object()

    private val mainScope = lifecycleScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Thread oluşturma işlemini başlat
        createNewThread()

        // Text-to-Speech ve Firebase'i başlat
        initTextToSpeech()
        initializeFirebase()

        setContent {
            SpeechWithAI_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var isRecording by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                if (!isProcessing) {
                                    if (isRecording) {
                                        stopRecordingAndProcess()
                                        isRecording = false
                                    } else {
                                        startRecording()
                                        isRecording = true
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = when {
                                    isProcessing -> "İşleniyor..."
                                    isRecording -> "Dinlemeyi Durdur"
                                    else -> "Soru Sormak İçin Başlat"
                                },
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        if (isRecording) {
                            Text(
                                text = "Dinleniyor...",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Türkçe dil desteği
                val locale = Locale("tr", "TR")
                val result = textToSpeech.setLanguage(locale)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Türkçe yoksa dil paketini indir
                    val installIntent = Intent()
                    installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                    startActivity(installIntent)
                }

                // Ses ayarları
                textToSpeech.setSpeechRate(0.85f)  // Konuşma hızı
                textToSpeech.setPitch(1.0f)        // Ses tonu
            }
        }
    }

    private fun startRecording() {
        if (checkPermission()) {
            currentRecordingFile = audioRecorder.startRecording(this)
        }
    }

    private fun stopRecordingAndProcess() {
        if (isProcessing) return
        isProcessing = true
        audioRecorder.stopRecording()

        mainScope.launch(Dispatchers.IO) {
            try {
                // Thread kontrolü
                val threadId = currentThreadId ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Asistan başlatılıyor...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    createNewThreadAndWait()
                }

                currentRecordingFile?.let { file ->
                    processRecordedFile(file, threadId)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                isProcessing = false
            }
        }
    }

    private suspend fun createNewThreadAndWait(): String {
        createNewThread()
        var attempts = 0
        while (currentThreadId == null && attempts < 10) {
            delay(500)
            attempts++
        }
        return currentThreadId ?: throw Exception("Asistan başlatılamadı")

    }

    private suspend fun processRecordedFile(file: File, threadId: String) {
        // Ses dosyasını hazırla
        val requestFile = file.readBytes().toRequestBody("audio/*".toMediaType())
        val body = MultipartBody.Part.createFormData("file", "audio.m4a", requestFile)

        // Ses -> Metin dönüşümü
        val transcriptionResponse = chatGPTService.getTranscription(body)
        val userMessage = transcriptionResponse.text

        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@MainActivity,
                "Soru: $userMessage",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Assistant yanıtı al
        val aiResponse = processAssistantResponse(threadId, userMessage)

        withContext(Dispatchers.Main) {
            handleAssistantResponse(userMessage, aiResponse)
        }

        // Temizlik
        file.delete()
    }

    private fun handleAssistantResponse(userMessage: String, aiResponse: String) {
        if (aiResponse.isNotBlank()) {
            // Sesli yanıt ver
            textToSpeech.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, null)

            // Firebase'e kaydet
            mainScope.launch(Dispatchers.IO) {
                firebaseRepository.saveConversation(
                    ConversationData(
                        query = userMessage,
                        response = aiResponse
                    )
                )
            }
        } else {
            Toast.makeText(
                this@MainActivity,
                "Yanıt alınamadı",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun handleError(e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@MainActivity,
                "Hata: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        e.printStackTrace()

        // Hata durumunda thread'i sıfırla
        if (e.message?.contains("thread", ignoreCase = true) == true) {
            synchronized(threadLock) {
                currentThreadId = null
                createNewThread()
            }
        }
    }

    private fun checkPermission(): Boolean {
        val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (notGrantedPermissions.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                1234
            )
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1234) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // İzinler verildi, kaydı başlat
                startRecording()
            } else {
                Toast.makeText(
                    this,
                    "Uygulama için gerekli izinler verilmedi",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkGooglePlayServices(): Boolean {
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)

        return if (resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS) {
            true
        } else {
            if (availability.isUserResolvableError(resultCode)) {
                availability.getErrorDialog(this, resultCode, 1)?.show()
            } else {
                Toast.makeText(
                    this,
                    "Bu cihaz Google Play Services desteklemiyor",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        }
    }

    private fun initializeFirebase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                firebaseRepository.initializeStocks()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Stok verisi yüklenemedi", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        audioRecorder.stopRecording()
    }

    private suspend fun processAssistantResponse(threadId: String, userMessage: String): String {
        return try {
            val eventHandler = EventHandler(chatGPTService, firebaseRepository)

            // 1. Kullanıcı mesajını gönder
            chatGPTService.sendMessage(
                threadId = threadId,
                message = AssistantMessage(content = userMessage)
            )

            // 2. Run başlat ve stream'i al
            val responseBody = chatGPTService.streamRun(threadId)
            val reader = responseBody.charStream().buffered()

            // 3. Stream'den gelen verileri işle
            withContext(Dispatchers.IO) {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data: ")) {
                            val eventJson = line.substring(6)
                            if (eventJson != "[DONE]") {
                                try {
                                    val event = parseEvent(eventJson)
                                    eventHandler.handleEvent(event)
                                } catch (e: Exception) {
                                    println("Event parse hatası: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            // 4. Son yanıtı al
            eventHandler.responseFlow.value.ifEmpty {
                throw Exception("Yanıt alınamadı")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseEvent(json: String): AssistantEvent {
        return Gson().fromJson(json, AssistantEvent::class.java)
    }

    private fun extractProductName(message: String): String {
        // Basit bir ürün adı çıkarma fonksiyonu
        return message.lowercase()
            .replace("kaç tane", "")
            .replace("var", "")
            .replace("?", "")
            .trim()
    }

    private fun createNewThread() {
        mainScope.launch(Dispatchers.IO) {
            try {
                val threadResponse = chatGPTService.createThread()
                synchronized(threadLock) {
                    if (currentThreadId == null) {
                        currentThreadId = threadResponse.id
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Asistan hazır",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    println(e.message)
                    Toast.makeText(
                        this@MainActivity,
                        "Asistan başlatılamadı: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                delay(2000)
                createNewThread()
            }
        }
    }
}