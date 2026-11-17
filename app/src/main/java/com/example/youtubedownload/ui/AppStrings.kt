package com.example.youtubedownload.ui

import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.VideoQuality

enum class AppLanguage(val code: String, val toggleLabel: String) {
    ZH_TW("zh-TW", "EN"),
    EN("en", "繁中");

    fun next(): AppLanguage = if (this == ZH_TW) EN else ZH_TW

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ZH_TW
    }
}

data class AppStrings(
    val appTitle: String,
    val urlLabel: String,
    val pasteFromClipboard: String,
    val clear: String,
    val shareTip: String,
    val formatTitle: String,
    val mp4Label: String,
    val mp3Label: String,
    val videoQualityTitle: String,
    val bestQualityLabel: String,
    val audioBitrateTitle: String,
    val downloadingBtn: String,
    val cancelBtn: String,
    val preparingDownload: String,
    val processing: String,
    val downloadCompleted: String,
    val folderPrefix: String,
    val openFile: String,
    val openFolder: String,
    val downloadCancelled: String,
    val updateEngineRetry: String,
    val downloadFolderTitle: String,
    val openShort: String,
    val invalidUrlError: String,
    val engineUpdatedDone: String,
    val engineAlreadyUpToDate: String,
    val engineUpdateFinished: String,
    val updateYtDlpDesc: String
) {
    fun formatLabel(format: DownloadFormat): String = when (format) {
        DownloadFormat.MP4 -> mp4Label
        DownloadFormat.MP3 -> mp3Label
    }

    fun qualityLabel(quality: VideoQuality): String =
        if (quality == VideoQuality.BEST) bestQualityLabel else quality.label

    fun downloadButtonText(format: DownloadFormat): String =
        if (this === ZhTwStrings) "下載 ${format.name}" else "Download ${format.name}"

    fun etaText(seconds: Long): String =
        if (this === ZhTwStrings) "剩餘 ${seconds} 秒" else "ETA ${seconds}s"

    fun downloadedFilesHeader(count: Int): String =
        if (this === ZhTwStrings) "已下載檔案 ($count)" else "Downloaded Files ($count)"

    fun engineUpdateFailed(reason: String): String =
        if (this === ZhTwStrings) "更新失敗：$reason" else "Update failed: $reason"

    companion object {
        val ZhTwStrings = AppStrings(
            appTitle = "YouTube 下載器",
            urlLabel = "YouTube 網址",
            pasteFromClipboard = "從剪貼簿貼上",
            clear = "清除",
            shareTip = "提示：在 YouTube 點擊「分享 → YouTube Downloader」可直接帶入網址。",
            formatTitle = "下載格式",
            mp4Label = "MP4（影片）",
            mp3Label = "MP3（音訊）",
            videoQualityTitle = "影片畫質",
            bestQualityLabel = "最佳",
            audioBitrateTitle = "音訊位元率",
            downloadingBtn = "下載中...",
            cancelBtn = "取消",
            preparingDownload = "正在準備下載...",
            processing = "處理中...",
            downloadCompleted = "下載完成",
            folderPrefix = "資料夾：",
            openFile = "開啟檔案",
            openFolder = "開啟資料夾",
            downloadCancelled = "已取消下載。",
            updateEngineRetry = "更新 yt-dlp 引擎並重試",
            downloadFolderTitle = "下載資料夾",
            openShort = "開啟",
            invalidUrlError = "請輸入有效的 YouTube 網址（youtube.com 或 youtu.be）。",
            engineUpdatedDone = "yt-dlp 已更新至最新版本。",
            engineAlreadyUpToDate = "yt-dlp 已是最新版本。",
            engineUpdateFinished = "yt-dlp 更新完成。",
            updateYtDlpDesc = "更新 yt-dlp"
        )

        val EnStrings = AppStrings(
            appTitle = "YouTube Downloader",
            urlLabel = "YouTube URL",
            pasteFromClipboard = "Paste from Clipboard",
            clear = "Clear",
            shareTip = "Tip: In YouTube, tap Share → YouTube Downloader to send a video here directly.",
            formatTitle = "Format",
            mp4Label = "MP4 (Video)",
            mp3Label = "MP3 (Audio)",
            videoQualityTitle = "Video Quality",
            bestQualityLabel = "Best",
            audioBitrateTitle = "Audio Bitrate",
            downloadingBtn = "Downloading...",
            cancelBtn = "Cancel",
            preparingDownload = "Preparing download...",
            processing = "Processing...",
            downloadCompleted = "Download Completed",
            folderPrefix = "Folder: ",
            openFile = "Open File",
            openFolder = "Open Folder",
            downloadCancelled = "Download cancelled.",
            updateEngineRetry = "Update yt-dlp Engine & Retry",
            downloadFolderTitle = "Download Folder",
            openShort = "Open",
            invalidUrlError = "Please enter a valid YouTube URL (youtube.com or youtu.be).",
            engineUpdatedDone = "yt-dlp updated to the latest version.",
            engineAlreadyUpToDate = "yt-dlp is already up to date.",
            engineUpdateFinished = "yt-dlp update finished.",
            updateYtDlpDesc = "Update yt-dlp"
        )

        fun forLanguage(language: AppLanguage): AppStrings = when (language) {
            AppLanguage.ZH_TW -> ZhTwStrings
            AppLanguage.EN -> EnStrings
        }
    }
}
