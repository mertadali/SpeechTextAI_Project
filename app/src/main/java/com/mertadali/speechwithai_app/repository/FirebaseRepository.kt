package com.mertadali.speechwithai_app.repository

import kotlinx.coroutines.tasks.await

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
class FirebaseRepository {
    private val db = Firebase.firestore
    private var stocks: Map<String, Int> = emptyMap()

    suspend fun initializeStocks() {
        try {
            println("Stoklar kontrol ediliyor...")
            val snapshot = db.collection("stocks").get().await()

            if (snapshot.isEmpty) {
                println("Stok verileri oluşturuluyor...")
                val initialStocks = mapOf(
                    "A" to 100,
                    "B" to 150,
                    "C" to 200,
                    "D" to 75,
                    "E" to 300,
                    "F" to 0
                )

                initialStocks.forEach { (name, quantity) ->
                    db.collection("stocks").document(name).set(
                        mapOf(
                            "name" to name,
                            "quantity" to quantity
                        )
                    ).await()
                }

                stocks = initialStocks
                println("Örnek stok verileri oluşturuldu: $stocks")
            } else {
                stocks = snapshot.documents.associate { doc ->
                    doc.id to (doc.getLong("quantity")?.toInt() ?: 0)
                }
                println("Mevcut stoklar yüklendi: $stocks")
            }
        } catch (e: Exception) {
            println("Stok yükleme/oluşturma hatası: ${e.message}")
            throw e
        }
    }

    fun getStockQuantity(productName: String): Int {
        println("Stok sorgusu: $productName")
        val quantity = stocks[productName.uppercase()]
        println("Bulunan miktar: $quantity")
        return quantity ?: 0
    }
    /*
        suspend fun updateStock(productName: String, newQuantity: Int) {
            try {
                db.collection("stocks")
                    .document(productName)
                    .update("quantity", newQuantity)
                    .await()

                stocks = stocks + (productName to newQuantity)

                println("Stok güncellendi: $productName = $newQuantity")
            } catch (e: Exception) {
                println("Stok güncelleme hatası: ${e.message}")
                throw e
            }
        }

     */

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
/*
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

 */

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