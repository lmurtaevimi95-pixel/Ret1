package com.example.clickmacro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import kotlin.math.abs

// Плавающие кнопки макросов: рисуются поверх всех приложений через сервис специальных возможностей
class FloatingButtons(private val service: ClickService) {

    private class Holder(
        val root: FrameLayout,
        val image: ImageView,
        val label: TextView,
        val params: WindowManager.LayoutParams
    ) {
        var imageKey: String? = null
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val holders = mutableMapOf<Long, Holder>()
    private var touchable = true

    // Показать выбранные кнопки, убрать лишние, применить новый вид
    fun refresh() {
        val macros = MacroStore.load(service).filter { it.floatOn }
        val ids = macros.map { it.id }.toSet()
        for (id in holders.keys.toList()) {
            if (id !in ids) remove(id)
        }
        for (m in macros) {
            val h = holders[m.id]
            if (h == null) {
                add(m)
            } else {
                applyLook(h, m)
                try {
                    windowManager.updateViewLayout(h.root, h.params)
                } catch (e: Exception) {
                    // окно уже убрано
                }
            }
        }
    }

    fun removeAll() {
        for (id in holders.keys.toList()) remove(id)
    }

    // Пока идёт макрос, кнопки пропускают касания, чтобы не мешать нажатиям макроса
    fun setTouchable(value: Boolean) {
        touchable = value
        for (h in holders.values) updateFlags(h)
    }

    private fun updateFlags(h: Holder) {
        h.params.flags = if (touchable) {
            h.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            h.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            windowManager.updateViewLayout(h.root, h.params)
        } catch (e: Exception) {
            // окно уже убрано
        }
    }

    private fun remove(id: Long) {
        val h = holders.remove(id) ?: return
        try {
            windowManager.removeView(h.root)
        } catch (e: Exception) {
            // окно уже убрано
        }
    }

    private fun add(m: Macro) {
        val root = FrameLayout(service)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#3F6FD9"))
        root.background = bg
        root.clipToOutline = true

        val image = ImageView(service)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val label = TextView(service)
        label.text = "▶"
        label.setTextColor(Color.WHITE)
        label.gravity = Gravity.CENTER
        root.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val lp = WindowManager.LayoutParams(
            100,
            100,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = m.floatX
        lp.y = m.floatY

        val h = Holder(root, image, label, lp)
        applyLook(h, m)
        attachTouch(h, m.id)
        try {
            windowManager.addView(root, lp)
            holders[m.id] = h
            if (!touchable) updateFlags(h)
        } catch (e: Exception) {
            // не удалось показать окно
        }
    }

    // Размер, видимость и картинка
    private fun applyLook(h: Holder, m: Macro) {
        val density = service.resources.displayMetrics.density
        val size = (m.floatSize * density).toInt()
        h.params.width = size
        h.params.height = size
        h.root.alpha = m.floatAlpha / 100f
        h.label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, m.floatSize * 0.4f)
        if (h.imageKey != m.floatImage) {
            h.imageKey = m.floatImage
            val bmp = loadBitmap(m.floatImage)
            if (bmp != null) {
                h.image.setImageBitmap(bmp)
                h.label.visibility = View.GONE
            } else {
                h.image.setImageDrawable(null)
                h.label.visibility = View.VISIBLE
            }
        }
    }

    private fun loadBitmap(name: String): Bitmap? {
        if (name.isBlank()) return null
        val f = File(service.filesDir, File(name).name)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    // Перетаскивание пальцем; короткое нажатие запускает макрос
    private fun attachTouch(h: Holder, id: Long) {
        val slop = 8 * service.resources.displayMetrics.density
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        h.root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = h.params.x
                    startY = h.params.y
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        h.params.x = startX + dx.toInt()
                        h.params.y = startY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(h.root, h.params)
                        } catch (ex: Exception) {
                            // окно уже убрано
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) savePosition(id, h.params.x, h.params.y) else onTap(id)
                }
            }
            true
        }
    }

    private fun onTap(id: Long) {
        val m = MacroStore.load(service).firstOrNull { it.id == id }
        if (m == null) {
            remove(id)
            return
        }
        service.play(m)
    }

    private fun savePosition(id: Long, x: Int, y: Int) {
        val list = MacroStore.load(service)
        val m = list.firstOrNull { it.id == id } ?: return
        m.floatX = x
        m.floatY = y
        MacroStore.save(service, list)
    }
}
