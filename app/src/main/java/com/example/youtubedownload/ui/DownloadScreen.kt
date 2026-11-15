package com.example.youtubedownload.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.youtubedownload.domain.AudioBitrate
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadStatus
import com.example.youtubedownload.domain.VideoQuality

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(
    uiState: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onFormatSelected: (DownloadFormat) -> Unit,
    onVideoQualitySelected: (VideoQuality) -> Unit,
    onAudioBitrateSelected: (AudioBitrate) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onUpdateEngine: () -> Unit,
    onDismissEngineMessage: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.engineUpdateMessage) {
        val message = uiState.engineUpdateMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissEngineMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Downloader") },
                actions = {
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
                                contentDescription = "Update yt-dlp"
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                label = { Text("YouTube URL") },
                placeholder = { Text("https://www.youtube.com/watch?v=...") },
                singleLine = true,
                enabled = !uiState.isDownloading,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        if (uiState.url.isNotEmpty() && !uiState.isDownloading) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                            }
                        }
                        if (!uiState.isDownloading) {
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let(onUrlChange)
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                            }
                        }
                    }
                }
            )

            Text(
                text = "Format",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DownloadFormat.entries.forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = uiState.format == format,
                        onClick = { onFormatSelected(format) },
                        enabled = !uiState.isDownloading,
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
                        Text(format.label)
                    }
                }
            }

            if (uiState.format == DownloadFormat.MP4) {
                Text(
                    text = "Video Quality",
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
                            enabled = !uiState.isDownloading,
                            label = { Text(quality.label) }
                        )
                    }
                }
            } else {
                Text(
                    text = "Audio Bitrate",
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
                            enabled = !uiState.isDownloading,
                            label = { Text(bitrate.label) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartDownload,
                    enabled = !uiState.isDownloading && uiState.url.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isDownloading) "Downloading..." else "Download ${uiState.format.name}")
                }

                if (uiState.isDownloading) {
                    OutlinedButton(onClick = onCancelDownload) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            }

            StatusCard(status = uiState.status)
        }
    }
}

@Composable
private fun StatusCard(status: DownloadStatus) {
    when (status) {
        DownloadStatus.Idle -> Unit

        DownloadStatus.Initializing -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preparing download...", style = MaterialTheme.typography.titleSmall)
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
                            "Processing..."
                        }
                        Text(pctText, style = MaterialTheme.typography.titleSmall)
                        if (status.etaSeconds > 0L) {
                            Text(
                                "ETA ${status.etaSeconds}s",
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
                Row(
                    modifier = Modifier.padding(16.dp),
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
                            text = "Saved: ${status.fileName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = status.savedLocation,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        DownloadStatus.Cancelled -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Download cancelled.",
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
                Row(
                    modifier = Modifier.padding(16.dp),
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
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
