package com.example.youtubedownload.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.youtubedownload.YouTubeDownloadApp
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadOptionBuilder
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class DownloadedFileResult(
    val fileName: String,
    val savedLocation: String
)

class YouTubeDownloadRepository(private val context: Context) {

    private suspend fun ensureInitialized() {
        val app = context.applicationContext as YouTubeDownloadApp
        val initError = app.initResult.await()
        if (initError != null) {
            throw IllegalStateException(
                "Downloader engine failed to initialize: ${initError.localizedMessage}",
                initError
            )
        }
    }

    suspend fun download(
        config: DownloadConfig,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit
    ): DownloadedFileResult = withContext(Dispatchers.IO) {
        ensureInitialized()

        val tempDir = File(context.cacheDir, "yt_temp/$processId").apply { mkdirs() }
        try {
            val outputTemplate = File(tempDir, "%(title).120B.%(ext)s").absolutePath
            val request = YoutubeDLRequest(config.url.trim()).apply {
                DownloadOptionBuilder.buildOptions(config, outputTemplate).forEach { (flag, value) ->
                    if (value == null) addOption(flag) else addOption(flag, value)
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line)
            }

            val outputFile = findOutputFile(tempDir, config.format.extension)
                ?: throw IOException("Download finished but no .${config.format.extension} file was produced.")

            publishToDownloads(outputFile, config.format)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun cancel(processId: String): Boolean {
        return try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to destroy process $processId", t)
            false
        }
    }

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        ensureInitialized()
        YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
    }

    private fun findOutputFile(tempDir: File, expectedExt: String): File? {
        val files = tempDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: return null
        return files.firstOrNull { it.extension.equals(expectedExt, ignoreCase = true) }
            ?: files.maxByOrNull { it.length() }
    }

    private fun publishToDownloads(sourceFile: File, format: DownloadFormat): DownloadedFileResult {
        val mimeType = when (format) {
            DownloadFormat.MP4 -> "video/mp4"
            DownloadFormat.MP3 -> "audio/mpeg"
        }
        val fileName = sourceFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER_NAME"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore entry for $fileName")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IOException("Failed to open output stream for $uri")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }

            return DownloadedFileResult(
                fileName = fileName,
                savedLocation = "Downloads/$SUBFOLDER_NAME/$fileName"
            )
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, SUBFOLDER_NAME).apply { mkdirs() }
            val targetFile = uniqueTargetFile(targetDir, fileName)
            sourceFile.copyTo(targetFile, overwrite = false)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )

            return DownloadedFileResult(
                fileName = targetFile.name,
                savedLocation = targetFile.absolutePath
            )
        }
    }

    private fun uniqueTargetFile(dir: File, fileName: String): File {
        val initial = File(dir, fileName)
        if (!initial.exists()) return initial

        val base = initial.nameWithoutExtension
        val ext = initial.extension
        var index = 1
        while (true) {
            val candidateName = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            val candidate = File(dir, candidateName)
            if (!candidate.exists()) return candidate
            index++
        }
    }

    companion object {
        private const val TAG = "YouTubeDownloadRepo"
        private const val SUBFOLDER_NAME = "YouTubeDownload"
    }
}
