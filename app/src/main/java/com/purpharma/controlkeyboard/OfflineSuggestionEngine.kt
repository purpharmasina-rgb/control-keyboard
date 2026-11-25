package com.purpharma.controlkeyboard

import android.content.Context
import android.content.SharedPreferences
import java.text.Normalizer
import java.util.Locale

/**
 * Small, private, fully-offline suggestion engine.
 * It starts with a compact Arabic/English seed vocabulary and improves from
 * words and adjacent-word pairs learned locally on this device.
 */
class OfflineSuggestionEngine(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("offline_suggestions", Context.MODE_PRIVATE)

    private val seedNextWords = mapOf(
        "صباح" to listOf("الخير"),
        "مساء" to listOf("الخير", "النور"),
        "السلام" to listOf("عليكم"),
        "ازيك" to listOf("عامل", "عاملة"),
        "عامل" to listOf("ايه"),
        "انا" to listOf("عايز", "عاوز", "محتاج"),
        "عايز" to listOf("اعرف", "اشتري", "اسأل"),
        "محتاج" to listOf("مساعدة", "اعرف"),
        "شكرا" to listOf("جدا"),
        "على" to listOf("فكرة", "الطول"),
        "how" to listOf("are"),
        "are" to listOf("you"),
        "good" to listOf("morning", "to"),
        "thank" to listOf("you"),
        "i" to listOf("am", "need"),
        "can" to listOf("you"),
        "see" to listOf("you")
    )

    private val seedWords = (
        "انا انت انتي احنا هو هي هم ده دي دول اللي اللى على علي في فى من الى الى مع مش كان كانت يكون "
        + "عايز عاوز عايزة ممكن لو طيب تمام شكرا شكراً معلش ازيك عامل اخبار ايه ايه ليه امتى فين "
        + "صباح الخير مساء النور السلام عليكم وعليكم السلام حاضر بعدين دلوقتي النهارده بكرة امبارح "
        + "البيت الشغل الشركة الموبايل الكمبيوتر الكيبورد الكتابة رسالة رسالة جديدة ابعت ابعتلي "
        + "محتاج محتاجه عايزك تساعدني خلينا نكمل الموضوع الفكرة المشروع التطبيق البرنامج "
        + "كبير صغير كويس ممتاز جميل جديد قديم سريع سهل صعب مضبوط صح غلط تمام جدا قوي اوي "
        + "مصر القاهرة اسكندرية الاسكندرية العربية الانجليزي عربي انجليزي كلمة كلمات توقع اقتراح "
        + "keyboard control phone android samsung hello hi thanks thank you please yes no "
        + "the and you are is it this that with for from have can will good great please "
        + "hello world today tomorrow yesterday keyboard typing message word words suggestion "
        + "prediction smart offline online project app application phone mobile android"
    ).split(Regex("\\s+")).toSet()

    private fun normalize(value: String): String {
        val noMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
    }

    private fun learnedWords(): Map<String, Int> =
        prefs.all.filterValues { it is Int }
            .mapNotNull { (key, value) ->
                if (key.startsWith("w:") && value is Int) key.removePrefix("w:") to value else null
            }.toMap()

    private fun bigramCounts(previous: String): Map<String, Int> {
        val normalized = normalize(previous)
        val prefix = "b:$normalized|"
        val learned = prefs.all.filter { it.key.startsWith(prefix) }
            .mapNotNull { (key, value) ->
                if (value is Int) key.removePrefix(prefix) to value else null
            }.toMap()
        val seeded = seedNextWords[normalized].orEmpty().associateWith { 2 }
        return seeded + learned.mapValues { (word, count) ->
            count + (seeded[word] ?: 0)
        }
    }

    fun suggestions(prefix: String, previousWord: String, limit: Int = 3): List<String> {
        val learned = learnedWords()
        val bigrams = if (previousWord.isNotBlank()) bigramCounts(previousWord) else emptyMap()
        val all = (seedWords + learned.keys + bigrams.keys).distinct()
        if (prefix.isBlank()) {
            val contextual = bigrams.entries.sortedByDescending { it.value }.map { it.key }
            val frequent = learned.entries.sortedByDescending { it.value }.map { it.key }
            return (contextual + frequent).distinct().take(limit)
        }

        val target = normalize(prefix)
        val ranked = all.mapNotNull { word ->
            val normalized = normalize(word)
            val learnedScore = learned[word] ?: 0
            val contextScore = bigrams[word] ?: 0
            val prefixMatch = normalized.startsWith(target)
            val distance = if (!prefixMatch && target.length >= 3 && normalized.length >= 3)
                editDistanceAtMostOne(target, normalized) else 2
            if (!prefixMatch && distance > 1) return@mapNotNull null
            val score = (if (prefixMatch) 100 else 35) +
                learnedScore * 4 + contextScore * 9 - distance * 12 -
                (normalized.length - target.length).coerceAtLeast(0)
            word to score
        }
        return ranked.sortedByDescending { it.second }
            .map { it.first }.distinct().take(limit)
    }

    fun learnSentenceContext(textBeforeCursor: String) {
        val tokens = textBeforeCursor.trim().split(Regex("[\\s\\p{Punct}،؛؟]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return
        val current = tokens.last()
        val previous = tokens.getOrNull(tokens.lastIndex - 1)
        val editor = prefs.edit()
        val wordKey = "w:$current"
        editor.putInt(wordKey, (prefs.getInt(wordKey, 0) + 1).coerceAtMost(500))
        if (!previous.isNullOrBlank()) {
            val pairKey = "b:${normalize(previous)}|${current}"
            editor.putInt(pairKey, (prefs.getInt(pairKey, 0) + 1).coerceAtMost(500))
        }
        editor.apply()
    }

    private fun editDistanceAtMostOne(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                edits++
                if (edits > 1) return 2
                when {
                    a.length > b.length -> i++
                    b.length > a.length -> j++
                    else -> { i++; j++ }
                }
            }
        }
        if (i < a.length || j < b.length) edits++
        return if (edits <= 1) edits else 2
    }
}
