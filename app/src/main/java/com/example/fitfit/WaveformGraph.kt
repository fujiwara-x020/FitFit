package com.example.fitfit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun WaveformGraph(
    dataPoints: List<Float>
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds() // 【修正】描画領域外を切り取る
            .graphicsLayer { alpha = 0.6f } // 少し薄くしてタイマーを見やすくする
    ) {
        if (dataPoints.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val centerY = height / 2

        // 【修正】倍率を調整 (以前は5fで大きすぎたので、1.5fくらいに抑える)
        // BITalinoのデータは0-1023。中心は512。
        val scaleY = height / 1024f * 1.5f

        val path = Path()
        // X軸のステップ幅
        val stepX = width / (dataPoints.size.coerceAtLeast(1) - 1).toFloat()

        dataPoints.forEachIndexed { index, value ->
            // 512を中心として、上下に振幅させる
            val normalizedY = centerY - ((value - 512f) * scaleY)
            val x = index * stepX
            val y = normalizedY

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Blue600,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}