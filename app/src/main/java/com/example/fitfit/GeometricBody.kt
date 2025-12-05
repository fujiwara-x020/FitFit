package com.example.fitfit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GeometricBody(onMuscleSelect: (String) -> Unit) {
    val scale = 1.1f
    Box(
        modifier = Modifier
            .width(240.dp * scale)
            .height(500.dp * scale)
    ) {
        val bodyColor = Slate200
        val borderColor = Slate300
        val borderSize = 2.dp

        @Composable
        fun BodyPart(w: Dp, h: Dp, x: Dp, y: Dp, radius: Dp, rotation: Float = 0f) {
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(w, h)
                    .rotate(rotation)
                    .background(bodyColor, RoundedCornerShape(radius))
                    .border(borderSize, borderColor, RoundedCornerShape(radius))
            )
        }

        val midX = 120.dp * scale
        BodyPart(50.dp, 60.dp, midX - 25.dp, 0.dp, 25.dp)
        BodyPart(20.dp, 15.dp, midX - 10.dp, 55.dp, 4.dp)
        BodyPart(70.dp, 110.dp, midX - 35.dp, 68.dp, 15.dp)
        BodyPart(70.dp, 40.dp, midX - 35.dp, 180.dp, 15.dp)
        BodyPart(24.dp, 70.dp, midX - 35.dp - 24.dp, 70.dp, 12.dp, 5f)
        BodyPart(24.dp, 70.dp, midX + 35.dp, 70.dp, 12.dp, -5f)
        BodyPart(20.dp, 70.dp, midX - 38.dp - 20.dp, 145.dp, 10.dp, 5f)
        BodyPart(20.dp, 70.dp, midX + 38.dp, 145.dp, 10.dp, -5f)
        BodyPart(30.dp, 100.dp, midX - 15.dp - 25.dp, 225.dp, 15.dp, 2f)
        BodyPart(30.dp, 100.dp, midX - 15.dp + 25.dp, 225.dp, 15.dp, -2f)
        BodyPart(26.dp, 100.dp, midX - 13.dp - 25.dp, 330.dp, 13.dp, 0f)
        BodyPart(26.dp, 100.dp, midX - 13.dp + 25.dp, 330.dp, 13.dp, 0f)

        @Composable
        fun Hotspot(xOffset: Dp, yOffset: Dp, name: String) {
            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .size(32.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .border(2.dp, Blue600, CircleShape)
                    .clickable { onMuscleSelect(name) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Blue600, modifier = Modifier.size(16.dp))
            }
        }
        Hotspot(midX - 60.dp, 105.dp, "Right Biceps")
        Hotspot(midX + 28.dp, 105.dp, "Left Biceps")
        Hotspot(midX - 16.dp, 100.dp, "Pectoralis")
        Hotspot(midX - 16.dp, 150.dp, "Abdominals")
        Hotspot(midX - 50.dp, 275.dp, "Right Quadriceps")
        Hotspot(midX + 18.dp, 275.dp, "Left Quadriceps")
    }
}