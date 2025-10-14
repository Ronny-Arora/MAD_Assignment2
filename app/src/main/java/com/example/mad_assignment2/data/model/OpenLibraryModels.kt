/*
 * OpenLibraryModels.kt
 * ============================================
 * This file defines the data classes for parsing the JSON response returned by the Open Library public search API.
 *
 * -> Purpose:
 *      Model the structure of "search.json" responses (title, author, public year, and cover ID) so Retrofit + Moshi can automatically deserialize them.
 *
 * -> Why is it necessary?
 *      - The API fields don't directly match the local Book model.
 *      - Having separate API models avoids coupling network response formats to the internal app logic
 *
 * -> Typical usage:
 *      - Retrofit uses these classes to parse results from the web API.
 *      - The "toBook()" extension function converts each API record into the internal Book data model for display or saving.
 *  *   */






// OpenLibraryModels.kt
package com.example.mad_assignment2.data.model

import com.squareup.moshi.Json

data class OpenLibrarySearchResponse(
    @Json(name = "docs") val docs: List<Doc> = emptyList()
)

data class Doc(
    @Json(name = "key") val key: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "author_name") val authorName: List<String>? = null,
    @Json(name = "first_publish_year") val firstPublishYear: Int? = null,
    @Json(name = "cover_i") val coverId: Int? = null,
)


fun Doc.toBook(): Book = Book(
    id = key ?: (title + (authorName?.firstOrNull() ?: "") + firstPublishYear).hashCode().toString(),
    title = title ?: "Unknown Title",
    author = (authorName?.firstOrNull() ?: "Unknown Author"),
    year = firstPublishYear?.toString() ?: "-",
    coverId = coverId,
)