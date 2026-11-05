package com.example.clickmacro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Одно действие макроса: касание (x1,y1 = x2,y2) или свайп (x1,y1 -> x2,y2)
data class Step(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val duration: Long, // сколько длится жест, мс
    val delay: Long     // пауза перед жестом, мс
)

// repeat: сколько раз повторить макрос, 0 = бесконечно
data class Macro(
    val id: Long,
    var name: String,
    var repeat: Int,
    val steps: List<Step>
)

object MacroStore {
    private const val PREFS = "macros"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Macro> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return fromJson(raw).toMutableList()
    }

    fun save(ctx: Context, list: List<Macro>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, toJson(list))
            .apply()
    }

    // Используется и для хранения, и для экспорта в файл
    fun toJson(list: List<Macro>): String {
        val arr = JSONArray()
        for (m in list) {
            val steps = JSONArray()
            for (s in m.steps) {
                steps.put(
                    JSONObject()
                        .put("x1", s.x1.toDouble())
                        .put("y1", s.y1.toDouble())
                        .put("x2", s.x2.toDouble())
                        .put("y2", s.y2.toDouble())
                        .put("duration", s.duration)
                        .put("delay", s.delay)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("repeat", m.repeat)
                    .put("steps", steps)
            )
        }
        return arr.toString()
    }

    // Используется и при загрузке, и при импорте из файла (файлу не доверяем: ограничиваем время жестов)
    fun fromJson(raw: String): List<Macro> {
        val result = mutableListOf<Macro>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sArr = o.getJSONArray("steps")
                val steps = mutableListOf<Step>()
                for (j in 0 until sArr.length()) {
                    val s = sArr.getJSONObject(j)
                    steps.add(
                        Step(
                            s.getDouble("x1").toFloat(),
                            s.getDouble("y1").toFloat(),
                            s.getDouble("x2").toFloat(),
                            s.getDouble("y2").toFloat(),
                            s.getLong("duration").coerceIn(1L, 60000L),
                            s.getLong("delay").coerceIn(0L, 600000L)
                        )
                    )
                }
                if (steps.isNotEmpty()) {
                    result.add(
                        Macro(
                            o.optLong("id", System.currentTimeMillis() + i),
                            o.getString("name"),
                            o.optInt("repeat", 1).coerceAtLeast(0),
                            steps
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // повреждённые данные — берём то, что успели прочитать
        }
        return result
    }
}
