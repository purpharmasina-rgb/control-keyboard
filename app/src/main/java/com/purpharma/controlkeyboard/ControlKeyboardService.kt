
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
    private var symbolPage = 0

    private val modifierButtons = mutableMapOf<String, Button>()
    private val letterButtons = mutableMapOf<String, Button>()
    private var languageButton: Button? = null
    private var topShiftButton: Button? = null
    private var symbolPageButton: Button? = null

    private lateinit var lettersPanel: LinearLayout
    private lateinit var letterRowsPanel: LinearLayout
    private lateinit var numbersPanel: LinearLayout

    // Arabic key positions follow the Samsung layout shown by the user.
    private val arabic = mapOf(
        "Q" to "ض", "W" to "ص", "E" to "ث", "R" to "ق",
        "T" to "ف", "Y" to "غ", "U" to "ع", "I" to "ه",
        "O" to "خ", "P" to "ح", "BRACKET_LEFT" to "ج",
        "A" to "ش", "S" to "س", "D" to "ي", "F" to "ب",
        "G" to "ل", "H" to "ا", "J" to "ت", "K" to "ن",
        "L" to "م", "SEMICOLON" to "ك", "APOSTROPHE" to "ط",
        "BACKTICK" to "ذ", "X" to "ء", "C" to "ؤ",
        "V" to "ر", "N" to "ى", "M" to "ة",
        "COMMA" to "و", "PERIOD" to "ز", "SLASH" to "ظ",
        "BRACKET_RIGHT" to "د"
    )

    private val arabicRow1 = listOf(
        "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "BRACKET_LEFT"
    )
    private val arabicRow2 = listOf(
        "A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE"
    )
    private val arabicRow3 = listOf(
        "BACKTICK", "X", "C", "V", "N", "M", "COMMA", "PERIOD", "SLASH", "BRACKET_RIGHT"
    )

    override fun onCreateInputView(): View {
        modifierButtons.clear()
        letterButtons.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
            setBackgroundColor(Color.rgb(30, 30, 30))
        }

        addControlRow(root, listOf("CTRL", "SHIFT", "ALT", "ESC"))
        addControlRow(root, listOf("HOME", "END", "DEL", "←", "→"))
        addControlRow(root, listOf("↑", "↓", "TAB", "LANG"))

        addControlRow(root, listOf("LINE", "ALL", "COPY", "CUT", "PASTE"))

        lettersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        letterRowsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lettersPanel.addView(letterRowsPanel)
        rebuildLetterRows()
        addControlRow(lettersPanel, listOf("123", ",", "SPACE", ".", "ENTER"))

        numbersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        rebuildNumberRows()

        root.addView(lettersPanel)
        root.addView(numbersPanel)

        updateKeyboard()
        return root
    }

    private fun addControlRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Keep the authored key order stable on RTL Android locales:
            // 123 stays left and ENTER stays right.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        keys.forEach { key ->
            val button = makeButton(key, 12f, 38)

            if (key in listOf("CTRL", "SHIFT", "ALT")) {
                modifierButtons[key] = button
            }
            if (key == "SHIFT") topShiftButton = button
            if (key == "LANG") languageButton = button
            if (key == "PAGE") symbolPageButton = button

            button.setOnClickListener { handleKey(key) }

            val keyWeight = if (key == "SPACE") 2.5f else 1f
            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(38), keyWeight).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        root.addView(row)
    }

    private fun addLetterRow(
        root: LinearLayout,
        keys: List<String>,
        centered: Boolean = false,
        shiftAndBack: Boolean = false,
        backOnly: Boolean = false,
        sideSpacerWeight: Float = 0.5f
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Keep both Latin and Arabic letter rows in their intended
            // left-to-right key order, independent of the phone's RTL locale.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        if (centered) addSpacer(row, sideSpacerWeight)

        if (shiftAndBack) {
            val shift = makeButton("SHIFT", 12f, 48)
            modifierButtons["SHIFT"] = shift
            shift.setOnClickListener { handleKey("SHIFT") }
            row.addView(shift, LinearLayout.LayoutParams(0, dp(48), 1.5f).apply {
                setMargins(dp(1), dp(2), dp(1), dp(2))
            })
        }

        keys.forEach { key ->
            val button = makeButton(
                if (arabicOn) arabic[key] ?: key else key,
                19f,
                48
            )

            letterButtons[key] = button
            button.setOnClickListener { handleLetter(key) }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                }
            )
        }

        if (centered) addSpacer(row, sideSpacerWeight)

        if (shiftAndBack || backOnly) {
            val back = makeButton("⌫", 19f, 48)
            back.setOnClickListener { handleKey("BACK") }
            row.addView(back, LinearLayout.LayoutParams(0, dp(48), 1.5f).apply {
                setMargins(dp(1), dp(2), dp(1), dp(2))
            })
        }

        root.addView(row)
    }

    private fun addNumberRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Prevent the device's RTL locale from reversing number/symbol keys.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        keys.forEach { key ->
            val button = makeButton(key, 18f, 44)
            button.setOnClickListener { handleKey(key) }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        root.addView(row)
    }

    private fun rebuildNumberRows() {
        if (!::numbersPanel.isInitialized) return
        numbersPanel.removeAllViews()

        // Four compact pages: numbers, then three complete common symbol sets.
        val pages = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "\\$", "%", "&", "*", "+", "=", "(", ")"),
            listOf("!", "?", ":", ";", "\\", "/", "-", "_", "\"", "'"),
            listOf("[", "]", "{", "}", "<", ">", "|", "~", "^", "€")
        )
        val keys = pages[symbolPage.coerceIn(0, pages.lastIndex)]
        addNumberRow(numbersPanel, keys.take(5))
        addNumberRow(numbersPanel, keys.drop(5))
        addControlRow(numbersPanel, listOf("ABC", "PAGE", ",", ".", "ENTER"))
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
            setPadding(dp(2), dp(1), dp(2), dp(1))
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
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
        languageButton?.text = if (arabicOn) "EN" else "عربي"
        symbolPageButton?.text = "${symbolPage + 1}/4"

        if (::lettersPanel.isInitialized && ::numbersPanel.isInitialized) {
            lettersPanel.visibility =
                if (numberPage) View.GONE else View.VISIBLE

            numbersPanel.visibility =
                if (numberPage) View.VISIBLE else View.GONE
        }
    }

    private fun rebuildLetterRows() {
        if (!::letterRowsPanel.isInitialized) return
        letterRowsPanel.removeAllViews()
        letterButtons.clear()

        // In Arabic mode the top SHIFT control remains the active modifier;
        // the Samsung third row has letters plus Backspace, without a second Shift key.
        topShiftButton?.let { modifierButtons["SHIFT"] = it }
        if (arabicOn) {
            addLetterRow(letterRowsPanel, arabicRow1)
            addLetterRow(letterRowsPanel, arabicRow2)
            addLetterRow(letterRowsPanel, arabicRow3, backOnly = true)
        } else {
            addLetterRow(
                letterRowsPanel,
                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            )
            addLetterRow(
                letterRowsPanel,
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
                centered = true
            )
            addLetterRow(
                letterRowsPanel,
                listOf("Z", "X", "C", "V", "B", "N", "M"),
                centered = true,
                backOnly = true,
                sideSpacerWeight = 0.75f
            )
        }
    }

    private fun handleLetter(key: String) {
        val ic = currentInputConnection ?: return

        if (ctrlOn) {
            sendCtrlShortcut(ic, letterKeyCode(key))
            ctrlOn = false
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
            "BRACKET_LEFT" -> KeyEvent.KEYCODE_LEFT_BRACKET
            "SEMICOLON" -> KeyEvent.KEYCODE_SEMICOLON
            "APOSTROPHE" -> KeyEvent.KEYCODE_APOSTROPHE
            "BACKTICK" -> KeyEvent.KEYCODE_GRAVE
            "COMMA" -> KeyEvent.KEYCODE_COMMA
            "PERIOD" -> KeyEvent.KEYCODE_PERIOD
            "SLASH" -> KeyEvent.KEYCODE_SLASH
            "BRACKET_RIGHT" -> KeyEvent.KEYCODE_RIGHT_BRACKET
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

            "LANG" -> {
                arabicOn = !arabicOn
                rebuildLetterRows()
            }

            "123" -> numberPage = true
            "ABC" -> numberPage = false
            "PAGE" -> {
                symbolPage = (symbolPage + 1) % 4
                rebuildNumberRows()
            }

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
            "ALL" -> selectAllWithFallback(ic)
            "COPY" -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_C)
            "CUT" -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_X)
            "PASTE" -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_V)

            "," -> ic.commitText(",", 1)
            "." -> ic.commitText(".", 1)
            "-" -> ic.commitText("-", 1)
            "/" -> ic.commitText("/", 1)
            ":" -> ic.commitText(":", 1)
            "?" , "!", "@", "#", "$", "%", "&", "*", "+", "=", "(", ")",
            "\\", "_", "\"", "'", "[", "]", "{", "}", "<", ">", "|", "~", "^", "€" ->
                ic.commitText(key, 1)

            in "0".."9" -> ic.commitText(key, 1)
        }

        updateKeyboard()
    }

    private fun selectAllWithFallback(ic: InputConnection) {
        // Try the editor's native Select All action before synthesizing Ctrl+A.
        val handled = ic.performContextMenuAction(android.R.id.selectAll)
        if (!handled) {
            sendCtrlShortcut(ic, KeyEvent.KEYCODE_A)
        } else {
            ctrlOn = false
            updateKeyboard()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun sendCtrlShortcut(ic: InputConnection, code: Int) {
        if (code == 0) return
        val downTime = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        fun dispatch(action: Int, keyCode: Int, metaState: Int) {
            ic.sendKeyEvent(
                KeyEvent(
                    downTime, SystemClock.uptimeMillis(), action, keyCode, 0,
                    metaState, KeyEvent.KEYCODE_UNKNOWN, 0, flags
                )
            )
        }
        val ctrlMeta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        dispatch(KeyEvent.ACTION_DOWN, code, ctrlMeta)
        dispatch(KeyEvent.ACTION_UP, code, ctrlMeta)
        ctrlOn = false
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

        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        ic.sendKeyEvent(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, code, 0, meta,
                KeyEvent.KEYCODE_UNKNOWN, 0, flags)
        )
        val upTime = SystemClock.uptimeMillis()
        ic.sendKeyEvent(
            KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, code, 0, meta,
                KeyEvent.KEYCODE_UNKNOWN, 0, flags)
        )

        shiftOn = false
        ctrlOn = false
        altOn = false
    }
}
