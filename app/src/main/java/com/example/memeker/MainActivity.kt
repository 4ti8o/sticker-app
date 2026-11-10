package com.example.memeker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.memeker.data.PackRepository
import com.example.memeker.ui.MainScreen
import com.example.memeker.ui.theme.MemekerTheme

class MainActivity : ComponentActivity() {
    private var isDatabaseReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Keep the splash screen on-screen until the condition is false
        splashScreen.setKeepOnScreenCondition {
            !isDatabaseReady
        }

        setContent {
            MemekerTheme {
                var readyToShow by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Simulate initialization (e.g. loading settings, checking auth, etc)
                    kotlinx.coroutines.delay(1000) // Artificial delay to see the splash
                    isDatabaseReady = true
                    readyToShow = true
                }

                if (readyToShow) {
                    MainScreen(PackRepository(this@MainActivity))
                } else {
                    LoadingScreen()
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

