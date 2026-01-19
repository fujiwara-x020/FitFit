package com.example.fitfit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

class MockEmgReader : EmgReader {

    override suspend fun connect() {
        Log.d("MockEmgReader", "Simulating connection...")
        delay(1000) // 接続シミュレーション
        Log.d("MockEmgReader", "Connected (Mock)")
    }

    override suspend fun readEmgForSeconds(
        durationSec: Int,
        onDataReceived: ((Float) -> Unit)?
    ) {
        withContext(Dispatchers.Default) {
        Log.d("MockEmgReader", "Starting mock data generation for ${durationSec}s")
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (durationSec * 1000)
        
        // 1000Hzを想定
        val sleepTimeMs = 1L 
        var t = 0.0

        while (System.currentTimeMillis() < endTime && isActive) {
            // 合成波形: 揺らぎのあるサイン波 + ノイズ
            // ベースの変動 (筋肉の収縮のような動き)
            val baseSignal = if (t % 500 < 250) {
                 // 収縮期
                 (sin(t * 0.05) * 300 + Random.nextFloat() * 200).toFloat()
            } else {
                 // 弛緩期
                 (Random.nextFloat() * 50).toFloat()
            }
            
            val finalVal = 500f + baseSignal // ベースライン 500
            
            onDataReceived?.invoke(finalVal)
            
            t += 1.0
            delay(sleepTimeMs)
        }
        Log.d("MockEmgReader", "Mock data generation finished")
    }
    }

    override fun close() {
        Log.d("MockEmgReader", "Closed (Mock)")
    }
}
