// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Entiende fecha, hora y prioridad escritas al final de una tarea, en español, inglés,
 * francés, alemán e italiano:
 *
 * - "Llamar al dentista mañana" → vence mañana.
 * - "Pagar alquiler el viernes !" → vence el próximo viernes, importante.
 * - "Llamar a Ana a las 17" → hoy (o mañana si ya pasó) con aviso a las 17:00.
 * - "Rendez-vous demain à 9h", "Arzt morgen um 9 Uhr", "Dentista domani alle 9:30".
 * - "Revisar informe 15/11" → el 15 de noviembre (el próximo que llegue).
 * - "Sacar la basura cada lunes a las 21" → se repite los lunes, aviso a las 21:00.
 *   También "todos los días", "every week", "tous les mois", "jährlich", "ogni venerdì"…
 * - "Comprar pilas #casa #urgente" → etiquetas "casa" y "urgente" (en cualquier parte).
 *
 * Solo se miran las últimas palabras, para no tocar títulos como "Mañana de playa".
 */
object QuickAddParser {

    data class Result(
        val title: String,
        val due: LocalDate? = null,
        val important: Boolean = false,
        /** Hora del aviso. Sin fecha explícita: hoy si aún no ha pasado, si no mañana. */
        val time: LocalTime? = null,
        /** Repetición (RRULE) escrita al final: "cada lunes", "every day"… */
        val rrule: String? = null,
        val tags: List<String> = emptyList()
    ) {
        val hasExtras: Boolean get() = due != null || important || time != null || rrule != null || tags.isNotEmpty()

        /** Fecha efectiva (la escrita o la deducida de la hora). */
        fun effectiveDue(today: LocalDate, now: LocalTime): LocalDate? {
            // "cada día a las 8" escrito a las 10: la primera es mañana.
            if (due != null && rrule != null && time != null && due == today && !time.isAfter(now)) {
                return Recurrence.next(rrule, due, today.plusDays(1))?.due ?: due
            }
            return due ?: time?.let { if (it.isAfter(now)) today else today.plusDays(1) }
        }

        fun reminder(today: LocalDate, now: LocalTime): LocalDateTime? = time?.let { effectiveDue(today, now)!!.atTime(it) }
    }

    private val DATE_PREFIXES = listOf(
        "para el proximo", "para el", "el proximo", "este", "para", "el", // es
        "on", "by", "next", "this", "due", // en
        "pour le", "pour", "le", "ce", // fr
        "am nachsten", "am", "bis", "nachsten", "nachste", // de
        "per il", "per", "entro", "il" // it
    ).map { it.split(' ') }.sortedByDescending { it.size }

    private val TIME_PREFIXES = listOf(
        "a las", "a la", "sobre las", "at", "around", "a", "vers", "um", "gegen", "alle", "alle ore", "ore", "verso le"
    ).map { it.split(' ') }.sortedByDescending { it.size }

    /** Prefijos que bastan para que un número suelto sea una hora ("a las 9", "um 9"). */
    private val STRONG_TIME_PREFIXES = setOf("a las", "a la", "sobre las", "at", "um", "gegen", "alle", "alle ore", "ore", "verso le")

    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to listOf("lunes", "monday", "lundi", "montag", "lunedi"),
        DayOfWeek.TUESDAY to listOf("martes", "tuesday", "mardi", "dienstag", "martedi"),
        DayOfWeek.WEDNESDAY to listOf("miercoles", "wednesday", "mercredi", "mittwoch", "mercoledi"),
        DayOfWeek.THURSDAY to listOf("jueves", "thursday", "jeudi", "donnerstag", "giovedi"),
        DayOfWeek.FRIDAY to listOf("viernes", "friday", "vendredi", "freitag", "venerdi"),
        DayOfWeek.SATURDAY to listOf("sabado", "saturday", "samedi", "samstag", "sabato"),
        DayOfWeek.SUNDAY to listOf("domingo", "sunday", "dimanche", "sonntag", "domenica")
    ).flatMap { (day, names) -> names.map { it to day } }.toMap()

    private val TODAY = setOf("hoy", "today", "tonight", "aujourd'hui", "aujourdhui", "ce soir", "heute", "heute abend", "oggi", "stasera", "esta noche", "esta tarde")
    private val TOMORROW = setOf("manana", "tomorrow", "demain", "morgen", "domani")
    private val AFTER_TOMORROW = setOf("pasado manana", "apres-demain", "apres demain", "ubermorgen", "dopodomani")
    private val NEXT_WEEK = setOf("next week", "proxima semana", "la proxima semana", "semaine prochaine", "nachste woche", "prossima settimana", "la settimana prossima")

    private val NUMERIC_DATE = Regex("""^(\d{1,2})[/.](\d{1,2})(?:[/.](\d{2}|\d{4}))?$""")
    // Sin "." como separador: "15.11" es una fecha (formato alemán), no una hora.
    private val TIME_TOKEN = Regex("""^(\d{1,2})(?:([:h])(\d{2})?)?(am|pm|h|uhr)?$""")

    private val TAG = Regex("""^#([\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*)$""")

    private val DAILY = setOf("cada dia", "todos los dias", "diario", "diariamente", "a diario", "every day", "daily", "everyday",
        "chaque jour", "tous les jours", "quotidien", "taglich", "jeden tag", "ogni giorno", "tutti i giorni", "giornaliero")
    private val WEEKLY = setOf("cada semana", "todas las semanas", "semanal", "semanalmente", "every week", "weekly",
        "chaque semaine", "toutes les semaines", "hebdomadaire", "wochentlich", "jede woche", "ogni settimana", "settimanale")
    private val MONTHLY = setOf("cada mes", "todos los meses", "mensual", "mensualmente", "every month", "monthly",
        "chaque mois", "tous les mois", "mensuel", "monatlich", "jeden monat", "ogni mese", "mensile")
    private val YEARLY = setOf("cada ano", "todos los anos", "anual", "anualmente", "every year", "yearly", "annually",
        "chaque annee", "tous les ans", "annuel", "jahrlich", "jedes jahr", "ogni anno", "annuale")
    /** Delante de un día de la semana: "cada lunes", "every monday", "tous les lundis", "jeden Montag", "ogni lunedì". */
    private val EVERY_DAY_PREFIXES = listOf("cada", "todos los", "los", "every", "chaque", "tous les", "les", "jeden", "ogni", "tutti i")
        .map { it.split(' ') }.sortedByDescending { it.size }

    fun parse(input: String, today: LocalDate = LocalDate.now()): Result {
        val original = input.trim()
        val allWords = original.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        // Las #etiquetas pueden ir en cualquier parte (siempre que quede título).
        val tags = allWords.mapNotNull { TAG.matchEntire(it)?.groupValues?.get(1) }.distinct()
        val words = (if (tags.isNotEmpty() && allWords.any { TAG.matchEntire(it) == null }) allWords.filter { TAG.matchEntire(it) == null } else allWords)
            .toMutableList()
        val usedTags = if (words.size < allWords.size) tags else emptyList()
        var due: LocalDate? = null
        var time: LocalTime? = null
        var important = false
        var rrule: String? = null
        var repeatStart: LocalDate? = null

        repeat(4) {
            if (words.size < 2) return@repeat
            val last = words.last()
            if (!important && (last == "!" || last == "!!" || last == "¡!")) {
                important = true
                words.removeAt(words.lastIndex)
                return@repeat
            }
            val normalized = words.map(::normalize)
            if (time == null) {
                matchTimeSuffix(normalized)?.let { (t, count) ->
                    time = t
                    repeat(count) { words.removeAt(words.lastIndex) }
                    return@repeat
                }
            }
            if (rrule == null && due == null) {
                matchRecurrenceSuffix(normalized, today)?.let { (rule, start, count) ->
                    rrule = rule
                    repeatStart = start
                    repeat(count) { words.removeAt(words.lastIndex) }
                    return@repeat
                }
            }
            if (due == null) {
                matchDateSuffix(normalized, today)?.let { (d, count) ->
                    due = d
                    repeat(count) { words.removeAt(words.lastIndex) }
                }
            }
        }

        val title = words.joinToString(" ").trim()
        return if (title.isEmpty()) Result(original)
        else Result(title, due ?: repeatStart, important, time, rrule, usedTags)
    }

    /** Repetición al final de [words]: (RRULE, primera fecha, palabras que ocupa). */
    private fun matchRecurrenceSuffix(words: List<String>, today: LocalDate): Triple<String, LocalDate, Int>? {
        for (size in 3 downTo 1) {
            if (words.size <= size) continue
            when (words.takeLast(size).joinToString(" ")) {
                in DAILY -> return Triple("FREQ=DAILY", today, size)
                in WEEKLY -> return Triple("FREQ=WEEKLY", today, size)
                in MONTHLY -> return Triple("FREQ=MONTHLY", today, size)
                in YEARLY -> return Triple("FREQ=YEARLY", today, size)
            }
        }
        // "cada lunes", "tous les lundis"; o "montags", "mondays" solos.
        val last = words.last()
        val day = WEEKDAYS[last] ?: WEEKDAYS[last.removeSuffix("s")]
        if (day != null && words.size >= 2) {
            val before = words.dropLast(1)
            val prefix = EVERY_DAY_PREFIXES.firstOrNull { p -> before.size > p.size && before.takeLast(p.size) == p }
            // "montags", "mondays" (y "on mondays"); "on monday" es una fecha, no una repetición.
            val plural = prefix == null && (last.endsWith("tags") || last.endsWith("days"))
            if (prefix != null || plural) {
                val used = 1 + (prefix?.size ?: if (before.size >= 2 && before.last() == "on") 1 else 0)
                return Triple("FREQ=WEEKLY;BYDAY=${day.name.take(2)}", today.with(TemporalAdjusters.nextOrSame(day)), used)
            }
        }
        return null
    }

    /** Hora al final de [words] y cuántas palabras ocupa. */
    private fun matchTimeSuffix(words: List<String>): Pair<LocalTime, Int>? {
        // "9 uhr", "9 pm", "18 h": el marcador va en una palabra aparte.
        val separateMarker = words.last() in setOf("uhr", "h", "am", "pm")
        val numberIndex = if (separateMarker) words.lastIndex - 1 else words.lastIndex
        if (numberIndex < 1) return null
        val token = if (separateMarker) words[numberIndex] + words.last() else words[numberIndex]
        val m = TIME_TOKEN.matchEntire(token) ?: return null

        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val suffix = m.groupValues[4]
        val hasMarker = m.groupValues[2].isNotEmpty() || suffix.isNotEmpty()

        val before = words.subList(0, numberIndex)
        val prefix = TIME_PREFIXES.firstOrNull { p -> before.size > p.size && before.takeLast(p.size) == p }
        val strong = prefix?.joinToString(" ") in STRONG_TIME_PREFIXES
        if (!hasMarker && !strong) return null
        // "a" solo (francés "à") exige marcador: "à 9h".
        if (!hasMarker && prefix?.joinToString(" ") == "a") return null

        if (suffix == "pm" && hour in 1..11) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val used = (words.size - numberIndex) + (prefix?.size ?: 0)
        if (used >= words.size) return null
        return LocalTime.of(hour, minute) to used
    }

    /** Fecha al final de [words] y cuántas palabras ocupa (incluidas "para el", "am"…). */
    private fun matchDateSuffix(words: List<String>, today: LocalDate): Pair<LocalDate, Int>? {
        for (size in 3 downTo 1) {
            if (words.size <= size) continue
            val date = phraseToDate(words.takeLast(size), today) ?: continue
            val before = words.dropLast(size)
            val prefix = DATE_PREFIXES.firstOrNull { p -> before.size > p.size && before.takeLast(p.size) == p }
            return date to size + (prefix?.size ?: 0)
        }
        return null
    }

    private fun phraseToDate(phrase: List<String>, today: LocalDate): LocalDate? {
        val text = phrase.joinToString(" ")
        when (text) {
            in TODAY -> return today
            in TOMORROW -> return today.plusDays(1)
            in AFTER_TOMORROW -> return today.plusDays(2)
            in NEXT_WEEK -> return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        if (phrase.size != 1) return null
        WEEKDAYS[text]?.let { return today.with(TemporalAdjusters.next(it)) }
        NUMERIC_DATE.matchEntire(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearText = m.groupValues[3]
            return runCatching {
                if (yearText.isNotEmpty()) {
                    val year = yearText.toInt().let { if (it < 100) 2000 + it else it }
                    LocalDate.of(year, month, day)
                } else {
                    val candidate = LocalDate.of(today.year, month, day)
                    if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
                }
            }.getOrNull()
        }
        return null
    }

    /** Minúsculas y sin tildes: "Mañana" → "manana", "Übermorgen" → "ubermorgen", "à" → "a". */
    private fun normalize(word: String): String =
        Normalizer.normalize(word.lowercase().replace('’', '\''), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .trim(',', ';')
            .removeSuffix(".") // "mañana." al final de una frase; "15.11." (fecha alemana) → "15.11"
}
