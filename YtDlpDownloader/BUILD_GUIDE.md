# Build Guide - YT-DLP Downloader

This guide explains how to build the APK for installation on Android devices.

## Prerequisites

### Required Software

1. **Android Studio** (Hedgehog 2023.1.1 or newer)
   - Download: https://developer.android.com/studio
   - Includes: JDK, Android SDK, Gradle

2. **Git** (optional, for version control)
   - Download: https://git-scm.com/

### System Requirements

- **RAM:** 8GB minimum, 16GB recommended
- **Disk Space:** 10GB free space
- **OS:** Windows 10/11, macOS 10.14+, or Linux

## Step-by-Step Build Instructions

### Step 1: Install Android Studio

1. Download Android Studio from the official website
2. Run the installer and follow the setup wizard
3. During setup, make sure to install:
   - Android SDK
   - Android SDK Platform-Tools
   - Android Emulator (optional)
   - Android SDK Build-Tools

### Step 2: Open the Project

1. Launch Android Studio
2. Click "Open" (not "New Project")
3. Navigate to the `YtDlpDownloader` folder
4. Click "OK"

### Step 3: Sync Project with Gradle

1. Android Studio will automatically start syncing
2. Wait for the sync to complete (5-10 minutes first time)
3. You'll see "Sync finished" in the status bar

**If sync fails:**
- Check your internet connection
- Try: File → Invalidate Caches / Restart
- Check that `settings.gradle.kts` has the Chaquopy repository

### Step 4: Build Debug APK

#### Option A: Using Android Studio UI

1. Make sure you have a device connected or emulator running
2. Click the green "Run" button (play icon) in the toolbar
3. Or select: Run → Run 'app'

#### Option B: Using Command Line

```bash
# Navigate to project directory
cd YtDlpDownloader

# Make gradlew executable (Linux/Mac)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# On Windows
gradlew.bat assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Build Release APK

#### Step 5a: Create a Keystore (First Time Only)

A keystore is used to sign your app. You need to create it once.

**Using Android Studio:**
1. Build → Generate Signed Bundle / APK
2. Select "APK"
3. Click "Create new..." under Key store path
4. Fill in the details:
   - Key store path: Choose a location (e.g., `~/yt-dlp-keystore.jks`)
   - Password: Create a strong password
   - Key alias: `yt-dlp-key`
   - Key password: Same as keystore password
   - Validity: 25 years
   - Certificate: Fill in your information
5. Click "OK"

**Using Command Line:**
```bash
keytool -genkey -v \
  -keystore yt-dlp-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias yt-dlp-key
```

#### Step 5b: Configure Signing

Create `keystore.properties` in the project root:

```properties
storeFile=yt-dlp-keystore.jks
storePassword=your-store-password
keyAlias=yt-dlp-key
keyPassword=your-key-password
```

**IMPORTANT:** Never commit this file to version control! Add it to `.gitignore`.

#### Step 5c: Build Release APK

**Using Android Studio:**
1. Build → Generate Signed Bundle / APK
2. Select "APK"
3. Choose your keystore
4. Click "Next"
5. Select "release" build variant
6. Click "Finish"

**Using Command Line:**
```bash
./gradlew assembleRelease
```

The APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

## Installing the APK

### Method 1: Android Studio (Debug Only)

1. Connect your Android device via USB
2. Enable USB debugging on your device:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → USB Debugging
3. Click "Run" in Android Studio
4. Select your device from the list

### Method 2: ADB (Command Line)

```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Install release APK
adb install app/build/outputs/apk/release/app-release.apk

# If app is already installed, use -r to reinstall
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 3: Manual Installation

1. Copy the APK to your device (USB, email, cloud storage)
2. On your device, enable "Install from Unknown Sources":
   - Settings → Security → Unknown Sources (Android 7 and below)
   - Settings → Apps → Special Access → Install Unknown Apps (Android 8+)
3. Open the APK file on your device
4. Tap "Install"

## Troubleshooting Build Issues

### Issue: "Gradle sync failed"

**Solutions:**
1. Check internet connection
2. Try: File → Invalidate Caches / Restart
3. Delete `.gradle` folder and sync again
4. Check that `settings.gradle.kts` includes Chaquopy repository

### Issue: "Could not resolve com.chaquo.python"

**Solution:**
Make sure `settings.gradle.kts` has:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://chaquo.com/maven")  // Required for Chaquopy
    }
}
```

### Issue: "Python is not started"

**Solution:**
Ensure Python is initialized in `YtdlpApplication.kt`:
```kotlin
override fun onCreate() {
    super.onCreate()
    if (!Python.isStarted()) {
        Python.start(AndroidPlatform(this))
    }
}
```

### Issue: "Out of memory" during build

**Solution:**
Increase heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

### Issue: "INSTALL_FAILED_UPDATE_INCOMPATIBLE"

**Solution:**
The app is already installed with a different signature. Uninstall first:
```bash
adb uninstall com.example.ytdlpdownloader
```

Then install again.

## Build Variants

| Variant | Purpose | Signed | Optimized |
|---------|---------|--------|-----------|
| debug | Development | No | No |
| release | Distribution | Yes | Yes |

### Debug Build
- Includes debugging symbols
- No code optimization
- Faster build time
- Larger APK size

### Release Build
- Code obfuscation (ProGuard/R8)
- Resource shrinking
- Signed with keystore
- Smaller APK size
- Better performance

## Continuous Integration (CI)

### GitHub Actions Example

Create `.github/workflows/build.yml`:

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Build with Gradle
      run: ./gradlew assembleDebug
      
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

## Distribution

### Google Play Store

1. Build App Bundle (AAB) instead of APK:
   ```bash
   ./gradlew bundleRelease
   ```
2. Sign in to Google Play Console
3. Create new app
4. Upload the AAB file
5. Fill in store listing information
6. Submit for review

### Direct Distribution

Share the APK file directly:
- Email
- Cloud storage (Google Drive, Dropbox)
- Website download
- Third-party app stores

**Note:** Users need to enable "Install from Unknown Sources"

## Summary

| Task | Command |
|------|---------|
| Build Debug APK | `./gradlew assembleDebug` |
| Build Release APK | `./gradlew assembleRelease` |
| Build App Bundle | `./gradlew bundleRelease` |
| Clean Build | `./gradlew clean` |
| Install Debug | `./gradlew installDebug` |
| Run Tests | `./gradlew test` |

**Output Locations:**
- Debug APK: `app/build/outputs/apk/debug/`
- Release APK: `app/build/outputs/apk/release/`
- App Bundle: `app/build/outputs/bundle/release/`
