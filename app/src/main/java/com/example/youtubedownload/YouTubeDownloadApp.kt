package com.example.youtubedownload

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point that asynchronously initializes native youtubedl-android
 * and FFmpeg binaries off the main thread, and updates yt-dlp on first launch.
 */
class YouTubeDownloadApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Resolves with null on success or the initialization Throwable on failure. */
    val initResult = CompletableDeferred<Throwable?>()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@YouTubeDownloadApp)
                FFmpeg.getInstance().init(this@YouTubeDownloadApp)
                autoUpdateYtDlpIfFirstRun()
                initResult.complete(null)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize youtubedl-android / FFmpeg", t)
                initResult.complete(t)
            }
        }
    }

    private fun autoUpdateYtDlpIfFirstRun() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIAL_UPDATE_DONE, false)) return
        try {
            YoutubeDL.getInstance().updateYoutubeDL(
                this@YouTubeDownloadApp,
                YoutubeDL.UpdateChannel.NIGHTLY
            )
            prefs.edit().putBoolean(KEY_INITIAL_UPDATE_DONE, true).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Initial yt-dlp auto-update skipped", t)
        }
    }

    companion object {
        private const val TAG = "YouTubeDownloadApp"
        private const val PREFS_NAME = "yt_downloader_prefs"
        private const val KEY_INITIAL_UPDATE_DONE = "initial_ytdlp_update_done"
    }
}
