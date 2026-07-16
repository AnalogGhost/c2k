package com.hackerapps.c2k.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hackerapps.c2k.ui.navigation.NavGraph
import com.hackerapps.c2k.ui.theme.C2KTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock portrait at runtime on phones only: a manifest screenOrientation lock
        // trips Play's large-screen checks, and Android 16 ignores it on sw600dp+
        // devices anyway. Large screens rotate freely and get the landscape layouts.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        enableEdgeToEdge()
        setContent {
            C2KTheme {
                NavGraph()
            }
        }
    }
}
