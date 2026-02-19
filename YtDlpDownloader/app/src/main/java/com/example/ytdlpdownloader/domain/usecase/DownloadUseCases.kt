package com.example.ytdlpdownloader.domain.usecase

import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.Quality
import com.example.ytdlpdownloader.domain.repository.DownloadRepository
import com.example.ytdlpdownloader.domain.repository.SettingsRepository
import com.example.ytdlpdownloader.domain.repository.VideoInfoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AddDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        url: String,
        format: DownloadFormat? = null,
        quality: Quality? = null
    ): Result<DownloadItem> {
        val actualFormat = format ?: settingsRepository.getDefaultFormat()
        val actualQuality = quality ?: settingsRepository.getDefaultQuality()
        return downloadRepository.addDownload(url, actualFormat, actualQuality)
    }
}

class GetDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> = downloadRepository.getAllDownloads()
}

class GetActiveDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> = downloadRepository.getActiveDownloads()
}

class PauseDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        downloadRepository.pauseDownload(downloadId)
    }
}

class ResumeDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        downloadRepository.resumeDownload(downloadId)
    }
}

class CancelDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        downloadRepository.cancelDownload(downloadId)
    }
}

class RetryDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        downloadRepository.retryDownload(downloadId)
    }
}

class RemoveDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        downloadRepository.removeDownload(downloadId)
    }
}

class ClearCompletedDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke() {
        downloadRepository.clearCompletedDownloads()
    }
}

class GetVideoInfoUseCase @Inject constructor(
    private val videoInfoRepository: VideoInfoRepository
) {
    suspend operator fun invoke(url: String) = videoInfoRepository.getVideoInfo(url)
}

class GetPlaylistInfoUseCase @Inject constructor(
    private val videoInfoRepository: VideoInfoRepository
) {
    suspend operator fun invoke(url: String) = videoInfoRepository.getPlaylistInfo(url)
}

class ValidateUrlUseCase @Inject constructor(
    private val videoInfoRepository: VideoInfoRepository
) {
    suspend operator fun invoke(url: String): Boolean = videoInfoRepository.isValidUrl(url)
}

class GetAvailableFormatsUseCase @Inject constructor(
    private val videoInfoRepository: VideoInfoRepository
) {
    suspend operator fun invoke(url: String) = videoInfoRepository.getAvailableFormats(url)
}
