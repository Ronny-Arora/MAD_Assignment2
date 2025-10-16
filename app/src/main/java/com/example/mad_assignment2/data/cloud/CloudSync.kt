package com.example.mad_assignment2.data.cloud

import android.util.Log
import com.example.mad_assignment2.data.local.AppDb
import com.example.mad_assignment2.data.local.toEntity
import com.example.mad_assignment2.data.model.Book
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CloudSync(private val db: AppDb) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val auth get() = Firebase.auth
    private val fs get() = Firebase.firestore

    /** Replace slashes so we can safely use OL ids like `/works/OL123W` as doc ids. */
    private fun safeId(id: String): String = id.trim('/').replace("/", "_")

    private suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        val res: AuthResult = auth.signInAnonymously().await()
        return res.user?.uid ?: error("Anonymous sign in returned null user")
    }

    private suspend fun userBooksCol() =
        fs.collection("users").document(ensureSignedIn()).collection("books")

    /** Push/upsert a single book to Firestore and clear local 'dirty' flag. */
    fun push(book: Book) {
        scope.launch {
            try {
                val data = mapOf(
                    "id" to book.id,
                    "title" to book.title,
                    "author" to book.author,
                    "year" to book.year,
                    "coverId" to book.coverId,
                    "coverUrl" to book.coverUrl,
                    "photoUri" to book.photoUri,
                    "createdAt" to book.createdAt,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                val col = userBooksCol()
                col.document(safeId(book.id)).set(data).await()
                db.books().markDirty(book.id, false)
                Log.d("CloudSync", "push ok id=${book.id}")
            } catch (t: Throwable) {
                Log.w("CloudSync", "push failed; will retry later id=${book.id}", t)
            }
        }
    }

    /** Best-effort delete in cloud (local already removed by repo). */
    fun delete(id: String) {
        scope.launch {
            try {
                val col = userBooksCol()
                col.document(safeId(id)).delete().await()
                Log.d("CloudSync", "delete ok id=$id")
            } catch (t: Throwable) {
                Log.w("CloudSync", "delete failed id=$id (ignored)", t)
            }
        }
    }

    /** One-shot pull from cloud and merge into local Room (dirty=false). */
    fun pullOnceMerge() {
        scope.launch {
            try {
                val col = userBooksCol()
                val snap = col.get().await()
                for (doc in snap.documents) {
                    val pulledId = doc.getString("id") ?: doc.id
                    val book = Book(
                        id = pulledId,
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
                Log.d("CloudSync", "pullOnceMerge ok, count=${snap.size()}")
            } catch (t: Throwable) {
                Log.w("CloudSync", "pullOnceMerge failed; will try again later", t)
            }
        }
    }
}
