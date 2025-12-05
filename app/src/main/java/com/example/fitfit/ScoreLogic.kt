package com.example.fitfit

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

/**
 * 擬似 EMG データから 0–100 のスコアを算出
 */
fun calculatePseudoScore(data: List<Float>): Int {
    if (data.isEmpty()) return 0

    val sumSquares = data.sumOf { (it * it).toDouble() }
    val rms = sqrt(sumSquares / data.size)

    val baseScore = 100.0 - (rms * 1.5)
    return baseScore.toInt().coerceIn(20, 98)
}

/**
 * スコアに応じた色
 */
fun calculateScoreColor(score: Int): Color {
    return when {
        score >= 80 -> Green500
        score >= 50 -> Blue600
        else -> Red500
    }
}

/**
 * スコアに応じた評価コメント
 */
fun evaluateCondition(score: Int): String {
    return when {
        score >= 80 -> "EXCELLENT"
        score >= 60 -> "GOOD"
        score >= 40 -> "FATIGUED"
        else -> "RECOVERY NEEDED"
    }
}
