package com.example.youtubedownload.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.youtubedownload.domain.AudioBitrate
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadStatus
import com.example.youtubedownload.domain.DownloadedItem
import com.example.youtubedownload.domain.PreviewState
import com.example.youtubedownload.domain.QueueItem
import com.example.youtubedownload.domain.QueueItemStatus
import com.example.youtubedownload.domain.SearchResultItem
import com.example.youtubedownload.domain.SearchState
import com.example.youtubedownload.domain.VideoQuality
import com.example.youtubedownload.domain.YouTubeUrlValidator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(
    uiState: DownloadUiState,
    onTabSelected: (Int) -> Unit,
    onUrlChange: (String) -> Unit,
    onPasteText: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onDownloadSearchResult: (SearchResultItem) -> Unit,
    onFormatSelected: (DownloadFormat) -> Unit,
    onVideoQualitySelected: (VideoQuality) -> Unit,
    onAudioBitrateSelected: (AudioBitrate) -> Unit,
    onToggleEmbedCover: () -> Unit,
    onToggleEmbedSubtitles: () -> Unit,
    onToggleAllowPlaylist: () -> Unit,
    onStartDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onClearFinishedQueue: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteFile: (DownloadedItem) -> Unit,
    onUpdateEngine: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onDismissEngineMessage: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = AppStrings.forLanguage(uiState.language)

    LaunchedEffect(uiState.engineUpdateMessage) {
        val message = uiState.engineUpdateMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissEngineMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.appTitle) },
                actions = {
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (uiState.isDarkMode) {
                                Icons.Default.LightMode
                            } else {
                                Icons.Default.DarkMode
                            },
                            contentDescription = strings.toggleThemeDesc
                        )
                    }
                    TextButton(onClick = onToggleLanguage) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(uiState.language.toggleLabel)
                    }
                    IconButton(
                        onClick = onUpdateEngine,
                        enabled = !uiState.isUpdatingEngine && !uiState.isDownloading
                    ) {
                        if (uiState.isUpdatingEngine) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdateAlt,
                                contentDescription = strings.updateYtDlpDesc
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TabRow(selectedTabIndex = uiState.activeTab) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text(strings.tabLink) }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text(strings.tabSearch) }
                )
            }

            if (uiState.activeTab == 0) {
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = onUrlChange,
                    label = { Text(strings.urlLabel) },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (uiState.url.isNotEmpty()) {
                                IconButton(onClick = { onUrlChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = strings.clear)
                                }
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let(onPasteText)
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = strings.pasteFromClipboard
                                )
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let(onPasteText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.pasteFromClipboard)
                    }

                    if (uiState.url.isNotEmpty()) {
                        OutlinedButton(onClick = { onUrlChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.clear)
                        }
                    }
                }

                Text(
                    text = strings.shareTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VideoPreviewCard(
                    previewState = uiState.previewState,
                    isPlaylistUrl = YouTubeUrlValidator.isPlaylistUrl(uiState.url),
                    strings = strings
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text(strings.searchLabel) },
                        placeholder = { Text(strings.searchPlaceholder) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearchSubmit()
                            }
                        ),
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = strings.clear)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSearchSubmit()
                        },
                        enabled = uiState.searchQuery.isNotBlank() &&
                            uiState.searchState !is SearchState.Searching
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = strings.searchBtn,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SearchResultsSection(
                    searchState = uiState.searchState,
                    strings = strings,
                    onDownloadItem = onDownloadSearchResult
                )
            }

            Text(
                text = strings.formatTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DownloadFormat.entries.forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = uiState.format == format,
                        onClick = { onFormatSelected(format) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DownloadFormat.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = when (format) {
                                    DownloadFormat.MP4 -> Icons.Default.VideoFile
                                    DownloadFormat.MP3 -> Icons.Default.AudioFile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text(strings.formatLabel(format))
                    }
                }
            }

            if (uiState.format == DownloadFormat.MP4) {
                Text(
                    text = strings.videoQualityTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VideoQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = uiState.videoQuality == quality,
                            onClick = { onVideoQualitySelected(quality) },
                            label = { Text(strings.qualityLabel(quality)) }
                        )
                    }
                }
            } else {
                Text(
                    text = strings.audioBitrateTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioBitrate.entries.forEach { bitrate ->
                        FilterChip(
                            selected = uiState.audioBitrate == bitrate,
                            onClick = { onAudioBitrateSelected(bitrate) },
                            label = { Text(bitrate.label) }
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.format == DownloadFormat.MP3) {
                    FilterChip(
                        selected = uiState.embedCoverAndMetadata,
                        onClick = onToggleEmbedCover,
                        leadingIcon = if (uiState.embedCoverAndMetadata) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(strings.embedCoverLabel) }
                    )
                } else {
                    FilterChip(
                        selected = uiState.embedSubtitles,
                        onClick = onToggleEmbedSubtitles,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(strings.embedSubtitlesLabel) }
                    )
                }

                FilterChip(
                    selected = uiState.allowPlaylist,
                    onClick = onToggleAllowPlaylist,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text(strings.allowPlaylistLabel) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartDownload,
                    enabled = uiState.url.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (uiState.isDownloading) {
                            strings.addToQueue
                        } else {
                            strings.downloadButtonText(uiState.format)
                        }
                    )
                }

                if (!uiState.isDownloading) {
                    FilledTonalButton(
                        onClick = onAddToQueue,
                        enabled = uiState.url.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddToQueue,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addToQueue)
                    }
                } else {
                    OutlinedButton(onClick = onCancelDownload) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.cancelBtn)
                    }
                }
            }

            StatusCard(
                status = uiState.status,
                strings = strings,
                onOpenFile = { uriStr, mimeType -> openDownloadedFile(context, uriStr, mimeType) },
                onOpenFolder = { openDownloadsFolder(context) },
                onUpdateEngine = onUpdateEngine
            )

            if (uiState.queue.isNotEmpty()) {
                QueueCard(
                    queue = uiState.queue,
                    strings = strings,
                    onRemoveItem = onRemoveQueueItem,
                    onClearFinished = onClearFinishedQueue
                )
            }

            DownloadFolderCard(
                folderPath = uiState.downloadFolderPath,
                files = uiState.downloadedFiles,
                strings = strings,
                onOpenFolder = { openDownloadsFolder(context) },
                onOpenFile = { item ->
                    item.contentUri?.let { openDownloadedFile(context, it, item.mimeType) }
                },
                onShareFile = { item ->
                    item.contentUri?.let { shareDownloadedFile(context, it, item.mimeType) }
                },
                onDeleteFile = onDeleteFile
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    searchState: SearchState,
    strings: AppStrings,
    onDownloadItem: (SearchResultItem) -> Unit
) {
    when (searchState) {
        SearchState.Idle -> {
            Text(
                text = strings.tapResultToDownload,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SearchState.Searching -> {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(strings.searching, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        is SearchState.Results -> {
            if (searchState.items.isEmpty()) {
                Text(
                    text = strings.noSearchResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = strings.tapResultToDownload,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    searchState.items.forEach { item ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDownloadItem(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 112.dp, height = 64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildString {
                                        if (item.uploader.isNotBlank()) append(item.uploader)
                                        if (item.durationSeconds > 0L) {
                                            if (isNotEmpty()) append(" • ")
                                            append(formatDuration(item.durationSeconds))
                                        }
                                    }
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        is SearchState.Error -> {
            Text(
                text = searchState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun VideoPreviewCard(
    previewState: PreviewState,
    isPlaylistUrl: Boolean,
    strings: AppStrings
) {
    when (previewState) {
        PreviewState.Idle -> Unit

        PreviewState.Loading -> {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = strings.loadingPreview,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        is PreviewState.Loaded -> {
            val preview = previewState.preview
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!preview.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = preview.thumbnailUrl,
                            contentDescription = preview.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 112.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = preview.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            if (preview.uploader.isNotBlank()) append(preview.uploader)
                            if (preview.durationSeconds > 0L) {
                                if (isNotEmpty()) append(" • ")
                                append(formatDuration(preview.durationSeconds))
                            }
                        }
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isPlaylistUrl) {
                            Text(
                                text = strings.playlistDetectedHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        is PreviewState.Error -> {
            Text(
                text = previewState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueueCard(
    queue: List<QueueItem>,
    strings: AppStrings,
    onRemoveItem: (String) -> Unit,
    onClearFinished: () -> Unit
) {
    val hasFinished = queue.any {
        it.status is QueueItemStatus.Completed ||
            it.status is QueueItemStatus.Cancelled ||
            it.status is QueueItemStatus.Error
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.queueTitle} (${queue.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (hasFinished) {
                    TextButton(onClick = onClearFinished) {
                        Text(strings.clearFinished)
                    }
                }
            }

            queue.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusLabel = when (val st = item.status) {
                            QueueItemStatus.Pending -> "${item.config.format.name} • ${strings.pendingStatus}"
                            is QueueItemStatus.Downloading -> {
                                val pct = if (st.progress >= 0f) "${st.progress.toInt()}%" else strings.processing
                                "${item.config.format.name} • $pct"
                            }
                            is QueueItemStatus.Completed -> "${item.config.format.name} • ${strings.filesSavedCount(st.fileCount)}"
                            QueueItemStatus.Cancelled -> strings.downloadCancelled
                            is QueueItemStatus.Error -> st.message
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (item.status is QueueItemStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (item.status is QueueItemStatus.Pending) {
                        IconButton(onClick = { onRemoveItem(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.cancelBtn,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: DownloadStatus,
    strings: AppStrings,
    onOpenFile: (String, String) -> Unit,
    onOpenFolder: () -> Unit,
    onUpdateEngine: () -> Unit
) {
    when (status) {
        DownloadStatus.Idle -> Unit

        DownloadStatus.Initializing -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.preparingDownload, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        is DownloadStatus.Downloading -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val pctText = if (status.progress >= 0f) {
                            "${status.progress.toInt().coerceIn(0, 100)}%"
                        } else {
                            strings.processing
                        }
                        Text(pctText, style = MaterialTheme.typography.titleSmall)
                        if (status.etaSeconds > 0L) {
                            Text(
                                strings.etaText(status.etaSeconds),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (status.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { (status.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (status.statusLine.isNotBlank()) {
                        Text(
                            text = status.statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is DownloadStatus.Completed -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = strings.downloadCompleted,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (status.fileCount > 1) {
                                    "${status.fileName} (${strings.filesSavedCount(status.fileCount)})"
                                } else {
                                    status.fileName
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${strings.folderPrefix}${status.savedLocation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status.contentUri != null) {
                            Button(
                                onClick = { onOpenFile(status.contentUri, status.mimeType) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.openFile)
                            }
                        }
                        OutlinedButton(onClick = onOpenFolder) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.openFolder)
                        }
                    }
                }
            }
        }

        DownloadStatus.Cancelled -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.downloadCancelled,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is DownloadStatus.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onUpdateEngine) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.updateEngineRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFolderCard(
    folderPath: String,
    files: List<DownloadedItem>,
    strings: AppStrings,
    onOpenFolder: () -> Unit,
    onOpenFile: (DownloadedItem) -> Unit,
    onShareFile: (DownloadedItem) -> Unit,
    onDeleteFile: (DownloadedItem) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.downloadFolderTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onOpenFolder) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.openShort)
                }
            }

            if (files.isNotEmpty()) {
                Text(
                    text = strings.downloadedFilesHeader(files.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                files.take(15).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.mimeType.startsWith("audio")) {
                                    Icons.Default.AudioFile
                                } else {
                                    Icons.Default.VideoFile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            if (item.contentUri != null) {
                                IconButton(onClick = { onOpenFile(item) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = strings.openShort,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { onShareFile(item) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = strings.shareShort,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteFile(item) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.deleteShort,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024.0)
    }
}

private fun openDownloadsFolder(context: Context) {
    try {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(
            context,
            "Saved in Internal Storage / Download / YouTubeDownload",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun openDownloadedFile(context: Context, uriString: String, mimeType: String) {
    try {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Throwable) {
        openDownloadsFolder(context)
    }
}

private fun shareDownloadedFile(context: Context, uriString: String, mimeType: String) {
    try {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (_: Throwable) {
        Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
    }
}
