package com.example.ytdlpdownloader.di

import android.content.Context
import com.example.ytdlpdownloader.data.local.DownloadDao
import com.example.ytdlpdownloader.data.local.DownloadDatabase
import com.example.ytdlpdownloader.data.local.SettingsDataStore
import com.example.ytdlpdownloader.data.remote.YtdlpService
import com.example.ytdlpdownloader.data.repository.DownloadRepositoryImpl
import com.example.ytdlpdownloader.data.repository.SettingsRepositoryImpl
import com.example.ytdlpdownloader.data.repository.VideoInfoRepositoryImpl
import com.example.ytdlpdownloader.domain.repository.DownloadRepository
import com.example.ytdlpdownloader.domain.repository.SettingsRepository
import com.example.ytdlpdownloader.domain.repository.VideoInfoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDownloadDatabase(@ApplicationContext context: Context): DownloadDatabase {
        return DownloadDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao {
        return database.downloadDao()
    }
    
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
    
    @Provides
    @Singleton
    fun provideYtdlpService(@ApplicationContext context: Context): YtdlpService {
        return YtdlpService(context)
    }
    
    @Provides
    @Singleton
    fun provideDownloadRepository(
        downloadDao: DownloadDao
    ): DownloadRepository {
        return DownloadRepositoryImpl(downloadDao)
    }
    
    @Provides
    @Singleton
    fun provideVideoInfoRepository(
        ytdlpService: YtdlpService
    ): VideoInfoRepository {
        return VideoInfoRepositoryImpl(ytdlpService)
    }
    
    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsDataStore: SettingsDataStore
    ): SettingsRepository {
        return SettingsRepositoryImpl(settingsDataStore)
    }
}
