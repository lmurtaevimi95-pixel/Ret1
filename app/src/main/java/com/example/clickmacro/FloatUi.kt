package com.example.clickmacro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

// Окна настройки плавающих кнопок: выбор макросов, размер, видимость, картинка
class FloatUi(private val activity: Activity) {

    companion object {
        const val REQ_IMAGE = 3
    }

    private var pendingImageId = 0L

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun commit(list: List<Macro>) {
        MacroStore.save(activity, list)
        ClickService.instance?.refreshFloating()
    }

    // Список макросов с галочками: какие показывать на экране
    fun choose() {
        if (ClickService.instance == null) {
            toast("Сначала включите сервис MBmacro в специальных возможностях")
            return
        }
        val list = MacroStore.load(activity)
        if (list.isEmpty()) {
            toast("Сначала запишите макрос")
            return
        }
        val names = Array(list.size) { i -> list[i].name }
        val checked = BooleanArray(list.size) { i -> list[i].floatOn }
        AlertDialog.Builder(activity)
            .setTitle("Какие макросы показать на экране?")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Показать") { _, _ ->
                var slot = 0
                for (i in list.indices) {
                    val m = list[i]
                    if (checked[i] && !m.floatOn) {
                        // новые кнопки ставим столбиком, чтобы не лежали друг на друге
                        m.floatX = dp(16)
                        m.floatY = dp(120) + slot * dp(m.floatSize + 12)
                        slot++
                    }
                    m.floatOn = checked[i]
                }
                commit(list)
                toast("Кнопок на экране: ${checked.count { it }}")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Выбрать макрос, у которого меняем вид кнопки
    fun pickForLook() {
        val list = MacroStore.load(activity)
        if (list.isEmpty()) {
            toast("Сначала запишите макрос")
            return
        }
        val names = Array(list.size) { i -> list[i].name }
        AlertDialog.Builder(activity)
            .setTitle("Для какого макроса настроить кнопку?")
            .setItems(names) { _, i -> settings(list[i].id) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Настройки кнопки: показывать, размер, видимость, картинка. Изменения видны сразу.
    fun settings(id: Long) {
        val list = MacroStore.load(activity)
        val m = list.firstOrNull { it.id == id } ?: return

        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), 0)

        val show = CheckBox(activity)
        show.text = "Показывать на экране"
        show.isChecked = m.floatOn
        show.setOnCheckedChangeListener { _, checked ->
            if (checked && ClickService.instance == null) {
                toast("Сначала включите сервис MBmacro в специальных возможностях")
            }
            m.floatOn = checked
            commit(list)
        }
        col.addView(show)

        val sizeText = TextView(activity)
        sizeText.text = "Размер: ${m.floatSize}"
        sizeText.setPadding(0, dp(12), 0, 0)
        col.addView(sizeText)
        val sizeBar = SeekBar(activity)
        sizeBar.max = 170
        sizeBar.progress = m.floatSize - 30
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                m.floatSize = progress + 30
                sizeText.text = "Размер: ${m.floatSize}"
                if (fromUser) commit(list)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        col.addView(sizeBar)

        val alphaText = TextView(activity)
        alphaText.text = "Видимость: ${m.floatAlpha}%"
        alphaText.setPadding(0, dp(12), 0, 0)
        col.addView(alphaText)
        val alphaBar = SeekBar(activity)
        alphaBar.max = 90
        alphaBar.progress = m.floatAlpha - 10
        alphaBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                m.floatAlpha = progress + 10
                alphaText.text = "Видимость: ${m.floatAlpha}%"
                if (fromUser) commit(list)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        col.addView(alphaBar)

        AlertDialog.Builder(activity)
            .setTitle("Кнопка: ${m.name}")
            .setView(col)
            .setPositiveButton("Готово", null)
            .setNeutralButton("🖼 Картинка") { _, _ -> pickImage(id) }
            .setNegativeButton("Без картинки") { _, _ -> clearImage(id) }
            .show()
    }

    private fun pickImage(id: Long) {
        pendingImageId = id
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        activity.startActivityForResult(intent, REQ_IMAGE)
    }

    // Вызывается из MainActivity, когда картинка выбрана
    fun onImagePicked(uri: Uri) {
        val id = pendingImageId
        val name = saveImage(uri, id)
        if (name == null) {
            toast("Не удалось загрузить картинку")
            return
        }
        val list = MacroStore.load(activity)
        val m = list.firstOrNull { it.id == id }
        if (m == null) {
            removeImageFile(name)
            return
        }
        removeImageFile(m.floatImage)
        m.floatImage = name
        commit(list)
        settings(id)
    }

    private fun clearImage(id: Long) {
        val list = MacroStore.load(activity)
        val m = list.firstOrNull { it.id == id } ?: return
        removeImageFile(m.floatImage)
        m.floatImage = ""
        commit(list)
        toast("Картинка убрана")
    }

    private fun removeImageFile(name: String) {
        if (name.isBlank()) return
        File(activity.filesDir, File(name).name).delete()
    }

    // Копируем картинку в папку приложения, уменьшив до 512 px
    private fun saveImage(uri: Uri, id: Long): String? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            activity.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = activity.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val name = "float_${id}_${System.currentTimeMillis()}.png"
            FileOutputStream(File(activity.filesDir, name)).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            name
        } catch (e: Exception) {
            null
        }
    }
}
