package com.example.youtubedownload.domain

enum class DownloadFormat(val label: String, val extension: String, val mimeType: String) {
    MP4("MP4 (Video)", "mp4", "video/mp4"),
    MP3("MP3 (Audio)", "mp3", "audio/mpeg")
}

enum class VideoQuality(val label: String, val maxHeight: Int?) {
    BEST("Best", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360);

    /** Returns a yt-dlp format selector string preferring MP4 video + M4A audio. */
    fun toFormatSelector(): String = if (maxHeight == null) {
        "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    } else {
        "bestvideo[height<=$maxHeight][ext=mp4]+bestaudio[ext=m4a]/" +
            "best[height<=$maxHeight][ext=mp4]/best[height<=$maxHeight]/best"
    }
}

enum class AudioBitrate(val label: String, val ffmpegArg: String) {
    K320("320 kbps", "320K"),
    K192("192 kbps", "192K"),
    K128("128 kbps", "128K")
}

data class VideoPreview(
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?
)

sealed interface PreviewState {
    data object Idle : PreviewState
    data object Loading : PreviewState
    data class Loaded(val preview: VideoPreview) : PreviewState
    data class Error(val message: String) : PreviewState
}

data class DownloadConfig(
    val url: String,
    val format: DownloadFormat,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val audioBitrate: AudioBitrate = AudioBitrate.K192,
    val embedCoverAndMetadata: Boolean = true,
    val embedSubtitles: Boolean = false,
    val allowPlaylist: Boolean = false
)

data class DownloadedItem(
    val fileName: String,
    val folderPath: String,
    val sizeBytes: Long,
    val contentUri: String?,
    val mimeType: String
)

sealed interface QueueItemStatus {
    data object Pending : QueueItemStatus
    data class Downloading(
        val progress: Float,
        val etaSeconds: Long,
        val statusLine: String
    ) : QueueItemStatus
    data class Completed(val fileCount: Int) : QueueItemStatus
    data object Cancelled : QueueItemStatus
    data class Error(val message: String) : QueueItemStatus
}

data class QueueItem(
    val id: String,
    val config: DownloadConfig,
    val title: String,
    val status: QueueItemStatus = QueueItemStatus.Pending
)

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Initializing : DownloadStatus
    data class Downloading(
        val progress: Float,
        val etaSeconds: Long,
        val statusLine: String
    ) : DownloadStatus
    data class Completed(
        val fileName: String,
        val savedLocation: String,
        val contentUri: String?,
        val mimeType: String,
        val fileCount: Int = 1
    ) : DownloadStatus
    data object Cancelled : DownloadStatus
    data class Error(val message: String) : DownloadStatus
}

/** Pure helper for extracting and validating YouTube URLs. */
object YouTubeUrlValidator {

    private val urlExtractRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val youtubeHostRegex = Regex(
        pattern = """^https?://(www\.|m\.|music\.)?(youtube\.com|youtu\.be)/.+""",
        option = RegexOption.IGNORE_CASE
    )
    private val playlistRegex = Regex("""([?&]list=|/playlist\b)""", RegexOption.IGNORE_CASE)

    /** Extracts the first HTTP(S) URL from shared text and validates it as a YouTube URL. */
    fun extractYouTubeUrl(rawText: String): String? {
        val candidate = urlExtractRegex.find(rawText.trim())?.value ?: return null
        val cleaned = candidate.trimEnd('.', ',', ')', ']', '"', '\'')
        return cleaned.takeIf { isValidYouTubeUrl(it) }
    }

    fun isValidYouTubeUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isNotEmpty() && youtubeHostRegex.matches(trimmed)
    }

    fun isPlaylistUrl(url: String): Boolean = playlistRegex.containsMatchIn(url)
}

/** Pure builder for yt-dlp CLI options, decoupled from Android runtime for unit testing. */
object DownloadOptionBuilder {

    fun buildOptions(config: DownloadConfig, outputTemplate: String): List<Pair<String, String?>> {
        val options = mutableListOf<Pair<String, String?>>(
            (if (config.allowPlaylist) "--yes-playlist" else "--no-playlist") to null,
            "--no-mtime" to null,
            "--no-part" to null,
            "--force-ipv4" to null,
            "--windows-filenames" to null,
            "-o" to outputTemplate
        )

        when (config.format) {
            DownloadFormat.MP4 -> {
                options += "-f" to config.videoQuality.toFormatSelector()
                options += "--merge-output-format" to "mp4"
                if (config.embedSubtitles) {
                    options += "--write-subs" to null
                    options += "--write-auto-subs" to null
                    options += "--sub-langs" to "zh-Hant,zh-TW,zh,en"
                    options += "--embed-subs" to null
                }
            }
            DownloadFormat.MP3 -> {
                options += "-x" to null
                options += "--audio-format" to "mp3"
                options += "--audio-quality" to config.audioBitrate.ffmpegArg
                if (config.embedCoverAndMetadata) {
                    options += "--embed-thumbnail" to null
                    options += "--add-metadata" to null
                }
            }
        }
        return options
    }
}

/** Summarizes verbose multi-line yt-dlp / exception output into 1-2 concise lines. */
object ErrorMessageFormatter {

    fun summarize(rawMessage: String?, maxLines: Int = 2, maxChars: Int = 220): String {
        if (rawMessage.isNullOrBlank()) return "Download failed due to an unknown error."

        val lines = rawMessage
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) return "Download failed due to an unknown error."

        val errorLines = lines.filter { it.startsWith("ERROR:", ignoreCase = true) }
        val selected = if (errorLines.isNotEmpty()) {
            errorLines.takeLast(maxLines)
        } else {
            lines.takeLast(maxLines)
        }

        val joined = selected.joinToString("\n")
        return if (joined.length > maxChars) {
            joined.take(maxChars - 1).trimEnd() + "…"
        } else {
            joined
        }
    }
}
