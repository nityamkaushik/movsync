package com.nityam.movsync

import android.app.Application
import com.google.firebase.FirebaseApp
import com.nityam.movsync.di.AppContainer

class MovSyncApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        container = AppContainer(this)
    }
}
