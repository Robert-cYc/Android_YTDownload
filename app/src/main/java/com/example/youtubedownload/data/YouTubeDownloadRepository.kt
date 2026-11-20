package com.example.youtubedownload.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.youtubedownload.YouTubeDownloadApp
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadOptionBuilder
import com.example.youtubedownload.domain.DownloadedItem
import com.example.youtubedownload.domain.SearchResultItem
import com.example.youtubedownload.domain.VideoPreview
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class DownloadedFileResult(
    val fileName: String,
    val savedLocation: String,
    val contentUri: String?,
    val mimeType: String
)

class YouTubeDownloadRepository(private val context: Context) {

    fun getDownloadFolderDisplayPath(): String =
        "Internal Storage / ${Environment.DIRECTORY_DOWNLOADS} / $SUBFOLDER_NAME"

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

    suspend fun fetchVideoInfo(url: String): VideoPreview = withContext(Dispatchers.IO) {
        ensureInitialized()
        PythonDnsPatchInstaller.refreshHostCache(context)

        val request = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-playlist")
            addOption("--force-ipv4")
        }
        val info = YoutubeDL.getInstance().getInfo(request)
        VideoPreview(
            title = info.title ?: "YouTube Video",
            uploader = info.uploader ?: "",
            durationSeconds = info.duration.toLong(),
            thumbnailUrl = info.thumbnail
        )
    }

    suspend fun searchYouTube(query: String, limit: Int = 8): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            PythonDnsPatchInstaller.refreshHostCache(context)

            val request = YoutubeDLRequest("ytsearch$limit:${query.trim()}").apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--force-ipv4")
            }
            val response = YoutubeDL.getInstance().execute(request, "yt-search") { _, _, _ -> }
            parseSearchResults(response.out.orEmpty())
        }

    private fun parseSearchResults(rawOut: String): List<SearchResultItem> {
        val jsonLine = rawOut.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("{") && it.contains("\"entries\"") }
            ?: return emptyList()

        val root = JSONObject(jsonLine)
        val entries = root.optJSONArray("entries") ?: return emptyList()
        val results = mutableListOf<SearchResultItem>()

        for (i in 0 until entries.length()) {
            val obj = entries.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").ifBlank { "YouTube Video" }
            val uploader = obj.optString("uploader").ifBlank {
                obj.optString("channel").ifBlank { "" }
            }
            val duration = obj.optDouble("duration", 0.0).toLong().coerceAtLeast(0L)
            val thumb = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: obj.optJSONArray("thumbnails")?.let { arr ->
                    if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
                }?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val videoUrl = obj.optString("webpage_url").takeIf { it.startsWith("http") }
                ?: obj.optString("url").takeIf { it.startsWith("http") }
                ?: "https://www.youtube.com/watch?v=$id"

            results += SearchResultItem(
                id = id,
                title = title,
                uploader = uploader,
                durationSeconds = duration,
                thumbnailUrl = thumb,
                videoUrl = videoUrl
            )
        }
        return results
    }

    suspend fun download(
        config: DownloadConfig,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit
    ): List<DownloadedFileResult> = withContext(Dispatchers.IO) {
        ensureInitialized()
        PythonDnsPatchInstaller.refreshHostCache(context)

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

            val outputFiles = findOutputFiles(tempDir, config.format.extension)
            if (outputFiles.isEmpty()) {
                throw IOException("Download finished but no .${config.format.extension} file was produced.")
            }

            outputFiles.map { file -> publishToDownloads(file, config.format) }
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
        PythonDnsPatchInstaller.refreshHostCache(context)
        YoutubeDL.getInstance().updateYoutubeDL(
            context.applicationContext,
            YoutubeDL.UpdateChannel.NIGHTLY
        )
    }

    suspend fun listDownloadedFiles(): List<DownloadedItem> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStoreDownloads()
            } else {
                listLegacyDownloadFiles()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to list downloaded files", t)
            emptyList()
        }
    }

    suspend fun deleteDownloadedFile(item: DownloadedItem): Boolean = withContext(Dispatchers.IO) {
        val uriString = item.contentUri ?: return@withContext false
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null) > 0
            } else {
                val path = uri.path ?: return@withContext false
                File(path).delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to delete $uriString", t)
            false
        }
    }

    private fun queryMediaStoreDownloads(): List<DownloadedItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val items = mutableListOf<DownloadedItem>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.RELATIVE_PATH
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$SUBFOLDER_NAME%")
        val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: guessMimeType(name)
                val relPath = cursor.getString(pathCol)
                    ?: "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER_NAME/"
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                items += DownloadedItem(
                    fileName = name,
                    folderPath = "Internal Storage / ${relPath.trimEnd('/')}",
                    sizeBytes = size,
                    contentUri = contentUri,
                    mimeType = mime
                )
            }
        }
        return items
    }

    private fun listLegacyDownloadFiles(): List<DownloadedItem> {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, SUBFOLDER_NAME)
        if (!targetDir.exists()) return emptyList()

        return targetDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                DownloadedItem(
                    fileName = file.name,
                    folderPath = targetDir.absolutePath,
                    sizeBytes = file.length(),
                    contentUri = Uri.fromFile(file).toString(),
                    mimeType = guessMimeType(file.name)
                )
            }
            ?: emptyList()
    }

    private fun guessMimeType(fileName: String): String =
        if (fileName.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "video/mp4"

    private fun findOutputFiles(tempDir: File, expectedExt: String): List<File> {
        val files = tempDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: return emptyList()
        val matching = files.filter { it.extension.equals(expectedExt, ignoreCase = true) }
        return if (matching.isNotEmpty()) {
            matching.sortedBy { it.name }
        } else {
            listOfNotNull(files.maxByOrNull { it.length() })
        }
    }

    private fun publishToDownloads(sourceFile: File, format: DownloadFormat): DownloadedFileResult {
        val mimeType = format.mimeType
        val fileName = sourceFile.name
        val displayFolder = getDownloadFolderDisplayPath()

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
                savedLocation = "$displayFolder / $fileName",
                contentUri = uri.toString(),
                mimeType = mimeType
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
                savedLocation = targetFile.absolutePath,
                contentUri = Uri.fromFile(targetFile).toString(),
                mimeType = mimeType
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
        const val SUBFOLDER_NAME = "YouTubeDownload"
    }
}
