package com.mertadali.speechwithai_app.service

import com.mertadali.speechwithai_app.util.Constans
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.RequestBody
import retrofit2.http.Body
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import kotlin.math.pow

interface ChatGPTService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun getTranscription(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-1".toRequestBody("text/plain".toMediaType())
    ): TranscriptionResponse

    @POST("v1/threads/{thread_id}/messages")
    suspend fun sendMessage(
        @Path("thread_id") threadId: String,
        @Body message: AssistantMessage
    ): MessageResponse

    @GET("v1/threads/{thread_id}/runs/{run_id}")
    suspend fun getRunStatus(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String
    ): RunResponse

    companion object {
        const val ASSISTANT_ID = "asst_ObhY59Uzf80z3SftkBgNDrwx"
        private var instance: ChatGPTService? = null

        @Synchronized
        fun create(): ChatGPTService {
            if (instance == null) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val request = original.newBuilder()
                            .header("Authorization", "Bearer ${Constans.API_KEY}")
                            .header("OpenAI-Beta", "assistants=v1")
                            .build()

                        var response = chain.proceed(request)
                        var retryCount = 0

                        while (!response.isSuccessful && response.code == 429 && retryCount < 3) {
                            response.close()
                            retryCount++
                            Thread.sleep(1000L * (2.0.pow(retryCount.toDouble())).toLong())
                            response = chain.proceed(request)
                        }

                        response
                    }
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                instance = Retrofit.Builder()
                    .baseUrl("https://api.openai.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(ChatGPTService::class.java)
            }
            return instance!!
        }
    }
}

data class AssistantMessage(
    val role: String = "user",
    val content: String
)

data class MessageResponse(
    val id: String,
    val thread_id: String,
    val content: List<MessageContent>
)

data class MessageContent(
    val type: String,
    val text: TextContent
)

data class TextContent(
    val value: String
)

data class RunResponse(
    val id: String,
    val status: String
)

data class TranscriptionResponse(
    val text: String
)
