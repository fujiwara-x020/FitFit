package com.example.fitfit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WaveformGraph() {
    val points = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        repeat(50) { points.add(0f) }
        while (isActive) {
            delay(16)
            points.removeAt(0)
            val base = (Math.random() - 0.5) * 10
            val spike = if (Math.random() > 0.9) (Math.random() - 0.5) * 50 else 0.0
            points.add((base + spike).toFloat())
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.4f }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val stepX = width / points.size

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = centerY + value
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
