package com.example.mad_assignment2.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity (
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val year: String,
    val coverId: Int?,
    val photoUri: String?,
    val createdAt: Long,
    val dirty: Boolean,
)

// Mapping
fun com.example.mad_assignment2.data.model.Book.toEntity() = BookEntity(
    id, title, author, year, coverId, photoUri, createdAt, dirty
)

fun BookEntity.toDomain() = com.example.mad_assignment2.data.model.Book(
    id, title, author, year, coverId, photoUri, createdAt, dirty
)
