package com.example.fitfit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.UUID

class BitalinoEmgReader(
    private val context: Context,
    private val macAddress: String
) : EmgReader {
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: throw Exception("Bluetooth not supported")
        val device = adapter.getRemoteDevice(macAddress)

        Log.d("BITalinoRaw", "Connecting to $macAddress...")
        try {
            val tmpSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            tmpSocket.connect()
            bluetoothSocket = tmpSocket
        } catch (e1: IOException) {
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val tmpSocket = m.invoke(device, 1) as BluetoothSocket
                tmpSocket.connect()
                bluetoothSocket = tmpSocket
            } catch (e2: Exception) {
                throw IOException("Connection failed", e2)
            }
        }
        bluetoothSocket?.let {
            inputStream = it.inputStream
            outputStream = it.outputStream
        }
        Thread.sleep(1500)
    }
    }

    override suspend fun readEmgForSeconds(
        durationSec: Int,
        onDataReceived: ((Float) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
        if (bluetoothSocket == null) throw IllegalStateException("Device not connected.")

        try {
            // 【変更1】Java SDKと同じ手順でコマンドを送る
            // 1. サンプリングレート設定 (100Hz = 0x02)
            //    Command: (0x02 << 6) | 0x03 = 0x83
            outputStream?.write(0x83)
            Thread.sleep(100)

            // 2. 計測開始 (A1チャンネル = 0)
            //    Command: 1 | (1 << (2 + 0)) = 1 | 4 = 5 (0x05)
            //    BITalinoDevice.java の start() ロジック準拠
            outputStream?.write(0x05)
            outputStream?.flush()

            Log.d("BITalinoRaw", "Sent Java-SDK Style Start Commands (0x83 -> 0x05)")

            val endTime = System.currentTimeMillis() + (durationSec * 1000)

            // 1chモードは3バイト (Java SDK: ceil((12+10)/8) = 3)
            val frameLength = 3
            val buffer = ByteArray(frameLength)

            while (System.currentTimeMillis() < endTime && isActive) {
                // 3バイト読み込み
                var bytesRead = 0
                while (bytesRead < frameLength && isActive) {
                    val r = inputStream?.read(buffer, bytesRead, frameLength - bytesRead) ?: -1
                    if (r == -1) break
                    bytesRead += r
                }
                if (bytesRead < frameLength) break

                // 【変更2】Java SDK BITalinoFrameDecoder.java のロジックでデコード
                // buffer[2]: Seq(4bit) + CRC(4bit)
                // buffer[1]: Dig(4bit) + AnalogHigh(4bit)
                // buffer[0]: AnalogLow(6bit) + Padding(2bit)

                if (checkCRC(buffer)) {
                    // Java SDKの計算式:
                    // (((buffer[j-1] & 0xF) << 6) | ((buffer[j-2] & 0XFC) >> 2))
                    // j=2 なので、buffer[1]の下位4bit と buffer[0]の上位6bit を使う

                    val b1 = buffer[1].toInt()
                    val b0 = buffer[0].toInt()

                    // 下位4bitを取り出して6bit左へ
                    val upper = (b1 and 0x0F) shl 6

                    // 上位6bitを取り出して2bit右へ（符号なしシフト）
                    val lower = (b0 and 0xFC).toUByte().toInt() ushr 2

                    val rawValue = upper or lower
                    val finalValue = (rawValue and 0x03FF).toFloat()

                    onDataReceived?.invoke(finalValue)
                } else {
                    // CRCエラー時は1バイト空読みして同期ズレを直す
                    inputStream?.read()
                }
            }
        } catch (e: Exception) {
            Log.e("BITalinoRaw", "Read Error", e)
        } finally {
            try { outputStream?.write(0x00) } catch (e: Exception) {}
            }
    }
    }

    // Java SDK互換のCRCチェック
    private fun checkCRC(buffer: ByteArray): Boolean {
        val len = buffer.size
        // CRCは最後のバイト(buffer[2])の下位4bit
        val receivedCRC = (buffer[len - 1].toInt() and 0x0F)

        var x0 = 0; var x1 = 0; var x2 = 0; var x3 = 0

        for (i in 0 until len) {
            val b = buffer[i].toInt()
            for (bit in 7 downTo 0) {
                var inp = (b shr bit) and 0x01
                if (i == (len - 1) && bit < 4) inp = 0

                val out = x3
                x3 = x2
                x2 = x1
                x1 = out xor x0
                x0 = inp xor out
            }
        }
        val calculatedCRC = (x3 shl 3) or (x2 shl 2) or (x1 shl 1) or x0
        return receivedCRC == calculatedCRC
    }

    override fun close() {
        try { bluetoothSocket?.close() } catch (e: Exception) {}
        bluetoothSocket = null
    }
}