package com.example.fitfit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Cyberpunk / Future Tech Palette (New) ---
val CyberDark = Color(0xFF0F172A)          // bg-dark
val CyberPanel = Color(0xFF1E293B)         // bg-card (base)
val CyberCyan = Color(0xFF38BDF8)          // primary
val CyberPink = Color(0xFFF472B6)          // accent
val CyberRed = Color(0xFFEF4444)           // danger
val CyberGreen = Color(0xFF10B981)         // success
val CyberTextMain = Color(0xFFF8FAFC)      // text-main
val CyberTextSub = Color(0xFF94A3B8)       // text-sub

// --- Classic Palette (Legacy Support) ---
// 既存のファイルが壊れないように残します
val MyofluxBlue = Color(0xFF0061A4)
val MyofluxPurple = Color(0xFF6750A4)
val MyofluxBackground = Color(0xFFF8FAFC)

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
    // ダークテーマ（サイバーパンク）をベースにしつつ、
    // 既存のコンポーネントがエラーにならないようにします。
    val colorScheme = darkColorScheme(
        primary = CyberCyan,
        secondary = CyberPink,
        background = CyberDark,
        surface = CyberPanel,
        onBackground = CyberTextMain,
        onSurface = CyberTextMain,
        error = CyberRed
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}