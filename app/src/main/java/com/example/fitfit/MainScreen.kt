package com.example.fitfit

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.launch

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun MainScreen(macAddress: String) {
    var selectedMuscle by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Readerの保持
    val reader = remember {
        BitalinoEmgReader(context, macAddress)
    }

    var isConnected by remember { mutableStateOf(false) }

    // 画面起動時に接続を試みる（またはボタンで接続するように変更も可）
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // 実機がないとエラーになるため、try-catchで囲む
                // reader.connect()
                // isConnected = true
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    val dotColor = Slate200
    val backgroundColor = Color.White
    val gridSize = 24.dp
    val dotRadius = 1.5.dp

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(color = backgroundColor)
                    val stepPx = gridSize.toPx()
                    val radiusPx = dotRadius.toPx()
                    val stepsX = (size.width / stepPx).toInt()
                    val stepsY = (size.height / stepPx).toInt()

                    for (i in 0..stepsX) {
                        for (j in 0..stepsY) {
                            drawCircle(
                                color = dotColor,
                                radius = radiusPx,
                                center = Offset(i * stepPx, j * stepPx)
                            )
                        }
                    }
                }
                .padding(innerPadding)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BODY SCAN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Blue600,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = "Select Target Area",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Slate800,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    GeometricBody(
                        onMuscleSelect = { muscleName ->
                            selectedMuscle = muscleName
                        }
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }

            AnimatedVisibility(
                visible = selectedMuscle != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                )
            ) {
                selectedMuscle?.let { muscle ->
                    MeasurementSheet(
                        muscleName = muscle,
                        onClose = { selectedMuscle = null },
                        isConnected = isConnected, // ここではFalse(未接続)として動作
                        reader = reader,
                        onConnectRequest = {
                            // シート内のボタンから接続を再試行する場合の処理
                            scope.launch {
                                try {
                                    reader.connect()
                                    isConnected = true
                                } catch (e: Exception) {
                                    // Error handle
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}