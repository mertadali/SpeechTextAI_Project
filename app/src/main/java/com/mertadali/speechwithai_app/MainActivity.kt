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
import com.mertadali.speechwithai_app.service.Tool
import com.mertadali.speechwithai_app.service.FunctionDefinition
import com.mertadali.speechwithai_app.service.ToolOutput
import com.mertadali.speechwithai_app.service.ToolOutputsRequest
import com.mertadali.speechwithai_app.service.StockQueryArgs
import com.mertadali.speechwithai_app.service.ProcessVoiceInputArgs
import com.mertadali.speechwithai_app.service.VoiceInput


@Suppress("DEPRECATION")
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

        // Firebase'i başlat
        initializeFirebase()

        // Thread oluşturma işlemini başlat
        createNewThread()

        // Text-to-Speech ve Firebase'i başlat
        initTextToSpeech()

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
                println("Ses kaydı işleniyor...") // Debug log

                // Thread kontrolü
                val threadId = currentThreadId ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Asistan başlatılıyor...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    try {
                        createNewThreadAndWait()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            Toast.makeText(
                                this@MainActivity,
                                "Asistan başlatılamadı: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }
                }

                currentRecordingFile?.let { file ->
                    try {
                        processRecordedFile(file, threadId)
                    } catch (e: Exception) {
                        println("Ses işleme hatası: ${e.message}")
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            Toast.makeText(
                                this@MainActivity,
                                "Ses işleme hatası: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        Toast.makeText(
                            this@MainActivity,
                            "Ses dosyası oluşturulamadı",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                handleError(e)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
            } finally {
                currentRecordingFile?.delete()
                withContext(Dispatchers.Main) {
                    isProcessing = false  // Her durumda isProcessing'i sıfırla
                }
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
        try {
            println("Ses dosyası işleniyor...") // Debug log

            // 1. Ses dosyasını text'e çevir
            val requestFile = file.readBytes().toRequestBody("audio/*".toMediaType())
            val body = MultipartBody.Part.createFormData("file", "audio.m4a", requestFile)
            val transcriptionResponse = chatGPTService.getTranscription(body)
            val userMessage = transcriptionResponse.text

            println("Kullanıcı mesajı: $userMessage") // Debug log

            // 2. Kullanıcıya geri bildirim
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Soru: $userMessage", Toast.LENGTH_SHORT).show()
            }

            // 3. Asistan yanıtını al
            val aiResponse = processAssistantResponse(threadId, userMessage)
            println("Asistan yanıtı: $aiResponse") // Debug log

            // 4. Yanıtı işle
            withContext(Dispatchers.Main) {
                if (aiResponse.isNotBlank()) {
                    try {
                        if (::textToSpeech.isInitialized) {
                            textToSpeech.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, null)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Ses motoru hazır değil, yanıt: $aiResponse",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        println("TextToSpeech hatası: ${e.message}")
                        Toast.makeText(
                            this@MainActivity,
                            aiResponse,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    // İşlem tamamlandı, isProcessing'i sıfırla
                    isProcessing = false

                    // Firebase'e kaydet (arka planda)
                    mainScope.launch(Dispatchers.IO) {
                        try {
                            firebaseRepository.saveConversation(
                                ConversationData(
                                    query = userMessage,
                                    response = aiResponse
                                )
                            )
                        } catch (e: Exception) {
                            println("Konuşma kaydetme hatası: ${e.message}")
                        }
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Yanıt alınamadı",
                        Toast.LENGTH_SHORT
                    ).show()
                    isProcessing = false
                }
            }
        } catch (e: Exception) {
            println("Ses işleme hatası: ${e.message}")
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                isProcessing = false
                Toast.makeText(
                    this@MainActivity,
                    "Hata: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            throw e
        }
    }
    /*
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

     */

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

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
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
    /*

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

     */

    private fun initializeFirebase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                println("Firebase stokları yükleniyor...") // Debug log
                firebaseRepository.initializeStocks()
                println("Firebase stokları başarıyla yüklendi") // Debug log
            } catch (e: Exception) {
                println("Firebase başlatma hatası: ${e.message}") // Debug log
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Stok verisi yüklenemedi: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
            println("Asistan yanıtı işleniyor... Mesaj: $userMessage") // Debug log

            // 1. Kullanıcı mesajını gönder
            chatGPTService.sendMessage(threadId, AssistantMessage(content = userMessage))

            // 2. Run başlat
            val runResponse = chatGPTService.createRun(
                threadId,
                RunRequest(assistant_id = ChatGPTService.ASSISTANT_ID)
            )

            // 3. Run durumunu kontrol et
            var run = chatGPTService.getRunStatus(threadId, runResponse.id)
            var attempts = 0
            val maxAttempts = 30 // 30 saniye maksimum bekleme

            while (run.status == "queued" || run.status == "in_progress") {
                delay(1000)
                run = chatGPTService.getRunStatus(threadId, runResponse.id)
                println("Run durumu: ${run.status}") // Debug log

                attempts++
                if (attempts >= maxAttempts) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                    }
                    return "Yanıt zaman aşımına uğradı"
                }

                // Function calling gerekiyorsa
                if (run.status == "requires_action") {
                    val toolCalls = run.required_action?.submit_tool_outputs?.tool_calls
                    toolCalls?.forEach { toolCall ->
                        if (toolCall.function.name == "check_stock") {
                            try {
                                // Stok bilgisini sorgula
                                val args = Gson().fromJson(toolCall.function.arguments, StockQueryArgs::class.java)
                                val productCode = args.product_code.uppercase() // Firebase'deki kodlar büyük harf
                                val stockQuantity = firebaseRepository.getStockQuantity(productCode)
                                println("Stok sorgusu sonucu: $productCode = $stockQuantity")

                                chatGPTService.submitToolOutputs(
                                    threadId,
                                    run.id,
                                    ToolOutputsRequest(
                                        tool_outputs = listOf(
                                            ToolOutput(
                                                tool_call_id = toolCall.id,
                                                output = stockQuantity.toString()
                                            )
                                        )
                                    )
                                )
                            } catch (e: Exception) {
                                println("Tool output hatası: ${e.message}")
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                }
                            }
                        }
                    }

                    // Run'ı tekrar kontrol et
                    run = chatGPTService.getRunStatus(threadId, runResponse.id)
                }
            }

            // 4. Run tamamlandıysa yanıtı al
            if (run.status == "completed") {
                val messages = chatGPTService.getMessages(threadId)
                messages.data.firstOrNull()?.content?.firstOrNull()?.text?.value ?: "Yanıt alınamadı"
            } else {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
                "İşlem tamamlanamadı: ${run.status}"
            }
        } catch (e: Exception) {
            println("Asistan yanıt hatası: ${e.message}")
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                isProcessing = false
            }
            "Bir hata oluştu: ${e.message}"
        }
    }

    private fun extractProductName(message: String): String {
        val words = message.lowercase().split(" ")
        var productName = ""

        // "kaç" veya "stok" kelimesinden önceki kelimeyi al
        for (i in words.indices) {
            if (words[i] == "kaç" || words[i] == "stok") {
                if (i > 0) {
                    productName = words[i-1]
                    break
                }
            }
        }

        println("Çıkarılan ürün adı: $productName") // Debug log
        return productName.uppercase() // Firebase'deki stok kodları büyük harf
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