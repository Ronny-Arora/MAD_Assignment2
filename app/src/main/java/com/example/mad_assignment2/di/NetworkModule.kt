package com.example.mad_assignment2.di

import com.example.mad_assignment2.data.remote.OpenLibraryApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    // Moshi with Kotlin adapter so nullable/defaults work
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val openLibrary: OpenLibraryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")              // ← MUST end with /
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi)) // ← REQUIRED
            .build()
            .create(OpenLibraryApi::class.java)
    }
}
