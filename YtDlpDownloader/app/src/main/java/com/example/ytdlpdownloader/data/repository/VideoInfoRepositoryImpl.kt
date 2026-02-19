package com.example.ytdlpdownloader.data.repository

import com.example.ytdlpdownloader.data.remote.YtdlpService
import com.example.ytdlpdownloader.domain.model.VideoFormat
import com.example.ytdlpdownloader.domain.model.VideoInfo
import com.example.ytdlpdownloader.domain.repository.VideoInfoRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoInfoRepositoryImpl @Inject constructor(
    private val ytdlpService: YtdlpService
) : VideoInfoRepository {
    
    override suspend fun getVideoInfo(url: String): Result<VideoInfo> {
        return try {
            ytdlpService.extractVideoInfo(url)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get video info")
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylistInfo(url: String): Result<List<VideoInfo>> {
        return try {
            val result = ytdlpService.extractVideoInfo(url)
            result.fold(
                onSuccess = { info ->
                    if (info.isPlaylist) {
                        // For playlists, we need to extract each video
                        // This is a simplified implementation
                        Result.success(listOf(info))
                    } else {
                        Result.success(listOf(info))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get playlist info")
            Result.failure(e)
        }
    }
    
    override suspend fun getAvailableFormats(url: String): Result<List<VideoFormat>> {
        return try {
            ytdlpService.getAvailableFormats(url)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available formats")
            Result.failure(e)
        }
    }
    
    override suspend fun isValidUrl(url: String): Boolean {
        return try {
            ytdlpService.isValidUrl(url)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate URL")
            false
        }
    }
}
