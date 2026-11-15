package com.example.youtubedownload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.example.youtubedownload.ui.DownloadScreen
import com.example.youtubedownload.ui.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()

                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.startDownload()
                    }
                }

                DownloadScreen(
                    uiState = uiState,
                    onUrlChange = viewModel::onUrlChange,
                    onFormatSelected = viewModel::onFormatSelected,
                    onVideoQualitySelected = viewModel::onVideoQualitySelected,
                    onAudioBitrateSelected = viewModel::onAudioBitrateSelected,
                    onStartDownload = {
                        if (needsLegacyStoragePermission()) {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.startDownload()
                        }
                    },
                    onCancelDownload = viewModel::cancelDownload,
                    onUpdateEngine = viewModel::updateEngine,
                    onDismissEngineMessage = viewModel::dismissEngineMessage
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::onSharedText)
        }
    }

    private fun needsLegacyStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
    }
}
