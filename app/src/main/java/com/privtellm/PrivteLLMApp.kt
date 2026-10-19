package com.privtellm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PrivteLLMApp : Application() {

    companion object {
        const val CHANNEL_ID = "privtellm_server_channel"
        const val CHANNEL_NAME = "Local AI Server & Inference"
        lateinit var instance: PrivteLLMApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running embedded OpenAI API server and local quantized LLM inference"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
