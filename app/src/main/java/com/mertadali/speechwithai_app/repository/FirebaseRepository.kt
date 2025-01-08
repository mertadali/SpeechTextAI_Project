package com.mertadali.speechwithai_app.repository

import kotlinx.coroutines.tasks.await
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class FirebaseRepository {
    private val db = Firebase.firestore
    private var stocks: Map<String, Int> = emptyMap()

    suspend fun initializeStocks() {
        try {
            println("Stoklar yükleniyor...") // Debug log
            val snapshot = db.collection("stocks").get().await()
            stocks = snapshot.documents.associate { doc ->
                doc.id to (doc.getLong("quantity")?.toInt() ?: 0)
            }
            println("Yüklenen stoklar: $stocks") // Debug log
        } catch (e: Exception) {
            println("Stok yükleme hatası: ${e.message}")
            throw e
        }
    }

    fun getStockQuantity(productName: String): Int {
        println("Stok sorgusu: $productName") // Debug log
        val quantity = stocks[productName.lowercase()]
        println("Bulunan miktar: $quantity") // Debug log
        return quantity ?: 0
    }

    suspend fun saveConversation(conversationData: ConversationData) {
        try {
            db.collection("conversations")
                .add(conversationData)
                .await()
        } catch (e: Exception) {
            println("Konuşma kaydetme hatası: ${e.message}")
            throw e
        }
    }
}

data class StockInfo(
    val name: String,
    val quantity: Int,
    val unit: String
) {
    fun toMap() = mapOf(
        "name" to name,
        "quantity" to quantity,
        "unit" to unit
    )

    override fun toString(): String = "$name: $quantity $unit"
}

data class ConversationData(
    val query: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap() = mapOf(
        "query" to query,
        "response" to response,
        "timestamp" to timestamp
    )
}