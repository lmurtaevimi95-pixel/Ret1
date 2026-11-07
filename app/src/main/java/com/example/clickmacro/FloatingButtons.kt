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
import android.widget.Toast
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

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

    // корзина, которая появляется, пока кнопку тянут
    private var trash: TextView? = null
    private var overTrash = false

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
        removeTrash()
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

    // Перетаскивание пальцем; короткое нажатие запускает макрос;
    // долгое (около секунды) убирает кнопку с экрана;
    // если отпустить кнопку над корзиной, она тоже уберётся
    private fun attachTouch(h: Holder, id: Long) {
        val slop = 8 * service.resources.displayMetrics.density
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            hideButton(id)
        }
        h.root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = h.params.x
                    startY = h.params.y
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                    longPressed = false
                    h.root.postDelayed(longPress, 800)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        h.root.removeCallbacks(longPress)
                        showTrash()
                    }
                    if (moved) {
                        h.params.x = startX + dx.toInt()
                        h.params.y = startY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(h.root, h.params)
                        } catch (ex: Exception) {
                            // окно уже убрано
                        }
                        updateTrash(e.rawX, e.rawY)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    h.root.removeCallbacks(longPress)
                    if (!longPressed) {
                        if (moved) {
                            val drop = overTrash
                            removeTrash()
                            if (drop) {
                                hideButton(id)
                            } else {
                                savePosition(id, h.params.x, h.params.y)
                            }
                        } else {
                            onTap(id)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    h.root.removeCallbacks(longPress)
                    removeTrash()
                }
            }
            true
        }
    }

    // ---------- Корзина ----------

    private fun trashBackground(active: Boolean): GradientDrawable {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor(if (active) "#FF3B3B" else "#CC3A3A3A"))
        return bg
    }

    // Круглая корзина внизу по центру экрана. Касания она не перехватывает.
    private fun showTrash() {
        if (trash != null) return
        val density = service.resources.displayMetrics.density
        val size = (72 * density).toInt()

        val t = TextView(service)
        t.text = "🗑"
        t.textSize = 30f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.background = trashBackground(false)

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = (64 * density).toInt()
        try {
            windowManager.addView(t, lp)
            trash = t
            overTrash = false
        } catch (e: Exception) {
            // не удалось показать корзину
        }
    }

    // Над корзиной ли палец; корзина краснеет, когда да
    private fun updateTrash(rawX: Float, rawY: Float) {
        val t = trash ?: return
        if (t.width == 0) return
        val loc = IntArray(2)
        t.getLocationOnScreen(loc)
        val cx = loc[0] + t.width / 2f
        val cy = loc[1] + t.height / 2f
        val over = hypot(rawX - cx, rawY - cy) < t.width * 0.9f
        if (over != overTrash) {
            overTrash = over
            t.background = trashBackground(over)
            t.textSize = if (over) 38f else 30f
        }
    }

    private fun removeTrash() {
        val t = trash ?: return
        try {
            windowManager.removeView(t)
        } catch (e: Exception) {
            // уже убрана
        }
        trash = null
        overTrash = false
    }

    // Убрать кнопку с экрана. Макрос остаётся в списке, кнопку можно вернуть в приложении.
    private fun hideButton(id: Long) {
        val list = MacroStore.load(service)
        val m = list.firstOrNull { it.id == id }
        if (m != null) {
            m.floatOn = false
            MacroStore.save(service, list)
        }
        remove(id)
        Toast.makeText(
            service,
            "Кнопка убрана. Вернуть: «Плавающие кнопки» в приложении",
            Toast.LENGTH_LONG
        ).show()
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
