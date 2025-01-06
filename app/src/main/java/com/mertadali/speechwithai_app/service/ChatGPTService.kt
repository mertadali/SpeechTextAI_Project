package com.mertadali.speechwithai_app.service

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ChatGPTService {
    @Multipart
    @POST("audio/transcriptions")
    suspend fun getTranscription(@Part file : MultipartBody.Part) : ChatGPTResponse

    companion object {
        fun create(): ChatGPTService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(OkHttpClient())
                .build()

            return retrofit.create(ChatGPTService::class.java)

        }
    }

}

data class ChatGPTResponse(
    val text: String?
)