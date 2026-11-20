package com.example.youtubedownload.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadStatus
import com.example.youtubedownload.domain.DownloadedItem
import com.example.youtubedownload.domain.ErrorMessageFormatter
import com.example.youtubedownload.domain.QueueItem
import com.example.youtubedownload.domain.QueueItemStatus
import com.example.youtubedownload.service.DownloadForegroundService
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DownloadCoordinatorState(
    val queue: List<QueueItem> = emptyList(),
    val activeStatus: DownloadStatus = DownloadStatus.Idle,
    val isRunning: Boolean = false,
    val activeTitle: String = "",
    val downloadedFiles: List<DownloadedItem> = emptyList()
)

object DownloadCoordinator {

    private const val TAG = "DownloadCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DownloadCoordinatorState())
    val state: StateFlow<DownloadCoordinatorState> = _state.asStateFlow()

    private var workerJob: Job? = null
    @Volatile
    private var activeProcessId: String? = null
    @Volatile
    private var activeItemId: String? = null

    fun refreshFiles(context: Context) {
        val repo = YouTubeDownloadRepository(context.applicationContext)
        scope.launch {
            val files = repo.listDownloadedFiles()
            _state.update { it.copy(downloadedFiles = files) }
        }
    }

    fun deleteFile(context: Context, item: DownloadedItem) {
        val repo = YouTubeDownloadRepository(context.applicationContext)
        scope.launch {
            if (repo.deleteDownloadedFile(item)) {
                val files = repo.listDownloadedFiles()
                _state.update { it.copy(downloadedFiles = files) }
            }
        }
    }

    fun enqueue(context: Context, config: DownloadConfig, title: String) {
        val appContext = context.applicationContext
        val newItem = QueueItem(
            id = "q-${UUID.randomUUID()}",
            config = config,
            title = title.ifBlank { config.url },
            status = QueueItemStatus.Pending
        )
        _state.update { it.copy(queue = it.queue + newItem) }
        startWorkerIfNeeded(appContext)
    }

    fun removePendingItem(itemId: String) {
        _state.update { current ->
            current.copy(
                queue = current.queue.filterNot {
                    it.id == itemId && it.status is QueueItemStatus.Pending
                }
            )
        }
    }

    fun clearFinishedQueue() {
        _state.update { current ->
            current.copy(
                queue = current.queue.filter {
                    it.status is QueueItemStatus.Pending || it.status is QueueItemStatus.Downloading
                }
            )
        }
    }

    fun cancelActive(context: Context) {
        val processId = activeProcessId
        val itemId = activeItemId
        if (processId != null) {
            YouTubeDownloadRepository(context.applicationContext).cancel(processId)
        }
        workerJob?.cancel()
        workerJob = null
        activeProcessId = null
        activeItemId = null

        _state.update { current ->
            current.copy(
                isRunning = false,
                activeStatus = DownloadStatus.Cancelled,
                queue = current.queue.map { item ->
                    if (item.id == itemId || item.status is QueueItemStatus.Downloading) {
                        item.copy(status = QueueItemStatus.Cancelled)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun startWorkerIfNeeded(appContext: Context) {
        if (workerJob?.isActive == true) return

        val serviceIntent = Intent(appContext, DownloadForegroundService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start foreground service", t)
        }

        val repo = YouTubeDownloadRepository(appContext)
        workerJob = scope.launch {
            _state.update { it.copy(isRunning = true) }
            try {
                while (true) {
                    val nextItem = _state.value.queue.firstOrNull {
                        it.status is QueueItemStatus.Pending
                    } ?: break

                    val processId = "yt-${UUID.randomUUID()}"
                    activeProcessId = processId
                    activeItemId = nextItem.id

                    _state.update { current ->
                        current.copy(
                            activeTitle = nextItem.title,
                            activeStatus = DownloadStatus.Initializing,
                            queue = current.queue.map {
                                if (it.id == nextItem.id) {
                                    it.copy(status = QueueItemStatus.Downloading(-1f, 0L, ""))
                                } else {
                                    it
                                }
                            }
                        )
                    }

                    try {
                        val results = repo.download(nextItem.config, processId) { progress, eta, line ->
                            _state.update { current ->
                                if (current.activeStatus is DownloadStatus.Cancelled) {
                                    current
                                } else {
                                    current.copy(
                                        activeStatus = DownloadStatus.Downloading(progress, eta, line),
                                        queue = current.queue.map {
                                            if (it.id == nextItem.id) {
                                                it.copy(
                                                    status = QueueItemStatus.Downloading(progress, eta, line)
                                                )
                                            } else {
                                                it
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        val first = results.first()
                        val files = repo.listDownloadedFiles()
                        _state.update { current ->
                            current.copy(
                                activeStatus = DownloadStatus.Completed(
                                    fileName = first.fileName,
                                    savedLocation = first.savedLocation,
                                    contentUri = first.contentUri,
                                    mimeType = first.mimeType,
                                    fileCount = results.size
                                ),
                                downloadedFiles = files,
                                queue = current.queue.map {
                                    if (it.id == nextItem.id) {
                                        it.copy(status = QueueItemStatus.Completed(results.size))
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    } catch (_: YoutubeDL.CanceledException) {
                        markItemCancelled(nextItem.id)
                        break
                    } catch (_: CancellationException) {
                        markItemCancelled(nextItem.id)
                        break
                    } catch (t: Throwable) {
                        Log.e(TAG, "Queue item failed: ${nextItem.id}", t)
                        val summary = ErrorMessageFormatter.summarize(t.localizedMessage)
                        _state.update { current ->
                            current.copy(
                                activeStatus = DownloadStatus.Error(summary),
                                queue = current.queue.map {
                                    if (it.id == nextItem.id) {
                                        it.copy(status = QueueItemStatus.Error(summary))
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    } finally {
                        activeProcessId = null
                        activeItemId = null
                    }
                }
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    private fun markItemCancelled(itemId: String) {
        _state.update { current ->
            current.copy(
                activeStatus = DownloadStatus.Cancelled,
                queue = current.queue.map {
                    if (it.id == itemId) it.copy(status = QueueItemStatus.Cancelled) else it
                }
            )
        }
    }
}
