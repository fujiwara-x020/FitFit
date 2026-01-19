package com.example.fitfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyofluxTheme {
                FitFitApp()
            }
        }
    }
}

@Composable
fun FitFitApp() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }

    Crossfade(targetState = showSplash, label = "SplashTransition") { isSplash ->
        if (isSplash) {
            MyofluxSplashScreen()
        } else {
            // 【修正完了】引数名を変更済み
            MainScreen(initialMacAddress = "20:16:07:18:14:56")
        }
    }
}