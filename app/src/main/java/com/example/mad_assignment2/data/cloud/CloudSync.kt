package com.example.mad_assignment2.data.cloud

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.mad_assignment2.data.local.AppDb
import com.example.mad_assignment2.data.local.toEntity
import com.example.mad_assignment2.data.model.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CloudSync(private val db: AppDb) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val auth get() = Firebase.auth
    private val fs get() = Firebase.firestore

    private fun userBooksCol() = fs.collection("users").document(currentUid()).collection("books")

    private fun currentUid(): String {
        val user = auth.currentUser
        return if(user != null) user.uid else {
            val res = auth.signInAnonymously()
            auth.currentUser?.uid ?: "anon"
        }
    }

    fun push(book: Book) {
        scope.launch {
            val data = mapOf(
                "id" to book.id,
                "title" to book.title,
                "author" to book.author,
                "year" to book.year,
                "coverId" to book.coverId,
                "photoUri" to book.photoUri,
                "createdAt" to book.createdAt,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            userBooksCol().document(book.id).set(data)
            db.books().markDirty(book.id, false)
        }
    }

    fun delete(id: String) {
        scope.launch { userBooksCol().document(id).delete() }
    }

    fun pullOnceMerge() {
        scope.launch {
            val snap  =userBooksCol().get().await()
            for (doc in snap.documents) {
                val book = Book(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: "",
                    author = doc.getString("author") ?: "",
                    year = doc.getString("year") ?: "",
                    coverId = (doc.getLong("coverId")?.toInt()),
                    photoUri = doc.getString("photoUri"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    dirty = false
                )
                db.books().upsert(book.toEntity())
            }
        }
    }
}