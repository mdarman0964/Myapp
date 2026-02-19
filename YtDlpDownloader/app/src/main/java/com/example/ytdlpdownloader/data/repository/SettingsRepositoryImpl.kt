package com.example.ytdlpdownloader.data.repository

import com.example.ytdlpdownloader.data.local.SettingsDataStore
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.Quality
import com.example.ytdlpdownloader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {
    
    override suspend fun getDefaultFormat(): DownloadFormat {
        return settingsDataStore.getDefaultFormat()
    }
    
    override suspend fun setDefaultFormat(format: DownloadFormat) {
        settingsDataStore.setDefaultFormat(format)
    }
    
    override suspend fun getDefaultQuality(): Quality {
        return settingsDataStore.getDefaultQuality()
    }
    
    override suspend fun setDefaultQuality(quality: Quality) {
        settingsDataStore.setDefaultQuality(quality)
    }
    
    override suspend fun getDownloadLocation(): String? {
        return settingsDataStore.getDownloadLocation()
    }
    
    override suspend fun setDownloadLocation(uri: String) {
        settingsDataStore.setDownloadLocation(uri)
    }
    
    override suspend fun hasAcceptedDisclaimer(): Boolean {
        return settingsDataStore.hasAcceptedDisclaimer()
    }
    
    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        settingsDataStore.setDisclaimerAccepted(accepted)
    }
    
    override suspend fun getConcurrentDownloadLimit(): Int {
        return settingsDataStore.getConcurrentDownloadLimit()
    }
    
    override suspend fun setConcurrentDownloadLimit(limit: Int) {
        settingsDataStore.setConcurrentDownloadLimit(limit)
    }
    
    override suspend fun isWifiOnlyEnabled(): Boolean {
        return settingsDataStore.isWifiOnlyEnabled()
    }
    
    override suspend fun setWifiOnly(enabled: Boolean) {
        settingsDataStore.setWifiOnly(enabled)
    }
    
    // Flow getters for UI observation
    fun getDefaultFormatFlow(): Flow<DownloadFormat> = settingsDataStore.defaultFormat
    fun getDefaultQualityFlow(): Flow<Quality> = settingsDataStore.defaultQuality
    fun getDownloadLocationFlow(): Flow<String?> = settingsDataStore.downloadLocation
    fun getConcurrentDownloadLimitFlow(): Flow<Int> = settingsDataStore.concurrentDownloadLimit
    fun getWifiOnlyFlow(): Flow<Boolean> = settingsDataStore.isWifiOnly
}
