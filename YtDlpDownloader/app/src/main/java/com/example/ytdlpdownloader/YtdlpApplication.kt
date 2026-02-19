package com.example.ytdlpdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YtdlpApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DOWNLOAD_PROGRESS,
                    getString(R.string.channel_download_progress),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.channel_download_progress_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_DOWNLOAD_COMPLETE,
                    getString(R.string.channel_download_complete),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.channel_download_complete_desc)
                },
                NotificationChannel(
                    CHANNEL_DOWNLOAD_ERROR,
                    getString(R.string.channel_download_error),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_download_error_desc)
                }
            )

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(channels)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "download_progress"
        const val CHANNEL_DOWNLOAD_COMPLETE = "download_complete"
        const val CHANNEL_DOWNLOAD_ERROR = "download_error"
    }
}
