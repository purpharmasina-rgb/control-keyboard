
package com.purpharma.controlkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.LinearLayout

class ControlKeyboardService : InputMethodService() {

    private var shiftOn = false
    private var ctrlOn = false
    private var altOn = false
    private var arabicOn = false

    private val arabic = mapOf(
        "Q" to "ض", "W" to "ص", "E" to "ث",
        "R" to "ق", "T" to "ف", "Y" to "غ",
        "U" to "ع", "I" to "ه", "O" to "خ",
        "P" to "ح", "A" to "ش", "S" to "س",
        "D" to "ي", "F" to "ب", "G" to "ل",
        "H" to "ا", "J" to "ت", "K" to "ن",
        "L" to "م", "Z" to "ئ", "X" to "ء",
        "C" to "ؤ", "V" to "ر", "B" to "لا",
        "N" to "ى", "M" to "ة"
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(3, 4, 3, 4)
            setBackgroundColor(Color.rgb(30, 30, 30))
        }

        addRow(root, listOf("CTRL", "SHIFT", "ALT", "ESC"), 42)
        addRow(root, listOf("HOME", "END", "DEL", "←", "→"), 42)
        addRow(root, listOf("↑", "↓", "TAB", "LANG"), 42)

        addRow(root, listOf(
            "LINE", "ALL", "COPY", "CUT", "PASTE"
        ), 42)

        addRow(root, listOf(
            "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "0"
        ), 42)

        addLetterRow(root, listOf(
            "Q", "W", "E", "R", "T",
            "Y", "U", "I", "O", "P"
        ))

        addLetterRow(root, listOf(
            "A", "S", "D", "F", "G",
            "H", "J", "K", "L"
        ))

        addRow(root, listOf(
            "SHIFT", "Z", "X", "C", "V",
            "B", "N", "M", "BACK"
        ), 46)

        addRow(root, listOf(
            ",", "SPACE", ".", "ENTER"
        ), 46)

        return root
    }

    private fun addRow(
        root: LinearLayout,
        keys: List<String>,
        height: Int
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        keys.forEach { key ->

            val active = when (key) {
                "CTRL" -> ctrlOn
                "SHIFT" -> shiftOn
                "ALT" -> altOn
                else -> false
            }

            val label = when (key) {
                "LANG" -> if (arabicOn) "عربي" else "EN"
                "BACK" -> "⌫"
                else -> key
            }

            val button = Button(this).apply {
                text = label
                textSize = if (key == "SPACE") 12f else 10f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(1, 1, 1, 1)
                setTextColor(Color.WHITE)

                setBackgroundColor(
                    if (active) Color.rgb(35, 105, 190)
                    else Color.rgb(65, 65, 65)
                )

                setOnClickListener {
                    handleKey(key)
                }
            }

            val weight = if (key == "SPACE") 3f else 1f

            row.addView(
                button,
                LinearLayout.LayoutParams(
                    0, height, weight
                ).apply {
                    setMargins(2, 2, 2, 2)
                }
            )
        }

        root.addView(row)
    }

    private fun addLetterRow(
        root: LinearLayout,
        keys: List<String>
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        keys.forEach { key ->

            val label = if (arabicOn) {
                arabic[key] ?: key
            } else {
                key
            }

            val button = Button(this).apply {
                text = label
                textSize = 15f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(1, 1, 1, 1)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(75, 75, 75))

                setOnClickListener {
                    handleLetter(key)
                }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(
                    0, 47, 1f
                ).apply {
                    setMargins(2, 2, 2, 2)
                }
            )
        }

        root.addView(row)
    }

    private fun handleLetter(key: String) {
        val ic = currentInputConnection ?: return

        if (ctrlOn) {
            val code = letterKeyCode(key)

            if (code != 0) {
                sendKey(ic, code)
            }

            ctrlOn = false
            refreshKeyboard()
            return
        }

        val value = if (arabicOn) {
            arabic[key] ?: key
        } else {
            if (shiftOn) key else key.lowercase()
        }

        ic.commitText(value, 1)

        shiftOn = false
        refreshKeyboard()
    }

    private fun letterKeyCode(key: String): Int {
        return when (key) {
            "A" -> KeyEvent.KEYCODE_A
            "B" -> KeyEvent.KEYCODE_B
            "C" -> KeyEvent.KEYCODE_C
            "D" -> KeyEvent.KEYCODE_D
            "E" -> KeyEvent.KEYCODE_E
            "F" -> KeyEvent.KEYCODE_F
            "G" -> KeyEvent.KEYCODE_G
            "H" -> KeyEvent.KEYCODE_H
            "I" -> KeyEvent.KEYCODE_I
            "J" -> KeyEvent.KEYCODE_J
            "K" -> KeyEvent.KEYCODE_K
            "L" -> KeyEvent.KEYCODE_L
            "M" -> KeyEvent.KEYCODE_M
            "N" -> KeyEvent.KEYCODE_N
            "O" -> KeyEvent.KEYCODE_O
            "P" -> KeyEvent.KEYCODE_P
            "Q" -> KeyEvent.KEYCODE_Q
            "R" -> KeyEvent.KEYCODE_R
            "S" -> KeyEvent.KEYCODE_S
            "T" -> KeyEvent.KEYCODE_T
            "U" -> KeyEvent.KEYCODE_U
            "V" -> KeyEvent.KEYCODE_V
            "W" -> KeyEvent.KEYCODE_W
            "X" -> KeyEvent.KEYCODE_X
            "Y" -> KeyEvent.KEYCODE_Y
            "Z" -> KeyEvent.KEYCODE_Z
            else -> 0
        }
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "CTRL" -> ctrlOn = !ctrlOn
            "SHIFT" -> shiftOn = !shiftOn
            "ALT" -> altOn = !altOn

            "LANG" -> arabicOn = !arabicOn

            "HOME" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME)
            "END" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END)

            "DEL" -> sendKey(
                ic, KeyEvent.KEYCODE_FORWARD_DEL
            )

            "BACK" -> ic.deleteSurroundingText(1, 0)

            "←" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            "TAB" -> sendKey(ic, KeyEvent.KEYCODE_TAB)
            "ESC" -> sendKey(ic, KeyEvent.KEYCODE_ESCAPE)
            "ENTER" -> sendKey(ic, KeyEvent.KEYCODE_ENTER)

            "SPACE" -> ic.commitText(" ", 1)

            "LINE" -> selectCurrentLine(ic)
            "ALL" -> selectAll(ic)
            "COPY" -> ic.performContextMenuAction(android.R.id.copy)
            "CUT" -> ic.performContextMenuAction(android.R.id.cut)
            "PASTE" -> ic.performContextMenuAction(android.R.id.paste)

            "," -> ic.commitText(",", 1)
            "." -> ic.commitText(".", 1)

            in "0".."9" -> ic.commitText(key, 1)
        }

        refreshKeyboard()
    }

    private fun selectAll(ic: InputConnection) {
        ic.performContextMenuAction(android.R.id.selectAll)
    }

    private fun selectCurrentLine(ic: InputConnection) {
        val extracted = ic.getExtractedText(
            ExtractedTextRequest(), 0
        ) ?: return

        val text = extracted.text?.toString() ?: return
        val cursor = extracted.selectionEnd.coerceIn(0, text.length)

        val previous = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
        val next = text.indexOf('\n', cursor)

        val start = if (previous < 0) 0 else previous + 1
        val end = if (next < 0) text.length else next

        ic.setSelection(start, end)
    }

    private fun sendKey(ic: InputConnection, code: Int) {
        var meta = 0

        if (shiftOn) meta = meta or KeyEvent.META_SHIFT_ON
        if (ctrlOn) meta = meta or KeyEvent.META_CTRL_ON
        if (altOn) meta = meta or KeyEvent.META_ALT_ON

        val now = SystemClock.uptimeMillis()

        ic.sendKeyEvent(
            KeyEvent(
                now, now,
                KeyEvent.ACTION_DOWN,
                code, 0, meta
            )
        )

        ic.sendKeyEvent(
            KeyEvent(
                now, now,
                KeyEvent.ACTION_UP,
                code, 0, meta
            )
        )

        ctrlOn = false
        altOn = false
    }

    private fun refreshKeyboard() {
        setInputView(onCreateInputView())
    }
}
