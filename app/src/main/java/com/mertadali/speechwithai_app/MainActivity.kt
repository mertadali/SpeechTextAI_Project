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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mertadali.speechwithai_app.ui.theme.SpeechWithAI_AppTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeechWithAI_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpeechScreen(
                        onSpeechButtonClick = { checkPermissionAndStartSpeech() }
                    )

                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            //  startSpeechRecognition()
        } else {
            Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndStartSpeech() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // welcomeAndStartListening()
            }

            else -> {
                requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }

    @Composable
    fun SpeechScreen(onSpeechButtonClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center

        ) {
            Button(
                onClick = onSpeechButtonClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)

            ) {
                Text(text = "Konuşmayı Başlat")
            }
        }
    }
}
