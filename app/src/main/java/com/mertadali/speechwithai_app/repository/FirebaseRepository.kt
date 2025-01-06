package com.mertadali.speechwithai_app.repository

import com.google.firebase.firestore.FirebaseFirestore


class FirebaseRepository {
    private val database = FirebaseFirestore.getInstance()

    fun saveTexToFirebase(text: String) {
        val data = hashMapOf(
            "text" to text
        )
        database.collection("texts")
            .add(data)
            .addOnSuccessListener { documentReference ->
                println("DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                println("Error adding document: $e")
            }
    }

}