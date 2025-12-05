package com.example.fitfit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Colors ---
val MyofluxBlue = Color(0xFF0061A4)
val MyofluxPurple = Color(0xFF6750A4)
val MyofluxBackground = Color(0xFFF8FAFC) // Light Slate 50

val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate500 = Color(0xFF64748B)
val Slate800 = Color(0xFF1E293B)

val Blue600 = Color(0xFF2563EB)
val Red500 = Color(0xFFEF4444)
val Green500 = Color(0xFF22C55E)

// --- Theme ---
@Composable
fun MyofluxTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = MyofluxBlue,
        secondary = MyofluxPurple,
        background = MyofluxBackground,
        surface = MyofluxBackground,
        onBackground = Slate800,
        onSurface = Slate800
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
