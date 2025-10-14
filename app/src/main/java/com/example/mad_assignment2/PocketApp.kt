package com.example.mad_assignment2

import android.app.Application
import com.google.firebase.FirebaseApp

class PocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
