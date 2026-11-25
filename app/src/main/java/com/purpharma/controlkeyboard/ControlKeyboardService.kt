package com.purpharma.controlkeyboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Space

class ControlKeyboardService : InputMethodService() {

    private var shiftOn = false
    private var ctrlOn = false
    private var altOn = false
    private var arabicOn = false
    private var numberPage = false
    private var symbolPage = 0

    // browserMode: the target app is a web browser (e.g. Cloudflare's Monaco editor).
    // In that mode we send real Ctrl key events instead of context-menu actions,
    // because the editor keeps only the current line in its hidden text field.
    // forceKeyMode: manual override, toggled by long-pressing ALL.
    private var browserMode = false
    private var forceKeyMode = false
    private val keyMode: Boolean get() = browserMode || forceKeyMode

    private val uiHandler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var activeRepeat: Runnable? = null
    private var pendingInputAction: Runnable? = null
    private lateinit var suggestionPanel: LinearLayout
    private val suggestionButtons = mutableListOf<Button>()
    private val suggestionEngine by lazy { OfflineSuggestionEngine(this) }

    private val modifierButtons = mutableMapOf<String, Button>()
    private val letterButtons = mutableMapOf<String, Button>()
    private var languageButton: Button? = null
    private var topShiftButton: Button? = null
    private var symbolPageButton: Button? = null
    private var allButton: Button? = null

    private lateinit var lettersPanel: LinearLayout
    private lateinit var letterRowsPanel: LinearLayout
    private lateinit var numbersPanel: LinearLayout

    private val keyColor = Color.rgb(75, 75, 75)
    private val keyPressed = Color.rgb(125, 125, 125)
    private val activeColor = Color.rgb(35, 105, 190)
    private val activePressed = Color.rgb(75, 145, 230)

    private val keyFlags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
    private val ctrlMeta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    private val shiftMeta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private val altMeta = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON

    private val repeatKeys = setOf("←", "→", "↑", "↓", "BACK", "DEL")

    private val browserPackages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "org.mozilla.fenix", "com.brave.browser", "com.microsoft.emmx",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.opera.browser", "com.opera.mini.native", "com.kiwibrowser.browser",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser"
    )

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

    // Long-press alternatives for Arabic letters (key names follow the map above).
    private val arabicLetterPopups = mapOf(
        "H" to listOf("أ", "إ", "آ"),
        "D" to listOf("ئ"),
        "V" to listOf("لا"),
        "X" to listOf(
            "\u064B", "\u064C", "\u064D", "\u064E",
            "\u064F", "\u0650", "\u0651", "\u0652"
        )
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

    // ---------------------------------------------------------------- lifecycle

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopRepeat()
        cancelPendingInputAction()
        browserMode = (info?.packageName ?: "") in browserPackages
        dismissPopup()
        updateKeyboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        cancelPendingInputAction()
        dismissPopup()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        stopRepeat()
        cancelPendingInputAction()
        dismissPopup()
        super.onWindowHidden()
    }

    override fun onCreateInputView(): View {
        modifierButtons.clear()
        letterButtons.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
            setBackgroundColor(Color.rgb(30, 30, 30))
        }

        addControlRow(root, listOf("ESC", "SHIFT", "ALT", "CTRL"))
        addControlRow(root, listOf("HOME", "END", "DEL"))
        addArrowLanguageRow(root)
        addControlRow(root, listOf("LINE", "ALL", "COPY", "CUT", "PASTE", "REPL", "CLR"))

        lettersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        suggestionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            visibility = View.GONE
        }
        repeat(3) {
            val suggestion = makeButton("", 15f, 34)
            suggestion.visibility = View.GONE
            suggestion.setOnClickListener { applySuggestion(suggestion.text.toString()) }
            suggestionButtons.add(suggestion)
            suggestionPanel.addView(suggestion, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }

        letterRowsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lettersPanel.addView(suggestionPanel)
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

    // ---------------------------------------------------------------- building UI

    private fun addControlRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Keep the authored key order stable on RTL Android locales.
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
            if (key == "ALL") allButton = button

            if (key in repeatKeys) {
                attachRepeat(button, key)
            } else {
                button.setOnClickListener {
                    buzz(it)
                    handleKey(key)
                }
                when (key) {
                    ".", "," -> button.setOnLongClickListener { longPressFor(button, key) }
                    "ALL" -> button.setOnLongClickListener {
                        toggleForceKeyMode(button)
                        true
                    }
                }
            }

            val keyWeight = when {
                key == "SPACE" -> 2.5f
                !arabicOn && key == "X" -> 1.2f
                else -> 1f
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(38), keyWeight).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        root.addView(row)
    }

    // Keep LANG on the far left and align the vertical arrows directly
    // beneath the horizontal arrows in the row above.
    private fun addArrowLanguageRow(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        val lang = makeButton("عربي", 12f, 90)
        languageButton = lang
        lang.setOnClickListener { buzz(it); handleKey("LANG") }
        row.addView(lang, LinearLayout.LayoutParams(0, dp(96), 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })

        val cluster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        fun clusterRow(left: String?, center: String?, right: String?) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            listOf(left, center, right).forEach { key ->
                if (key == null) {
                    line.addView(Space(this), LinearLayout.LayoutParams(0, dp(30), 1f))
                } else {
                    val button = makeButton(key, if (key == "TAB") 12f else 16f, 30)
                    if (key == "TAB") button.setOnClickListener { buzz(it); handleKey("TAB") }
                    else attachRepeat(button, key)
                    line.addView(button, LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                        setMargins(dp(2), dp(1), dp(2), dp(1))
                    })
                }
            }
            cluster.addView(line, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
            ))
        }
        clusterRow(null, "↑", null)
        clusterRow("←", "TAB", "→")
        clusterRow(null, "↓", null)
        row.addView(cluster, LinearLayout.LayoutParams(0, dp(96), 3f))
        root.addView(row)
    }
    private fun addLetterRow(
        root: LinearLayout,
        keys: List<String>,
        centered: Boolean = false,
        backOnly: Boolean = false,
        sideSpacerWeight: Float = 0.5f
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        if (centered) addSpacer(row, sideSpacerWeight)

        keys.forEach { key ->
            val button = makeButton(
                if (arabicOn) arabic[key] ?: key else key,
                19f,
                48
            )

            letterButtons[key] = button
            if (arabicOn && arabicLetterPopups.containsKey(key)) {
                // Preserve the existing Arabic alternate-character popup keys.
                button.setOnClickListener {
                    buzz(it)
                    handleLetter(key)
                }
                button.setOnLongClickListener { longPressFor(button, key) }
            } else {
                // Ordinary letters repeat while the key is held down.
                attachRepeatAction(button) { handleLetter(key) }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                }
            )
        }

        if (centered) addSpacer(row, sideSpacerWeight)

        if (backOnly) {
            val back = makeButton("⌫", 19f, 48)
            attachRepeat(back, "BACK")
            row.addView(back, LinearLayout.LayoutParams(0, dp(48), 1.5f).apply {
                setMargins(dp(1), dp(2), dp(1), dp(2))
            })
        }

        root.addView(row)
    }

    private fun addNumberRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        keys.forEach { key ->
            val button = makeButton(key, 18f, 44)
            button.setOnClickListener {
                buzz(it)
                handleKey(key)
            }

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

        // The first screen includes digits plus the punctuation users need most.
        // Remaining pages hold the less frequently used symbols.
        val pages = listOf(
            listOf(
                "1", "2", "3", "4", "5", "6", "7",
                "8", "9", "0", ",", ".", "?", "!",
                "$", "/", "€", "@", "#", "%"
            ),
            listOf("&", "*", "+", "=", "(", ")", "-", "_", "\"", "'"),
            listOf(":", ";", "\\", "[", "]", "{", "}", "<", ">", "£"),
            listOf("|", "~", "^", "¥", "°", "…", "\u0060", "§", "©", "®")
        )
        val keys = pages[symbolPage.coerceIn(0, pages.lastIndex)]
        val rowSize = if (symbolPage == 0) 7 else 5
        keys.chunked(rowSize).forEach { addNumberRow(numbersPanel, it) }
        addControlRow(numbersPanel, listOf("ABC", "PAGE", ",", ".", "ENTER"))
    }

    private fun addSpacer(row: LinearLayout, weight: Float) {
        row.addView(
            Space(this),
            LinearLayout.LayoutParams(0, 1, weight)
        )
    }

    private fun keyBg(normal: Int, pressed: Int): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(6).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(normal))
        }
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
            background = keyBg(keyColor, keyPressed)
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

            button.background =
                if (active) keyBg(activeColor, activePressed)
                else keyBg(keyColor, keyPressed)
        }

        letterButtons.forEach { (key, button) ->
            button.text = if (arabicOn) arabic[key] ?: key else key
        }
        languageButton?.text = if (arabicOn) "EN" else "عربي"
        symbolPageButton?.text = "${symbolPage + 1}/4"
        allButton?.text = if (forceKeyMode) "ALL*" else "ALL"
        updateSuggestionBar()

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

        // The top SHIFT control stays the active modifier in both layouts.
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
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
            )
            addLetterRow(
                letterRowsPanel,
                listOf("Z", "X", "C", "V", "B", "N", "M"),
                backOnly = true
            )
        }
    }

    private fun updateSuggestionBar() {
        if (!::suggestionPanel.isInitialized) return
        val type = currentInputEditorInfo?.inputType ?: run { suggestionPanel.visibility = View.GONE; return }
        val inputClass = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val password = inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        if (password || inputClass != InputType.TYPE_CLASS_TEXT) {
            suggestionPanel.visibility = View.GONE
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val parts = before.split(Regex("[\\\\s،؛؟,.!]+"))
        val boundary = before.isEmpty() || before.last().isWhitespace() || before.last() in "،؛؟,.!"
        val prefix = if (boundary) "" else parts.lastOrNull().orEmpty()
        val previous = if (prefix.isBlank()) parts.lastOrNull { it.isNotBlank() }.orEmpty()
            else parts.getOrNull(parts.lastIndex - 1).orEmpty()
        val choices = suggestionEngine.suggestions(prefix, previous)
        if (choices.isEmpty()) { suggestionPanel.visibility = View.GONE; return }
        suggestionButtons.forEachIndexed { index, button ->
            val choice = choices.getOrNull(index)
            button.visibility = if (choice == null) View.INVISIBLE else View.VISIBLE
            button.text = choice ?: ""
        }
        suggestionPanel.visibility = View.VISIBLE
    }

    private fun applySuggestion(word: String) {
        if (word.isBlank()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val boundary = before.isEmpty() || before.last().isWhitespace() || before.last() in "،؛؟,.!"
        val current = if (boundary) "" else before.split(Regex("[\\\\s،؛؟,.!]+" )).lastOrNull().orEmpty()
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText(if (boundary) word + " " else word, 1)
        buzz(suggestionPanel)
        updateSuggestionBar()
    }

    private fun learnBeforeDelimiter(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        suggestionEngine.learnSentenceContext(before)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateSuggestionBar()
    }
    // ---------------------------------------------------------------- touch helpers

    private fun buzz(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // Press once = one action; holding repeats the supplied key action.
    private fun attachRepeat(button: Button, key: String) {
        attachRepeatAction(button) { handleKey(key) }
    }

    private fun attachRepeatAction(button: Button, action: () -> Unit) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopRepeat()
                    v.isPressed = true
                    buzz(v)
                    action()
                    val repeater = object : Runnable {
                        override fun run() {
                            if (!v.isPressed) {
                                if (activeRepeat === this) activeRepeat = null
                                return
                            }
                            action()
                            uiHandler.postDelayed(this, 60)
                        }
                    }
                    activeRepeat = repeater
                    uiHandler.postDelayed(repeater, 380)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                }
            }
            true
        }
    }

    private fun stopRepeat() {
        activeRepeat?.let { uiHandler.removeCallbacks(it) }
        activeRepeat = null
    }

    private fun cancelPendingInputAction() {
        pendingInputAction?.let { uiHandler.removeCallbacks(it) }
        pendingInputAction = null
    }

    private fun scheduleInputAction(action: () -> Unit) {
        cancelPendingInputAction()
        val task = Runnable {
            pendingInputAction = null
            action()
            updateKeyboard()
        }
        pendingInputAction = task
        uiHandler.postDelayed(task, 150)
    }

    private fun toggleForceKeyMode(v: View) {
        forceKeyMode = !forceKeyMode
        buzz(v)
        updateKeyboard()
    }

    // ---------------------------------------------------------------- popups

    private fun popupItems(key: String): List<String> {
        return if (arabicOn && !numberPage) {
            when (key) {
                "." -> listOf("؟", "!", "،", "؛", ":", "…")
                "," -> listOf("-", "_", "'", "\"", "(", ")", "/", "@")
                else -> arabicLetterPopups[key] ?: emptyList()
            }
        } else {
            when (key) {
                "." -> listOf("?", "!", ":", ";", "…")
                "," -> listOf("-", "_", "'", "\"", "(", ")", "/", "@")
                else -> emptyList()
            }
        }
    }

    private fun longPressFor(button: Button, key: String): Boolean {
        val items = popupItems(key)
        if (items.isEmpty()) return false
        buzz(button)
        showPopup(button, items)
        return true
    }

    private fun popupLabel(item: String): String {
        // Show Arabic diacritics attached to a tatweel so they are visible.
        return if (item.length == 1 && item[0] in '\u064B'..'\u0652') "\u0640$item" else item
    }

    private fun showPopup(anchor: View, items: List<String>) {
        dismissPopup()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 50, 50))
                cornerRadius = dp(8).toFloat()
            }
        }

        items.forEach { item ->
            val b = Button(this).apply {
                text = popupLabel(item)
                textSize = 20f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = keyBg(keyColor, keyPressed)
            }
            b.setOnClickListener {
                buzz(it)
                currentInputConnection?.commitText(item, 1)
                shiftOn = false
                dismissPopup()
                updateKeyboard()
            }
            row.addView(
                b,
                LinearLayout.LayoutParams(dp(40), dp(44)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        val width = items.size * dp(44) + dp(8)
        val height = dp(44) + dp(4) + dp(8)

        val pw = PopupWindow(row, width, height)
        pw.isOutsideTouchable = true
        pw.isFocusable = false
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup = pw

        try {
            pw.showAsDropDown(anchor, 0, -(anchor.height + height + dp(4)))
        } catch (e: Exception) {
            popup = null
        }
    }

    private fun dismissPopup() {
        popup?.dismiss()
        popup = null
    }

    // ---------------------------------------------------------------- input handling

    private fun handleLetter(key: String) {
        val ic = currentInputConnection ?: return

        if (ctrlOn || altOn) {
            val code = letterKeyCode(key)
            if (ctrlOn) sendCtrlShortcut(ic, code) else sendKey(ic, code)
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
                dismissPopup()
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
            "BACK" -> sendKey(ic, KeyEvent.KEYCODE_DEL)

            "←" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            "TAB" -> sendKey(ic, KeyEvent.KEYCODE_TAB)
            "ESC" -> sendKey(ic, KeyEvent.KEYCODE_ESCAPE)
            "ENTER" -> sendEnter(ic)

            "SPACE" -> {
                learnBeforeDelimiter(ic)
                ic.commitText(" ", 1)
                shiftOn = false
            }

            "LINE" -> selectCurrentLine(ic)
            "ALL" -> selectAll(ic)
            "COPY" -> clipboardAction(ic, android.R.id.copy, KeyEvent.KEYCODE_C)
            "CUT" -> clipboardAction(ic, android.R.id.cut, KeyEvent.KEYCODE_X)
            "PASTE" -> clipboardAction(ic, android.R.id.paste, KeyEvent.KEYCODE_V)
            "REPL" -> replaceAll()
            "CLR" -> clearAll()

            "," -> ic.commitText(",", 1)
            "." -> ic.commitText(".", 1)
            "-" -> ic.commitText("-", 1)
            "/" -> ic.commitText("/", 1)
            ":" -> ic.commitText(":", 1)
            "?", "!", "@", "#", "$", "%", "&", "*", "+", "=", "(", ")",
            "\\", "_", "\"", "'", "[", "]", "{", "}", "<", ">", "|", "~", "^", "€" ->
                ic.commitText(key, 1)

            in "0".."9" -> ic.commitText(key, 1)
        }

        updateKeyboard()
    }

    // ---------------------------------------------------------------- select / copy / paste

    private fun selectAll(ic: InputConnection) {
        if (keyMode) {
            // Browsers / web editors: send a real Ctrl+A so the page selects everything.
            sendCtrlShortcut(ic, KeyEvent.KEYCODE_A)
            return
        }
        // Normal fields: try the editor's native Select All first.
        if (ic.performContextMenuAction(android.R.id.selectAll)) {
            resetModifiers()
        } else {
            sendCtrlShortcut(ic, KeyEvent.KEYCODE_A)
        }
    }

    private fun clipboardAction(ic: InputConnection, actionId: Int, code: Int) {
        if (keyMode) {
            sendCtrlShortcut(ic, code)
            return
        }
        if (ic.performContextMenuAction(actionId)) {
            resetModifiers()
        } else {
            sendCtrlShortcut(ic, code)
        }
    }

    // Select everything, then paste the clipboard over it (replace a whole file in one tap).
    private fun replaceAll() {
        val ic = currentInputConnection ?: return
        selectAll(ic)
        scheduleInputAction {
            clipboardAction(ic, android.R.id.paste, KeyEvent.KEYCODE_V)
        }
    }

    // Select everything, then delete it.
    private fun clearAll() {
        val ic = currentInputConnection ?: return
        selectAll(ic)
        scheduleInputAction {
            sendRaw(ic, KeyEvent.KEYCODE_DEL, 0)
        }
    }

    private fun selectCurrentLine(ic: InputConnection) {
        if (keyMode) {
            // Web editors only expose the current line to us, so use Home + Shift+End.
            sendRaw(ic, KeyEvent.KEYCODE_MOVE_HOME, 0)
            sendRaw(ic, KeyEvent.KEYCODE_MOVE_END, shiftMeta)
            return
        }

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

    // ---------------------------------------------------------------- key events

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun resetModifiers() {
        shiftOn = false
        ctrlOn = false
        altOn = false
    }

    private fun deviceId(): Int =
        if (keyMode) KeyCharacterMap.VIRTUAL_KEYBOARD else 0

    private fun keyEvent(
        downTime: Long,
        action: Int,
        code: Int,
        meta: Int
    ): KeyEvent {
        return KeyEvent(
            downTime, SystemClock.uptimeMillis(), action, code, 0,
            meta, deviceId(), 0, keyFlags
        )
    }

    private fun sendRaw(ic: InputConnection, code: Int, meta: Int) {
        if (code == 0) return
        val downTime = SystemClock.uptimeMillis()
        ic.sendKeyEvent(keyEvent(downTime, KeyEvent.ACTION_DOWN, code, meta))
        ic.sendKeyEvent(keyEvent(downTime, KeyEvent.ACTION_UP, code, meta))
    }

    private fun currentMeta(): Int {
        var meta = 0
        if (shiftOn) meta = meta or shiftMeta
        if (ctrlOn) meta = meta or ctrlMeta
        if (altOn) meta = meta or altMeta
        return meta
    }

    private fun sendKey(ic: InputConnection, code: Int) {
        if (code == 0) return
        sendRaw(ic, code, currentMeta())
        resetModifiers()
    }

    private fun sendCtrlShortcut(ic: InputConnection, code: Int) {
        if (code == 0) return

        var meta = ctrlMeta
        if (shiftOn) meta = meta or shiftMeta
        if (altOn) meta = meta or altMeta

        if (keyMode) {
            // Behave like a real keyboard: Ctrl down, key down/up, Ctrl up.
            val t = SystemClock.uptimeMillis()
            ic.sendKeyEvent(keyEvent(t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, ctrlMeta))
            sendRaw(ic, code, meta)
            ic.sendKeyEvent(keyEvent(t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0))
        } else {
            sendRaw(ic, code, meta)
        }

        resetModifiers()
    }

    // ENTER runs the field's action (search / send / go) when it has one.
    private fun sendEnter(ic: InputConnection) {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        val hasAction = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED

        if (!noEnterAction && hasAction) {
            ic.performEditorAction(action)
            resetModifiers()
        } else {
            sendKey(ic, KeyEvent.KEYCODE_ENTER)
        }
    }
}