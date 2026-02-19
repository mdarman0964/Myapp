package com.example.ytdlpdownloader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.Quality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    
    // Keys
    private val DEFAULT_FORMAT = stringPreferencesKey("default_format")
    private val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    private val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
    private val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    private val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
    private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    
    // Default Format
    val defaultFormat: Flow<DownloadFormat> = dataStore.data.map { prefs ->
        prefs[DEFAULT_FORMAT]?.let { DownloadFormat.fromString(it) } ?: DownloadFormat.MP4
    }
    
    suspend fun getDefaultFormat(): DownloadFormat = defaultFormat.first()
    
    suspend fun setDefaultFormat(format: DownloadFormat) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_FORMAT] = format.name
        }
    }
    
    // Default Quality
    val defaultQuality: Flow<Quality> = dataStore.data.map { prefs ->
        prefs[DEFAULT_QUALITY]?.let { Quality.fromString(it) } ?: Quality.BEST
    }
    
    suspend fun getDefaultQuality(): Quality = defaultQuality.first()
    
    suspend fun setDefaultQuality(quality: Quality) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_QUALITY] = quality.name
        }
    }
    
    // Download Location
    val downloadLocation: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_LOCATION]
    }
    
    suspend fun getDownloadLocation(): String? = downloadLocation.first()
    
    suspend fun setDownloadLocation(uri: String) {
        dataStore.edit { prefs ->
            prefs[DOWNLOAD_LOCATION] = uri
        }
    }
    
    // Disclaimer
    val hasAcceptedDisclaimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DISCLAIMER_ACCEPTED] ?: false
    }
    
    suspend fun hasAcceptedDisclaimer(): Boolean = hasAcceptedDisclaimer.first()
    
    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[DISCLAIMER_ACCEPTED] = accepted
        }
    }
    
    // Concurrent Downloads
    val concurrentDownloadLimit: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CONCURRENT_DOWNLOADS] ?: 3
    }
    
    suspend fun getConcurrentDownloadLimit(): Int = concurrentDownloadLimit.first()
    
    suspend fun setConcurrentDownloadLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[CONCURRENT_DOWNLOADS] = limit.coerceIn(1, 5)
        }
    }
    
    // WiFi Only
    val isWifiOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WIFI_ONLY] ?: false
    }
    
    suspend fun isWifiOnlyEnabled(): Boolean = isWifiOnly.first()
    
    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WIFI_ONLY] = enabled
        }
    }
}
