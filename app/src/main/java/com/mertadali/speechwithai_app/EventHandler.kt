package com.mertadali.speechwithai_app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.mertadali.speechwithai_app.repository.FirebaseRepository

class EventHandler(
    private val chatGPTService: ChatGPTService,
    private val firebaseRepository: FirebaseRepository
) {
    private val _responseFlow = MutableStateFlow<String>("")
    val responseFlow: StateFlow<String> = _responseFlow

    suspend fun handleEvent(event: AssistantEvent) {
        when (event) {
            is AssistantEvent.Text -> handleTextEvent(event)
            is AssistantEvent.StockQuery -> handleStockQuery(event)
            // ... diğer event tipleri
            else -> {
                println("Bilinmeyen event tipi: $event")

            }
        }
    }

    private suspend fun handleStockQuery(event: AssistantEvent.StockQuery) {
        try {
            val productName = event.productName
            val quantity = firebaseRepository.getStockQuantity(productName)

            val response = if (quantity > 0) {
                "$productName ürününden $quantity adet bulunmaktadır."
            } else {
                "$productName ürünü stokta bulunmamaktadır."
            }

            _responseFlow.value = response

        } catch (e: Exception) {
            println("Stok sorgu hatası: ${e.message}")
            _responseFlow.value = "Stok bilgisi alınamadı: ${e.message}"
        }
    }

    private suspend fun handleTextEvent(event: AssistantEvent.Text) {
        _responseFlow.value = event.content
    }
}

sealed class AssistantEvent {
    data class Text(val content: String) : AssistantEvent()
    data class StockQuery(val productName: String) : AssistantEvent()
    // ... diğer event tipleri
}