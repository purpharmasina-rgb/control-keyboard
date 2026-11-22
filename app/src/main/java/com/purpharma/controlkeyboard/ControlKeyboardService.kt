
package com.purpharma.controlkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout

class ControlKeyboardService : InputMethodService() {

    private var shiftOn = false
    private var ctrlOn = false
    private var altOn = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        addRow(root, listOf("CTRL", "SHIFT", "ALT", "ESC"))
        addRow(root, listOf("HOME", "END", "DEL", "←", "→"))
        addRow(root, listOf("↑", "↓", "TAB", "SPACE"))
        addRow(root, listOf("A", "C", "X", "V", "ENTER"))

        return root
    }

    private fun addRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        keys.forEach { key ->
            val button = Button(this).apply {
                text = key
                textSize = 12f
                isAllCaps = false
                setPadding(2, 2, 2, 2)
                setOnClickListener { handleKey(key) }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, 52, 1f)
            )
        }

        root.addView(row)
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "CTRL" -> ctrlOn = !ctrlOn
            "SHIFT" -> shiftOn = !shiftOn
            "ALT" -> altOn = !altOn

            "HOME" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME)
            "END" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END)
            "DEL" -> ic.deleteSurroundingText(1, 0)

            "←" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            "TAB" -> sendKey(ic, KeyEvent.KEYCODE_TAB)
            "ESC" -> sendKey(ic, KeyEvent.KEYCODE_ESCAPE)
            "ENTER" -> sendKey(ic, KeyEvent.KEYCODE_ENTER)
            "SPACE" -> ic.commitText(" ", 1)

            "A", "C", "X", "V" -> {
                if (ctrlOn) {
                    val code = when (key) {
                        "A" -> KeyEvent.KEYCODE_A
                        "C" -> KeyEvent.KEYCODE_C
                        "X" -> KeyEvent.KEYCODE_X
                        else -> KeyEvent.KEYCODE_V
                    }
                    sendKey(ic, code)
                    ctrlOn = false
                } else {
                    ic.commitText(key.lowercase(), 1)
                }
            }
        }
    }

    private fun sendKey(ic: InputConnection, code: Int) {
        var meta = 0

        if (shiftOn) {
            meta = meta or KeyEvent.META_SHIFT_ON
        }
        if (ctrlOn) {
            meta = meta or KeyEvent.META_CTRL_ON
        }
        if (altOn) {
            meta = meta or KeyEvent.META_ALT_ON
        }

        ic.sendKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)
        )
        ic.sendKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta)
        )

        shiftOn = false
        altOn = false
    }
}
