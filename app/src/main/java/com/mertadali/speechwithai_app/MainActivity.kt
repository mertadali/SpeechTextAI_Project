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


class MainActivity : ComponentActivity() {

    private val audioRecorder = AudioRecorder()
    private val firebaseRepository = FirebaseRepository()
    private var currentRecordingFile: File? = null
    private val chatGPTService by lazy { ChatGPTService.create() }
    private lateinit var textToSpeech: TextToSpeech
    private var isProcessing = false

    // Sabit thread ID tanımlayalım
    companion object {
        private const val THREAD_ID = "thread_7kL9XyPVBGJHutAFZGgcKqWx" // OpenAI'dan aldığınız thread ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Google Play Services kontrolü
        checkGooglePlayServices()

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

        currentRecordingFile?.let { file ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Ses -> Metin
                    val requestFile = file.readBytes().toRequestBody("audio/*".toMediaType())
                    val body = MultipartBody.Part.createFormData("file", "audio.m4a", requestFile)
                    val transcriptionResponse = chatGPTService.getTranscription(body)

                    // Assistant yanıtı al
                    val aiResponse = processAssistantResponse(THREAD_ID, transcriptionResponse.text)

                    withContext(Dispatchers.Main) {
                        // Sesli yanıt ver
                        textToSpeech.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, null)

                        // Firebase'e kaydet
                        firebaseRepository.saveConversation(
                            ConversationData(
                                query = transcriptionResponse.text,
                                response = aiResponse
                            )
                        )
                    }

                    file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Hata: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                1234
            )
            false
        }
    }

    private fun checkGooglePlayServices() {
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            availability.getErrorDialog(this, resultCode, 1)?.show()
        }
    }

    private fun initializeFirebase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                firebaseRepository.initializeStocks()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Stok verisi yüklenemedi", Toast.LENGTH_SHORT).show()
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
            // Mesajı gönder
            chatGPTService.sendMessage(
                threadId = threadId,
                message = AssistantMessage(content = userMessage)
            )

            // Run başlat
            val runResponse = chatGPTService.createRun(
                threadId = threadId,
                RunRequest()
            )

            // Run durumunu kontrol et
            var status = runResponse.status
            while (status != "completed") {
                delay(1000)
                status = chatGPTService.getRunStatus(threadId, runResponse.id).status
                if (status == "failed") throw Exception("Assistant yanıt veremedi")
            }

            // Son mesajı al
            chatGPTService.getMessages(threadId).data
                .firstOrNull()?.content
                ?.firstOrNull()?.text?.value
                ?: "Yanıt alınamadı"

        } catch (e: Exception) {
            "Hata: ${e.message}"
        }
    }
}