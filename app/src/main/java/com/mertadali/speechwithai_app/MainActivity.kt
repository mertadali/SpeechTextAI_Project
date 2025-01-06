package com.mertadali.speechwithai_app

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mertadali.speechwithai_app.repository.FirebaseRepository
import com.mertadali.speechwithai_app.service.ChatGPTService
import com.mertadali.speechwithai_app.ui.theme.SpeechWithAI_AppTheme
import java.io.File


class MainActivity : ComponentActivity() {

    private val audioRecorder: AudioRecorder = AudioRecorder()
    private val firebaseRepository = FirebaseRepository()
    private val audioFile: File? = null

    private val chatGPTService: ChatGPTService by lazy { ChatGPTService.create() }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "İzin verildi", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Ses kaydı için izin gerekli", Toast.LENGTH_LONG).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeechWithAI_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    var isRecording by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                if (isRecording) {
                                    audioRecorder.stopRecording()
                                    isRecording = false
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Ses kaydı durduruldu",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    audioRecorder.startRecording(this@MainActivity)
                                    isRecording = true
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Ses kaydı başlatıldı",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = if (isRecording) "Durdur" else "Başlat")
                        }
                    }


                }
            }
        }
    }


    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startSpeechRecognition()
            }

            shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    this,
                    "Ses kaydı yapabilmek için izin gerekli",
                    Toast.LENGTH_LONG
                ).show()
                requestPermission()
            }

            else -> {
                requestPermission()
            }
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun startSpeechRecognition() {
        Toast.makeText(this, "Ses kaydı başlatılıyor...", Toast.LENGTH_SHORT).show()
    }
}


