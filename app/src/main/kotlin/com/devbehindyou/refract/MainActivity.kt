package com.devbehindyou.refract

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 1: deliberately empty beyond the splash screen and a themed, content-free
 * surface. No navigation graph and no screens — those arrive with Phase 3+ (see
 * screens/SCREEN_INDEX.md). This is the entire UI for this phase, per its "no feature
 * code, no UI beyond the empty Activity" scope.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() per androidx.core.splashscreen's
        // documented usage.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Intentionally empty — Phase 1 ships no UI beyond this surface.
                }
            }
        }
    }
}
