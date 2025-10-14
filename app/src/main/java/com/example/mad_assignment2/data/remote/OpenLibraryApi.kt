// OpenLibraryApi.kt
package com.example.mad_assignment2.data.remote

import com.example.mad_assignment2.data.model.OpenLibrarySearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenLibraryApi {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("fields") fields: String =
            "key,title,author_name,first_publish_year,cover_i",
        @Query("limit") limit: Int = 20
    ): OpenLibrarySearchResponse
}
