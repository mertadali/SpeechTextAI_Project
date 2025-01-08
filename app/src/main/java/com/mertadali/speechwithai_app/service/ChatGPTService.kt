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
import kotlin.math.pow
import kotlin.random.Random
import com.google.gson.annotations.SerializedName

interface ChatGPTService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun getTranscription(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-1".toRequestBody("text/plain".toMediaType())
    ): TranscriptionResponse

    @POST("v1/threads")
    @Headers("Content-Type: application/json")
    suspend fun createThread(
        @Body request: CreateThreadRequest = CreateThreadRequest()
    ): ThreadResponse

    @POST("v1/threads/{thread_id}/messages")
    suspend fun sendMessage(
        @Path("thread_id") threadId: String,
        @Body message: AssistantMessage
    ): MessageResponse

    @POST("v2/threads/{thread_id}/runs")
    suspend fun createRun(
        @Path("thread_id") threadId: String,
        @Body runRequest: RunRequest
    ): RunResponse

    @GET("v2/threads/{thread_id}/runs/{run_id}")
    suspend fun getRunStatus(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String
    ): RunResponse

    @GET("v2/threads/{thread_id}/messages")
    suspend fun getMessages(
        @Path("thread_id") threadId: String
    ): MessagesResponse

    @POST("v2/threads/{thread_id}/runs/{run_id}/submit_tool_outputs")
    suspend fun submitToolOutputs(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body toolOutputs: ToolOutputsRequest
    ): RunResponse

    @Streaming
    @GET("v2/threads/{thread_id}/runs/stream")
    suspend fun streamRun(
        @Path("thread_id") threadId: String,
        @Query("assistant_id") assistantId: String = ASSISTANT_ID
    ): okhttp3.ResponseBody

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
                        val request = chain.request()

                        // Request detaylarını logla
                        println("Request URL: ${request.url}")
                        println("Request Method: ${request.method}")
                        println("Request Headers: ${request.headers}")

                        val modifiedRequest = request.newBuilder()
                            .header("Authorization", "Bearer ${Constans.API_KEY}")
                            .header("OpenAI-Beta", "assistants=v1")
                            .build()

                        try {
                            val response = chain.proceed(modifiedRequest)
                            if (!response.isSuccessful) {
                                val errorBody = response.peekBody(Long.MAX_VALUE).string()
                                println("API Error: ${response.code} - $errorBody")
                                println("Full Request URL: ${request.url}")
                                println("Full Request Headers: ${request.headers}")
                            }
                            response
                        } catch (e: Exception) {
                            println("API Call Error: ${e.message}")
                            throw e
                        }
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
data class AssistantMessage(
    val role: String = "user",
    val content: String
)

data class MessageResponse(
    val id: String,
    val `object`: String,    val created_at: Long,
    val thread_id: String,
    val role: String,
    val content: List<MessageContent>
)

data class MessageContent(
    val type: String,
    val text: TextContent
)

data class TextContent(
    val value: String,
    val annotations: List<Any> = emptyList()
)

data class RunResponse(
    val id: String,
    val `object`: String,    val created_at: Long,
    val thread_id: String,
    val assistant_id: String,
    val status: String
)

data class TranscriptionResponse(
    val text: String
)

data class RunRequest(
    val assistant_id: String = ASSISTANT_ID
)

data class MessagesResponse(
    val `object`: String,    val data: List<MessageResponse>,
    val first_id: String,
    val last_id: String,
    val has_more: Boolean
)

data class ThreadResponse(
    val id: String,
    @SerializedName("object") val objectType: String,
    val created_at: Long
)

data class CreateThreadRequest(
    val messages: List<InitialMessage> = emptyList()
)

data class InitialMessage(
    val role: String = "user",
    val content: String = ""
)