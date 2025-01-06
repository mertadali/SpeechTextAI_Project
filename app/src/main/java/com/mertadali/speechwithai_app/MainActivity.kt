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
import com.mertadali.speechwithai_app.service.ChatGPTRequest
import com.mertadali.speechwithai_app.service.Message


class MainActivity : ComponentActivity() {

    private val audioRecorder = AudioRecorder()
    private val firebaseRepository = FirebaseRepository()
    private var currentRecordingFile: File? = null
    private val chatGPTService = ChatGPTService.create()
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initTextToSpeech()

        // Örnek stokları ekle
        lifecycleScope.launch {
            try {
                firebaseRepository.addInitialStocks()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Stok verisi yüklenemedi", Toast.LENGTH_SHORT).show()
            }
        }

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
                                if (isRecording) {
                                    stopRecordingAndProcess()
                                    isRecording = false
                                } else {
                                    startRecording()
                                    isRecording = true
                                }
                            },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = if (isRecording) "Dinlemeyi Durdur" else "Soru Sormak İçin Başlat",
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
        audioRecorder.stopRecording()
        Toast.makeText(this, "Ses kaydı tamamlandı, işleniyor...", Toast.LENGTH_SHORT).show()

        currentRecordingFile?.let { file ->
            lifecycleScope.launch {
                try {
                    // Ses -> Metin
                    val requestFile = file.readBytes().toRequestBody("audio/*".toMediaType())
                    val body = MultipartBody.Part.createFormData("file", "audio.m4a", requestFile)
                    val transcriptionResponse = chatGPTService.getTranscription(body)

                    // Stok bilgisini kontrol et
                    val query = transcriptionResponse.text
                    val stockInfo = when {
                        query.contains("a çikolata", ignoreCase = true) ->
                            firebaseRepository.getStockInfo("A çikolatası")
                        query.contains("b çikolata", ignoreCase = true) ->
                            firebaseRepository.getStockInfo("B çikolatası")
                        else -> null
                    }

                    // AI yanıtı al
                    val chatResponse = chatGPTService.getChatResponse(
                        ChatGPTRequest(
                            messages = listOf(
                                Message("system", ChatGPTService.SYSTEM_PROMPT),
                                Message("user", query),
                                Message(
                                    "system",
                                    "Stok bilgisi: ${stockInfo?.toString() ?: "Ürün bulunamadı"}"
                                )
                            )
                        )
                    )

                    val aiResponse = chatResponse.choices.firstOrNull()?.message?.content ?: "Yanıt alınamadı"

                    // Yanıtı göster ve seslendir
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, aiResponse, Toast.LENGTH_LONG).show()
                    }
                    textToSpeech.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, null)

                    // Kaydet
                    firebaseRepository.saveConversation(query, aiResponse)

                    file.delete()
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        audioRecorder.stopRecording()
    }
}


