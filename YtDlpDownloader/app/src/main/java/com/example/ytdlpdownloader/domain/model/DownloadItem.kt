package com.example.ytdlpdownloader.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null,
    val uploader: String? = null,
    val format: DownloadFormat,
    val quality: Quality,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val downloadSpeed: String? = null,
    val eta: String? = null,
    val fileSize: Long? = null,
    val downloadedBytes: Long = 0,
    val localPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val playlistIndex: Int? = null,
    val playlistTotal: Int? = null
) : Parcelable {
    
    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED
    
    val canPause: Boolean
        get() = status == DownloadStatus.DOWNLOADING
    
    val canResume: Boolean
        get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED
    
    val canCancel: Boolean
        get() = status == DownloadStatus.DOWNLOADING || 
                status == DownloadStatus.PAUSED || 
                status == DownloadStatus.PENDING
    
    val displayTitle: String
        get() = title ?: url
    
    val isPlaylistItem: Boolean
        get() = playlistIndex != null && playlistTotal != null
}

enum class DownloadFormat {
    MP4,
    MP3;
    
    companion object {
        fun fromString(value: String): DownloadFormat = when(value.uppercase()) {
            "MP3" -> MP3
            else -> MP4
        }
    }
}

enum class Quality {
    BEST,
    HIGH,
    MEDIUM,
    LOW;
    
    companion object {
        fun fromString(value: String): Quality = when(value.uppercase()) {
            "BEST" -> BEST
            "HIGH" -> HIGH
            "LOW" -> LOW
            else -> MEDIUM
        }
    }
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;
    
    fun displayName(): String = when(this) {
        PENDING -> "Pending"
        DOWNLOADING -> "Downloading"
        PAUSED -> "Paused"
        COMPLETED -> "Completed"
        FAILED -> "Failed"
        CANCELLED -> "Cancelled"
    }
}
