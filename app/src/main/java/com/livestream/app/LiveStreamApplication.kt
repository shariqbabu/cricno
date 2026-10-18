package com.livestream.app

import android.app.Application
import android.util.Log

class LiveStreamApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("LiveStreamApp", "LiveStream Pro Application Started")
    }
}
