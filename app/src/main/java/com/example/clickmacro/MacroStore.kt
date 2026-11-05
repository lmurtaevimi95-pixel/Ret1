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
    var delay: Long     // пауза перед жестом, мс
)

// repeat: сколько раз повторить макрос, 0 = бесконечно
// float*: настройки плавающей кнопки (размер в dp, видимость в %, картинка, позиция в px)
data class Macro(
    val id: Long,
    var name: String,
    var repeat: Int,
    val steps: List<Step>,
    var floatOn: Boolean = false,
    var floatSize: Int = 56,
    var floatAlpha: Int = 90,
    var floatImage: String = "",
    var floatX: Int = 40,
    var floatY: Int = 400
)

object MacroStore {
    private const val PREFS = "macros"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Macro> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return fromJson(raw, true).toMutableList()
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
                    .put("floatOn", m.floatOn)
                    .put("floatSize", m.floatSize)
                    .put("floatAlpha", m.floatAlpha)
                    .put("floatImage", m.floatImage)
                    .put("floatX", m.floatX)
                    .put("floatY", m.floatY)
            )
        }
        return arr.toString()
    }

    // withFloat = true только для своего хранилища. При импорте из файла настройки кнопки не берём.
    fun fromJson(raw: String, withFloat: Boolean = false): List<Macro> {
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
                    val m = Macro(
                        o.optLong("id", System.currentTimeMillis() + i),
                        o.getString("name"),
                        o.optInt("repeat", 1).coerceAtLeast(0),
                        steps
                    )
                    if (withFloat) {
                        m.floatOn = o.optBoolean("floatOn", false)
                        m.floatSize = o.optInt("floatSize", 56).coerceIn(30, 200)
                        m.floatAlpha = o.optInt("floatAlpha", 90).coerceIn(10, 100)
                        m.floatImage = o.optString("floatImage", "")
                        m.floatX = o.optInt("floatX", 40)
                        m.floatY = o.optInt("floatY", 400)
                    }
                    result.add(m)
                }
            }
        } catch (e: Exception) {
            // повреждённые данные — берём то, что успели прочитать
        }
        return result
    }
}
