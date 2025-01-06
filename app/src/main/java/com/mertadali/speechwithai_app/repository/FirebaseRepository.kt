package com.mertadali.speechwithai_app.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseRepository {
    private val database = FirebaseFirestore.getInstance()

    // Stok sorgulama
    suspend fun getStockInfo(productName: String): Map<String, Any>? {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.collection("chocolate_stocks")
                    .whereEqualTo("name", productName)
                    .get()
                    .await()
                snapshot.documents.firstOrNull()?.data
            } catch (e: Exception) {
                null
            }
        }
    }

    // Konuşma ve yanıtları kaydetme
    suspend fun saveConversation(query: String, response: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "query" to query,
                    "response" to response,
                    "timestamp" to System.currentTimeMillis()
                )
                val documentRef = database.collection("conversations")
                    .add(data)
                    .await()
                Result.success(documentRef.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Örnek stok verilerini ekleme
    suspend fun addInitialStocks() {
        val stocks = listOf(
            mapOf(
                "name" to "A çikolatası",
                "quantity" to 15,
                "unit" to "adet"
            ),
            mapOf(
                "name" to "B çikolatası",
                "quantity" to 20,
                "unit" to "adet"
            )
        )
        stocks.forEach { stock ->
            database.collection("chocolate_stocks").add(stock)
        }
    }
}

