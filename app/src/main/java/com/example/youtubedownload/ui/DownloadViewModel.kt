package com.example.youtubedownload.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubedownload.data.YouTubeDownloadRepository
import com.example.youtubedownload.domain.AudioBitrate
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadStatus
import com.example.youtubedownload.domain.DownloadedItem
import com.example.youtubedownload.domain.VideoQuality
import com.example.youtubedownload.domain.YouTubeUrlValidator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DownloadUiState(
    val url: String = "",
    val format: DownloadFormat = DownloadFormat.MP4,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val audioBitrate: AudioBitrate = AudioBitrate.K192,
    val status: DownloadStatus = DownloadStatus.Idle,
    val isUpdatingEngine: Boolean = false,
    val engineUpdateMessage: String? = null,
    val downloadFolderPath: String = "",
    val downloadedFiles: List<DownloadedItem> = emptyList()
) {
    val isDownloading: Boolean
        get() = status is DownloadStatus.Initializing || status is DownloadStatus.Downloading
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeDownloadRepository(application)

    private val _uiState = MutableStateFlow(
        DownloadUiState(downloadFolderPath = repository.getDownloadFolderDisplayPath())
    )
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeProcessId: String? = null
    private var lastAutoFilledClipboardUrl: String? = null

    init {
        refreshDownloadedFiles()
    }

    fun refreshDownloadedFiles() {
        viewModelScope.launch {
            val files = repository.listDownloadedFiles()
            _uiState.update { it.copy(downloadedFiles = files) }
        }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(url = newUrl) }
    }

    fun onPasteText(rawText: String) {
        val extracted = YouTubeUrlValidator.extractYouTubeUrl(rawText) ?: rawText.trim()
        if (extracted.isNotEmpty()) {
            lastAutoFilledClipboardUrl = extracted
            _uiState.update { it.copy(url = extracted, status = DownloadStatus.Idle) }
        }
    }

    fun onClipboardYouTubeUrlDetected(url: String) {
        if (_uiState.value.url.isBlank() && url != lastAutoFilledClipboardUrl) {
            lastAutoFilledClipboardUrl = url
            _uiState.update { it.copy(url = url, status = DownloadStatus.Idle) }
        }
    }

    fun onSharedText(rawText: String) {
        val extracted = YouTubeUrlValidator.extractYouTubeUrl(rawText) ?: rawText.trim()
        if (extracted.isNotEmpty()) {
            lastAutoFilledClipboardUrl = extracted
            _uiState.update { it.copy(url = extracted, status = DownloadStatus.Idle) }
        }
    }

    fun onFormatSelected(format: DownloadFormat) {
        _uiState.update { it.copy(format = format) }
    }

    fun onVideoQualitySelected(quality: VideoQuality) {
        _uiState.update { it.copy(videoQuality = quality) }
    }

    fun onAudioBitrateSelected(bitrate: AudioBitrate) {
        _uiState.update { it.copy(audioBitrate = bitrate) }
    }

    fun dismissEngineMessage() {
        _uiState.update { it.copy(engineUpdateMessage = null) }
    }

    fun startDownload() {
        val current = _uiState.value
        if (current.isDownloading) return

        val trimmedUrl = current.url.trim()
        if (!YouTubeUrlValidator.isValidYouTubeUrl(trimmedUrl)) {
            _uiState.update {
                it.copy(
                    status = DownloadStatus.Error(
                        "Please enter a valid YouTube URL (youtube.com or youtu.be)."
                    )
                )
            }
            return
        }

        val processId = "yt-${UUID.randomUUID()}"
        activeProcessId = processId
        val config = DownloadConfig(
            url = trimmedUrl,
            format = current.format,
            videoQuality = current.videoQuality,
            audioBitrate = current.audioBitrate
        )

        _uiState.update { it.copy(status = DownloadStatus.Initializing) }

        activeJob = viewModelScope.launch {
            try {
                val result = repository.download(config, processId) { progress, etaSeconds, line ->
                    _uiState.update { state ->
                        if (state.status is DownloadStatus.Cancelled) {
                            state
                        } else {
                            state.copy(
                                status = DownloadStatus.Downloading(
                                    progress = progress,
                                    etaSeconds = etaSeconds,
                                    statusLine = line
                                )
                            )
                        }
                    }
                }
                val files = repository.listDownloadedFiles()
                _uiState.update {
                    it.copy(
                        status = DownloadStatus.Completed(
                            fileName = result.fileName,
                            savedLocation = result.savedLocation,
                            contentUri = result.contentUri,
                            mimeType = result.mimeType
                        ),
                        downloadedFiles = files
                    )
                }
            } catch (_: YoutubeDL.CanceledException) {
                _uiState.update { it.copy(status = DownloadStatus.Cancelled) }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(status = DownloadStatus.Cancelled) }
            } catch (t: Throwable) {
                Log.e(TAG, "Download failed", t)
                _uiState.update {
                    it.copy(
                        status = DownloadStatus.Error(
                            t.localizedMessage ?: "Download failed due to an unknown error."
                        )
                    )
                }
            } finally {
                activeProcessId = null
                activeJob = null
            }
        }
    }

    fun cancelDownload() {
        val processId = activeProcessId
        if (processId != null) {
            repository.cancel(processId)
        }
        activeJob?.cancel()
        _uiState.update { it.copy(status = DownloadStatus.Cancelled) }
    }

    fun updateEngine() {
        if (_uiState.value.isUpdatingEngine || _uiState.value.isDownloading) return

        _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }
        viewModelScope.launch {
            try {
                val status = repository.updateYtDlp()
                val message = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated to the latest version."
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date."
                    null -> "yt-dlp update finished."
                }
                _uiState.update { it.copy(isUpdatingEngine = false, engineUpdateMessage = message) }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update yt-dlp", t)
                _uiState.update {
                    it.copy(
                        isUpdatingEngine = false,
                        engineUpdateMessage = "Update failed: ${t.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "DownloadViewModel"
    }
}
