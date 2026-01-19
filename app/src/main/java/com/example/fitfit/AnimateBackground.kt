package com.example.fitfit

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// パーティクル情報の保持用クラス
private data class Particle(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float
)

@Composable
fun AnimatedBackground() {
    // 無限アニメーション用のクロック
    val infiniteTransition = rememberInfiniteTransition(label = "bgAnim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing), // 20秒->60秒へ変更し、さらにゆっくりに
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // パーティクルの初期化（一度だけ実行）
    val particles = remember {
        List(30) {
            Particle(
                x = Random.nextFloat(), // 0.0 - 1.0
                y = Random.nextFloat(),
                // 速度を大幅に落とす
                speed = 0.0001f + Random.nextFloat() * 0.0003f,
                size = 1.5f + Random.nextFloat() * 2.5f,
                // 透明度を下げて目立たなくする (0.02 ~ 0.12)
                alpha = 0.02f + Random.nextFloat() * 0.1f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 背景グラデーション (Radial Gradient at Top Right)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                center = Offset(w, 0f),
                radius = w * 1.2f
            )
        )

        // 2. グリッド描画 (薄く維持)
        val gridSize = 40.dp.toPx()
        val gridColor = CyberCyan.copy(alpha = 0.03f) // さらに薄く

        // 縦線
        var gx = 0f
        while (gx < w) {
            drawLine(gridColor, start = Offset(gx, 0f), end = Offset(gx, h), strokeWidth = 1f)
            gx += gridSize
        }
        // 横線
        var gy = 0f
        while (gy < h) {
            drawLine(gridColor, start = Offset(0f, gy), end = Offset(w, gy), strokeWidth = 1f)
            gy += gridSize
        }

        // 3. (削除) 擬似波形アニメーション

        // 4. パーティクル更新と描画
        particles.forEach { p ->
            // 上に上がるように修正: Y座標を減算していく
            // time (0->1000) * speed * 係数 で移動量を算出
            val moveY = time * p.speed * 50f

            // 初期位置 p.y から移動量を引く
            val rawY = p.y - moveY

            // 0.0~1.0 の範囲でループさせる (floorを使って少数部分だけ取り出す)
            // rawYが負になっても、rawY - floor(rawY) は常に 0.0~1.0 の正の値になる
            val currentYRatio = rawY - kotlin.math.floor(rawY)

            val px = p.x * w
            val py = currentYRatio * h

            drawCircle(
                color = CyberCyan.copy(alpha = p.alpha),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}