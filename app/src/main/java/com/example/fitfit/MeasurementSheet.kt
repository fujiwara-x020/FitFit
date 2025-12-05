package com.example.fitfit

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MeasurementSheet(
    muscleName: String,
    onClose: () -> Unit,
    isConnected: Boolean,
    reader: BitalinoEmgReader,
    onConnectRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isMeasuring by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(30) }

    var calculatedScore by remember { mutableStateOf(0) }
    val dataBuffer = remember { mutableStateListOf<Float>() }

    val scoreAnim = remember { Animatable(0f) }

    LaunchedEffect(isMeasuring) {
        if (isMeasuring) {
            val durationSec = 30
            timeLeft = durationSec
            isFinished = false
            scoreAnim.snapTo(0f)
            dataBuffer.clear()

            val timerJob = launch {
                for (sec in durationSec downTo 1) {
                    timeLeft = sec
                    delay(1000L)
                }
                timeLeft = 0
            }

            // 非同期でデータ取得
            val emgSamples = if (isConnected) {
                try {
                    reader.readEmgForSeconds(durationSec)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            } else {
                // シミュレーション（デバイス未接続時）
                delay(durationSec * 1000L)
                emptyList()
            }

            timerJob.join()

            if (emgSamples.isNotEmpty()) {
                dataBuffer.addAll(emgSamples)
            } else {
                // デモ用ダミーデータ
                repeat(durationSec * 100) {
                    dataBuffer.add((Math.random() * 100).toFloat())
                }
            }

            isMeasuring = false
            isFinished = true
            calculatedScore = calculatePseudoScore(dataBuffer)

            scoreAnim.animateTo(
                targetValue = calculatedScore.toFloat(),
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(muscleName) {
        isMeasuring = false
        isFinished = false
        timeLeft = 30
        scoreAnim.snapTo(0f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .background(Slate200, CircleShape)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "TARGET MUSCLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Blue600,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = muscleName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Slate800
                        )
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Slate100, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = isFinished, label = "StateTransition") { showResult ->
                    if (showResult) {
                        ResultDisplay(score = scoreAnim.value.toInt(), maxScore = 100)
                    } else {
                        ScanningDisplay(isMeasuring = isMeasuring, timeLeft = timeLeft, totalTime = 30)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val buttonColor = when {
                isFinished -> Slate800
                isMeasuring -> Red500
                !isConnected -> Slate500
                else -> Blue600
            }

            Button(
                onClick = {
                    if (isFinished) {
                        isFinished = false
                        timeLeft = 30
                        scope.launch { scoreAnim.snapTo(0f) }
                    } else {
                        if (isConnected) {
                            isMeasuring = !isMeasuring
                        } else {
                            onConnectRequest() // 未接続なら接続試行
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                elevation = ButtonDefaults.buttonElevation(6.dp)
            ) {
                Icon(
                    imageVector = when {
                        isFinished -> Icons.Default.Refresh
                        isMeasuring -> Icons.Default.Close
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when {
                        isFinished -> "RETRY ANALYSIS"
                        isMeasuring -> "STOP SCANNING"
                        isConnected -> "START BODY SCAN"
                        else -> "CONNECT DEVICE"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun ScanningDisplay(isMeasuring: Boolean, timeLeft: Int, totalTime: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Slate200, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(Slate50)
    ) {
        if (isMeasuring) {
            WaveformGraph()
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ready to Scan", color = Slate300, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(140.dp), color = Slate200, strokeWidth = 8.dp)
                    CircularProgressIndicator(
                        progress = { 1f - (timeLeft.toFloat() / totalTime.toFloat()) },
                        modifier = Modifier.size(140.dp),
                        color = Blue600,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "$timeLeft",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Slate800, fontFeatureSettings = "tnum")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isMeasuring) "ACQUIRING EMG SIGNAL..." else "DEVICE READY",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (isMeasuring) Blue600 else Slate500, letterSpacing = 1.sp)
                )
            }
        }
    }
}

@Composable
fun ResultDisplay(score: Int, maxScore: Int = 100) {
    val scoreColor = calculateScoreColor(score)
    val conditionText = evaluateCondition(score)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(200.dp)) {
                drawCircle(color = Slate100, style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round))
            }
            val animatedProgress = score.toFloat() / maxScore.toFloat()
            Canvas(modifier = Modifier.size(200.dp)) {
                drawArc(color = scoreColor, startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false, style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$score", style = MaterialTheme.typography.displayLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Slate800, fontSize = 80.sp))
                Text(text = "SCORE", style = MaterialTheme.typography.titleSmall.copy(color = Slate500, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(color = scoreColor.copy(alpha = 0.1f), shape = RoundedCornerShape(100), border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.2f))) {
            Text(text = conditionText, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = scoreColor))
        }
    }
}