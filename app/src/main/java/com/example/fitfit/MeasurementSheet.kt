package com.example.fitfit

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "FitFitDebug"
private const val SCORE_TAG = "EmgScore"

@Composable
fun MeasurementSheet(
    muscleName: String,
    onClose: () -> Unit,
    isConnected: Boolean,
    reader: EmgReader,
    onConnectRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isMeasuring by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(15) }
    var calculatedScore by remember { mutableStateOf(0) }

    // 前回のデータ
    var previousRecord by remember { mutableStateOf<MeasurementRecord?>(null) }

    // デバッグ用
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentSampleCount by remember { mutableStateOf(0) }

    // 15秒 * 1000Hz = 15000点。余裕を見て25000確保
    val maxSamples = 25000
    val rawDataBuffer = remember { FloatArray(maxSamples) }
    val writeIndex = remember { AtomicInteger(0) }

    var visualData by remember { mutableStateOf<List<Float>>(emptyList()) }
    val scoreAnim = remember { Animatable(0f) }

    // --- 起動時に前回のデータをロード ---
    LaunchedEffect(muscleName) {
        previousRecord = MeasurementRepository.getLastRecordForMuscle(context, muscleName)
    }

    /**
     * 計測フロー本体
     */
    LaunchedEffect(isMeasuring) {
        if (!isMeasuring) return@LaunchedEffect

        Log.d(TAG, "--- Measurement Started ---")

        val durationSec = 15
        timeLeft = durationSec
        isFinished = false
        errorMessage = null
        scoreAnim.snapTo(0f)

        delay(300) // 安全マージン

        // バッファ初期化
        writeIndex.set(0)
        visualData = emptyList()
        currentSampleCount = 0
        Log.d(TAG, "Buffer reset. Max samples: $maxSamples")

        // 終了フラグ（コルーチンキャンセルではなく論理フラグで制御）
        var keepReading = true

        coroutineScope {
            // 1. タイマー（Main）
            val timerJob = launch {
                try {
                    for (sec in durationSec downTo 1) {
                        Log.d(TAG, "Timer: $sec")
                        timeLeft = sec
                        delay(1000L)
                    }
                    timeLeft = 0
                    Log.d(TAG, "Timer finished")
                    keepReading = false
                } catch (e: Exception) {
                    Log.e(TAG, "Timer error", e)
                }
            }

            // 2. UI 更新ループ（Main）
            val uiUpdateJob = launch {
                try {
                    Log.d(TAG, "UI Update loop started")
                    while (keepReading && isActive) {
                        delay(100L) // 10FPS

                        val currentIndex = writeIndex.get()
                        currentSampleCount = currentIndex

                        if (currentIndex > 0) {
                            val windowSize = 3000
                            val startIndex =
                                (currentIndex - windowSize).coerceAtLeast(0)

                            // 重い処理だけ別スレッドへ
                            val downsampled = withContext(Dispatchers.Default) {
                                downsampleEmgBuffer(
                                    buffer = rawDataBuffer,
                                    startIndex = startIndex,
                                    endIndex = currentIndex,
                                    maxIndex = maxSamples,
                                    targetResolution = 300
                                )
                            }
                            visualData = downsampled
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "UI Update loop cancelled normally")
                } catch (e: Throwable) {
                    Log.e(TAG, "UI Update loop crashed", e)
                    errorMessage = "UI Error: ${e.message}"
                }
            }

            // 3. データ受信（IO）
            val readerJob = launch(Dispatchers.IO) {
                if (isConnected) {
                    try {
                        Log.d(TAG, "Starting Reader...")
                        reader.readEmgForSeconds(durationSec) { newValue ->
                            if (keepReading) {
                                val idx = writeIndex.get()
                                if (idx < maxSamples) {
                                    rawDataBuffer[idx] = newValue
                                    val newIdx = writeIndex.incrementAndGet()
                                    if (newIdx % 1000 == 0) {
                                        Log.d(TAG, "Received $newIdx samples.")
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Reader loop finished normally")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Reader Error", e)
                        withContext(Dispatchers.Main) {
                            errorMessage = "Reader Error: ${e.message}"
                        }
                    }
                } else {
                    Log.d(TAG, "Not connected, simulating delay")
                    while (keepReading && timeLeft > 0) {
                        delay(100)
                    }
                }
            }

            // タイマー終了待ち
            timerJob.join()

            // タイマー終了後、読み取りを止める
            keepReading = false

            // 念のため Reader / UI をキャンセル
            readerJob.cancel()
            uiUpdateJob.cancel()
            readerJob.join()
        }

        Log.d(TAG, "Measurement sequence fully finished")

        // スコア計算
        try {
            val count = writeIndex.get()
            Log.d(TAG, "Total samples captured: $count")

            if (count > 0) {
                val validData = ArrayList<Float>(count)
                for (i in 0 until count) {
                    validData.add(rawDataBuffer[i])
                }

                // 統計特徴量算出 & ログ & スコア算出
                calculatedScore = calculatePseudoScore(validData)
                Log.d(TAG, "Calculated Score: $calculatedScore")

                // --- 【保存処理】 ---
                val condition = evaluateCondition(calculatedScore)
                val newRecord = MeasurementRecord(
                    timestamp = System.currentTimeMillis(),
                    muscle = muscleName,
                    score = calculatedScore,
                    condition = condition
                )
                MeasurementRepository.saveRecord(context, newRecord)

                // 次回のために「前回データ」も更新しておく
                previousRecord = newRecord
                // ------------------

                // 一応アニメーション（UI は calculatedScore を表示）
                scoreAnim.animateTo(
                    targetValue = calculatedScore.toFloat(),
                    animationSpec = tween(
                        durationMillis = 1500,
                        easing = FastOutSlowInEasing
                    )
                )

            } else {
                calculatedScore = 0
                Log.w(TAG, "No data collected (Count is 0)")
                if (isConnected && errorMessage == null) {
                    Toast.makeText(
                        context,
                        "No data collected.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Calc Error", e)
            errorMessage = "Calc Error: ${e.message}"
        }

        // 表示状態更新
        isFinished = true
        isMeasuring = false
    }

    // 筋肉変更時のリセット
    LaunchedEffect(muscleName) {
        isMeasuring = false
        isFinished = false
        timeLeft = 15
        scoreAnim.snapTo(0f)
        visualData = emptyList()
        errorMessage = null
        currentSampleCount = 0
        calculatedScore = 0
        // 前回データの再ロード
        previousRecord = MeasurementRepository.getLastRecordForMuscle(context, muscleName)
    }

    // =======================
    // UI
    // =======================

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
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = muscleName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                    )
                    // --- 前回データの表示 ---
                    if (previousRecord != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last Score: ${previousRecord!!.score} (${previousRecord!!.condition})",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Slate500,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    // -----------------------
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Slate100, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate500
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = isFinished,
                    label = "StateTransition"
                ) { showResult ->
                    if (showResult) {
                        ResultDisplay(
                            score = calculatedScore,
                            maxScore = 100
                        )
                    } else {
                        ScanningDisplay(
                            isMeasuring = isMeasuring,
                            timeLeft = timeLeft,
                            totalTime = 15,
                            graphData = visualData,
                            sampleCount = currentSampleCount,
                            errorMsg = errorMessage
                        )
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
                        // リトライ
                        isFinished = false
                        timeLeft = 15
                        calculatedScore = 0
                        scope.launch { scoreAnim.snapTo(0f) }
                    } else {
                        if (isConnected) {
                            isMeasuring = !isMeasuring
                        } else {
                            onConnectRequest()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                ),
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
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun ScanningDisplay(
    isMeasuring: Boolean,
    timeLeft: Int,
    totalTime: Int,
    graphData: List<Float>,
    sampleCount: Int,
    errorMsg: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Slate200, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .clipToBounds()
            .background(Slate50)
    ) {
        if (isMeasuring) {
            WaveformGraph(dataPoints = graphData)

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Samples: $sampleCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red.copy(alpha = 0.7f)
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.8f))
                            .padding(4.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Ready to Scan",
                    color = Slate300,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.9f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(140.dp),
                        color = Slate200,
                        strokeWidth = 8.dp
                    )
                    CircularProgressIndicator(
                        progress = { 1f - (timeLeft.toFloat() / totalTime.toFloat()) },
                        modifier = Modifier.size(140.dp),
                        color = Blue600,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "$timeLeft",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Slate800,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isMeasuring) "ACQUIRING EMG SIGNAL..." else "DEVICE READY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isMeasuring) Blue600 else Slate500,
                        letterSpacing = 1.sp
                    )
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
                drawCircle(
                    color = Slate100,
                    style = Stroke(
                        width = 24.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
            val animatedProgress = score.toFloat() / maxScore.toFloat()
            Canvas(modifier = Modifier.size(200.dp)) {
                drawArc(
                    color = scoreColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(
                        width = 24.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800,
                        fontSize = 80.sp
                    )
                )
                Text(
                    text = "SCORE",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Slate500,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            color = scoreColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(100),
            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.2f))
        ) {
            Text(
                text = conditionText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            )
        }
    }
}

/**
 * EMG バッファをダウンサンプリングして min/max の線を作る純粋関数
 */
private fun downsampleEmgBuffer(
    buffer: FloatArray,
    startIndex: Int,
    endIndex: Int,
    maxIndex: Int,
    targetResolution: Int
): List<Float> {
    val count = endIndex - startIndex
    if (count <= 0) return emptyList()

    val chunkSize = (count / targetResolution).coerceAtLeast(1)
    val downsampled = ArrayList<Float>(targetResolution * 2)

    var i = startIndex
    while (i < endIndex) {
        var chunkMin = Float.MAX_VALUE
        var chunkMax = -Float.MAX_VALUE
        val chunkEnd = (i + chunkSize).coerceAtMost(endIndex)

        var j = i
        while (j < chunkEnd && j < maxIndex) {
            val v = buffer[j]
            if (!v.isNaN() && !v.isInfinite()) {
                if (v < chunkMin) chunkMin = v
                if (v > chunkMax) chunkMax = v
            }
            j++
        }

        if (chunkMin != Float.MAX_VALUE && chunkMax != -Float.MAX_VALUE) {
            downsampled.add(chunkMin)
            downsampled.add(chunkMax)
        }

        i += chunkSize
    }

    return downsampled
}

/**
 * CSV 保存（必要になったら Measurement 内から呼ぶ）
 */
fun saveToCsv(context: Context, muscleName: String, data: List<Float>) {
    try {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "FitFitArgs"
        )
        if (!dir.exists()) dir.mkdirs()
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "EMG_${muscleName.replace(" ", "_")}_$timeStamp.csv"
        val file = File(dir, fileName)
        FileWriter(file).use { writer ->
            writer.append("Timestamp_ms,Value\n")
            data.forEachIndexed { index, value ->
                writer.append("${index * 1.0},$value\n")
            }
        }
        Log.d(TAG, "CSV Saved: ${file.absolutePath}")
        Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e(TAG, "CSV Save Failed", e)
        Toast.makeText(context, "Failed to save CSV", Toast.LENGTH_SHORT).show()
    }
}

/* ============================================
 * ここから 統計特徴量 & スコア周り
 * ============================================ */

/**
 * EMG の統計特徴量まとめ
 */
data class EmgFeatures(
    val n: Int,
    val mean: Double,
    val std: Double,
    val rms: Double,
    val min: Float,
    val max: Float,
    val peakToPeak: Double,
    val meanAbs: Double,
    val median: Double,
    val percentile25: Double,
    val percentile75: Double,
    val zeroCrossings: Int
)

/**
 * EMG データから統計特徴量を計算して、ログに吐きつつ返す。
 */
fun computeEmgFeatures(data: List<Float>): EmgFeatures {
    if (data.isEmpty()) {
        val empty = EmgFeatures(
            n = 0,
            mean = 0.0,
            std = 0.0,
            rms = 0.0,
            min = 0f,
            max = 0f,
            peakToPeak = 0.0,
            meanAbs = 0.0,
            median = 0.0,
            percentile25 = 0.0,
            percentile75 = 0.0,
            zeroCrossings = 0
        )
        Log.w(SCORE_TAG, "EMG features: data is EMPTY")
        return empty
    }

    val n = data.size

    // ---------- 基本統計 ----------
    var sum = 0.0
    var sumSq = 0.0
    var minVal = Float.MAX_VALUE
    var maxVal = -Float.MAX_VALUE

    for (v in data) {
        val d = v.toDouble()
        sum += d
        sumSq += d * d
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
    }

    val mean = sum / n
    val rms = sqrt(sumSq / n)

    // 標準偏差
    var varSum = 0.0
    for (v in data) {
        val diff = v - mean
        varSum += diff * diff
    }
    val std = sqrt(varSum / n)

    val peakToPeak = (maxVal - minVal).toDouble()

    // 平均絶対値（DC成分を引いてから）
    var absSum = 0.0
    for (v in data) {
        absSum += abs(v - mean)
    }
    val meanAbs = absSum / n

    // ---------- ソート系（中央値・四分位数） ----------
    val sorted = data.map { it.toDouble() }.sorted()

    fun percentile(p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = p * (sorted.size - 1)
        val idx = pos.toInt()
        val frac = pos - idx
        return if (idx + 1 < sorted.size) {
            sorted[idx] * (1.0 - frac) + sorted[idx + 1] * frac
        } else {
            sorted[idx]
        }
    }

    val median = percentile(0.5)
    val p25 = percentile(0.25)
    val p75 = percentile(0.75)

    // ---------- 零交差数 ----------
    var zeroCrossings = 0
    var prev = data[0] - mean
    for (i in 1 until n) {
        val cur = data[i] - mean
        if (prev * cur < 0) {
            zeroCrossings++
        }
        prev = cur
    }

    val features = EmgFeatures(
        n = n,
        mean = mean,
        std = std,
        rms = rms,
        min = minVal,
        max = maxVal,
        peakToPeak = peakToPeak,
        meanAbs = meanAbs,
        median = median,
        percentile25 = p25,
        percentile75 = p75,
        zeroCrossings = zeroCrossings
    )

    // ---------- ログ出力 ----------
    Log.d(
        SCORE_TAG,
        """
        ===== EMG FEATURES =====
        n            = $n
        mean         = $mean
        std          = $std
        rms          = $rms
        min          = $minVal
        max          = $maxVal
        peakToPeak   = $peakToPeak
        meanAbs      = $meanAbs
        median       = $median
        p25 / p75    = $p25 / $p75
        zeroCrossing = $zeroCrossings
        ========================
        """.trimIndent()
    )

    return features
}

/**
 * 暫定スコア:
 * - RMS を 0〜800 の範囲で 0〜100 に線形マッピング
 */
fun calculatePseudoScore(data: List<Float>): Int {
    val features = computeEmgFeatures(data)
    if (features.n == 0) return 0

    val maxRms = 800.0
    val normalized = (features.rms / maxRms).coerceIn(0.0, 1.0)
    val score = (normalized * 100.0).roundToInt()

    Log.d(
        SCORE_TAG,
        "Calculated score=$score from rms=${features.rms}, meanAbs=${features.meanAbs}, peakToPeak=${features.peakToPeak}"
    )
    return score
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