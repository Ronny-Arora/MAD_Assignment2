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

    val allBooks: Flow<List<Book>> = db.books().all().map { it.map { e -> e.toDomain() } }

    fun searchLocal(q: String): Flow<List<Book>> =
        db.books().search(q).map { it.map { e -> e.toDomain() } }

    suspend fun searchRemote(query: String): List<Book> =
        api.search(query).docs.map {it.toBook()}

    suspend fun save(book: Book) {
        db.books().upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)
    }

    suspend fun update(book: Book) {
        db.books().upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)
    }

    suspend fun remove(book: Book) {
        db.books().delete(book.toEntity())
        cloud.delete(book.id)
    }

    fun restoreFromCloudOnce() = cloud.pullOnceMerge()


}