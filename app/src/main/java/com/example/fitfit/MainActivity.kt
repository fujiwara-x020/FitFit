package com.example.fitfit  // ← あなたのパッケージ名に合わせて変更

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitFitApp() }
    }
}

/** アプリのルート（UIは関数にカプセル化） */
@Composable
fun FitFitApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TitleCenter("FITFIT")
        }
    }
}

/** タイトルだけ中央に表示（処理は明快に分離） */
@Composable
fun TitleCenter(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium)
    }
}
