package com.purpharma.controlkeyboard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var defaultButton: Button

    private fun imeComponent() = ComponentName(this, ControlKeyboardService::class.java)

    private fun isEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val target = imeComponent().flattenToString()
        return imm.enabledInputMethodList.any { it.id == target }
    }

    private fun isDefault(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return current == imeComponent().flattenToString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }

        val title = TextView(this).apply {
            text = "إعداد Control Keyboard"
            textSize = 25f
        }
        status = TextView(this).apply {
            textSize = 18f
            setPadding(0, 24, 0, 24)
        }
        enableButton = Button(this).apply {
            text = "1. تفعيل لوحة المفاتيح"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }
        defaultButton = Button(this).apply {
            text = "2. اختيارها كلوحة افتراضية"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(enableButton)
        root.addView(defaultButton)
        setContentView(root)
        refreshSetup()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshSetup()
    }

    private fun refreshSetup() {
        val enabled = isEnabled()
        val selected = isDefault()

        status.text = when {
            !enabled -> "الخطوة الأولى مطلوبة: فعّل Control Keyboard من إعدادات أندرويد. لن نختارها بدلًا منك."
            !selected -> "تم التفعيل. الخطوة الثانية: اختر Control Keyboard بنفسك من نافذة لوحات المفاتيح."
            else -> "اكتمل الإعداد. Control Keyboard مفعّلة ومختارة كلوحة افتراضية."
        }

        enableButton.isEnabled = !enabled
        defaultButton.isEnabled = enabled && !selected
    }
}
