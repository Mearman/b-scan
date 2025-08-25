package com.bscan.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bscan.model.UpdateInfo
import com.bscan.model.UpdateStatus
import com.bscan.repository.UpdateRepository
import com.bscan.update.DownloadProgress
import com.bscan.update.UpdateDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    
    private val updateRepository = UpdateRepository(application)
    private val downloadService = UpdateDownloadService(application)
    
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()
    
    init {
        // Check for updates on startup if auto-check is enabled
        checkForUpdatesAutomatically()
    }
    
    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    status = UpdateStatus.CHECKING,
                    error = null
                )
            }
            
            val result = updateRepository.checkForUpdates(force)
            
            result.fold(
                onSuccess = { updateInfo ->
                    val status = if (updateInfo.isUpdateAvailable) {
                        // Check if this version was previously dismissed
                        if (updateRepository.isVersionDismissed(updateInfo.latestVersion)) {
                            UpdateStatus.NOT_AVAILABLE
                        } else {
                            UpdateStatus.AVAILABLE
                        }
                    } else {
                        UpdateStatus.NOT_AVAILABLE
                    }
                    
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            status = status,
                            updateInfo = updateInfo,
                            error = null
                        )
                    }
                },
                onFailure = { exception ->
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            status = UpdateStatus.ERROR,
                            error = exception.message ?: "Unknown error occurred",
                            updateInfo = null
                        )
                    }
                }
            )
        }
    }
    
    fun downloadUpdate() {
        val updateInfo = _uiState.value.updateInfo ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(status = UpdateStatus.DOWNLOADING)
            }
            
            val fileName = "b-scan-${updateInfo.latestVersion}.apk"
            
            downloadService.downloadUpdate(updateInfo.downloadUrl, fileName).collect { progress ->
                when (progress) {
                    is DownloadProgress.Started -> {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                status = UpdateStatus.DOWNLOADING,
                                downloadProgress = 0
                            )
                        }
                    }
                    is DownloadProgress.InProgress -> {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                downloadProgress = progress.progress
                            )
                        }
                    }
                    is DownloadProgress.Completed -> {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                status = UpdateStatus.DOWNLOADED,
                                downloadedFilePath = progress.filePath,
                                downloadProgress = 100
                            )
                        }
                    }
                    is DownloadProgress.Failed -> {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                status = UpdateStatus.ERROR,
                                error = progress.error
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun installUpdate() {
        val filePath = _uiState.value.downloadedFilePath ?: return
        _uiState.value = _uiState.value.copy(status = UpdateStatus.INSTALLING)
        downloadService.installUpdate(filePath)
    }
    
    fun dismissUpdate() {
        val updateInfo = _uiState.value.updateInfo ?: return
        updateRepository.dismissVersion(updateInfo.latestVersion)
        _uiState.value = _uiState.value.copy(
            status = UpdateStatus.NOT_AVAILABLE,
            updateInfo = null
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null,
            status = UpdateStatus.NOT_AVAILABLE
        )
    }
    
    private fun checkForUpdatesAutomatically() {
        if (updateRepository.isAutoCheckEnabled()) {
            checkForUpdates(force = false)
        }
    }
    
    fun getAutoCheckEnabled(): Boolean = updateRepository.isAutoCheckEnabled()
    
    fun setAutoCheckEnabled(enabled: Boolean) {
        updateRepository.setAutoCheckEnabled(enabled)
    }
    
    fun getCheckIntervalHours(): Int = updateRepository.getCheckIntervalHours()
    
    fun setCheckIntervalHours(hours: Int) {
        updateRepository.setCheckIntervalHours(hours)
    }
}

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.NOT_AVAILABLE,
    val updateInfo: UpdateInfo? = null,
    val downloadProgress: Int = 0,
    val downloadedFilePath: String? = null,
    val error: String? = null
)