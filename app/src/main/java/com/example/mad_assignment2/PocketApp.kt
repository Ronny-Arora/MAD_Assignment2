package com.example.mad_assignment2

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class PocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val opts = FirebaseApp.getInstance().options
        Log.d("FirebaseInit", "projectId=${opts.projectId}, appId=${opts.applicationId}")
    }
}
