package com.example.ytdlpdownloader.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoInfo(
    val id: String,
    val url: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null,
    val uploader: String? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val isPlaylist: Boolean = false,
    val playlistCount: Int? = null,
    val formats: List<VideoFormat> = emptyList(),
    val subtitles: List<SubtitleInfo> = emptyList()
) : Parcelable {
    
    val displayDuration: String
        get() = duration?.let { formatDuration(it) } ?: "Unknown"
    
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}

@Parcelize
data class VideoFormat(
    val formatId: String,
    val ext: String,
    val quality: String,
    val resolution: String? = null,
    val fileSize: Long? = null,
    val hasVideo: Boolean = true,
    val hasAudio: Boolean = true,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val fps: Int? = null,
    val bitrate: Long? = null
) : Parcelable {
    
    val displayQuality: String
        get() = when {
            resolution != null && resolution != "audio only" -> resolution
            quality.contains("audio") -> "Audio Only"
            else -> quality
        }
    
    val displayFileSize: String
        get() = fileSize?.let { formatFileSize(it) } ?: "Unknown"
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }
}

@Parcelize
data class SubtitleInfo(
    val language: String,
    val name: String,
    val url: String
) : Parcelable

@Parcelize
data class PlaylistInfo(
    val id: String,
    val title: String,
    val uploader: String? = null,
    val videoCount: Int,
    val thumbnailUrl: String? = null
) : Parcelable
