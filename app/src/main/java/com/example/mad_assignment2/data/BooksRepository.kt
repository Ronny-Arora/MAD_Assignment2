package com.example.mad_assignment2.data

import android.content.Context
import com.example.mad_assignment2.data.cloud.CloudSync
import com.example.mad_assignment2.data.local.AppDb
import com.example.mad_assignment2.data.local.toEntity
import com.example.mad_assignment2.data.local.toDomain
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.data.model.toBook
import com.example.mad_assignment2.data.remote.OpenLibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Bridges Local (Room) + Cloud (Firestore) + Remote (OpenLibrary). */
class BooksRepository(
    context: Context,
    private val api: OpenLibraryApi
) {
    private val db    = AppDb.get(context)
    private val dao   = db.books()
    private val cloud = CloudSync(db)

    /** Stream of all locally saved books for UI. */
    val allBooks: Flow<List<Book>> =
        dao.all().map { rows -> rows.map { it.toDomain() } }

    fun searchLocal(q: String): Flow<List<Book>> =
        dao.search(q).map { rows -> rows.map { it.toDomain() } }

    /** OpenLibrary search → domain models. */
    suspend fun searchRemote(query: String): List<Book> =
        api.search(query).docs.map { it.toBook() }

    /** Save/update locally (dirty=true) then push to cloud. */
    suspend fun save(book: Book) {
        dao.upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)
    }

    suspend fun update(book: Book) {
        dao.upsert(book.copy(dirty = true).toEntity())
        cloud.push(book)
    }

    /** Remove locally and try to delete from cloud (best-effort). */
    suspend fun remove(book: Book) {
        dao.deleteById(book.id)
        cloud.delete(book.id)
    }

    /** One-shot pull cloud → local (used on first launch / second device). */
    fun restoreFromCloudOnce() = cloud.pullOnceMerge()

    /** Push all locally dirty rows (used by your periodic sync loop). */
    suspend fun syncDirtyNow() {
        val dirty = dao.dirtyOnes()
        for (row in dirty) cloud.push(row.toDomain())
    }
}
