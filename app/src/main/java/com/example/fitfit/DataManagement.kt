package com.example.fitfit

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 計測データを保持するデータクラス
 */
data class MeasurementRecord(
    val timestamp: Long,
    val muscle: String,
    val score: Int,
    val condition: String
)

/**
 * データの保存と読み出しを行うリポジトリ
 * SharedPreferencesを使用して簡易的にJSON形式で保存します
 */
object MeasurementRepository {
    private const val PREF_NAME = "fitfit_history"
    private const val KEY_HISTORY = "history_data"

    // データを保存する
    fun saveRecord(context: Context, record: MeasurementRecord) {
        val history = getHistory(context).toMutableList()
        history.add(record)
        // 最新順にソート（任意）
        history.sortByDescending { it.timestamp }
        saveHistoryToPref(context, history)
    }

    // 全履歴を取得する
    fun getHistory(context: Context): List<MeasurementRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, "[]")
        return parseHistoryJson(jsonString ?: "[]")
    }

    // 特定の部位の最新データを取得する（前回データ用）
    fun getLastRecordForMuscle(context: Context, muscleName: String): MeasurementRecord? {
        val history = getHistory(context)
        // 同じ部位のデータの中で、一番新しいものを探す
        return history.firstOrNull { it.muscle == muscleName }
    }

    // 直近7日間のデータを取得する
    fun getWeeklyHistory(context: Context): List<MeasurementRecord> {
        val history = getHistory(context)
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        return history.filter { it.timestamp >= oneWeekAgo }
    }

    // --- Private Helpers ---

    private fun saveHistoryToPref(context: Context, history: List<MeasurementRecord>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        history.forEach { record ->
            val jsonObj = JSONObject().apply {
                put("timestamp", record.timestamp)
                put("muscle", record.muscle)
                put("score", record.score)
                put("condition", record.condition)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    private fun parseHistoryJson(jsonString: String): List<MeasurementRecord> {
        val list = mutableListOf<MeasurementRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MeasurementRecord(
                        timestamp = obj.getLong("timestamp"),
                        muscle = obj.getString("muscle"),
                        score = obj.getInt("score"),
                        condition = obj.getString("condition")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }
}