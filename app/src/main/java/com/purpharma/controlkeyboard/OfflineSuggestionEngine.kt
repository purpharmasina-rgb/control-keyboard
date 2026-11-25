package com.purpharma.controlkeyboard

import android.content.Context
import android.content.SharedPreferences
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Compact offline suggestion engine. All learned data stays in private app storage.
 * A single preferences snapshot is used per suggestion pass to avoid repeated full scans.
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
    ).entries.associate { normalize(it.key) to it.value }

    private val seedWords = (
        "انا انت انتي احنا هو هي هم ده دي دول اللي اللى على علي في فى من الى الى مع مش كان كانت يكون " +
        "عايز عاوز عايزة ممكن لو طيب تمام شكرا شكراً معلش ازيك عامل اخبار ايه ايه ليه امتى فين " +
        "صباح الخير مساء النور السلام عليكم وعليكم السلام حاضر بعدين دلوقتي النهارده بكرة امبارح " +
        "البيت الشغل الشركة الموبايل الكمبيوتر الكيبورد الكتابة رسالة رسالة جديدة ابعت ابعتلي " +
        "محتاج محتاجه عايزك تساعدني خلينا نكمل الموضوع الفكرة المشروع التطبيق البرنامج " +
        "كبير صغير كويس ممتاز جميل جديد قديم سريع سهل صعب مضبوط صح غلط تمام جدا قوي اوي " +
        "مصر القاهرة اسكندرية الاسكندرية العربية الانجليزي عربي انجليزي كلمة كلمات توقع اقتراح " +
        "keyboard control phone android samsung hello hi thanks thank you please yes no " +
        "the and you are is it this that with for from have can will good great please " +
        "hello world today tomorrow yesterday keyboard typing message word words suggestion " +
        "prediction smart offline online project app application phone mobile android"
    ).split(WORD_SPLIT).filter { it.isNotBlank() }.toSet()

    private val normalizedSeedWords = seedWords.associateBy(::normalize)

    fun suggestions(prefix: String, previousWord: String, limit: Int = 3): List<String> {
        val snapshot = prefs.all
        val learned = snapshot.entries.mapNotNull { (key, value) ->
            if (key.startsWith("w:") && value is Int) key.removePrefix("w:") to value else null
        }.toMap()
        val previous = normalize(previousWord)
        val bigramPrefix = "b:$previous|"
        val bigrams = snapshot.entries.mapNotNull { (key, value) ->
            if (key.startsWith(bigramPrefix) && value is Int)
                key.removePrefix(bigramPrefix) to value else null
        }.toMap().toMutableMap()
        seedNextWords[previous].orEmpty().forEach { seeded ->
            bigrams[seeded] = (bigrams[seeded] ?: 0) + 2
        }

        val candidates = normalizedSeedWords.toMutableMap()
        learned.keys.forEach { candidates.putIfAbsent(normalize(it), it) }
        bigrams.keys.forEach { candidates.putIfAbsent(normalize(it), it) }
        if (prefix.isBlank()) {
            val contextual = bigrams.entries.sortedByDescending { it.value }.map { it.key }
            val frequent = learned.entries.sortedByDescending { it.value }.map { it.key }
            return (contextual + frequent).distinct().take(limit)
        }

        val target = normalize(prefix)
        val ranked = candidates.entries.mapNotNull { (normalized, word) ->
            val learnedScore = learned[word] ?: learned[normalized] ?: 0
            val contextScore = bigrams[word] ?: bigrams[normalized] ?: 0
            val prefixMatch = normalized.startsWith(target)
            val distance = if (!prefixMatch && target.length >= 3 && normalized.length >= 3)
                editDistanceAtMostOne(target, normalized) else 2
            if (!prefixMatch && distance > 1) return@mapNotNull null
            val score = (if (prefixMatch) 100 else 35) +
                learnedScore * 4 + contextScore * 9 - distance * 12 -
                (normalized.length - target.length).coerceAtLeast(0)
            word to score
        }
        return ranked.sortedByDescending { it.second }.map { it.first }.distinct().take(limit)
    }

    fun learnSentenceContext(textBeforeCursor: String) {
        val tokens = textBeforeCursor.trim().split(WORD_SPLIT).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return
        val current = tokens.last()
        if (!isLearnable(current)) return
        val previous = tokens.getOrNull(tokens.lastIndex - 1)?.takeIf(::isLearnable)
        val editor = prefs.edit()
        val wordKey = "w:$current"
        editor.putInt(wordKey, (prefs.getInt(wordKey, 0) + 1).coerceAtMost(500))
        if (!previous.isNullOrBlank()) {
            val pairKey = "b:${normalize(previous)}|$current"
            editor.putInt(pairKey, (prefs.getInt(pairKey, 0) + 1).coerceAtMost(500))
        }
        editor.apply()
    }

    /** Deletes only locally learned words and pairs; built-in seed vocabulary remains available. */
    fun clearLearned() {
        prefs.edit().clear().apply()
    }

    private fun isLearnable(word: String): Boolean =
        word.length <= 32 && word.all { it.isLetter() }

    private fun normalize(value: String): String {
        val noMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(MARKS, "")
        return noMarks.lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
    }

    private fun editDistanceAtMostOne(a: String, b: String): Int {
        if (abs(a.length - b.length) > 1) return 2
        if (a.length == b.length) {
            val mismatches = a.indices.filter { a[it] != b[it] }
            return when (mismatches.size) {
                0 -> 0
                1 -> 1
                2 -> {
                    val i = mismatches[0]
                    val j = mismatches[1]
                    if (j == i + 1 && a[i] == b[j] && a[j] == b[i]) 1 else 2
                }
                else -> 2
            }
        }
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        var i = 0
        var j = 0
        var edits = 0
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) {
                i++
                j++
            } else {
                edits++
                if (edits > 1) return 2
                j++
            }
        }
        return if (edits <= 1) edits else 2
    }

    private companion object {
        val WORD_SPLIT = Regex("[\\s\\p{Punct}،؛؟]+")
        val MARKS = Regex("\\p{Mn}+")
    }
}
