package com.example.fitfit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.example.fitfit.EmgReader
import com.example.fitfit.MockEmgReader
import com.example.fitfit.BitalinoEmgReader

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

@SuppressLint("CoroutineCreationDuringComposition", "MissingPermission")
@Composable
fun BodyScanScreen(initialMacAddress: String = "") {
    // 状態管理
    var selectedMuscle by remember { mutableStateOf<String?>(null) }
    var showMeasurementSheet by remember { mutableStateOf(false) } // 実際に計測画面へ進むフラグ

    // 各部位のスコアを保持するMap (Key: 部位名, Value: スコア)
    var muscleScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- データロード関数 ---
    fun loadMuscleScores() {
        val muscles = listOf(
            "Right Biceps", "Left Biceps", "Pectoralis",
            "Abdominals", "Right Quadriceps", "Left Quadriceps"
        )
        val newScores = mutableMapOf<String, Int>()
        muscles.forEach { muscle ->
            val record = MeasurementRepository.getLastRecordForMuscle(context, muscle)
            if (record != null) {
                newScores[muscle] = record.score
            }
        }
        muscleScores = newScores
    }

    // 初回ロード
    LaunchedEffect(Unit) { loadMuscleScores() }

    // デバッグモード状態
    var isDebugMode by remember { mutableStateOf(false) }

    // --- Bluetooth設定 ---
    var targetMacAddress by remember { mutableStateOf(initialMacAddress) }
    var showDeviceList by remember { mutableStateOf(false) }
    var activeReader by remember { mutableStateOf<EmgReader?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    // 権限状態（UI更新用に state として保持）
    var btConnectGranted by remember { mutableStateOf(hasBluetoothConnectPermission(context)) }

    val btConnectPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            btConnectGranted = granted
            if (granted) {
                // 許可されたらデバイス一覧へ
                showDeviceList = true
            } else {
                Toast.makeText(context, "Bluetooth接続権限が拒否されました", Toast.LENGTH_LONG).show()
            }
        }

    fun ensureBluetoothConnectPermissionThen(openList: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Android 11以下は runtime では不要
            btConnectGranted = true
            if (openList) showDeviceList = true
            return
        }

        if (hasBluetoothConnectPermission(context)) {
            btConnectGranted = true
            if (openList) showDeviceList = true
        } else {
            // ここでランタイム許可を要求
            btConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    // --- Bluetooth Handlers ---
    val handleConnectionAction: () -> Unit = {
        scope.launch {
            if (isConnected) {
                activeReader?.close()
                activeReader = null
                isConnected = false
                if (isDebugMode) {
                     isDebugMode = false
                     Toast.makeText(context, "Debug Mode Disabled", Toast.LENGTH_SHORT).show()
                } else {
                     Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            } else {
                // ここで権限が無いと bondedDevices 参照で落ちるので先に要求
                ensureBluetoothConnectPermissionThen(openList = true)
            }
        }
    }

    val connectToDevice: (String) -> Unit = { mac ->
        scope.launch {
            // connect も BLUETOOTH_CONNECT が必要
            if (!hasBluetoothConnectPermission(context)) {
                btConnectGranted = false
                Toast.makeText(context, "Bluetooth接続権限が必要です", Toast.LENGTH_LONG).show()
                // 必要ならここで再リクエスト
                ensureBluetoothConnectPermissionThen(openList = false)
                return@launch
            }

            activeReader?.close()
            activeReader = null
            isConnected = false
            targetMacAddress = mac
            showDeviceList = false
            isConnecting = true
            try {
                val newReader = BitalinoEmgReader(context, mac)
                newReader.connect()
                activeReader = newReader
                isConnected = true
                Toast.makeText(context, "Connected to $mac", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                activeReader = null
                isConnected = false
                Toast.makeText(context, "Connection Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isConnecting = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 背景アニメーション
        AnimatedBackground()

        // 2. メインコンテンツ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .background(CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(CyberCyan, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "LIVE MONITORING",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Myoflux Hub",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTextMain
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (isDebugMode) "System Status: DEBUG MODE" else "System Status: Active",
                style = MaterialTheme.typography.bodySmall.copy(color = if (isDebugMode) CyberRed else CyberTextSub)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                GeometricBody(
                    onMuscleSelect = { muscleName ->
                        selectedMuscle = muscleName
                        showMeasurementSheet = false
                    },
                    muscleScores = muscleScores
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }

        // 3. Bluetooth Button (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            CyberBluetoothButton(
                isConnected = isConnected,
                isConnecting = isConnecting,
                onClick = handleConnectionAction
            )
        }

        // 4. Detail Card
        AnimatedVisibility(
            visible = selectedMuscle != null && !showMeasurementSheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            selectedMuscle?.let { muscle ->
                val score = muscleScores[muscle]
                MuscleDetailCard(
                    muscleName = muscle,
                    score = score,
                    onClose = { selectedMuscle = null },
                    onStartScan = { showMeasurementSheet = true }
                )
            }
        }

        // 5. Measurement Sheet
        AnimatedVisibility(
            visible = showMeasurementSheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            selectedMuscle?.let { muscle ->
                val readerToUse =
                    if (activeReader != null && isConnected) activeReader!!
                    else if (isDebugMode) MockEmgReader() // デバッグモードならMockを使用
                    else BitalinoEmgReader(context, "00:00:00:00:00:00") // Dummy

                MeasurementSheet(
                    muscleName = muscle,
                    onClose = {
                        showMeasurementSheet = false
                        selectedMuscle = null
                        loadMuscleScores()
                    },
                    isConnected = isConnected || isDebugMode, // デバッグ時は接続済み扱い
                    reader = readerToUse,
                    onConnectRequest = {
                         if (!isDebugMode) {
                              // MeasurementSheet 経由でも権限を要求
                              ensureBluetoothConnectPermissionThen(openList = true)
                         }
                    }
                )
            }
        }

        // 6. Device List Dialog
        if (showDeviceList) {
            DeviceSelectionDialog(
                context = context,
                onDismiss = { showDeviceList = false },
                onDeviceSelected = { device -> 
                    if (device == null) {
                        // Debug Mode
                        showDeviceList = false
                        isDebugMode = true
                        activeReader = MockEmgReader()
                        // Mock connect
                        scope.launch {
                            activeReader?.connect()
                            isConnected = true
                            Toast.makeText(context, "Debug Mode Activated", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        connectToDevice(device.address) 
                    }
                }
            )
        }
    }
}

// --- Components ---

@Composable
fun MuscleDetailCard(
    muscleName: String,
    score: Int?,
    onClose: () -> Unit,
    onStartScan: () -> Unit
) {
    val scoreText = score?.toString() ?: "--"
    val scoreColor = if (score != null) {
        if (score >= 80) CyberGreen else if (score >= 50) CyberCyan else CyberRed
    } else {
        CyberTextSub
    }

    val conditionText = if (score != null) {
        if (score >= 80) "EXCELLENT"
        else if (score >= 60) "GOOD"
        else if (score >= 40) "FATIGUED"
        else "RECOVERY"
    } else "NO DATA"

    val activityText = if (score != null) "HIGH" else "---"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 80.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("SELECTED AREA", style = MaterialTheme.typography.labelSmall, color = CyberTextSub)
                    Text(
                        muscleName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextMain
                    )
                }
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    if (score != null) {
                        val path = Path()
                        val w = size.width
                        val h = size.height
                        path.moveTo(0f, h / 2)
                        val amplitude = if (score < 50) 20f else 10f
                        for (i in 0..10) {
                            path.lineTo(w * (i / 10f), h / 2 + (if (i % 2 == 0) -amplitude else amplitude))
                        }
                        drawPath(path, color = scoreColor, style = Stroke(width = 3f))
                    } else {
                        drawLine(
                            color = CyberTextSub.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("CONDITION", fontSize = 10.sp, color = CyberTextSub)
                        Text(conditionText, fontWeight = FontWeight.Bold, color = scoreColor)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("ACTIVITY", fontSize = 10.sp, color = CyberTextSub)
                        Text(activityText, fontWeight = FontWeight.Bold, color = CyberGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, CyberTextSub),
                    modifier = Modifier.weight(1f)
                ) { Text("CANCEL", color = CyberTextSub) }

                Button(
                    onClick = onStartScan,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.weight(2f)
                ) { Text("START SCAN", color = CyberDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun CyberBluetoothButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isConnected) CyberCyan else CyberPanel
    val contentColor = if (isConnected) CyberDark else CyberTextSub

    IconButton(
        onClick = onClick,
        enabled = !isConnecting,
        modifier = Modifier
            .size(48.dp)
            .background(containerColor, CircleShape)
            .border(1.dp, if (isConnected) CyberCyan else CyberTextSub, CircleShape)
            .shadow(if (isConnected) 10.dp else 0.dp, CircleShape, spotColor = CyberCyan)
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = CyberCyan,
                strokeWidth = 2.dp
            )
        } else {
            AnimatedContent(
                targetState = isConnected,
                transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                label = "IconTransition"
            ) { connected ->
                if (connected) Icon(Icons.Filled.BluetoothConnected, "Connected", tint = contentColor)
                else Icon(Icons.Filled.BluetoothDisabled, "Disconnected", tint = contentColor)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionDialog(
    context: Context,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice?) -> Unit
) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter

    // Android 12+ で許可がないと bondedDevices 参照で SecurityException になるのでガード
    val bondedDevices = remember {
        if (hasBluetoothConnectPermission(context)) {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                
                // デバッグ起動ボタン (Long Clickなどで隠しコマンド的にしてもいいが今回はボタンを置く)
                Button(
                    onClick = {
                        onDeviceSelected(null) // null for Debug
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate100),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("START DEBUG MODE (No Device)", color = Slate800)
                }

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Device",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 許可がない場合の表示
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission(context)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Bluetooth接続権限が未許可です。", color = Slate500)
                    }
                    return@Surface
                }

                if (bondedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No paired devices found.\nPlease pair in Settings first.", color = Slate500)
                    }
                } else {
                    LazyColumn {
                        items(bondedDevices) { device: BluetoothDevice ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(device) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Blue600)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = device.name ?: "Unknown Device",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Slate500)
                                    )
                                }
                            }
                            HorizontalDivider(color = Slate100)
                        }
                    }
                }
            }
        }
    }
}