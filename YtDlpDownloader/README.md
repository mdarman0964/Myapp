# YT-DLP Downloader for Android

A clean, educational Android downloader application built with yt-dlp as the core download engine.

## Features

- Download videos in MP4 format (multiple quality options)
- Download audio in MP3 format
- Support for playlist downloads
- Real-time download progress with speed and ETA
- Pause, resume, and cancel downloads
- Background download service with notifications
- Material Design UI with light/dark theme support
- Legal disclaimer for responsible use

## Architecture

### Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Android Views with Material Components |
| Dependency Injection | Hilt |
| Database | Room |
| Preferences | DataStore |
| Background Work | Foreground Service + WorkManager |
| Python Integration | Chaquopy |
| Download Engine | yt-dlp |

### Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  MainActivity │  │ Settings     │  │ Legal        │     │
│  │  DownloadAdapter│ │ Activity     │  │ Disclaimer   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     ViewModel Layer                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MainViewModel                           │   │
│  │  - UI State Management                               │   │
│  │  - Event Handling                                    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Use Cases   │  │   Models     │  │ Repository   │     │
│  │              │  │              │  │ Interfaces   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Room DB     │  │  DataStore   │  │  yt-dlp      │     │
│  │  (Local)     │  │  (Settings)  │  │  (Remote)    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Download Flow

```
User enters URL
       │
       ▼
┌──────────────┐
│ Validate URL │
└──────────────┘
       │
       ▼
┌──────────────┐
│ Extract Info │ ──► Show video details & format selection
└──────────────┘
       │
       ▼
┌──────────────┐
│ Add to Queue │ ──► Save to Room Database
└──────────────┘
       │
       ▼
┌──────────────┐
│ Start Service│ ──► Foreground Service with Notification
└──────────────┘
       │
       ▼
┌──────────────┐
│ yt-dlp Download│ ──► Python via Chaquopy
└──────────────┘
       │
       ▼
┌──────────────┐
│ Progress     │ ──► Real-time updates via callbacks
└──────────────┘
       │
       ▼
┌──────────────┐
│ Complete     │ ──► Save to MediaStore / App Storage
└──────────────┘
```

## Storage Handling

### Scoped Storage Compliance

The app uses Android's Scoped Storage model:

1. **App-specific directory** (Primary): Downloads are saved to `getExternalFilesDir()/Downloads/`
2. **MediaStore API**: For Android 10+, files are indexed using MediaStore
3. **SAF (Storage Access Framework)**: Users can select custom download locations

### Permissions

| Permission | Purpose | SDK |
|------------|---------|-----|
| `INTERNET` | Network access for downloads | All |
| `FOREGROUND_SERVICE` | Background download service | All |
| `POST_NOTIFICATIONS` | Download progress notifications | 33+ |
| `WRITE_EXTERNAL_STORAGE` | Legacy storage access | ≤28 |

## yt-dlp Integration

### Python Wrapper (`ytdlp_wrapper.py`)

The wrapper provides a clean Kotlin-callable interface:

```python
# Initialize with download directory
initialize(download_dir: str)

# Extract video information
extract_info(url: str) -> JSON

# Get available formats
get_available_formats(url: str) -> JSON

# Download with progress callback
download(url, download_id, format_type, quality) -> JSON

# Cancel active download
cancel_download(download_id: str)

# Validate URL
is_valid_url(url: str) -> JSON
```

### Kotlin Service (`YtdlpService.kt`)

Wraps Python calls using Chaquopy:

```kotlin
// Extract video info
val result = ytdlpService.extractVideoInfo(url)

// Start download with progress
ytdlpService.startDownloadWithProgress(
    url = url,
    downloadId = id,
    format = DownloadFormat.MP4,
    quality = Quality.BEST,
    onProgress = { progress -> /* Update UI */ }
)
```

## Building the Project

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Python 3.11 (for Chaquopy)

### Build Steps

1. Clone the repository:
```bash
git clone <repository-url>
cd YtDlpDownloader
```

2. Open in Android Studio and sync project

3. Build debug APK:
```bash
./gradlew assembleDebug
```

4. Build release APK:
```bash
./gradlew assembleRelease
```

### Signing Configuration

Create `keystore.properties` in project root:

```properties
storeFile=my-release-key.jks
storePassword=your-store-password
keyAlias=my-key-alias
keyPassword=your-key-password
```

## Project Structure

```
YtDlpDownloader/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ytdlpdownloader/
│   │   │   ├── data/
│   │   │   │   ├── local/          # Room DB, DataStore
│   │   │   │   ├── remote/         # yt-dlp service
│   │   │   │   └── repository/     # Repository implementations
│   │   │   ├── domain/
│   │   │   │   ├── model/          # Data classes
│   │   │   │   ├── repository/     # Repository interfaces
│   │   │   │   └── usecase/        # Use cases
│   │   │   ├── service/            # DownloadService
│   │   │   ├── ui/
│   │   │   │   ├── main/           # MainActivity, ViewModel
│   │   │   │   └── settings/       # Settings, Disclaimer
│   │   │   ├── di/                 # Hilt modules
│   │   │   └── YtdlpApplication.kt
│   │   ├── python/
│   │   │   └── ytdlp_wrapper.py    # Python wrapper
│   │   └── res/                    # Layouts, strings, etc.
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Legal Disclaimer

This application is for **educational and personal use only**.

### Permitted Use:
- Downloading content you own
- Public domain or Creative Commons content
- Personal backups of your content
- Educational research

### Prohibited Use:
- Downloading DRM-protected content
- Redistributing content without permission
- Commercial use without authorization
- Violating copyright laws

**Users are solely responsible for complying with applicable laws.**

## License

```
Copyright 2024 YT-DLP Downloader Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) - The download engine
- [Chaquopy](https://chaquo.com/chaquopy/) - Python for Android
- [Material Components](https://material.io/components) - UI components
