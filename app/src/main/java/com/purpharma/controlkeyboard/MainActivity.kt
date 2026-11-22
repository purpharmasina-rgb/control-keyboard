package com.purpharma.controlkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
        }
        root.addView(TextView(this).apply {
            text = "Control Keyboard"
            textSize = 28f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "لوحة مفاتيح باختصارات التحكم. لتفعيلها، اسمح بها يدويًا من إعدادات أندرويد ثم اخترها من مبدّل لوحة المفاتيح."
            textSize = 17f
            setPadding(0, 24, 0, 24)
        })
        root.addView(TextView(this).apply {
            text = keyboardStatus()
            textSize = 15f
            setPadding(0, 8, 0, 20)
        })
        root.addView(actionButton("فتح إعدادات لوحات المفاتيح") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(actionButton("اختيار Control Keyboard") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })
        root.addView(TextView(this).apply {
            text = "ملاحظة: أندرويد هو المسؤول عن تفعيل لوحة المفاتيح واختيارها. التطبيق لا يغيّر هذه الإعدادات دون موافقتك."
            textSize = 14f
            setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }

    private fun keyboardStatus(): String {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        return "حالة اللوحة: " + (if (enabled) "مفعّلة في أندرويد" else "غير مفعّلة بعد") +
            "\nالاختيار الحالي: " + (if (selected) "Control Keyboard" else "لوحة أخرى")
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        setOnClickListener { action() }
    }
}
