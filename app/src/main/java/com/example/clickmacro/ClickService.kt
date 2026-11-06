package com.example.clickmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.min

class ClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val floating by lazy { FloatingButtons(this) }
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // запись
    private var recording = false
    private var recName = ""
    private val recSteps = mutableListOf<Step>()
    private var lastEventTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    // воспроизведение
    private var playing = false
    private var playToken = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floating.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeRecordButton()
        stopEverything()
        floating.removeAll()
        instance = null
        return super.onUnbind(intent)
    }

    // Обновить плавающие кнопки после изменения настроек
    fun refreshFloating() {
        floating.refresh()
    }

    // ---------- Плавающая кнопка «Запись» ----------

    private var recordButton: View? = null

    // Показывает плавающую кнопку. Нажатие: кнопка пропадает и начинается запись. Крестик отменяет.
    fun showRecordButton(name: String) {
        if (recording || playing) return
        removeRecordButton()

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#D64545"))
        bg.cornerRadius = 90f
        row.background = bg

        val start = TextView(this)
        start.text = "● Запись"
        start.setTextColor(Color.WHITE)
        start.textSize = 16f
        start.setPadding(48, 28, 24, 28)
        row.addView(start)

        val cancel = TextView(this)
        cancel.text = "✕"
        cancel.setTextColor(Color.WHITE)
        cancel.textSize = 16f
        cancel.setPadding(24, 28, 48, 28)
        row.addView(cancel)

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 40
        lp.y = 300

        val slop = 8 * resources.displayMetrics.density
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        row.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(row, lp)
                        } catch (ex: Exception) {
                            // окно уже убрано
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        removeRecordButton()
                        if (e.x > start.width) {
                            toast("Запись отменена")
                        } else {
                            // небольшая пауза, чтобы само нажатие на кнопку не попало в запись
                            handler.postDelayed({ startRecording(name) }, 300)
                        }
                    }
                }
            }
            true
        }

        try {
            windowManager?.addView(row, lp)
            recordButton = row
        } catch (e: Exception) {
            toast("Не удалось показать кнопку записи")
        }
    }

    private fun removeRecordButton() {
        val v = recordButton ?: return
        try {
            windowManager?.removeView(v)
        } catch (e: Exception) {
            // уже убрана
        }
        recordButton = null
    }

    // ---------- Запись ----------

    fun startRecording(name: String) {
        if (recording || playing) return
        recording = true
        floating.removeAll()
        recName = name
        recSteps.clear()
        lastEventTime = System.currentTimeMillis()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.argb(35, 63, 111, 217))
        root.setOnTouchListener { _, e ->
            onRecordTouch(e)
            true
        }
        val stop = makeButton("■ Стоп и сохранить") { finishRecording() }
        val stopParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        stopParams.topMargin = 160
        root.addView(stop, stopParams)

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (!addOverlay(root, lp)) {
            recording = false
            floating.refresh()
        }
    }

    private fun onRecordTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                val held = now - downTime
                val dist = hypot((e.rawX - downX).toDouble(), (e.rawY - downY).toDouble())
                val isSwipe = dist > 30.0
                val rawDelay = (downTime - lastEventTime).coerceAtLeast(0L)
                // первая пауза — не больше секунды, чтобы макрос не ждал, пока вы готовились
                val delay = if (recSteps.isEmpty()) min(rawDelay, 1000L) else rawDelay
                val step = Step(
                    downX,
                    downY,
                    if (isSwipe) e.rawX else downX,
                    if (isSwipe) e.rawY else downY,
                    if (isSwipe) held.coerceIn(100L, 3000L) else held.coerceIn(50L, 1000L),
                    delay
                )
                recSteps.add(step)
                lastEventTime = now

                // пропускаем это же нажатие в приложение под оверлеем
                handler.post { setTouchable(false) }
                handler.postDelayed({ perform(step) { setTouchable(true) } }, 60)
            }
        }
    }

    private fun finishRecording() {
        if (!recording) return
        recording = false
        removeOverlay()
        floating.refresh()
        if (recSteps.isEmpty()) {
            toast("Действий нет — макрос не сохранён")
            return
        }
        val list = MacroStore.load(this)
        list.add(Macro(System.currentTimeMillis(), recName, 1, recSteps.toList()))
        MacroStore.save(this, list)
        toast("Макрос «$recName» сохранён: ${recSteps.size} действий")
    }

    // ---------- Воспроизведение ----------

    fun play(macro: Macro) {
        if (recording || playing || macro.steps.isEmpty()) return
        playing = true
        val token = ++playToken
        showStopButton()
        floating.setTouchable(false)
        handler.postDelayed({ runStep(macro, 0, 0, token) }, macro.startDelay)
    }

    private fun runStep(m: Macro, index: Int, round: Int, token: Int) {
        if (!playing || token != playToken) return
        if (index >= m.steps.size) {
            val next = round + 1
            if (m.repeat != 0 && next >= m.repeat) {
                finishPlayback("Макрос выполнен")
                return
            }
            handler.postDelayed({ runStep(m, 0, next, token) }, 500)
            return
        }
        val step = m.steps[index]
        handler.postDelayed({
            if (!playing || token != playToken) return@postDelayed
            perform(step) { runStep(m, index + 1, round, token) }
        }, if (index == 0 && round == 0) 0L else step.delay)
    }

    fun stopPlayback() {
        if (!playing) return
        finishPlayback("Макрос остановлен")
    }

    private fun finishPlayback(message: String) {
        playing = false
        playToken++
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        floating.setTouchable(true)
        toast(message)
    }

    private fun showStopButton() {
        val btn = makeButton("■ Стоп") { stopPlayback() }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = 160
        if (!addOverlay(btn, lp)) playing = false
    }

    private fun stopEverything() {
        if (playing) finishPlayback("Макрос остановлен")
        if (recording) {
            recording = false
            removeOverlay()
        }
    }

    // ---------- Жесты и оверлей ----------

    private fun perform(step: Step, done: () -> Unit) {
        val path = Path()
        path.moveTo(step.x1, step.y1)
        if (step.x1 != step.x2 || step.y1 != step.y2) {
            path.lineTo(step.x2, step.y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, step.duration.coerceAtLeast(1L)))
            .build()
        val ok = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done()
            }
        }, null)
        if (!ok) done()
    }

    private fun setTouchable(touchable: Boolean) {
        val v = overlay ?: return
        val lp = overlayParams ?: return
        lp.flags = if (touchable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            windowManager?.updateViewLayout(v, lp)
        } catch (e: Exception) {
            // оверлей уже убран
        }
    }

    private fun addOverlay(v: View, lp: WindowManager.LayoutParams): Boolean {
        removeOverlay()
        return try {
            windowManager?.addView(v, lp)
            overlay = v
            overlayParams = lp
            true
        } catch (e: Exception) {
            toast("Не удалось показать панель управления")
            false
        }
    }

    private fun removeOverlay() {
        val v = overlay
        if (v != null) {
            try {
                windowManager?.removeView(v)
            } catch (e: Exception) {
                // уже убран
            }
        }
        overlay = null
        overlayParams = null
    }

    private fun makeButton(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = 16f
        b.setPadding(48, 28, 48, 28)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#D64545"))
        bg.cornerRadius = 60f
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
