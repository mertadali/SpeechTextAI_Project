package com.mertadali.speechwithai_app.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseRepository {
    private val database = FirebaseFirestore.getInstance()

    suspend fun getStockInfo(productName: String): StockInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.collection("stocks")
                    .whereEqualTo("name", productName.lowercase())
                    .get()
                    .await()

                snapshot.documents.firstOrNull()?.let { doc ->
                    StockInfo(
                        name = doc.getString("name") ?: "",
                        quantity = doc.getLong("quantity")?.toInt() ?: 0,
                        unit = doc.getString("unit") ?: "adet"
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveConversation(conversation: ConversationData): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val documentRef = database.collection("conversations")
                    .add(conversation.toMap())
                    .await()
                Result.success(documentRef.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun initializeStocks() {
        val stocks = listOf(
            StockInfo("a çikolatası", 15, "adet"),
            StockInfo("b çikolatası", 20, "adet")
        )

        stocks.forEach { stock ->
            database.collection("stocks")
                .whereEqualTo("name", stock.name)
                .get()
                .await()
                .documents
                .firstOrNull() ?: run {
                database.collection("stocks").add(stock.toMap())
            }
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

