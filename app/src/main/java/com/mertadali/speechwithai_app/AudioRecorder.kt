package com.mertadali.speechwithai_app

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.util.UUID

class AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording(context: Context): File {
        val audioFile = File(context.cacheDir, "audio_${UUID.randomUUID()}.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        return audioFile

    }

    fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
    }
}