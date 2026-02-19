# Developer Guide - YT-DLP Downloader

A step-by-step guide to understanding and modifying this Android downloader app.

## Table of Contents
1. [Getting Started](#getting-started)
2. [Understanding the Code](#understanding-the-code)
3. [Adding New Features](#adding-new-features)
4. [Common Tasks](#common-tasks)
5. [Troubleshooting](#troubleshooting)

## Getting Started

### Prerequisites

Before you start, you need:

1. **Android Studio** (latest stable version)
   - Download from: https://developer.android.com/studio

2. **JDK 17**
   - Usually included with Android Studio
   - Check: `java -version` in terminal

3. **Android SDK**
   - Install via Android Studio's SDK Manager
   - Required: API 34 (Android 14)

4. **Git**
   - For version control

### Opening the Project

1. Launch Android Studio
2. Click "Open" and select the `YtDlpDownloader` folder
3. Wait for Gradle sync to complete (may take 5-10 minutes first time)
4. Click "Run" (green play button) to build and install

### Project Structure

```
YtDlpDownloader/
├── app/                          # Main app module
│   ├── src/main/
│   │   ├── java/                 # Kotlin source code
│   │   │   └── com/example/ytdlpdownloader/
│   │   │       ├── data/         # Data layer
│   │   │       │   ├── local/    # Database & preferences
│   │   │       │   ├── remote/   # Network & yt-dlp
│   │   │       │   └── repository/
│   │   │       ├── domain/       # Business logic
│   │   │       │   ├── model/    # Data classes
│   │   │       │   ├── repository/
│   │   │       │   └── usecase/
│   │   │       ├── service/      # Background service
│   │   │       ├── ui/           # User interface
│   │   │       │   ├── main/     # Main screen
│   │   │       │   └── settings/ # Settings screens
│   │   │       └── di/           # Dependency injection
│   │   ├── python/               # Python code
│   │   │   └── ytdlp_wrapper.py  # yt-dlp wrapper
│   │   └── res/                  # Resources (layouts, strings, etc.)
│   └── build.gradle.kts          # App-level build config
├── build.gradle.kts              # Project-level build config
└── README.md
```

## Understanding the Code

### 1. The Download Process (Step by Step)

Let's trace what happens when a user downloads a video:

#### Step 1: User enters URL
**File:** `ui/main/MainActivity.kt`

```kotlin
// User types URL and clicks "Download"
binding.buttonDownload.setOnClickListener {
    val url = binding.editTextUrl.text.toString().trim()
    viewModel.onUrlChanged(url)
    viewModel.startDownload()
}
```

#### Step 2: ViewModel processes the request
**File:** `ui/main/MainViewModel.kt`

```kotlin
fun startDownload() {
    val state = _uiState.value
    val url = state.url.trim()
    
    viewModelScope.launch {
        // Call the use case
        addDownloadUseCase(
            url = url,
            format = state.selectedFormat,
            quality = state.selectedQuality
        ).fold(
            onSuccess = { downloadItem ->
                // Show success message
                _events.emit(MainEvent.ShowSnackbar("Download added to queue"))
            },
            onFailure = { error ->
                // Show error
                _events.emit(MainEvent.ShowError(error.message ?: "Failed"))
            }
        )
    }
}
```

#### Step 3: Use case applies business rules
**File:** `domain/usecase/DownloadUseCases.kt`

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
        
        // Save to database
        return downloadRepository.addDownload(url, actualFormat, actualQuality)
    }
}
```

#### Step 4: Repository saves to database
**File:** `data/repository/DownloadRepositoryImpl.kt`

```kotlin
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
        
        // Insert into Room database
        downloadDao.insertDownload(DownloadEntity.fromDomainModel(downloadItem))
        
        Result.success(downloadItem)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

#### Step 5: Service starts the download
**File:** `service/DownloadService.kt`

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START_DOWNLOAD -> {
            val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
            val format = DownloadFormat.valueOf(intent.getStringExtra(EXTRA_FORMAT)!!)
            val quality = Quality.valueOf(intent.getStringExtra(EXTRA_QUALITY)!!)
            startDownload(url, format, quality)
        }
    }
    return START_NOT_STICKY
}

private fun startDownload(url: String, format: DownloadFormat, quality: Quality) {
    serviceScope.launch {
        ytdlpService.startDownloadWithProgress(
            url = url,
            downloadId = downloadItem.id,
            format = format,
            quality = quality,
            onProgress = { progress ->
                handleProgress(downloadItem.id, progress)
            }
        )
    }
}
```

#### Step 6: yt-dlp downloads the file
**File:** `data/remote/YtdlpService.kt`

```kotlin
suspend fun startDownloadWithProgress(
    url: String,
    downloadId: String,
    format: DownloadFormat,
    quality: Quality,
    onProgress: (DownloadProgress) -> Unit
): Job {
    return serviceScope.launch {
        try {
            // Call Python wrapper
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
            }
        } catch (e: Exception) {
            onProgress(DownloadProgress.Error(
                downloadId = downloadId,
                error = e.message ?: "Unknown error"
            ))
        }
    }
}
```

#### Step 7: Python wrapper uses yt-dlp
**File:** `python/ytdlp_wrapper.py`

```python
def download(url: str, download_id: str, format_type: str, quality: str) -> str:
    """Download a video with progress tracking"""
    
    # Progress callback
    def progress_callback(data):
        print(f"PROGRESS:{json.dumps(data)}", flush=True)
    
    # Build format string
    format_string = _build_format_string(format_type, quality)
    
    ydl_opts = {
        'format': format_string,
        'outtmpl': os.path.join(download_dir, '%(title)s.%(ext)s'),
        'progress_hooks': [progress_callback],
    }
    
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = ydl.prepare_filename(info)
        
        return json.dumps({
            'success': True,
            'filename': filename,
            'title': info.get('title', 'Unknown')
        })
```

### 2. How Data Flows

```
User Action
    │
    ▼
┌─────────────┐
│   Activity  │  (UI Layer - handles clicks)
└─────────────┘
    │
    ▼
┌─────────────┐
│  ViewModel  │  (Manages UI state)
└─────────────┘
    │
    ▼
┌─────────────┐
│  Use Case   │  (Business logic)
└─────────────┘
    │
    ▼
┌─────────────┐
│ Repository  │  (Data operations)
└─────────────┘
    │
    ├──────────────┐
    ▼              ▼
┌─────────┐  ┌──────────┐
│ Room DB │  │ yt-dlp   │  (Data sources)
└─────────┘  └──────────┘
    │              │
    └──────────────┘
           │
           ▼
    Result updates
           │
           ▼
    Flow emits new data
           │
           ▼
    UI automatically updates
```

### 3. Key Concepts Explained

#### Flow (Kotlin Coroutines)

**What is it?** A stream of data that updates over time.

**Analogy:** Like a water pipe - data flows through it continuously.

```kotlin
// In Repository
fun getAllDownloads(): Flow<List<DownloadItem>> {
    return downloadDao.getAllDownloads().map { entities ->
        entities.map { it.toDomainModel() }
    }
}

// In ViewModel
val allDownloads: StateFlow<List<DownloadItem>> = getDownloadsUseCase()
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

// In Activity - automatically updates when data changes
lifecycleScope.launch {
    viewModel.allDownloads.collectLatest { downloads ->
        downloadAdapter.submitList(downloads)
    }
}
```

#### Dependency Injection (Hilt)

**What is it?** Automatically provides objects when needed.

**Analogy:** Like a restaurant where ingredients are prepped and ready.

```kotlin
// Define how to create an object
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDownloadDatabase(@ApplicationContext context: Context): DownloadDatabase {
        return DownloadDatabase.getDatabase(context)
    }
}

// Use it anywhere - Hilt automatically provides it
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {
    // downloadDao is automatically created and provided
}
```

#### Repository Pattern

**What is it?** Abstracts data sources from the rest of the app.

**Benefit:** Can change database or API without affecting other code.

```kotlin
// Interface - defines what operations are possible
interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    suspend fun addDownload(url: String, format: DownloadFormat, quality: Quality): Result<DownloadItem>
    suspend fun pauseDownload(id: String)
    // ...
}

// Implementation - how it's actually done
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {
    override fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { 
            it.map { entity -> entity.toDomainModel() } 
        }
    }
    // ...
}
```

## Adding New Features

### Example: Add a "Favorite Downloads" Feature

#### Step 1: Update the database
**File:** `data/local/DownloadEntity.kt`

```kotlin
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    // ... existing fields ...
    val isFavorite: Boolean = false  // Add this field
)
```

#### Step 2: Update the DAO
**File:** `data/local/DownloadDao.kt`

```kotlin
@Dao
interface DownloadDao {
    // ... existing methods ...
    
    @Query("SELECT * FROM downloads WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteDownloads(): Flow<List<DownloadEntity>>
    
    @Query("UPDATE downloads SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
```

#### Step 3: Update the model
**File:** `domain/model/DownloadItem.kt`

```kotlin
data class DownloadItem(
    val id: String,
    val url: String,
    // ... existing fields ...
    val isFavorite: Boolean = false
)
```

#### Step 4: Update the repository
**File:** `domain/repository/DownloadRepository.kt`

```kotlin
interface DownloadRepository {
    // ... existing methods ...
    fun getFavoriteDownloads(): Flow<List<DownloadItem>>
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
```

**File:** `data/repository/DownloadRepositoryImpl.kt`

```kotlin
override fun getFavoriteDownloads(): Flow<List<DownloadItem>> {
    return downloadDao.getFavoriteDownloads().map { entities ->
        entities.map { it.toDomainModel() }
    }
}

override suspend fun setFavorite(id: String, isFavorite: Boolean) {
    downloadDao.setFavorite(id, isFavorite)
}
```

#### Step 5: Create a use case
**File:** `domain/usecase/DownloadUseCases.kt`

```kotlin
class ToggleFavoriteUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String, isFavorite: Boolean) {
        downloadRepository.setFavorite(downloadId, isFavorite)
    }
}

class GetFavoriteDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> {
        return downloadRepository.getFavoriteDownloads()
    }
}
```

#### Step 6: Update the ViewModel
**File:** `ui/main/MainViewModel.kt`

```kotlin
class MainViewModel @Inject constructor(
    // ... existing use cases ...
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getFavoriteDownloadsUseCase: GetFavoriteDownloadsUseCase
) : ViewModel() {
    
    // ... existing code ...
    
    val favoriteDownloads: StateFlow<List<DownloadItem>> = 
        getFavoriteDownloadsUseCase()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun toggleFavorite(downloadId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            toggleFavoriteUseCase(downloadId, isFavorite)
        }
    }
}
```

#### Step 7: Update the UI
**File:** `ui/main/DownloadAdapter.kt`

```kotlin
class DownloadAdapter(
    // ... existing callbacks ...
    private val onFavoriteClick: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadViewHolder>(DiffCallback()) {
    
    inner class DownloadViewHolder(private val binding: ItemDownloadBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: DownloadItem) {
            // ... existing binding code ...
            
            // Add favorite button
            binding.buttonFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_favorite_filled 
                else R.drawable.ic_favorite_border
            )
            binding.buttonFavorite.setOnClickListener {
                onFavoriteClick(item)
            }
        }
    }
}
```

**File:** `ui/main/MainActivity.kt`

```kotlin
private fun setupRecyclerView() {
    downloadAdapter = DownloadAdapter(
        // ... existing callbacks ...
        onFavoriteClick = { item ->
            viewModel.toggleFavorite(item.id, !item.isFavorite)
        }
    )
    // ...
}
```

## Common Tasks

### Adding a New Setting

1. **Add to DataStore:**
```kotlin
// SettingsDataStore.kt
private val MY_SETTING = booleanPreferencesKey("my_setting")

val mySetting: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[MY_SETTING] ?: false
}

suspend fun setMySetting(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[MY_SETTING] = enabled
    }
}
```

2. **Add to Repository:**
```kotlin
// SettingsRepository.kt
suspend fun getMySetting(): Boolean
suspend fun setMySetting(enabled: Boolean)
```

3. **Add to Settings UI:**
```kotlin
// SettingsActivity.kt
binding.switchMySetting.setOnCheckedChangeListener { _, isChecked ->
    settingsRepository.setMySetting(isChecked)
}
```

### Adding Support for a New Format

1. **Add to enum:**
```kotlin
// DownloadFormat.kt
enum class DownloadFormat {
    MP4,
    MP3,
    WEBM  // Add new format
}
```

2. **Update Python wrapper:**
```python
# ytdlp_wrapper.py
def _build_format_string(self, format_type: str, quality: str) -> str:
    if format_type.lower() == 'webm':
        return 'bestvideo[ext=webm]+bestaudio[ext=webm]/best[ext=webm]/best'
    # ... existing formats ...
```

3. **Add UI option:**
```xml
<!-- activity_main.xml -->
<com.google.android.material.chip.Chip
    android:id="@+id/chipWebm"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="WEBM"
    style="@style/Widget.MaterialComponents.Chip.Choice" />
```

## Troubleshooting

### Build Errors

#### "Could not find com.chaquo.python..."
**Solution:** Make sure you have the Chaquopy repository in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://chaquo.com/maven")  // This is required
    }
}
```

#### "Python is not started"
**Solution:** Initialize Python in Application class before using it:
```kotlin
if (!Python.isStarted()) {
    Python.start(AndroidPlatform(context))
}
```

### Runtime Errors

#### "Download fails immediately"
**Check:**
1. Internet permission in manifest
2. URL is valid and supported
3. yt-dlp wrapper is initialized

#### "Progress not updating"
**Check:**
1. Flow collection in Activity is active
2. Database updates are happening
3. Progress callback is being called from Python

#### "Notification not showing"
**Check:**
1. Notification permission granted (Android 13+)
2. Notification channels created
3. Service is started as foreground service

### Debugging Tips

1. **Enable logging:**
```kotlin
Timber.plant(Timber.DebugTree())
```

2. **Check database contents:**
```kotlin
// In debug mode, add this to see database
val downloads = downloadDao.getAllDownloads().first()
Timber.d("Downloads: $downloads")
```

3. **Test Python wrapper:**
```python
# Add temporary test in ytdlp_wrapper.py
def test():
    print("Python wrapper is working!")
    return json.dumps({'status': 'ok'})
```

4. **Use Android Studio's Database Inspector:**
   - View → Tool Windows → App Inspection → Database Inspector

## Summary

This app uses modern Android architecture:
- **Clean Architecture** for separation of concerns
- **MVVM** pattern for UI
- **Repository pattern** for data
- **Dependency injection** for loose coupling
- **Kotlin Coroutines & Flow** for async operations

The download flow:
1. User enters URL → Activity
2. ViewModel processes → Use Case
3. Business rules applied → Repository
4. Data saved → Database
5. Service started → yt-dlp
6. Progress updates → UI
