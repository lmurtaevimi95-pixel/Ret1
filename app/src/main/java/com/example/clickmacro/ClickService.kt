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

        val cance
