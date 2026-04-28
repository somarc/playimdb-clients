/*
 * PlayIMDB TV — sideloaded Amazon Firestick app
 *
 * BUILD:
 *   cd playimdb-tv
 *   ./gradlew assembleDebug
 *   APK output: app/build/outputs/apk/debug/app-debug.apk
 *
 * INSTALL via ADB (Firestick on same network with ADB debugging enabled):
 *   adb connect <firestick-ip>:5555
 *   adb install -r app/build/outputs/apk/debug/app-debug.apk
 *
 * Then launch from Firestick: Settings → Applications → Manage Installed
 * Applications → PlayIMDB → Launch application. (After first launch it
 * appears in "Your Apps & Channels".)
 *
 * UNINSTALL:
 *   adb uninstall com.playimdb.tv
 */
package com.playimdb.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.playimdb.tv.ui.SearchScreen
import com.playimdb.tv.ui.theme.PlayImdbTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            viewModel.onQueryChange(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayImdbTvTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onResultSelected = { id -> openInBrowser("https://playimdb.com/title/$id") },
                    onVoiceSearch = { startVoiceSearch() },
                )
            }
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a movie or show title")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        // Intentionally no try/catch — if the device has no speech service we want
        // the ActivityNotFoundException to surface so the failure is visible.
        voiceLauncher.launch(intent)
    }
}
