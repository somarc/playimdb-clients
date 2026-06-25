/*
 * PlayIMDB TV — sideloaded Amazon Firestick app
 *
 * BUILD:
 *   cd android-tv
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playimdb.tv.ui.SearchScreen
import com.playimdb.tv.ui.SplashScreen
import com.playimdb.tv.ui.theme.PlayImdbTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayImdbTvTheme {
                var showSplash by remember { mutableStateOf(true) }
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 1_000),
                    label = "splash-to-search",
                ) { isSplash ->
                    if (isSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        SearchScreen(
                            viewModel = viewModel,
                            onResultSelected = { result ->
                                lifecycleScope.launch {
                                    openInBrowser(PlayUrlResolver.titleUrl(result.id, result.type))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
