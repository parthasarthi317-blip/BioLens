package com.example.biolens

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

class BioLensApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize App Check Debug Provider as early as possible
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
        // Manual initialization of Firebase to ensure it's ready with App Check
        Firebase.initialize(this)
    }
}
