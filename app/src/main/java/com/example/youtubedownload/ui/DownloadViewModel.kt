package com.example.youtubedownload.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubedownload.data.DownloadCoordinator
import com.example.youtubedownload.data.YouTubeDownloadRepository
import com.example.youtubedownload.domain.AudioBitrate
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadStatus
import com.example.youtubedownload.domain.DownloadedItem
import com.example.youtubedownload.domain.ErrorMessageFormatter
import com.example.youtubedownload.domain.PreviewState
import com.example.youtubedownload.domain.QueueItem
import com.example.youtubedownload.domain.VideoQuality
import com.example.youtubedownload.domain.YouTubeUrlValidator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val url: String = "",
    val format: DownloadFormat = DownloadFormat.MP4,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val audioBitrate: AudioBitrate = AudioBitrate.K192,
    val embedCoverAndMetadata: Boolean = true,
    val embedSubtitles: Boolean = false,
    val allowPlaylist: Boolean = false,
    val previewState: PreviewState = PreviewState.Idle,
    val status: DownloadStatus = DownloadStatus.Idle,
    val isDownloading: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val isUpdatingEngine: Boolean = false,
    val engineUpdateMessage: String? = null,
    val downloadFolderPath: String = "",
    val downloadedFiles: List<DownloadedItem> = emptyList(),
    val language: AppLanguage = AppLanguage.ZH_TW,
    val isDarkMode: Boolean = false
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeDownloadRepository(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        DownloadUiState(
            downloadFolderPath = repository.getDownloadFolderDisplayPath(),
            language = AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, AppLanguage.ZH_TW.code)),
            isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        )
    )
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private var lastAutoFilledClipboardUrl: String? = null
    private var lastPreviewedUrl: String? = null

    init {
        DownloadCoordinator.refreshFiles(application)
        viewModelScope.launch {
            DownloadCoordinator.state.collectLatest { coord ->
                _uiState.update { current ->
                    val resolvedStatus = if (current.status is DownloadStatus.Error &&
                        coord.activeStatus == DownloadStatus.Idle
                    ) {
                        current.status
                    } else {
                        coord.activeStatus
                    }
                    current.copy(
                        status = resolvedStatus,
                        isDownloading = coord.isRunning,
                        queue = coord.queue,
                        downloadedFiles = coord.downloadedFiles
                    )
                }
            }
        }
    }

    fun toggleLanguage() {
        val nextLang = _uiState.value.language.next()
        prefs.edit().putString(KEY_LANGUAGE, nextLang.code).apply()
        _uiState.update { it.copy(language = nextLang) }
    }

    fun toggleDarkMode() {
        val nextDark = !_uiState.value.isDarkMode
        prefs.edit().putBoolean(KEY_DARK_MODE, nextDark).apply()
        _uiState.update { it.copy(isDarkMode = nextDark) }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(url = newUrl) }
        schedulePreviewFetch(newUrl, debounceMs = 500L)
    }

    fun onPasteText(rawText: String) {
        val extracted = YouTubeUrlValidator.extractYouTubeUrl(rawText) ?: rawText.trim()
        if (extracted.isNotEmpty()) {
            lastAutoFilledClipboardUrl = extracted
            _uiState.update { it.copy(url = extracted, status = DownloadStatus.Idle) }
            schedulePreviewFetch(extracted, debounceMs = 0L)
        }
    }

    fun onClipboardYouTubeUrlDetected(url: String) {
        if (_uiState.value.url.isBlank() && url != lastAutoFilledClipboardUrl) {
            lastAutoFilledClipboardUrl = url
            _uiState.update { it.copy(url = url, status = DownloadStatus.Idle) }
            schedulePreviewFetch(url, debounceMs = 0L)
        }
    }

    fun onSharedText(rawText: String) {
        val extracted = YouTubeUrlValidator.extractYouTubeUrl(rawText) ?: rawText.trim()
        if (extracted.isNotEmpty()) {
            lastAutoFilledClipboardUrl = extracted
            _uiState.update { it.copy(url = extracted, status = DownloadStatus.Idle) }
            schedulePreviewFetch(extracted, debounceMs = 0L)
        }
    }

    private fun schedulePreviewFetch(rawUrl: String, debounceMs: Long) {
        val trimmed = rawUrl.trim()
        if (!YouTubeUrlValidator.isValidYouTubeUrl(trimmed)) {
            previewJob?.cancel()
            lastPreviewedUrl = null
            _uiState.update { it.copy(previewState = PreviewState.Idle) }
            return
        }
        if (trimmed == lastPreviewedUrl && _uiState.value.previewState is PreviewState.Loaded) {
            return
        }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            lastPreviewedUrl = trimmed
            _uiState.update { it.copy(previewState = PreviewState.Loading) }
            try {
                val preview = repository.fetchVideoInfo(trimmed)
                _uiState.update { it.copy(previewState = PreviewState.Loaded(preview)) }
            } catch (t: Throwable) {
                Log.w(TAG, "Preview fetch failed", t)
                val strings = AppStrings.forLanguage(_uiState.value.language)
                _uiState.update { it.copy(previewState = PreviewState.Error(strings.previewUnavailable)) }
            }
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

    fun onToggleEmbedCover() {
        _uiState.update { it.copy(embedCoverAndMetadata = !it.embedCoverAndMetadata) }
    }

    fun onToggleEmbedSubtitles() {
        _uiState.update { it.copy(embedSubtitles = !it.embedSubtitles) }
    }

    fun onToggleAllowPlaylist() {
        _uiState.update { it.copy(allowPlaylist = !it.allowPlaylist) }
    }

    fun dismissEngineMessage() {
        _uiState.update { it.copy(engineUpdateMessage = null) }
    }

    fun startOrEnqueueDownload(clearUrlAfterEnqueue: Boolean = false) {
        val current = _uiState.value
        val strings = AppStrings.forLanguage(current.language)
        val trimmedUrl = current.url.trim()
        if (!YouTubeUrlValidator.isValidYouTubeUrl(trimmedUrl)) {
            _uiState.update {
                it.copy(status = DownloadStatus.Error(strings.invalidUrlError))
            }
            return
        }

        val config = DownloadConfig(
            url = trimmedUrl,
            format = current.format,
            videoQuality = current.videoQuality,
            audioBitrate = current.audioBitrate,
            embedCoverAndMetadata = current.embedCoverAndMetadata,
            embedSubtitles = current.embedSubtitles,
            allowPlaylist = current.allowPlaylist
        )
        val previewTitle = (current.previewState as? PreviewState.Loaded)?.preview?.title ?: trimmedUrl

        DownloadCoordinator.enqueue(getApplication(), config, previewTitle)

        if (clearUrlAfterEnqueue) {
            lastPreviewedUrl = null
            _uiState.update { it.copy(url = "", previewState = PreviewState.Idle) }
        }
    }

    fun removePendingQueueItem(itemId: String) {
        DownloadCoordinator.removePendingItem(itemId)
    }

    fun clearFinishedQueue() {
        DownloadCoordinator.clearFinishedQueue()
    }

    fun cancelDownload() {
        DownloadCoordinator.cancelActive(getApplication())
    }

    fun deleteDownloadedFile(item: DownloadedItem) {
        DownloadCoordinator.deleteFile(getApplication(), item)
    }

    fun updateEngine() {
        val current = _uiState.value
        if (current.isUpdatingEngine || current.isDownloading) return

        _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }
        viewModelScope.launch {
            val strings = AppStrings.forLanguage(_uiState.value.language)
            try {
                val status = repository.updateYtDlp()
                val message = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> strings.engineUpdatedDone
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> strings.engineAlreadyUpToDate
                    null -> strings.engineUpdateFinished
                }
                _uiState.update { it.copy(isUpdatingEngine = false, engineUpdateMessage = message) }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update yt-dlp", t)
                val summary = ErrorMessageFormatter.summarize(t.localizedMessage, maxLines = 1)
                _uiState.update {
                    it.copy(
                        isUpdatingEngine = false,
                        engineUpdateMessage = strings.engineUpdateFailed(summary)
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "DownloadViewModel"
        private const val PREFS_NAME = "yt_downloader_prefs"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_DARK_MODE = "app_dark_mode"
    }
}
