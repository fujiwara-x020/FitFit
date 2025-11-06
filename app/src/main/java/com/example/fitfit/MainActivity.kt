package com.example.fitfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitFitApp() }
    }
}

@Composable
fun FitFitApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            // 1秒だけスプラッシュ表示 → 自動でグラフ表示
            var showGraph by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(1000)
                showGraph = true
            }
            if (showGraph) {
                EmgGraphScreenCentered()
            } else {
                SplashOnly()
            }
        }
    }
}

/* 1秒だけ表示するスプラッシュ */
@Composable
fun SplashOnly() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "FITFIT",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp)
        )
    }
}

/* 中央だけにコンパクトなグラフを表示 */
@Composable
fun EmgGraphScreenCentered(
    sampleRateHz: Int = 500,
    bufferPoints: Int = 600,
    chunkSize: Int = 12
) {
    val samples = remember { mutableStateListOf<Float>() }

    // ダミー波形（100Hz＋ノイズ）
    LaunchedEffect(Unit) {
        var t = 0.0
        val dt = 1.0 / sampleRateHz
        while (true) {
            repeat(chunkSize) {
                t += dt
                val signal = 0.6f * sin(2 * PI * 100 * t).toFloat()
                val noise = (Random.nextFloat() - 0.5f) * 0.3f
                samples += (signal + noise)
            }
            val overflow = samples.size - bufferPoints
            if (overflow > 0) repeat(overflow) { samples.removeAt(0) }
            delay((chunkSize * 1000.0 / sampleRateHz).toLong().coerceAtLeast(1))
        }
    }

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EMG Monitor", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)   // 画面幅の90%
                    .height(220.dp)       // コンパクト
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                EmgWaveCompact(samples)
            }
            Spacer(Modifier.height(8.dp))
            Text("SR: ${sampleRateHz}Hz / N: ${samples.size}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* コンパクト波形描画（Canvas 内で MaterialTheme を呼ばない） */
@Composable
fun EmgWaveCompact(samples: List<Float>) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val zeroLine = MaterialTheme.colorScheme.secondary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // グリッド
        val gxStep = w / 8f
        val gyStep = h / 6f
        var gx = 0f; while (gx <= w) { drawLine(outline, Offset(gx, 0f), Offset(gx, h), 1f); gx += gxStep }
        var gy = 0f; while (gy <= h) { drawLine(outline, Offset(0f, gy), Offset(w, gy), 1f); gy += gyStep }

        if (samples.isEmpty()) return@Canvas

        val absMax = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.1f)
        val amp = absMax * 1.2f
        fun mapY(v: Float) = h * (0.5f - 0.5f * (v / amp).coerceIn(-1f, 1f))

        val n = samples.size
        val stepX = if (n <= 1) w else w / (n - 1)
        val path = Path().apply {
            moveTo(0f, mapY(samples.first()))
            var i = 1
            while (i < n) { lineTo(stepX * i, mapY(samples[i])); i++ }
        }
        drawPath(path, primary, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawLine(zeroLine, Offset(0f, h / 2f), Offset(w, h / 2f), 1.5f)
    }
}
