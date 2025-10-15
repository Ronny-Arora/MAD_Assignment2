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
import android.util.Log

class CloudSync(private val db: AppDb) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val auth get() = Firebase.auth
    private val fs get() = Firebase.firestore

    // Quick, fire-and-forget anonymous sign-in. If hasn't completed yet,
    // fall back to "anon" so the demo still works (shared bucket).
    private fun currentUid(): String {
        val user = Firebase.auth.currentUser
        return if (user != null) {
            user.uid
        } else {
            Firebase.auth.signInAnonymously() // non-blocking
            Firebase.auth.currentUser?.uid ?: "anon" // fallback shared path
        }
    }

    private fun userBooksCol() =
        fs.collection("users").document(currentUid()).collection("books")

    // Push a single book to Firestore.
    // On success, clear dirty locally.
    fun push(book: Book) {
        scope.launch {
            try {
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
                // wait for Firestore to finish; throws if it fails
                userBooksCol().document(book.id).set(data).await()

                // only clear dirty if cloud write worked
                db.books().markDirty(book.id, false)
            } catch (t: Throwable) {
                Log.w("CloudSync", "push failed; will retry later", t)
            }
        }
    }

    // Cloud delete. Never crash UI if offline.
    fun delete(id: String) {
        scope.launch {
            try {
                userBooksCol().document(id).delete().await()   // await so errors throw here
            } catch (t: Throwable) {
                Log.w("CloudSync", "delete failed for id=$id; will ignore", t)
            }
        }
    }

    // Pull once from Firestore and merge into local Room (dirty=false).
    fun pullOnceMerge() {
        scope.launch {
            try {
                val snap = userBooksCol().get().await()
                for (doc in snap.documents) {
                    val book = Book(
                        id = doc.getString("id") ?: doc.id,
                        title = doc.getString("title") ?: "",
                        author = doc.getString("author") ?: "",
                        year = doc.getString("year") ?: "",
                        coverId = doc.getLong("coverId")?.toInt(),
                        photoUri = doc.getString("photoUri"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        dirty = false
                    )
                    db.books().upsert(book.toEntity())
                }
            } catch (t: Throwable) {
                Log.w("CloudSync", "pullOnceMerge failed; will try again later", t)
            }
        }
    }
}