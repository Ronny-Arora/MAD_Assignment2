/*
 * OpenLibraryModels.kt
 * ============================================
 * This file defines the data classes for parsing the JSON response returned by the Open Library public search API.
 *
 * -> Purpose:
 *      Model the structure of "search.json" responses (title, author, publish year, and cover ID)
 *      so Retrofit + Moshi can automatically deserialize them.
 *
 * -> Why is it necessary?
 *      - The API fields don't directly match the local Book model.
 *      - Having separate API models avoids coupling network response formats to the internal app logic.
 *
 * -> Typical usage:
 *      - Retrofit uses these classes to parse results from the web API.
 *      - The "toBook()" extension function converts each API record into the internal Book data model for display or saving.
 */

package com.example.mad_assignment2.data.model

import com.squareup.moshi.Json

/** Top-level response returned by https://openlibrary.org/search.json */
data class OpenLibrarySearchResponse(
    @Json(name = "numFound") val numFound: Int? = null,
    @Json(name = "docs") val docs: List<OpenLibraryDoc> = emptyList()
)

/** Each “doc” represents one search result (book/work). */
data class OpenLibraryDoc(
    @Json(name = "key") val key: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "author_name") val author_name: List<String>? = null,
    @Json(name = "first_publish_year") val first_publish_year: Int? = null,
    @Json(name = "cover_i") val cover_i: Int? = null
)

/**
 * Converts a network OpenLibraryDoc into the internal Book model
 * used throughout the app (for Room + UI).
 */
fun OpenLibraryDoc.toBook(): Book = Book(
    id = key ?: (title + (author_name?.firstOrNull() ?: "") + first_publish_year).hashCode().toString(),
    title = title ?: "Unknown Title",
    author = author_name?.firstOrNull() ?: "Unknown Author",
    year = first_publish_year?.toString() ?: "-",
    coverId = cover_i,
)
