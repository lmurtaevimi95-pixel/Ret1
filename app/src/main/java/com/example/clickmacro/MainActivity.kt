package com.example.clickmacro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val REQ_EXPORT = 1
private const val REQ_IMPORT = 2

private val COLOR_BG = Color.parseColor("#16213A")
private val COLOR_HEADER = Color.parseColor("#1F2D52")
private val COLOR_NAV = Color.parseColor("#0F1830")
private val COLOR_CARD = Color.parseColor("#24345E")
private val COLOR_ACCENT = Color.parseColor("#6EA8FF")
private val COLOR_MUTED = Color.parseColor("#8E9BB8")

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var navHome: TextView
    private lateinit var navMacros: TextView
    private var tab = 0
    private var pendingExport: List<Macro> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_HEADER
        window.navigationBarColor = COLOR_NAV

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(COLOR_BG)

        root.addView(buildHeader())
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(buildNav())

        setContentView(root)
        show(0)
    }

    override fun onResume() {
        super.onResume()
        show(tab)
    }

    // ---------- Каркас: шапка и нижнее меню ----------

    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setBackgroundColor(COLOR_HEADER)
        h.setPadding(dp(20), dp(16), dp(20), dp(16))

        val title = TextView(this)
        title.text = "ClickMacro"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        h.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))

        statusView = TextView(this)
        statusView.textSize = 14f
        h.addView(statusView)
        return h
    }

    private fun buildNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(COLOR_NAV)
        navHome = navItem("🏠\nГлавная", 0)
        navMacros = navItem("📋\nМакросы", 1)
        nav.addView(navHome, LinearLayout.LayoutParams(0, WRAP, 1f))
        nav.addView(navMacros, LinearLayout.LayoutParams(0, WRAP, 1f))
        return nav
    }

    private fun navItem(label: String, index: Int): TextView {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.setPadding(0, dp(10), 0, dp(10))
        t.setOnClickListener { show(index) }
        return t
    }

    private fun show(t: Int) {
        tab = t
        content.removeAllViews()
        content.addView(if (t == 0) homeView() else macrosView())
        navHome.setTextColor(if (t == 0) COLOR_ACCENT else COLOR_MUTED)
        navMacros.setTextColor(if (t == 1) COLOR_ACCENT else COLOR_MUTED)
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = ClickService.instance != null
        statusView.text = if (on) "● Включено" else "● Выключено"
        statusView.setTextColor(if (on) Color.parseColor("#4CAF50") else Color.parseColor("#FF7043"))
    }

    // ---------- Главная: плитки ----------

    private fun homeView(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(10), dp(10), dp(10))

        col.addView(
            tileRow(
                tile("➕", "Добавить макрос", "#3F6FD9") { addMacro() },
                tile("📋", "Мои макросы", "#2E9D6B") { show(1) }
            )
        )
        col.addView(
            tileRow(
                tile("📤", "Экспорт", "#8E3B6E") { exportMacros() },
                tile("📥", "Импорт", "#2A8CA8") { importMacros() }
            )
        )
        col.addView(
            tileRow(
                tile("⚙️", "Включить сервис", "#E07B2A") { openAccessibility() },
                tile("⏹", "Остановить макрос", "#D64545") { ClickService.instance?.stopPlayback() }
            )
        )

        val hint = TextView(this)
        hint.text = "1. Включите сервис ClickMacro в специальных возможностях.\n" +
            "2. Нажмите «Добавить макрос», выполните нажатия и нажмите «Стоп и сохранить».\n" +
            "3. Запускайте макрос из списка кнопкой ▶.\n" +
            "4. Нажмите на макрос, чтобы изменить повторы и паузы."
        hint.setTextColor(COLOR_MUTED)
        hint.textSize = 14f
        hint.setPadding(dp(10), dp(16), dp(10), 0)
        col.addView(hint)

        val sv = ScrollView(this)
        sv.addView(col)
        return sv
    }

    private fun tile(icon: String, label: String, color: String, onClick: () -> Unit): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.background = rounded(Color.parseColor(color), 24)
        t.setOnClickListener { onClick() }

        val i = TextView(this)
        i.text = icon
        i.textSize = 32f
        i.gravity = Gravity.CENTER
        t.addView(i)

        val l = TextView(this)
        l.text = label
        l.setTextColor(Color.WHITE)
        l.textSize = 15f
        l.gravity = Gravity.CENTER
        l.setPadding(dp(8), dp(8), dp(8), 0)
        t.addView(l)
        return t
    }

    private fun tileRow(a: View, b: View): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(a, cellParams())
        r.addView(b, cellParams())
        return r
    }

    private fun cellParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, dp(120), 1f)
        p.setMargins(dp(6), dp(6), dp(6), dp(6))
        return p
    }

    // ---------- Список макросов ----------

    private fun macrosView(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(10), dp(10), dp(10))

        val list = MacroStore.load(this)
        if (list.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Макросов пока нет. Нажмите «Добавить макрос» на главной или импортируйте файл."
            empty.setTextColor(COLOR_MUTED)
            empty.textSize = 15f
            empty.setPadding(dp(10), dp(20), dp(10), 0)
            col.addView(empty)
        }
        for (m in list) {
            val p = LinearLayout.LayoutParams(MATCH, WRAP)
            p.setMargins(dp(6), dp(6), dp(6), dp(6))
            col.addView(macroCard(m, list), p)
        }

        val sv = ScrollView(this)
        sv.addView(col)
        return sv
    }

    private fun macroCard(m: Macro, list: MutableList<Macro>): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(16), dp(12), dp(8), dp(12))
        card.background = rounded(COLOR_CARD, 20)
        card.setOnClickListener { macroMenu(m, list) }

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL

        val title = TextView(this)
        title.text = m.name
        title.setTextColor(Color.WHITE)
        title.textSize = 17f
        title.typeface = Typeface.DEFAULT_BOLD
        texts.addView(title)

        val rep = if (m.repeat == 0) "∞" else m.repeat.toString()
        val info = TextView(this)
        info.text = "${m.steps.size} действий, повторов: $rep"
        info.setTextColor(COLOR_MUTED)
        info.textSize = 13f
        texts.addView(info)

        card.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val play = TextView(this)
        play.text = "▶"
        play.textSize = 26f
        play.setTextColor(Color.parseColor("#4CAF50"))
        play.setPadding(dp(16), dp(10), dp(16), dp(10))
        play.setOnClickListener { startMacro(m) }
        card.addView(play)
        return card
    }

    private fun macroMenu(m: Macro, list: MutableList<Macro>) {
        val rep = if (m.repeat == 0) "∞" else m.repeat.toString()
        val items = arrayOf(
            "▶ Запустить",
            "🔁 Повторов: $rep",
            "⏱ Пауза для всех действий",
            "📝 Пауза по действиям",
            "✏️ Переименовать",
            "🗑 Удалить"
        )
        AlertDialog.Builder(this)
            .setTitle(m.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startMacro(m)
                    1 -> askText("Повторов (0 = бесконечно)", m.repeat.toString(), true) { v ->
                        m.repeat = v.trim().toIntOrNull()?.coerceAtLeast(0) ?: 1
                        MacroStore.save(this, list)
                        show(1)
                    }
                    2 -> setAllDelays(m, list)
                    3 -> editSteps(m, list)
                    4 -> askText("Название", m.name, false) { v ->
                        if (v.isNotBlank()) {
                            m.name = v.trim()
                            MacroStore.save(this, list)
                            show(1)
                        }
                    }
                    5 -> confirmDelete(m, list)
                }
            }
            .show()
    }

    private fun confirmDelete(m: Macro, list: MutableList<Macro>) {
        AlertDialog.Builder(this)
            .setTitle("Удалить «${m.name}»?")
            .setPositiveButton("Удалить") { _, _ ->
                list.removeAll { it.id == m.id }
                MacroStore.save(this, list)
                show(1)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- Паузы между действиями ----------

    // Пауза в секундах -> миллисекунды в диапазоне 0.100–1.000
    private fun parseDelayMs(text: String): Long? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return Math.round(v * 1000.0).coerceIn(100L, 1000L)
    }

    private fun fmtSec(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    private fun setAllDelays(m: Macro, list: MutableList<Macro>) {
        askText("Пауза между всеми действиями, сек (0.100–1.000)", "0.300", true, true) { v ->
            val ms = parseDelayMs(v)
            if (ms == null) {
                toast("Введите число, например 0.250")
            } else {
                for (s in m.steps) s.delay = ms
                MacroStore.save(this, list)
                toast("Пауза для всех действий: ${fmtSec(ms)} с")
                show(1)
            }
        }
    }

    private fun editSteps(m: Macro, list: MutableList<Macro>) {
        val items = Array(m.steps.size) { i ->
            val s = m.steps[i]
            val kind = if (s.x1 == s.x2 && s.y1 == s.y2) "касание" else "свайп"
            "${i + 1}. $kind, пауза ${fmtSec(s.delay)} с"
        }
        AlertDialog.Builder(this)
            .setTitle("Действия: ${m.name}")
            .setItems(items) { _, i ->
                val step = m.steps[i]
                askText(
                    "Пауза перед действием ${i + 1}, сек (0.100–1.000)",
                    fmtSec(step.delay.coerceIn(100L, 1000L)),
                    true,
                    true
                ) { v ->
                    val ms = parseDelayMs(v)
                    if (ms == null) {
                        toast("Введите число, например 0.250")
                    } else {
                        step.delay = ms
                        MacroStore.save(this, list)
                        editSteps(m, list)
                    }
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    // ---------- Запись и запуск ----------

    private fun addMacro() {
        val svc = ClickService.instance
        if (svc == null) {
            needService()
            return
        }
        val defaultName = "Макрос ${MacroStore.load(this).size + 1}"
        askText("Название макроса", defaultName, false) { name ->
            svc.startRecording(name.ifBlank { defaultName }.trim())
            Toast.makeText(
                this,
                "Запись идёт: делайте нажатия, затем «Стоп и сохранить» сверху",
                Toast.LENGTH_LONG
