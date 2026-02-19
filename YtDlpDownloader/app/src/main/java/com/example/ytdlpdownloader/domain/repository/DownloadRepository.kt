package com.example.ytdlpdownloader.domain.repository

import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.Quality
import com.example.ytdlpdownloader.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    
    /**
     * Get all downloads as a flow
     */
    fun getAllDownloads(): Flow<List<DownloadItem>>
    
    /**
     * Get active downloads (downloading or paused)
     */
    fun getActiveDownloads(): Flow<List<DownloadItem>>
    
    /**
     * Get completed downloads
     */
    fun getCompletedDownloads(): Flow<List<DownloadItem>>
    
    /**
     * Get a specific download by ID
     */
    suspend fun getDownloadById(id: String): DownloadItem?
    
    /**
     * Add a new download to the queue
     */
    suspend fun addDownload(
        url: String,
        format: DownloadFormat,
        quality: Quality
    ): Result<DownloadItem>
    
    /**
     * Update download progress
     */
    suspend fun updateProgress(
        id: String,
        progress: Int,
        speed: String?,
        eta: String?,
        downloadedBytes: Long
    )
    
    /**
     * Update download status
     */
    suspend fun updateStatus(id: String, status: com.example.ytdlpdownloader.domain.model.DownloadStatus)
    
    /**
     * Mark download as completed
     */
    suspend fun completeDownload(id: String, localPath: String)
    
    /**
     * Mark download as failed
     */
    suspend fun failDownload(id: String, errorMessage: String)
    
    /**
     * Pause a download
     */
    suspend fun pauseDownload(id: String)
    
    /**
     * Resume a download
     */
    suspend fun resumeDownload(id: String)
    
    /**
     * Cancel and remove a download
     */
    suspend fun cancelDownload(id: String)
    
    /**
     * Remove a completed download from history
     */
    suspend fun removeDownload(id: String)
    
    /**
     * Clear all completed downloads
     */
    suspend fun clearCompletedDownloads()
    
    /**
     * Retry a failed download
     */
    suspend fun retryDownload(id: String)
}

interface VideoInfoRepository {
    
    /**
     * Fetch video information from URL
     */
    suspend fun getVideoInfo(url: String): Result<VideoInfo>
    
    /**
     * Fetch playlist information
     */
    suspend fun getPlaylistInfo(url: String): Result<List<VideoInfo>>
    
    /**
     * Extract available formats for a video
     */
    suspend fun getAvailableFormats(url: String): Result<List<com.example.ytdlpdownloader.domain.model.VideoFormat>>
    
    /**
     * Validate if URL is supported
     */
    suspend fun isValidUrl(url: String): Boolean
}

interface SettingsRepository {
    
    /**
     * Get default download format
     */
    suspend fun getDefaultFormat(): DownloadFormat
    
    /**
     * Set default download format
     */
    suspend fun setDefaultFormat(format: DownloadFormat)
    
    /**
     * Get default quality
     */
    suspend fun getDefaultQuality(): Quality
    
    /**
     * Set default quality
     */
    suspend fun setDefaultQuality(quality: Quality)
    
    /**
     * Get download location URI
     */
    suspend fun getDownloadLocation(): String?
    
    /**
     * Set download location
     */
    suspend fun setDownloadLocation(uri: String)
    
    /**
     * Check if user has accepted legal disclaimer
     */
    suspend fun hasAcceptedDisclaimer(): Boolean
    
    /**
     * Set legal disclaimer acceptance
     */
    suspend fun setDisclaimerAccepted(accepted: Boolean)
    
    /**
     * Get concurrent download limit
     */
    suspend fun getConcurrentDownloadLimit(): Int
    
    /**
     * Set concurrent download limit
     */
    suspend fun setConcurrentDownloadLimit(limit: Int)
    
    /**
     * Check if Wi-Fi only download is enabled
     */
    suspend fun isWifiOnlyEnabled(): Boolean
    
    /**
     * Set Wi-Fi only download
     */
    suspend fun setWifiOnly(enabled: Boolean)
}
