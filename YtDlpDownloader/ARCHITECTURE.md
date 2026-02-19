# YT-DLP Downloader - Architecture Guide

This document explains the architecture and design decisions in beginner-friendly terms.

## Table of Contents
1. [Overview](#overview)
2. [Tech Stack Explained](#tech-stack-explained)
3. [Architecture Layers](#architecture-layers)
4. [Download Flow](#download-flow)
5. [Storage Handling](#storage-handling)
6. [yt-dlp Integration](#yt-dlp-integration)
7. [Background Downloads](#background-downloads)
8. [Error Handling](#error-handling)

## Overview

This app follows **Clean Architecture** principles, which means we separate the code into different layers. Think of it like organizing a kitchen:
- **UI Layer** = The dining area (what customers see)
- **Domain Layer** = The recipes (business logic)
- **Data Layer** = The pantry and ingredients (data storage)

This separation makes the code easier to maintain, test, and expand.

## Tech Stack Explained

### Kotlin
The main programming language for Android. It's modern, safe, and concise.

### Hilt (Dependency Injection)
**What it does:** Automatically provides objects when they're needed.

**Analogy:** Like a restaurant kitchen where ingredients are prepped and ready when the chef needs them.

```kotlin
// Without Hilt - you have to create everything manually
val database = DownloadDatabase.getDatabase(context)
val dao = database.downloadDao()
val repository = DownloadRepositoryImpl(dao)

// With Hilt - objects are automatically provided
@Inject
lateinit var repository: DownloadRepository
```

### Room (Database)
**What it does:** Stores download information locally on the device.

**Stores:**
- Download URLs
- Progress percentages
- Status (pending, downloading, completed, etc.)
- File paths

### Chaquopy (Python for Android)
**What it does:** Allows running Python code (yt-dlp) inside an Android app.

**Why needed:** yt-dlp is written in Python, so we need a way to run it on Android.

### yt-dlp
**What it does:** The actual download engine that extracts and downloads videos from websites.

## Architecture Layers

### 1. UI Layer

**Purpose:** Display information and handle user interactions.

**Key Components:**
- `MainActivity.kt` - Main screen with URL input and download list
- `DownloadAdapter.kt` - RecyclerView adapter for showing downloads
- `SettingsActivity.kt` - Settings screen
- `LegalDisclaimerActivity.kt` - Legal information screen

**Responsibilities:**
- Show download progress
- Handle button clicks
- Display error messages
- Navigate between screens

### 2. ViewModel Layer

**Purpose:** Manage UI-related data and survive configuration changes (like screen rotation).

**Key Component:** `MainViewModel.kt`

**What it does:**
```kotlin
class MainViewModel @Inject constructor(
    private val addDownloadUseCase: AddDownloadUseCase,
    private val getDownloadsUseCase: GetDownloadsUseCase,
    // ... more use cases
) : ViewModel() {
    
    // UI State - what the screen should show
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Downloads list - automatically updates when database changes
    val allDownloads: StateFlow<List<DownloadItem>> = getDownloadsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

### 3. Domain Layer

**Purpose:** Contains business logic - the rules of what the app does.

#### Models
Data classes that represent app concepts:
```kotlin
data class DownloadItem(
    val id: String,           // Unique identifier
    val url: String,          // Video URL
    val title: String?,       // Video title
    val format: DownloadFormat, // MP4 or MP3
    val quality: Quality,     // Best, High, Medium, Low
    val status: DownloadStatus, // Pending, Downloading, etc.
    val progress: Int,        // 0-100
    // ... more fields
)
```

#### Repository Interfaces
Define what operations are possible:
```kotlin
interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    suspend fun addDownload(url: String, format: DownloadFormat, quality: Quality): Result<DownloadItem>
    suspend fun pauseDownload(id: String)
    suspend fun resumeDownload(id: String)
    suspend fun cancelDownload(id: String)
    // ... more operations
}
```

#### Use Cases
Single-purpose operations:
```kotlin
class AddDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        url: String,
        format: DownloadFormat? = null,
        quality: Quality? = null
    ): Result<DownloadItem> {
        // Use default settings if not specified
        val actualFormat = format ?: settingsRepository.getDefaultFormat()
        val actualQuality = quality ?: settingsRepository.getDefaultQuality()
        return downloadRepository.addDownload(url, actualFormat, actualQuality)
    }
}
```

### 4. Data Layer

**Purpose:** Actually get and store data.

#### Local Storage (Room Database)
```kotlin
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val status: String,
    val progress: Int,
    // ... more fields
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    @Insert
    suspend fun insertDownload(download: DownloadEntity)
    
    @Update
    suspend fun updateDownload(download: DownloadEntity)
}
```

#### Remote Service (yt-dlp)
```kotlin
class YtdlpService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ytdlpModule: PyObject by lazy { 
        python.getModule("ytdlp_wrapper") 
    }
    
    fun extractVideoInfo(url: String): Result<VideoInfo> {
        val resultJson = ytdlpModule.callAttr("extract_info", url).toString()
        // Parse JSON and return VideoInfo
    }
    
    suspend fun startDownload(
        url: String,
        format: DownloadFormat,
        quality: Quality
    ): Result<DownloadResult> {
        // Call Python download function
    }
}
```

## Download Flow

Here's what happens when you tap "Download":

```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: User enters URL and taps "Download"                     │
│                                                                 │
│ UI: MainActivity → ViewModel: startDownload()                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Add to database queue                                   │
│                                                                 │
│ Domain: AddDownloadUseCase → Data: DownloadRepositoryImpl       │
│ Creates DownloadItem with status = PENDING                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: Start foreground service                                │
│                                                                 │
│ Service: DownloadService starts and shows notification          │
│ "Download Service - Managing active downloads"                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: yt-dlp downloads the file                               │
│                                                                 │
│ Python: ytdlp_wrapper.py → yt-dlp library                       │
│ Progress callbacks update every few seconds                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: Progress updates                                        │
│                                                                 │
│ Progress → Repository → Database → Flow → UI                    │
│ Progress bar updates automatically                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 6: Download completes                                      │
│                                                                 │
│ Status changes to COMPLETED                                     │
│ Notification shows "Download Complete"                          │
│ File is saved to app storage                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Storage Handling

### Scoped Storage (Android 10+)

Android 10+ requires apps to use Scoped Storage. This means:

1. **App-specific directory** (no permissions needed):
   ```kotlin
   val downloadsDir = context.getExternalFilesDir("Downloads")
   // Results in: /storage/emulated/0/Android/data/com.example.ytdlpdownloader/files/Downloads/
   ```

2. **MediaStore** (for files visible to other apps):
   ```kotlin
   val values = ContentValues().apply {
       put(MediaStore.Downloads.DISPLAY_NAME, filename)
       put(MediaStore.Downloads.MIME_TYPE, mimeType)
       put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
   }
   val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
   ```

3. **SAF (Storage Access Framework)** (for user-selected locations):
   ```kotlin
   val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
   startActivityForResult(intent, REQUEST_CODE)
   ```

### File Paths

| Location | Path | Visibility |
|----------|------|------------|
| App External | `/Android/data/com.example.ytdlpdownloader/files/Downloads/` | App only |
| MediaStore | `/Download/` | All apps |
| SAF Selected | User chosen | User controlled |

## yt-dlp Integration

### Python Wrapper (`ytdlp_wrapper.py`)

The wrapper translates between Kotlin and yt-dlp:

```python
class YtdlpWrapper:
    def __init__(self, download_dir: str):
        self.download_dir = download_dir
    
    def extract_info(self, url: str) -> dict:
        """Get video information without downloading"""
        ydl_opts = {
            'quiet': True,
            'skip_download': True,
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            return ydl.extract_info(url, download=False)
    
    def download(self, url: str, download_id: str, 
                 format_type: str, quality: str) -> dict:
        """Download a video with progress tracking"""
        
        def progress_hook(d):
            """Called by yt-dlp during download"""
            if d['status'] == 'downloading':
                progress = {
                    'status': 'downloading',
                    'downloaded_bytes': d.get('downloaded_bytes', 0),
                    'total_bytes': d.get('total_bytes', 0),
                    'speed': d.get('speed', 0),
                    'eta': d.get('eta', 0),
                }
                print(f"PROGRESS:{json.dumps(progress)}")
        
        ydl_opts = {
            'format': self._build_format_string(format_type, quality),
            'outtmpl': os.path.join(self.download_dir, '%(title)s.%(ext)s'),
            'progress_hooks': [progress_hook],
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            return {'success': True, 'filename': ydl.prepare_filename(info)}
```

### Kotlin Service (`YtdlpService.kt`)

```kotlin
class YtdlpService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ytdlpModule: PyObject by lazy { 
        python.getModule("ytdlp_wrapper") 
    }
    
    fun extractVideoInfo(url: String): Result<VideoInfo> {
        return try {
            // Call Python function
            val resultJson = ytdlpModule.callAttr("extract_info", url).toString()
            
            // Parse JSON response
            val info = gson.fromJson(resultJson, Map::class.java)
            
            if (info.containsKey("error")) {
                Result.failure(Exception(info["error"] as String))
            } else {
                Result.success(parseVideoInfo(info))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun startDownloadWithProgress(
        url: String,
        downloadId: String,
        format: DownloadFormat,
        quality: Quality,
        onProgress: (DownloadProgress) -> Unit
    ): Job {
        return serviceScope.launch {
            try {
                // Start Python download
                val result = withContext(Dispatchers.IO) {
                    ytdlpModule.callAttr(
                        "download",
                        url,
                        downloadId,
                        format.name.lowercase(),
                        quality.name.lowercase()
                    ).toString()
                }
                
                // Parse result
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
            }
        }
    }
}
```

## Background Downloads

### Foreground Service

A foreground service keeps downloads running even when the app is closed:

```kotlin
class DownloadService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        // Show persistent notification
        startForeground(NOTIFICATION_ID, createServiceNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> startDownload(url, format, quality)
            ACTION_PAUSE_DOWNLOAD -> pauseDownload(downloadId)
            ACTION_RESUME_DOWNLOAD -> resumeDownload(downloadId)
            ACTION_CANCEL_DOWNLOAD -> cancelDownload(downloadId)
        }
        return START_NOT_STICKY
    }
    
    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_DOWNLOAD_PROGRESS)
            .setContentTitle("Download Service")
            .setContentText("Managing active downloads")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .build()
    }
}
```

### Notification Channels

| Channel | Purpose | Importance |
|---------|---------|------------|
| download_progress | Active download progress | Low (no sound) |
| download_complete | Download finished | Default |
| download_error | Download failed | High (alerts user) |

## Error Handling

### Types of Errors

1. **Network Errors**
   - No internet connection
   - Server not responding
   - Timeout

2. **URL Errors**
   - Invalid URL format
   - Unsupported website
   - Video not found

3. **Storage Errors**
   - Not enough space
   - Permission denied
   - File already exists

4. **yt-dlp Errors**
   - Video unavailable
   - Age-restricted content
   - Geo-blocked content

### Error Handling Strategy

```kotlin
// Repository layer
try {
    ytdlpService.startDownload(url, format, quality)
} catch (e: NetworkException) {
    downloadRepository.failDownload(id, "Network error. Check your connection.")
} catch (e: StorageException) {
    downloadRepository.failDownload(id, "Not enough storage space.")
} catch (e: YtdlpException) {
    downloadRepository.failDownload(id, e.message ?: "Download failed")
}

// UI layer
viewModel.events.collect { event ->
    when (event) {
        is MainEvent.ShowError -> {
            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
                .show()
        }
    }
}
```

## Summary

This architecture provides:
- **Separation of concerns** - Each layer has a specific job
- **Testability** - Easy to write unit tests for each component
- **Maintainability** - Changes in one layer don't affect others
- **Scalability** - Easy to add new features

The download flow goes:
1. **UI** captures user input
2. **ViewModel** processes the action
3. **Domain** applies business rules
4. **Data** executes the operation
5. **yt-dlp** performs the actual download
6. Results flow back up to update the UI
