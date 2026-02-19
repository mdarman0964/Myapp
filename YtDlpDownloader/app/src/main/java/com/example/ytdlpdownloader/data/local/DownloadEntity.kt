package com.example.ytdlpdownloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.DownloadStatus
import com.example.ytdlpdownloader.domain.model.Quality

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null,
    val uploader: String? = null,
    val format: String,
    val quality: String,
    val status: String,
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
) {
    fun toDomainModel(): DownloadItem = DownloadItem(
        id = id,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        uploader = uploader,
        format = DownloadFormat.fromString(format),
        quality = Quality.fromString(quality),
        status = DownloadStatus.valueOf(status),
        progress = progress,
        downloadSpeed = downloadSpeed,
        eta = eta,
        fileSize = fileSize,
        downloadedBytes = downloadedBytes,
        localPath = localPath,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt,
        playlistIndex = playlistIndex,
        playlistTotal = playlistTotal
    )
    
    companion object {
        fun fromDomainModel(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
            duration = item.duration,
            uploader = item.uploader,
            format = item.format.name,
            quality = item.quality.name,
            status = item.status.name,
            progress = item.progress,
            downloadSpeed = item.downloadSpeed,
            eta = item.eta,
            fileSize = item.fileSize,
            downloadedBytes = item.downloadedBytes,
            localPath = item.localPath,
            errorMessage = item.errorMessage,
            createdAt = item.createdAt,
            completedAt = item.completedAt,
            playlistIndex = item.playlistIndex,
            playlistTotal = item.playlistTotal
        )
    }
}
