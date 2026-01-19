package com.example.fitfit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeeklySummaryScreen() {
    val context = LocalContext.current

    // --- データ状態 ---
    var weeklyHistory by remember { mutableStateOf<List<MeasurementRecord>>(emptyList()) }
    var averageScore by remember { mutableStateOf(0f) }
    var totalScans by remember { mutableStateOf(0) }
    var graphDataPoints by remember { mutableStateOf<List<Float>>(emptyList()) } // 7日分の日別平均スコア

    // データロード
    LaunchedEffect(Unit) {
        val history = MeasurementRepository.getWeeklyHistory(context)
        weeklyHistory = history
        totalScans = history.size

        if (history.isNotEmpty()) {
            averageScore = history.map { it.score }.average().toFloat()
        }

        // 過去7日間の日別平均スコアを計算
        val calendar = Calendar.getInstance()
        // 今日の日付の終わり（23:59:59）から遡る
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)

        val points = mutableListOf<Float>()

        // 6日前から今日まで (計7日)
        for (i in 6 downTo 0) {
            val endMillis = calendar.timeInMillis - (i * 24 * 60 * 60 * 1000L)
            val startMillis = endMillis - (24 * 60 * 60 * 1000L) + 1

            // その日のデータを抽出
            val daysRecords = history.filter { it.timestamp in startMillis..endMillis }
            val avg = if (daysRecords.isNotEmpty()) {
                daysRecords.map { it.score }.average().toFloat()
            } else {
                0f // データがない場合は0
            }
            points.add(avg)
        }
        graphDataPoints = points
    }

    // --- UI構築 ---
    Box(modifier = Modifier.fillMaxSize().background(CyberDark)) {
        // 背景エフェクト（共通コンポーネントがあれば使用、なければ簡易版）
        AnimatedBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header
            SummaryHeader()

            if (weeklyHistory.isEmpty()) {
                // データがない場合
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = CyberTextSub.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Scan Data Yet", color = CyberTextSub)
                        Text("Start a body scan to see insights", color = CyberTextSub, fontSize = 12.sp)
                    }
                }
            } else {
                // データがある場合
                LazyColumn(
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. Chart Section
                    item {
                        ChartCard(averageScore = averageScore, dataPoints = graphDataPoints)
                    }

                    // 2. Stats Grid
                    item {
                        StatsGrid(totalScans = totalScans, latestScore = weeklyHistory.firstOrNull()?.score ?: 0)
                    }

                    // 3. Recent History List
                    item {
                        Text(
                            text = "Recent History",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyberTextMain,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            weeklyHistory.take(10).forEach { record ->
                                HistoryListItem(record)
                            }
                        }
                    }

                    // Bottom Padding for Nav
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun SummaryHeader() {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val now = Date()
    val weekAgo = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)
    val dateRange = "${dateFormat.format(weekAgo)} - ${dateFormat.format(now)}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberDark.copy(alpha = 0.8f))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTextMain
                )
            )
            Surface(
                color = CyberCyan.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.2f))
            ) {
                Text(
                    text = dateRange.uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan
                    )
                )
            }
        }
        Text(
            text = "Weekly Performance Overview",
            style = MaterialTheme.typography.bodySmall,
            color = CyberTextSub
        )
    }
}

@Composable
fun ChartCard(averageScore: Float, dataPoints: List<Float>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CyberPanel),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Alignment.Baseline エラーの修正
            // Row の verticalAlignment を Bottom にし、テキストに alignByBaseline を適用して揃える
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "AVERAGE SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSub,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = "%.1f".format(averageScore),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    ),
                    modifier = Modifier.alignByBaseline()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Graph Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Grid Lines
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(4) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }

                // Line Chart
                if (dataPoints.isNotEmpty()) {
                    LineChart(dataPoints = dataPoints)
                }
            }

            // X-Axis Labels (Dynamic)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val dayFormat = SimpleDateFormat("EEE", Locale.US)

                repeat(7) {
                    Text(
                        text = dayFormat.format(cal.time).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyberTextSub
                        )
                    )
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
    }
}

@Composable
fun LineChart(dataPoints: List<Float>) {
    // アニメーション用
    val progress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) {
        progress.animateTo(1f, animationSpec = tween(1500))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxVal = 100f

        if (dataPoints.isEmpty()) return@Canvas

        val stepX = w / (dataPoints.size - 1)
        val path = Path()

        // パス生成
        dataPoints.forEachIndexed { index, value ->
            val x = index * stepX
            val y = h - (value / maxVal * h)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                // 簡易ベジェ曲線（なめらかにする）
                val prevX = (index - 1) * stepX
                val prevY = h - (dataPoints[index - 1] / maxVal * h)
                val cx1 = prevX + stepX / 2
                val cy1 = prevY
                val cx2 = x - stepX / 2
                val cy2 = y
                path.cubicTo(cx1, cy1, cx2, cy2, x, y)
            }
        }

        // 1. グラデーション塗りつぶし領域の描画
        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(w, h)
        fillPath.lineTo(0f, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    CyberCyan.copy(alpha = 0.5f * progress.value),
                    CyberCyan.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = h
            )
        )

        // 2. 線の描画
        // アニメーション中はPathMeasure等を使うのが本格的だが、ここでは簡易的にAlphaで表現
        drawPath(
            path = path,
            color = CyberCyan.copy(alpha = progress.value),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // 3. データポイントの描画
        dataPoints.forEachIndexed { index, value ->
            if (value > 0) { // データがある点のみ
                val x = index * stepX
                val y = h - (value / maxVal * h)

                drawCircle(
                    color = CyberPanel,
                    radius = 6.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = CyberCyan,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y),
                    alpha = progress.value
                )
            }
        }
    }
}

@Composable
fun StatsGrid(totalScans: Int, latestScore: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total Scans Card
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Total Scans",
            value = "$totalScans",
            icon = Icons.Default.FitnessCenter,
            trend = "+Recent"
        )
        // Recovery Status Card
        val status = when {
            latestScore >= 80 -> "High"
            latestScore >= 50 -> "Mod."
            latestScore > 0 -> "Low"
            else -> "--"
        }
        val statusColor = when {
            latestScore >= 80 -> CyberGreen
            latestScore >= 50 -> CyberCyan
            latestScore > 0 -> CyberRed
            else -> CyberTextSub
        }

        StatCard(
            modifier = Modifier.weight(1f),
            label = "Recovery",
            value = status,
            valueColor = statusColor,
            icon = Icons.Default.TrendingUp,
            trend = "Latest Status"
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color = CyberTextMain,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trend: String
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CyberTextSub, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = CyberTextSub)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(trend, style = MaterialTheme.typography.labelSmall, color = CyberTextSub.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

@Composable
fun HistoryListItem(record: MeasurementRecord) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(record.timestamp))

    val scoreColor = when {
        record.score >= 80 -> CyberGreen
        record.score >= 50 -> CyberCyan
        else -> CyberRed
    }

    val bgAlpha = 0.1f
    val bgColor = scoreColor.copy(alpha = bgAlpha)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberPanel.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            // border インポート追加によりエラー解消
            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Score Circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(bgColor, CircleShape)
                .border(1.dp, scoreColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${record.score}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.muscle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyberTextMain
                )
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSub)
            )
        }

        // Status Badge
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = record.condition.uppercase(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = scoreColor
                )
            )
        }
    }
}