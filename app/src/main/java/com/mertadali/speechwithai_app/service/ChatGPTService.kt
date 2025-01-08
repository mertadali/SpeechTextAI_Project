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

    @POST("v1/threads/{thread_id}/runs/{run_id}/submit_tool_outputs")
    suspend fun submitToolOutputs(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body toolOutputs: ToolOutputsRequest
    ): RunResponse

    @Streaming
    @GET("v1/threads/{thread_id}/runs/{run_id}/stream")
    suspend fun streamRun(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Query("assistant_id") assistantId: String = ASSISTANT_ID
    ): okhttp3.ResponseBody

    companion object {
        const val ASSISTANT_ID = "asst_zPRFy1ptOOYobHC6RBs24nmQ"
        private var instance: ChatGPTService? = null
        private var requestCounter = 0  // İstek sayacı ekle

        @Synchronized
        fun create(): ChatGPTService {
            if (instance == null) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor { chain ->
                        requestCounter++  // Her istek için sayacı artır
                        val request = chain.request()

                        // İstek detaylarını logla
                        println("""
                            ========== REQUEST #$requestCounter ==========
                            URL: ${request.url}
                            Method: ${request.method}
                            Headers: ${request.headers}
                            Body: ${request.body}
                            Time: ${java.util.Date()}
                            =======================================
                        """.trimIndent())

                        val modifiedRequest = request.newBuilder()
                            .header("Authorization", "Bearer ${Constans.API_KEY}")
                            .header("OpenAI-Beta", "assistants=v2")
                            .build()

                        try {
                            val response = chain.proceed(modifiedRequest)

                            // Yanıt detaylarını logla
                            println("""
                                ========== RESPONSE #$requestCounter ==========
                                Status: ${response.code} ${response.message}
                                Headers: ${response.headers}
                                Time: ${java.util.Date()}
                                =======================================
                            """.trimIndent())

                            if (!response.isSuccessful) {
                                val errorBody = response.peekBody(Long.MAX_VALUE).string()
                                println("""
                                    ========== ERROR #$requestCounter ==========
                                    Code: ${response.code}
                                    Error Body: $errorBody
                                    URL: ${request.url}
                                    Headers: ${request.headers}
                                    =======================================
                                """.trimIndent())
                            }
                            response
                        } catch (e: Exception) {
                            println("""
                                ========== EXCEPTION #$requestCounter ==========
                                Error: ${e.message}
                                Type: ${e.javaClass.simpleName}
                                Stack Trace: ${e.stackTraceToString()}
                                =======================================
                            """.trimIndent())
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
    val content: String,
    val role: String = "user"  // default değeri sona al
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
    val status: String,
    val required_action: RequiredAction? = null
)

data class TranscriptionResponse(
    val text: String
)

data class RunRequest(
    val assistant_id: String = ASSISTANT_ID,
    val tools: List<Tool> = listOf(
        Tool(
            type = "function",
            function = FunctionDefinition(
                name = "get_stock_info",
                description = "Ürünün stok bilgisini kontrol eder ve sesli yanıt verir",
                parameters = mapOf(
                    "type" to "object",
                    "required" to listOf("product_name"),
                    "properties" to mapOf(
                        "product_name" to mapOf(
                            "type" to "string",
                            "description" to "Stok bilgisi sorgulanacak ürünün adı"
                        )
                    )
                )
            )
        )
    )
)

data class Tool(
    val type: String,
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
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

data class RequiredAction(
    val submit_tool_outputs: SubmitToolOutputs
)

data class SubmitToolOutputs(
    val tool_calls: List<ToolCall>
)

data class ToolCall(
    val id: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String
)

data class ToolOutput(
    val tool_call_id: String,
    val output: String
)

data class ToolOutputsRequest(
    val tool_outputs: List<ToolOutput>
)

data class StockQueryArgs(
    val product_name: String
)