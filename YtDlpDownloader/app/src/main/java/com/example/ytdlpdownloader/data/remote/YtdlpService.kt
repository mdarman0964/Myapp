package com.example.ytdlpdownloader.data.remote

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.ytdlpdownloader.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtdlpService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val python: Python by lazy { Python.getInstance() }
    private val ytdlpModule: PyObject by lazy { python.getModule("ytdlp_wrapper") }
    private val gson = Gson()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _progressFlow = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)
    val progressFlow: SharedFlow<DownloadProgress> = _progressFlow.asSharedFlow()
    
    private val activeJobs = mutableMapOf<String, Job>()
    
    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        initializeWrapper()
    }
    
    private fun initializeWrapper() {
        try {
            val downloadDir = getDownloadDirectory().absolutePath
            ytdlpModule.callAttr("initialize", downloadDir)
            Timber.d("yt-dlp wrapper initialized with download dir: $downloadDir")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize yt-dlp wrapper")
        }
    }
    
    fun extractVideoInfo(url: String): Result<VideoInfo> {
        return try {
            val resultJson = ytdlpModule.callAttr("extract_info", url).toString()
            val info = gson.fromJson(resultJson, Map::class.java)
            
            if (info.containsKey("error")) {
                return Result.failure(Exception(info["error"] as String))
            }
            
            val videoInfo = parseVideoInfo(info, url)
            Result.success(videoInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract video info")
            Result.failure(e)
        }
    }
    
    fun getAvailableFormats(url: String): Result<List<VideoFormat>> {
        return try {
            val resultJson = ytdlpModule.callAttr("get_available_formats", url).toString()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val formats: List<Map<String, Any>> = gson.fromJson(resultJson, type)
            
            val videoFormats = formats.map { format ->
                VideoFormat(
                    formatId = format["format_id"] as? String ?: "",
                    ext = format["ext"] as? String ?: "",
                    quality = format["quality"] as? String ?: "unknown",
                    resolution = format["resolution"] as? String,
                    fileSize = (format["filesize"] as? Number)?.toLong(),
                    hasVideo = format["has_video"] as? Boolean ?: true,
                    hasAudio = format["has_audio"] as? Boolean ?: true,
                    videoCodec = format["video_codec"] as? String,
                    audioCodec = format["audio_codec"] as? String,
                    fps = (format["fps"] as? Number)?.toInt(),
                    bitrate = (format["bitrate"] as? Number)?.toLong()
                )
            }
            Result.success(videoFormats)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available formats")
            Result.failure(e)
        }
    }
    
    suspend fun startDownload(
        url: String,
        format: DownloadFormat,
        quality: Quality
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        
        try {
            // Run download in a coroutine
            val result = async {
                ytdlpModule.callAttr(
                    "download",
                    url,
                    downloadId,
                    format.name.lowercase(),
                    quality.name.lowercase()
                ).toString()
            }.await()
            
            val resultMap = gson.fromJson(result, Map::class.java)
            
            if (resultMap["success"] as? Boolean == true) {
                Result.success(
                    DownloadResult(
                        downloadId = downloadId,
                        filePath = resultMap["filename"] as String,
                        title = resultMap["title"] as? String,
                        duration = (resultMap["duration"] as? Number)?.toLong(),
                        uploader = resultMap["uploader"] as? String
                    )
                )
            } else {
                val error = resultMap["error"] as? String ?: "Unknown error"
                Result.failure(Exception(error))
            }
        } catch (e: CancellationException) {
            cancelDownload(downloadId)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            Result.failure(e)
        }
    }
    
    fun startDownloadWithProgress(
        url: String,
        downloadId: String,
        format: DownloadFormat,
        quality: Quality,
        onProgress: (DownloadProgress) -> Unit
    ): Job {
        val job = serviceScope.launch {
            try {
                // Capture progress from Python stdout
                val stdOut = python.getModule("sys").get("stdout")
                
                val result = withContext(Dispatchers.IO) {
                    ytdlpModule.callAttr(
                        "download",
                        url,
                        downloadId,
                        format.name.lowercase(),
                        quality.name.lowercase()
                    ).toString()
                }
                
                val resultMap = gson.fromJson(result, Map::class.java)
                
                if (resultMap["success"] as? Boolean == true) {
                    onProgress(DownloadProgress.Completed(
                        downloadId = downloadId,
                        filePath = resultMap["filename"] as String
                    ))
                } else {
                    onProgress(DownloadProgress.Error(
                        downloadId = downloadId,
                        error = resultMap["error"] as? String ?: "Unknown error"
                    ))
                }
            } catch (e: CancellationException) {
                cancelDownload(downloadId)
                onProgress(DownloadProgress.Cancelled(downloadId))
            } catch (e: Exception) {
                onProgress(DownloadProgress.Error(
                    downloadId = downloadId,
                    error = e.message ?: "Unknown error"
                ))
            }
        }
        
        activeJobs[downloadId] = job
        return job
    }
    
    fun cancelDownload(downloadId: String) {
        try {
            activeJobs[downloadId]?.cancel()
            activeJobs.remove(downloadId)
            ytdlpModule.callAttr("cancel_download", downloadId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel download")
        }
    }
    
    fun isValidUrl(url: String): Boolean {
        return try {
            val resultJson = ytdlpModule.callAttr("is_valid_url", url).toString()
            val result = gson.fromJson(resultJson, Map::class.java)
            result["valid"] as? Boolean ?: false
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate URL")
            false
        }
    }
    
    private fun parseVideoInfo(info: Map<*, *>, url: String): VideoInfo {
        val formats = (info["formats"] as? List<Map<*, *>>)?.map { fmt ->
            VideoFormat(
                formatId = fmt["format_id"] as? String ?: "",
                ext = fmt["ext"] as? String ?: "",
                quality = fmt["quality"] as? String ?: "unknown",
                resolution = fmt["resolution"] as? String,
                fileSize = (fmt["filesize"] as? Number)?.toLong()
            )
        } ?: emptyList()
        
        return VideoInfo(
            id = info["id"] as? String ?: "",
            url = url,
            title = info["title"] as? String ?: "Unknown",
            description = info["description"] as? String,
            thumbnailUrl = info["thumbnail"] as? String,
            duration = (info["duration"] as? Number)?.toLong(),
            uploader = info["uploader"] as? String,
            uploadDate = info["upload_date"] as? String,
            viewCount = (info["view_count"] as? Number)?.toLong(),
            isPlaylist = info["is_playlist"] as? Boolean ?: false,
            playlistCount = (info["playlist_count"] as? Number)?.toInt(),
            formats = formats
        )
    }
    
    private fun getDownloadDirectory(): File {
        val downloadsDir = context.getExternalFilesDir(null) 
            ?: context.filesDir
        val appDownloadDir = File(downloadsDir, "Downloads")
        if (!appDownloadDir.exists()) {
            appDownloadDir.mkdirs()
        }
        return appDownloadDir
    }
    
    fun cleanup() {
        serviceScope.cancel()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
    
    sealed class DownloadProgress {
        abstract val downloadId: String
        
        data class Downloading(
            override val downloadId: String,
            val percent: Float,
            val speed: String,
            val eta: String,
            val downloadedBytes: Long,
            val totalBytes: Long
        ) : DownloadProgress()
        
        data class Completed(
            override val downloadId: String,
            val filePath: String
        ) : DownloadProgress()
        
        data class Error(
            override val downloadId: String,
            val error: String
        ) : DownloadProgress()
        
        data class Cancelled(override val downloadId: String) : DownloadProgress()
    }
    
    data class DownloadResult(
        val downloadId: String,
        val filePath: String,
        val title: String?,
        val duration: Long?,
        val uploader: String?
    )
}
