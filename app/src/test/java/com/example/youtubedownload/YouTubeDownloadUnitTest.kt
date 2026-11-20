package com.example.youtubedownload

import com.example.youtubedownload.domain.AudioBitrate
import com.example.youtubedownload.domain.DownloadConfig
import com.example.youtubedownload.domain.DownloadFormat
import com.example.youtubedownload.domain.DownloadOptionBuilder
import com.example.youtubedownload.domain.ErrorMessageFormatter
import com.example.youtubedownload.domain.VideoQuality
import com.example.youtubedownload.domain.YouTubeUrlValidator
import com.example.youtubedownload.ui.AppLanguage
import com.example.youtubedownload.ui.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlValidatorTest {

    @Test
    fun `validates standard, short, shorts, and music YouTube URLs`() {
        assertTrue(YouTubeUrlValidator.isValidYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubeUrlValidator.isValidYouTubeUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YouTubeUrlValidator.isValidYouTubeUrl("https://m.youtube.com/shorts/abc123"))
        assertTrue(YouTubeUrlValidator.isValidYouTubeUrl("https://music.youtube.com/watch?v=abc123"))
    }

    @Test
    fun `rejects empty or non-YouTube URLs`() {
        assertFalse(YouTubeUrlValidator.isValidYouTubeUrl(""))
        assertFalse(YouTubeUrlValidator.isValidYouTubeUrl("   "))
        assertFalse(YouTubeUrlValidator.isValidYouTubeUrl("https://vimeo.com/12345"))
        assertFalse(YouTubeUrlValidator.isValidYouTubeUrl("not a url"))
    }

    @Test
    fun `extracts YouTube URL from shared text`() {
        val shared = "Check out this video! https://youtu.be/dQw4w9WgXcQ?si=test."
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=test",
            YouTubeUrlValidator.extractYouTubeUrl(shared)
        )
    }

    @Test
    fun `returns null when shared text has no YouTube URL`() {
        assertNull(YouTubeUrlValidator.extractYouTubeUrl("Hello world https://example.com"))
    }
}

class DownloadOptionBuilderTest {

    @Test
    fun `builds MP4 options with selected video quality`() {
        val config = DownloadConfig(
            url = "https://youtu.be/dQw4w9WgXcQ",
            format = DownloadFormat.MP4,
            videoQuality = VideoQuality.P720
        )

        val options = DownloadOptionBuilder.buildOptions(config, "/tmp/%(title)s.%(ext)s")

        assertTrue(options.contains("--no-playlist" to null))
        assertTrue(options.contains("--no-part" to null))
        assertTrue(options.contains("--force-ipv4" to null))
        assertTrue(options.contains("--windows-filenames" to null))
        assertTrue(options.contains("-o" to "/tmp/%(title)s.%(ext)s"))
        assertTrue(options.contains("--merge-output-format" to "mp4"))
        val formatSelector = options.first { it.first == "-f" }.second
        assertTrue(formatSelector!!.contains("height<=720"))
    }

    @Test
    fun `builds MP3 options with selected audio bitrate`() {
        val config = DownloadConfig(
            url = "https://youtu.be/dQw4w9WgXcQ",
            format = DownloadFormat.MP3,
            audioBitrate = AudioBitrate.K320
        )

        val options = DownloadOptionBuilder.buildOptions(config, "/tmp/%(title)s.%(ext)s")

        assertTrue(options.contains("-x" to null))
        assertTrue(options.contains("--audio-format" to "mp3"))
        assertTrue(options.contains("--audio-quality" to "320K"))
    }

    @Test
    fun `summarizes 100-line error output into at most 2 concise lines`() {
        val noisyLines = (1..100).joinToString("\n") { i ->
            if (i == 95) "ERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm you're not a bot"
            else "WARNING: some verbose line $i"
        }

        val summary = ErrorMessageFormatter.summarize(noisyLines)
        val resultLines = summary.lines()

        assertTrue(resultLines.size <= 2)
        assertTrue(summary.contains("ERROR: [youtube] dQw4w9WgXcQ"))
    }

    @Test
    fun `toggles between Traditional Chinese and English strings`() {
        assertEquals(AppLanguage.EN, AppLanguage.ZH_TW.next())
        assertEquals(AppLanguage.ZH_TW, AppLanguage.EN.next())
        assertEquals("YouTube 下載器", AppStrings.forLanguage(AppLanguage.ZH_TW).appTitle)
        assertEquals("YouTube Downloader", AppStrings.forLanguage(AppLanguage.EN).appTitle)
    }

    @Test
    fun `detects playlist URLs and builds playlist, subtitle, and cover options`() {
        assertTrue(YouTubeUrlValidator.isPlaylistUrl("https://www.youtube.com/playlist?list=PL123"))
        assertTrue(YouTubeUrlValidator.isPlaylistUrl("https://www.youtube.com/watch?v=abc&list=PL123"))
        assertFalse(YouTubeUrlValidator.isPlaylistUrl("https://youtu.be/dQw4w9WgXcQ"))

        val mp4Config = DownloadConfig(
            url = "https://www.youtube.com/playlist?list=PL123",
            format = DownloadFormat.MP4,
            embedSubtitles = true,
            allowPlaylist = true
        )
        val mp4Options = DownloadOptionBuilder.buildOptions(mp4Config, "/tmp/%(title)s.%(ext)s")
        assertTrue(mp4Options.contains("--yes-playlist" to null))
        assertTrue(mp4Options.contains("--embed-subs" to null))
        assertTrue(mp4Options.contains("--write-auto-subs" to null))

        val mp3Config = DownloadConfig(
            url = "https://youtu.be/dQw4w9WgXcQ",
            format = DownloadFormat.MP3,
            embedCoverAndMetadata = true
        )
        val mp3Options = DownloadOptionBuilder.buildOptions(mp3Config, "/tmp/%(title)s.%(ext)s")
        assertTrue(mp3Options.contains("--embed-thumbnail" to null))
        assertTrue(mp3Options.contains("--add-metadata" to null))
    }
}
