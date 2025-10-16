package com.example.mad_assignment2.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun all(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE title  LIKE '%' || :q || '%'
           OR author LIKE '%' || :q || '%'
        ORDER BY createdAt DESC
    """)
    fun search(q: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    // 🔹 used by repository's fast remove path
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET dirty = :dirty WHERE id = :id")
    suspend fun markDirty(id: String, dirty: Boolean)

    @Query("SELECT * FROM books WHERE dirty = 1 ORDER BY createdAt DESC")
    suspend fun dirtyOnes(): List<BookEntity>
}
