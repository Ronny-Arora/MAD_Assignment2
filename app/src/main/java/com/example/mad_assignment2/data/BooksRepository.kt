package com.example.mad_assignment2.data

import android.content.Context
import androidx.room.Query
import com.example.mad_assignment2.data.local.AppDb
import com.example.mad_assignment2.data.local.toEntity
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.data.model.toBook
import com.example.mad_assignment2.data.remote.OpenLibraryApi
import com.example.mad_assignment2.data.cloud.CloudSync
import com.example.mad_assignment2.data.local.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BooksRepository (
    context: Context,
    private val api: OpenLibraryApi
) {
    private val db = AppDb.get(context)
    private val cloud = CloudSync(db)

    // Expose all books as domain models for UI (Room -> Flow<List<Book>>)
    val allBooks: Flow<List<Book>> = db.books().all().map { it.map { e -> e.toDomain() } }

    fun searchLocal(q: String): Flow<List<Book>> =
        db.books().search(q).map { it.map { e -> e.toDomain() } }

    // Search remote API (OpenLibrary) and convert Doc -> Book for UI
    suspend fun searchRemote(query: String): List<Book> =
        api.search(query).docs.map {it.toBook()}

    // Save locally (dirty=true) - try push to cloud (if offline, push fails silently)
    suspend fun save(book: Book) {
        db.books().upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)    // marks dirty=false if cloud write succeeds
    }

    // Update locally (dirty=true) and try cloud
    suspend fun update(book: Book) {
        db.books().upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)
    }

    // Remove locally and try to delete in cloud (delete is best-effort)
    suspend fun remove(book: Book) {
        db.books().delete(book.toEntity())
        cloud.delete(book.id)
    }

    // One shot pull from cloud, merging into local (used on fresh install / second device)
    fun restoreFromCloudOnce() = cloud.pullOnceMerge()

    // Uploading all locally dirty rows (used by auto-sync loop)
    suspend fun syncDirtyNow() {
        val dirty = db.books().dirtyOnes()
        for (row in dirty) {
            cloud.push(row.toDomain())
        }
    }


}