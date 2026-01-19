package com.example.fitfit

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GeometricBody(
    onMuscleSelect: (String) -> Unit,
    muscleScores: Map<String, Int> = emptyMap()
) {
    val scale = 1.1f

    // ボディパーツの「ガラス風」スタイル
    val glassColor = Color.White.copy(alpha = 0.05f)
    val glassBorder = Color.White.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .width(240.dp * scale)
            .height(500.dp * scale)
    ) {
        @Composable
        fun BodyPart(
            w: Dp, h: Dp, x: Dp, y: Dp, radius: Dp,
            rotation: Float = 0f,
            isCritical: Boolean = false
        ) {
            val borderColor = if (isCritical) CyberRed.copy(alpha = 0.8f) else glassBorder
            val bgColor = if (isCritical) CyberRed.copy(alpha = 0.15f) else glassColor

            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(w, h)
                    .rotate(rotation)
                    .shadow(if(isCritical) 15.dp else 0.dp, RoundedCornerShape(radius), spotColor = CyberRed)
                    .background(bgColor, RoundedCornerShape(radius))
                    .border(1.dp, borderColor, RoundedCornerShape(radius))
            )
        }

        val midX = 120.dp * scale

        // --- 簡易ロジック: スコアが悪い部位を赤く点滅させる ---
        fun isMuscleCritical(name: String): Boolean {
            val s = muscleScores[name]
            return s != null && s < 50
        }

        // 各パーツ配置 (座標は維持)
        BodyPart(50.dp, 60.dp, midX - 25.dp, 0.dp, 25.dp) // Head
        BodyPart(20.dp, 15.dp, midX - 10.dp, 55.dp, 4.dp) // Neck
        BodyPart(70.dp, 110.dp, midX - 35.dp, 68.dp, 15.dp) // Torso Upper
        BodyPart(70.dp, 40.dp, midX - 35.dp, 180.dp, 15.dp) // Torso Lower

        BodyPart(24.dp, 70.dp, midX - 35.dp - 24.dp, 70.dp, 12.dp, 5f, isMuscleCritical("Right Biceps")) // R-Arm
        BodyPart(24.dp, 70.dp, midX + 35.dp, 70.dp, 12.dp, -5f, isMuscleCritical("Left Biceps")) // L-Arm

        BodyPart(20.dp, 70.dp, midX - 38.dp - 20.dp, 145.dp, 10.dp, 5f)
        BodyPart(20.dp, 70.dp, midX + 38.dp, 145.dp, 10.dp, -5f)

        BodyPart(30.dp, 100.dp, midX - 15.dp - 25.dp, 225.dp, 15.dp, 2f, isMuscleCritical("Right Quadriceps")) // R-Leg
        BodyPart(30.dp, 100.dp, midX - 15.dp + 25.dp, 225.dp, 15.dp, -2f, isMuscleCritical("Left Quadriceps")) // L-Leg
        BodyPart(26.dp, 100.dp, midX - 13.dp - 25.dp, 330.dp, 13.dp, 0f)
        BodyPart(26.dp, 100.dp, midX - 13.dp + 25.dp, 330.dp, 13.dp, 0f)

        // --- Hotspots (Interactive) ---
        @Composable
        fun CyberHotspot(
            xOffset: Dp, yOffset: Dp,
            muscleName: String,
            label: String
        ) {
            val score = muscleScores[muscleName]
            val isDataAvailable = score != null
            val isCritical = isDataAvailable && score!! < 50

            // 色決定
            val baseColor = when {
                !isDataAvailable -> CyberTextSub // データなし: グレー
                isCritical -> CyberRed           // 悪い: 赤
                else -> CyberCyan                // 良い: 青
            }

            val displayScore = score?.toString() ?: "--"

            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .size(44.dp)
                    .background(CyberDark.copy(alpha = 0.8f), CircleShape)
                    .border(1.dp, baseColor.copy(alpha = if(isDataAvailable) 1f else 0.5f), CircleShape)
                    .clickable { onMuscleSelect(muscleName) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayScore,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = baseColor
                        )
                    )
                    Text(
                        text = label,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 8.sp,
                            color = CyberTextSub
                        )
                    )
                }
            }
        }

        // 【位置修正】ボタンサイズ(44dp)の半分(22dp)を考慮して中心合わせ
        CyberHotspot(midX - 69.dp, 105.dp, "Right Biceps", "R-BIC")
        CyberHotspot(midX + 25.dp, 105.dp, "Left Biceps", "L-BIC")

        CyberHotspot(midX - 22.dp, 100.dp, "Pectoralis", "PEC")
        CyberHotspot(midX - 22.dp, 150.dp, "Abdominals", "ABS")

        CyberHotspot(midX - 47.dp, 275.dp, "Right Quadriceps", "R-Q")
        CyberHotspot(midX + 3.dp, 275.dp, "Left Quadriceps", "L-Q")
    }
}