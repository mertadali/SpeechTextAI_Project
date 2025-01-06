package com.mertadali.speechwithai_app.service

import com.mertadali.speechwithai_app.util.Constans
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Header
import okhttp3.RequestBody
import okhttp3.MediaType
import retrofit2.http.Body
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface ChatGPTService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun getTranscription(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-1".toRequestBody("text/plain".toMediaType())
    ): ChatGPTResponse

    @POST("v1/chat/completions")
    suspend fun getChatResponse(
        @Body request: ChatGPTRequest
    ): ChatCompletionResponse

    companion object {
        const val SYSTEM_PROMPT = """
            Sen bir çikolata mağazasının stok kontrol görevlisisin. Görevlerin:
            1. Sorulan çikolata ürününün stok durumunu kontrol et
            2. Net ve anlaşılır cevaplar ver
            3. Eğer ürün stokta varsa: "[Ürün adı]: [miktar] adet mevcut" formatında yanıt ver
            4. Eğer ürün stokta yoksa: "[Ürün adı] şu anda stokta bulunmuyor" şeklinde yanıt ver
            5. Eğer ürün sistemde yoksa: "Bu ürün bilgisi sistemde bulunmuyor" şeklinde yanıt ver
            6. Sadece stok bilgisi ver, başka konulara girme
            
            Örnek yanıtlar:
            - "A çikolatası: 15 adet mevcut"
            - "B çikolatası şu anda stokta bulunmuyor"
            - "Bu ürün bilgisi sistemde bulunmuyor"
        """

        fun create(): ChatGPTService {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer ${Constans.API_KEY}")
                            .build()
                    )
                }
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.openai.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(ChatGPTService::class.java)
        }
    }
}

data class ChatGPTRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<Message>,
    val temperature: Double = 0.3  // Daha tutarlı yanıtlar için
)

data class Message(val role: String, val content: String)
data class ChatGPTResponse(val text: String)
data class ChatCompletionResponse(val choices: List<Choice>)
data class Choice(val message: Message)