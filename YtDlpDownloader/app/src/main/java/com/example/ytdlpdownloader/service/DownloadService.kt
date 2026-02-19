package com.example.ytdlpdownloader.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.ytdlpdownloader.R
import com.example.ytdlpdownloader.YtdlpApplication
import com.example.ytdlpdownloader.data.remote.YtdlpService
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.DownloadStatus
import com.example.ytdlpdownloader.domain.model.Quality
import com.example.ytdlpdownloader.domain.repository.DownloadRepository
import com.example.ytdlpdownloader.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {
    
    @Inject
    lateinit var downloadRepository: DownloadRepository
    
    @Inject
    lateinit var ytdlpService: YtdlpService
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadJob>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadJob>> = _activeDownloads.asStateFlow()
    
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
    
    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createServiceNotification())
        processDownloadQueue()
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val format = DownloadFormat.valueOf(
                    intent.getStringExtra(EXTRA_FORMAT) ?: DownloadFormat.MP4.name
                )
                val quality = Quality.valueOf(
                    intent.getStringExtra(EXTRA_QUALITY) ?: Quality.BEST.name
                )
                startDownload(url, format, quality)
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                pauseDownload(downloadId)
            }
            ACTION_RESUME_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                resumeDownload(downloadId)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                cancelDownload(downloadId)
            }
            ACTION_STOP_SERVICE -> {
                stopService()
            }
        }
        return START_NOT_STICKY
    }
    
    fun startDownload(url: String, format: DownloadFormat, quality: Quality) {
        serviceScope.launch {
            val result = downloadRepository.addDownload(url, format, quality)
            result.onSuccess { downloadItem ->
                startDownloadJob(downloadItem)
            }
        }
    }
    
    private fun startDownloadJob(downloadItem: DownloadItem) {
        val job = serviceScope.launch {
            try {
                downloadRepository.updateStatus(downloadItem.id, DownloadStatus.DOWNLOADING)
                
                ytdlpService.startDownloadWithProgress(
                    url = downloadItem.url,
                    downloadId = downloadItem.id,
                    format = downloadItem.format,
                    quality = downloadItem.quality,
                    onProgress = { progress ->
                        handleProgress(downloadItem.id, progress)
                    }
                ).join()
                
            } catch (e: CancellationException) {
                downloadRepository.updateStatus(downloadItem.id, DownloadStatus.PAUSED)
            } catch (e: Exception) {
                downloadRepository.failDownload(downloadItem.id, e.message ?: "Unknown error")
            }
        }
        
        _activeDownloads.value += (downloadItem.id to DownloadJob(
            downloadId = downloadItem.id,
            job = job
        ))
    }
    
    private fun handleProgress(downloadId: String, progress: YtdlpService.DownloadProgress) {
        when (progress) {
            is YtdlpService.DownloadProgress.Downloading -> {
                serviceScope.launch {
                    downloadRepository.updateProgress(
                        id = downloadId,
                        progress = progress.percent.toInt(),
                        speed = progress.speed.toString(),
                        eta = progress.eta.toString(),
                        downloadedBytes = progress.downloadedBytes
                    )
                    updateProgressNotification(downloadId, progress.percent.toInt())
                }
            }
            is YtdlpService.DownloadProgress.Completed -> {
                serviceScope.launch {
                    downloadRepository.completeDownload(downloadId, progress.filePath)
                    removeActiveDownload(downloadId)
                    showCompleteNotification(downloadId, progress.filePath)
                }
            }
            is YtdlpService.DownloadProgress.Error -> {
                serviceScope.launch {
                    downloadRepository.failDownload(downloadId, progress.error)
                    removeActiveDownload(downloadId)
                    showErrorNotification(downloadId, progress.error)
                }
            }
            is YtdlpService.DownloadProgress.Cancelled -> {
                serviceScope.launch {
                    downloadRepository.cancelDownload(downloadId)
                    removeActiveDownload(downloadId)
                }
            }
        }
    }
    
    private fun pauseDownload(downloadId: String) {
        _activeDownloads.value[downloadId]?.job?.cancel()
        serviceScope.launch {
            downloadRepository.pauseDownload(downloadId)
        }
    }
    
    private fun resumeDownload(downloadId: String) {
        serviceScope.launch {
            val download = downloadRepository.getDownloadById(downloadId)
            download?.let {
                downloadRepository.resumeDownload(downloadId)
                startDownloadJob(it.copy(status = DownloadStatus.PENDING))
            }
        }
    }
    
    private fun cancelDownload(downloadId: String) {
        _activeDownloads.value[downloadId]?.job?.cancel()
        ytdlpService.cancelDownload(downloadId)
        serviceScope.launch {
            downloadRepository.cancelDownload(downloadId)
            removeActiveDownload(downloadId)
        }
    }
    
    private fun removeActiveDownload(downloadId: String) {
        _activeDownloads.value -= downloadId
        if (_activeDownloads.value.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }
    
    private fun processDownloadQueue() {
        serviceScope.launch {
            downloadRepository.getActiveDownloads().collect { downloads ->
                downloads.filter { it.status == DownloadStatus.PENDING }.forEach { download ->
                    if (!_activeDownloads.value.containsKey(download.id)) {
                        startDownloadJob(download)
                    }
                }
            }
        }
    }
    
    private fun stopService() {
        serviceScope.cancel()
        wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YtDlpDownloader::DownloadWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes timeout
        }
    }
    
    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, YtdlpApplication.CHANNEL_DOWNLOAD_PROGRESS)
            .setContentTitle(getString(R.string.download_service_running))
            .setContentText(getString(R.string.download_service_description))
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateProgressNotification(downloadId: String, progress: Int) {
        val notification = NotificationCompat.Builder(this, YtdlpApplication.CHANNEL_DOWNLOAD_PROGRESS)
            .setContentTitle(getString(R.string.downloading_notification_title))
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(downloadId.hashCode(), notification)
    }
    
    private fun showCompleteNotification(downloadId: String, filePath: String) {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, YtdlpApplication.CHANNEL_DOWNLOAD_COMPLETE)
            .setContentTitle(getString(R.string.download_complete_title))
            .setContentText(getString(R.string.download_complete_text))
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(downloadId.hashCode(), notification)
    }
    
    private fun showErrorNotification(downloadId: String, error: String) {
        val notification = NotificationCompat.Builder(this, YtdlpApplication.CHANNEL_DOWNLOAD_ERROR)
            .setContentTitle(getString(R.string.download_error_title))
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_error)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(downloadId.hashCode(), notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.release()
    }
    
    data class DownloadJob(
        val downloadId: String,
        val job: Job
    )
    
    companion object {
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_PAUSE_DOWNLOAD = "action_pause_download"
        const val ACTION_RESUME_DOWNLOAD = "action_resume_download"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        
        fun startDownload(context: Context, url: String, format: DownloadFormat, quality: Quality) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT, format.name)
                putExtra(EXTRA_QUALITY, quality.name)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
        
        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
        
        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}
