package com.nefeon.akzsa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nefeon.akzsa.ui.AkzsaApp

class MainActivity : ComponentActivity() {
    private var requestMicPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()   // required look & feel on Android 15+; Scaffold/TopAppBar consume the insets
        super.onCreate(savedInstanceState)
        requestMicPermission = intent?.getBooleanExtra(EXTRA_REQUEST_MIC_PERMISSION, false) == true
        setContent {
            AkzsaApp(requestMicPermission = requestMicPermission)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestMicPermission = intent.getBooleanExtra(EXTRA_REQUEST_MIC_PERMISSION, false)
    }

    companion object {
        const val EXTRA_REQUEST_MIC_PERMISSION = "com.nefeon.akzsa.REQUEST_MIC_PERMISSION"
    }
}
