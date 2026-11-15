package com.learningtoolshed.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/* One channel for "a new idea landed", so the notification has a
   name the teacher can recognise and silence separately. */
class ToolshedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New ideas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "An idea arrived in the Ideas Engine"
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ideas"
    }
}