package com.example.ytdlpdownloader.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdlpdownloader.domain.model.*
import com.example.ytdlpdownloader.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val addDownloadUseCase: AddDownloadUseCase,
    private val getDownloadsUseCase: GetDownloadsUseCase,
    private val getActiveDownloadsUseCase: GetActiveDownloadsUseCase,
    private val pauseDownloadUseCase: PauseDownloadUseCase,
    private val resumeDownloadUseCase: ResumeDownloadUseCase,
    private val cancelDownloadUseCase: CancelDownloadUseCase,
    private val retryDownloadUseCase: RetryDownloadUseCase,
    private val removeDownloadUseCase: RemoveDownloadUseCase,
    private val clearCompletedDownloadsUseCase: ClearCompletedDownloadsUseCase,
    private val getVideoInfoUseCase: GetVideoInfoUseCase,
    private val getPlaylistInfoUseCase: GetPlaylistInfoUseCase,
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val getAvailableFormatsUseCase: GetAvailableFormatsUseCase
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Downloads
    val allDownloads: StateFlow<List<DownloadItem>> = getDownloadsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val activeDownloads: StateFlow<List<DownloadItem>> = getActiveDownloadsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // Events
    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()
    
    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(url = url, urlError = null) }
    }
    
    fun onFormatSelected(format: DownloadFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }
    
    fun onQualitySelected(quality: Quality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }
    
    fun analyzeUrl() {
        val url = _uiState.value.url.trim()
        if (url.isEmpty()) {
            _uiState.update { it.copy(urlError = "Please enter a URL") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            
            validateUrlUseCase(url).let { isValid ->
                if (!isValid) {
                    _uiState.update { it.copy(isAnalyzing = false, urlError = "Invalid or unsupported URL") }
                    return@launch
                }
            }
            
            getVideoInfoUseCase(url).fold(
                onSuccess = { videoInfo ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            videoInfo = videoInfo,
                            showFormatDialog = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            urlError = error.message ?: "Failed to analyze URL"
                        )
                    }
                }
            )
        }
    }
    
    fun startDownload() {
        val state = _uiState.value
        val url = state.url.trim()
        
        if (url.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            addDownloadUseCase(
                url = url,
                format = state.selectedFormat,
                quality = state.selectedQuality
            ).fold(
                onSuccess = { downloadItem ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            url = "",
                            videoInfo = null,
                            showFormatDialog = false
                        )
                    }
                    _events.emit(MainEvent.ShowSnackbar("Download added to queue"))
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(MainEvent.ShowError(error.message ?: "Failed to add download"))
                }
            )
        }
    }
    
    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            pauseDownloadUseCase(downloadId)
            _events.emit(MainEvent.ShowSnackbar("Download paused"))
        }
    }
    
    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            resumeDownloadUseCase(downloadId)
            _events.emit(MainEvent.ShowSnackbar("Download resumed"))
        }
    }
    
    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            cancelDownloadUseCase(downloadId)
            _events.emit(MainEvent.ShowSnackbar("Download cancelled"))
        }
    }
    
    fun retryDownload(downloadId: String) {
        viewModelScope.launch {
            retryDownloadUseCase(downloadId)
            _events.emit(MainEvent.ShowSnackbar("Download queued for retry"))
        }
    }
    
    fun removeDownload(downloadId: String) {
        viewModelScope.launch {
            removeDownloadUseCase(downloadId)
            _events.emit(MainEvent.ShowSnackbar("Download removed"))
        }
    }
    
    fun clearCompletedDownloads() {
        viewModelScope.launch {
            clearCompletedDownloadsUseCase()
            _events.emit(MainEvent.ShowSnackbar("Completed downloads cleared"))
        }
    }
    
    fun dismissFormatDialog() {
        _uiState.update { it.copy(showFormatDialog = false) }
    }
    
    fun handleSharedUrl(url: String) {
        _uiState.update { it.copy(url = url) }
        analyzeUrl()
    }
}

data class MainUiState(
    val url: String = "",
    val urlError: String? = null,
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val selectedFormat: DownloadFormat = DownloadFormat.MP4,
    val selectedQuality: Quality = Quality.BEST,
    val videoInfo: com.example.ytdlpdownloader.domain.model.VideoInfo? = null,
    val showFormatDialog: Boolean = false
)

sealed class MainEvent {
    data class ShowSnackbar(val message: String) : MainEvent()
    data class ShowError(val message: String) : MainEvent()
    data class NavigateToSettings(val url: String) : MainEvent()
}
