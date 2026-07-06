package com.rodgers.haireel

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HaiReelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }
}
