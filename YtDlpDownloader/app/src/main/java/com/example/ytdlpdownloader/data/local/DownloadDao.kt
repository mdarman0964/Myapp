package com.example.ytdlpdownloader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'PAUSED', 'PENDING') ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)
    
    @Update
    suspend fun updateDownload(download: DownloadEntity)
    
    @Query("UPDATE downloads SET progress = :progress, downloadSpeed = :speed, eta = :eta, downloadedBytes = :downloadedBytes WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, speed: String?, eta: String?, downloadedBytes: Long)
    
    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
    
    @Query("UPDATE downloads SET status = 'COMPLETED', localPath = :localPath, completedAt = :completedAt WHERE id = :id")
    suspend fun completeDownload(id: String, localPath: String, completedAt: Long)
    
    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :errorMessage WHERE id = :id")
    suspend fun failDownload(id: String, errorMessage: String)
    
    @Delete
    suspend fun deleteDownload(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)
    
    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompletedDownloads()
    
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('DOWNLOADING', 'PENDING')")
    suspend fun getActiveDownloadCount(): Int
}
