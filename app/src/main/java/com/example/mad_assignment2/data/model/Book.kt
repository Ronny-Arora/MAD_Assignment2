
/*
 * Book.kt
 * ================================================
 * The purpose of this file is to define the main domain model used inside the app.
 *
 * -> Purpose:
 *      Represents a single book in the user's library-whether fetched from the Open Library API or added manually by the user
 *
 * -> Why is it necessary?
 *      - Acts as the "shared data type" that all layers (UI, database, and cloud) understands.
 *      - Keeps consistent fields for title, author, year, cover, etc.
 *      - Enables mapping to Room entities (for local storage) and to Firestore documents (for cloud sync)
 *
 * -> Typical uses:
 *      - Display book info in the Compose UI.
 *      - Store and retrieve items from Room database
 *      - Serialize and upload to Firebase Firestore.
 *
 * */

package com.example.mad_assignment2.data.model

data class Book (
    val id: String,
    val title: String,
    val author: String,
    val year: String,
    val coverId: Int? = null,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = false
) {
    val coverUrl: String?
        get() = coverId.let { "https://covers.openlibrary.org/b/id/${it}-S.jpg" }
}