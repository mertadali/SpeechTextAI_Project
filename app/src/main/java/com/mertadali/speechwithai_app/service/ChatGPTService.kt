package com.mertadali.speechwithai_app.service

import com.mertadali.speechwithai_app.service.ChatGPTService.Companion.ASSISTANT_ID
import com.mertadali.speechwithai_app.util.Constans
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

    @POST("v1/threads/{thread_id}/runs")
    suspend fun createRun(
        @Path("thread_id") threadId: String,
        @Body runRequest: RunRequest
    ): RunResponse

    @GET("v1/threads/{thread_id}/runs/{run_id}")
    suspend fun getRunStatus(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String
    ): RunResponse

    @GET("v1/threads/{thread_id}/messages")
    suspend fun getMessages(
        @Path("thread_id") threadId: String
    ): MessagesResponse

    @POST("v1/assistants/{assistant_id}/functions/{function_name}/invoke")
    suspend fun invokeFunction(
        @Path("assistant_id") assistantId: String,
        @Path("function_name") functionName: String,
        @Body parameters: Map<String, Any>
    ): FunctionResponse

    companion object {
        const val ASSISTANT_ID = "asst_ObhY59Uzf80z3SftkBgNDrwx"
        private var instance: ChatGPTService? = null

        @Synchronized
        fun create(): ChatGPTService {
            if (instance == null) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Authorization", "Bearer ${Constans.API_KEY}")
                            .header("OpenAI-Beta", "assistants=v1")
                            .build()

                        var response = chain.proceed(request)
                        var retryCount = 0

                        // Exponential backoff
                        while (!response.isSuccessful && response.code == 429 && retryCount < 3) {
                            response.close()
                            retryCount++
                            val backoffTime = 1000L * (1 shl retryCount) // 2^retryCount seconds
                            Thread.sleep(backoffTime)
                            response = chain.proceed(request)
                        }

                        response
                    }
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
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

// Data Classes
data class AssistantMessage(val role: String = "user", val content: String)
data class MessageResponse(val id: String, val thread_id: String, val content: List<MessageContent>)
data class MessageContent(val type: String, val text: TextContent)
data class TextContent(val value: String)
data class RunResponse(val id: String, val status: String)
data class TranscriptionResponse(val text: String)
data class RunRequest(val assistant_id: String = ASSISTANT_ID)
data class MessagesResponse(
    val data: List<MessageResponse>
)
data class FunctionResponse(
    val result: String
)