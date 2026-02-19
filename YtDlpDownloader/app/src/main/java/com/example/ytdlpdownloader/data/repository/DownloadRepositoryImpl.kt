package com.example.ytdlpdownloader.data.repository

import com.example.ytdlpdownloader.data.local.DownloadDao
import com.example.ytdlpdownloader.data.local.DownloadEntity
import com.example.ytdlpdownloader.domain.model.*
import com.example.ytdlpdownloader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {
    
    override fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getActiveDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getActiveDownloads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getCompletedDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getCompletedDownloads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getDownloadById(id: String): DownloadItem? {
        return downloadDao.getDownloadById(id)?.toDomainModel()
    }
    
    override suspend fun addDownload(
        url: String,
        format: DownloadFormat,
        quality: Quality
    ): Result<DownloadItem> {
        return try {
            val downloadItem = DownloadItem(
                id = UUID.randomUUID().toString(),
                url = url,
                format = format,
                quality = quality,
                status = DownloadStatus.PENDING
            )
            
            downloadDao.insertDownload(DownloadEntity.fromDomainModel(downloadItem))
            Timber.d("Added download: ${downloadItem.id}")
            Result.success(downloadItem)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add download")
            Result.failure(e)
        }
    }
    
    override suspend fun updateProgress(
        id: String,
        progress: Int,
        speed: String?,
        eta: String?,
        downloadedBytes: Long
    ) {
        try {
            downloadDao.updateProgress(id, progress, speed, eta, downloadedBytes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update progress for $id")
        }
    }
    
    override suspend fun updateStatus(id: String, status: DownloadStatus) {
        try {
            downloadDao.updateStatus(id, status.name)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update status for $id")
        }
    }
    
    override suspend fun completeDownload(id: String, localPath: String) {
        try {
            downloadDao.completeDownload(id, localPath, System.currentTimeMillis())
            Timber.d("Download completed: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to complete download $id")
        }
    }
    
    override suspend fun failDownload(id: String, errorMessage: String) {
        try {
            downloadDao.failDownload(id, errorMessage)
            Timber.d("Download failed: $id - $errorMessage")
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark download as failed $id")
        }
    }
    
    override suspend fun pauseDownload(id: String) {
        try {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
            Timber.d("Download paused: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause download $id")
        }
    }
    
    override suspend fun resumeDownload(id: String) {
        try {
            downloadDao.updateStatus(id, DownloadStatus.PENDING.name)
            Timber.d("Download resumed: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume download $id")
        }
    }
    
    override suspend fun cancelDownload(id: String) {
        try {
            downloadDao.deleteDownloadById(id)
            Timber.d("Download cancelled and removed: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel download $id")
        }
    }
    
    override suspend fun removeDownload(id: String) {
        try {
            // TODO: Also delete the actual file if needed
            downloadDao.deleteDownloadById(id)
            Timber.d("Download removed from history: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove download $id")
        }
    }
    
    override suspend fun clearCompletedDownloads() {
        try {
            downloadDao.clearCompletedDownloads()
            Timber.d("Cleared completed downloads")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear completed downloads")
        }
    }
    
    override suspend fun retryDownload(id: String) {
        try {
            val download = downloadDao.getDownloadById(id)
            download?.let {
                val updated = it.copy(
                    status = DownloadStatus.PENDING.name,
                    progress = 0,
                    errorMessage = null
                )
                downloadDao.insertDownload(updated)
            }
            Timber.d("Download queued for retry: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to retry download $id")
        }
    }
}
