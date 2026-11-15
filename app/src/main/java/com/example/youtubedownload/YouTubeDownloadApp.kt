package com.example.youtubedownload

import android.app.Application
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
 * and FFmpeg binaries off the main thread.
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
                initResult.complete(null)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize youtubedl-android / FFmpeg", t)
                initResult.complete(t)
            }
        }
    }

    companion object {
        private const val TAG = "YouTubeDownloadApp"
    }
}
