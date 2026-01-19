package com.example.fitfit

import java.io.IOException

interface EmgReader {
    suspend fun connect()
    suspend fun readEmgForSeconds(durationSec: Int, onDataReceived: ((Float) -> Unit)? = null)
    fun close()
}
