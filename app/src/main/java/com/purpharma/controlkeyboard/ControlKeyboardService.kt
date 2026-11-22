
package com.purpharma.controlkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space

class ControlKeyboardService : InputMethodService() {

    private var shiftOn = false
    private var ctrlOn = false
    private var altOn = false
    private var arabicOn = false
    private var numberPage = false

    private val modifierButtons = mutableMapOf<String, Button>()
    private val letterButtons = mutableMapOf<String, Button>()

    private lateinit var lettersPanel: LinearLayout
    private lateinit var numbersPanel: LinearLayout

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
        modifierButtons.clear()
        letterButtons.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(3, 4, 3, 4)
            setBackgroundColor(Color.rgb(30, 30, 30))
        }

        addControlRow(root, listOf("CTRL", "SHIFT", "ALT", "ESC"))
        addControlRow(root, listOf("HOME", "END", "DEL", "←", "→"))
        addControlRow(root, listOf("↑", "↓", "TAB", "LANG"))

        addControlRow(root, listOf("LINE", "ALL", "COPY", "CUT", "PASTE"))

        lettersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addLetterRow(
            lettersPanel,
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        )

        addLetterRow(
            lettersPanel,
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            centered = true
        )

        addLetterRow(
            lettersPanel,
            listOf("Z", "X", "C", "V", "B", "N", "M"),
            shiftAndBack = true
        )

        addControlRow(lettersPanel, listOf("123", ",", "SPACE", ".", "ENTER"))

        numbersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        addNumberRow(numbersPanel, listOf("1", "2", "3", "4", "5"))
        addNumberRow(numbersPanel, listOf("6", "7", "8", "9", "0"))
        addControlRow(numbersPanel, listOf("ABC", "-", "/", ":", "ENTER"))

        root.addView(lettersPanel)
        root.addView(numbersPanel)

        updateKeyboard()
        return root
    }

    private fun addControlRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            val button = makeButton(key, 10f, 42)

            if (key in listOf("CTRL", "SHIFT", "ALT")) {
                modifierButtons[key] = button
            }

            button.setOnClickListener { handleKey(key) }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, 42, 1f).apply {
                    setMargins(2, 2, 2, 2)
                }
            )
        }

        root.addView(row)
    }

    private fun addLetterRow(
        root: LinearLayout,
        keys: List<String>,
        centered: Boolean = false,
        shiftAndBack: Boolean = false
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        if (centered) addSpacer(row, 0.5f)

        if (shiftAndBack) {
            val shift = makeButton("SHIFT", 10f, 48)
            modifierButtons["SHIFT"] = shift
            shift.setOnClickListener { handleKey("SHIFT") }
            row.addView(shift, LinearLayout.LayoutParams(0, 48, 1.5f).apply {
                setMargins(2, 2, 2, 2)
            })
        }

        keys.forEach { key ->
            val button = makeButton(
                if (arabicOn) arabic[key] ?: key else key,
                15f,
                48
            )

            letterButtons[key] = button
            button.setOnClickListener { handleLetter(key) }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, 48, 1f).apply {
                    setMargins(2, 2, 2, 2)
                }
            )
        }

        if (centered) addSpacer(row, 0.5f)

        if (shiftAndBack) {
            val back = makeButton("⌫", 17f, 48)
            back.setOnClickListener { handleKey("BACK") }
            row.addView(back, LinearLayout.LayoutParams(0, 48, 1.5f).apply {
                setMargins(2, 2, 2, 2)
            })
        }

        root.addView(row)
    }

    private fun addNumberRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        keys.forEach { key ->
            val button = makeButton(key, 17f, 48)
            button.setOnClickListener { handleKey(key) }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, 48, 1f).apply {
                    setMargins(2, 2, 2, 2)
                }
            )
        }

        root.addView(row)
    }

    private fun addSpacer(row: LinearLayout, weight: Float) {
        row.addView(
            Space(this),
            LinearLayout.LayoutParams(0, 1, weight)
        )
    }

    private fun makeButton(
        label: String,
        size: Float,
        height: Int
    ): Button {
        return Button(this).apply {
            text = label
            textSize = size
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setPadding(1, 1, 1, 1)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 75, 75))
            layoutParams = LinearLayout.LayoutParams(
                0, height, 1f
            )
        }
    }

    private fun updateKeyboard() {
        modifierButtons.forEach { (key, button) ->
            val active = when (key) {
                "CTRL" -> ctrlOn
                "SHIFT" -> shiftOn
                "ALT" -> altOn
                else -> false
            }

            button.setBackgroundColor(
                if (active) Color.rgb(35, 105, 190)
                else Color.rgb(75, 75, 75)
            )
        }

        letterButtons.forEach { (key, button) ->
            button.text = if (arabicOn) arabic[key] ?: key else key
        }

        if (::lettersPanel.isInitialized && ::numbersPanel.isInitialized) {
            lettersPanel.visibility =
                if (numberPage) View.GONE else View.VISIBLE

            numbersPanel.visibility =
                if (numberPage) View.VISIBLE else View.GONE
        }
    }

    private fun handleLetter(key: String) {
        val ic = currentInputConnection ?: return

        if (ctrlOn) {
            val action = when (key) {
                "A" -> android.R.id.selectAll
                "C" -> android.R.id.copy
                "X" -> android.R.id.cut
                "V" -> android.R.id.paste
                else -> 0
            }

            if (action != 0) {
                val done = ic.performContextMenuAction(action)
                if (!done) sendKey(ic, letterKeyCode(key))
            } else {
                sendKey(ic, letterKeyCode(key))
            }

            ctrlOn = false
            shiftOn = false
            altOn = false
            updateKeyboard()
            return
        }

        val value = if (arabicOn) {
            arabic[key] ?: key
        } else {
            if (shiftOn) key else key.lowercase()
        }

        ic.commitText(value, 1)
        shiftOn = false
        updateKeyboard()
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

            "123" -> numberPage = true
            "ABC" -> numberPage = false

            "HOME" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME)
            "END" -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END)
            "DEL" -> sendKey(ic, KeyEvent.KEYCODE_FORWARD_DEL)
            "BACK" -> ic.deleteSurroundingText(1, 0)

            "←" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            "TAB" -> sendKey(ic, KeyEvent.KEYCODE_TAB)
            "ESC" -> sendKey(ic, KeyEvent.KEYCODE_ESCAPE)
            "ENTER" -> sendKey(ic, KeyEvent.KEYCODE_ENTER)

            "SPACE" -> {
                ic.commitText(" ", 1)
                shiftOn = false
            }

            "LINE" -> selectCurrentLine(ic)
            "ALL" -> ic.performContextMenuAction(android.R.id.selectAll)
            "COPY" -> ic.performContextMenuAction(android.R.id.copy)
            "CUT" -> ic.performContextMenuAction(android.R.id.cut)
            "PASTE" -> ic.performContextMenuAction(android.R.id.paste)

            "," -> ic.commitText(",", 1)
            "." -> ic.commitText(".", 1)
            "-" -> ic.commitText("-", 1)
            "/" -> ic.commitText("/", 1)
            ":" -> ic.commitText(":", 1)

            in "0".."9" -> ic.commitText(key, 1)
        }

        updateKeyboard()
    }

    private fun selectCurrentLine(ic: InputConnection) {
        val extracted = ic.getExtractedText(
            ExtractedTextRequest(), 0
        ) ?: return

        val text = extracted.text?.toString() ?: return
        val cursor = extracted.selectionEnd.coerceIn(0, text.length)

        val previous = text.lastIndexOf(
            '\n', (cursor - 1).coerceAtLeast(0)
        )
        val next = text.indexOf('\n', cursor)

        val start = if (previous < 0) 0 else previous + 1
        val end = if (next < 0) text.length else next

        ic.setSelection(
            extracted.startOffset + start,
            extracted.startOffset + end
        )
    }

    private fun sendKey(ic: InputConnection, code: Int) {
        if (code == 0) return

        var meta = 0
        if (shiftOn) meta = meta or KeyEvent.META_SHIFT_ON
        if (ctrlOn) meta = meta or KeyEvent.META_CTRL_ON
        if (altOn) meta = meta or KeyEvent.META_ALT_ON

        val downTime = SystemClock.uptimeMillis()

        ic.sendKeyEvent(
            KeyEvent(
                downTime, downTime,
                KeyEvent.ACTION_DOWN,
                code, 0, meta
            )
        )

        val upTime = SystemClock.uptimeMillis()

        ic.sendKeyEvent(
            KeyEvent(
                downTime, upTime,
                KeyEvent.ACTION_UP,
                code, 0, meta
            )
        )

        shiftOn = false
        ctrlOn = false
        altOn = false
    }
}
