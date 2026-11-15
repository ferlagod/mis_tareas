// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Tareas que se repiten, con reglas RRULE de iCalendar (RFC 5545 §3.3.10), las mismas que
 * usan Nextcloud Tasks, Tasks.org, Thunderbird o Recordatorios de Apple.
 *
 * Al completar una tarea que se repite no se marca como hecha: su fecha pasa a la siguiente
 * repetición (como hace Tasks.org). Se entienden FREQ (DAILY, WEEKLY, MONTHLY, YEARLY),
 * INTERVAL, BYDAY (también "1MO" o "-1FR" en reglas mensuales), BYMONTHDAY, COUNT y UNTIL.
 * El resto de partes de la regla se conservan al guardarla aunque no se apliquen.
 */
object Recurrence {

    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    data class Rule(
        val freq: Freq,
        val interval: Int = 1,
        /** Días de la semana con su ordinal opcional (0 = todos; 1 = el primero; -1 = el último). */
        val byDay: List<Pair<Int, DayOfWeek>> = emptyList(),
        val byMonthDay: Int? = null,
        val count: Int? = null,
        val until: LocalDate? = null,
        /** Partes de la regla tal cual, para reescribirla sin perder nada. */
        val parts: List<Pair<String, String>> = emptyList()
    ) {
        /** Regla sencilla ("cada día", "cada semana"…) de las que se eligen en el detalle de la tarea. */
        val isSimple: Boolean get() = interval == 1 && byDay.isEmpty() && byMonthDay == null && count == null && until == null &&
            parts.all { it.first in setOf("FREQ", "INTERVAL", "WKST") }
    }

    /** Resultado de completar una repetición. */
    data class Next(val due: LocalDate, val rrule: String)

    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY
    )

    fun simple(freq: Freq): String = "FREQ=${freq.name}"

    fun parse(rrule: String?): Rule? {
        if (rrule.isNullOrBlank()) return null
        val parts = rrule.trim().removePrefix("RRULE:").split(';')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().uppercase() to it[1].trim() } }
        val map = parts.toMap()
        val freq = Freq.entries.firstOrNull { it.name == map["FREQ"]?.uppercase() } ?: return null
        val byDay = map["BYDAY"]?.split(',')?.mapNotNull { token ->
            val t = token.trim().uppercase()
            val day = DAYS[t.takeLast(2)] ?: return@mapNotNull null
            (t.dropLast(2).toIntOrNull() ?: 0) to day
        }.orEmpty()
        return Rule(
            freq = freq,
            interval = map["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            byDay = byDay,
            byMonthDay = map["BYMONTHDAY"]?.split(',')?.singleOrNull()?.toIntOrNull()?.takeIf { it in -31..31 && it != 0 },
            count = map["COUNT"]?.toIntOrNull(),
            until = map["UNTIL"]?.take(8)?.let { runCatching { LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() },
            parts = parts
        )
    }

    /** Frecuencia de una regla sencilla (para marcar el chip elegido), o null si es personalizada. */
    fun simpleFreq(rrule: String?): Freq? = parse(rrule)?.takeIf { it.isSimple }?.freq

    /**
     * Siguiente repetición tras completar la que vence en [due]. Se saltan las que ya hayan pasado
     * (una tarea diaria vencida hace tres días vuelve para hoy, no para pasado mañana).
     * Devuelve null si la serie se ha terminado (COUNT o UNTIL): entonces la tarea se completa.
     */
    fun next(rrule: String, due: LocalDate, today: LocalDate): Next? {
        val rule = parse(rrule) ?: return null
        var count = rule.count
        var date = due
        var guard = 0
        do {
            date = step(rule, date) ?: return null
            if (count != null) {
                count--
                if (count <= 0) return null
            }
            if (rule.until != null && date.isAfter(rule.until)) return null
        } while (date.isBefore(today) && ++guard < 2000)
        return Next(date, if (count != null) withPart(rule, "COUNT", count.toString()) else rrule.trim().removePrefix("RRULE:"))
    }

    private fun withPart(rule: Rule, key: String, value: String): String =
        rule.parts.joinToString(";") { (k, v) -> if (k == key) "$k=$value" else "$k=$v" }

    /** Siguiente fecha de la regla estrictamente posterior a [from]. */
    private fun step(rule: Rule, from: LocalDate): LocalDate? = when (rule.freq) {
        Freq.DAILY -> from.plusDays(rule.interval.toLong())
        Freq.WEEKLY -> {
            val days = rule.byDay.map { it.second }.toSet()
            if (days.isEmpty()) from.plusWeeks(rule.interval.toLong())
            else {
                // Semanas que cuentan: las que distan un múltiplo de INTERVAL de la semana de [from].
                val anchor = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                generateSequence(from.plusDays(1)) { it.plusDays(1) }.take(7 * rule.interval + 7).firstOrNull { d ->
                    val weeks = ChronoUnit.WEEKS.between(anchor, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    d.dayOfWeek in days && weeks % rule.interval == 0L
                }
            }
        }
        Freq.MONTHLY -> {
            val ordinal = rule.byDay.firstOrNull { it.first != 0 }
            (1..48).asSequence().map { from.plusMonths((it * rule.interval).toLong()) }.mapNotNull { month ->
                when {
                    ordinal != null -> nthWeekday(month, ordinal.first, ordinal.second)
                    rule.byMonthDay != null -> monthDay(month, rule.byMonthDay)
                    else -> month
                }
            }.firstOrNull { it.isAfter(from) }
                // Por si [from] no era una fecha de la regla: la del mismo mes.
                ?.let { candidate ->
                    val sameMonth = when {
                        ordinal != null -> nthWeekday(from, ordinal.first, ordinal.second)
                        rule.byMonthDay != null -> monthDay(from, rule.byMonthDay)
                        else -> null
                    }
                    if (sameMonth != null && sameMonth.isAfter(from)) sameMonth else candidate
                }
        }
        Freq.YEARLY -> from.plusYears(rule.interval.toLong())
    }

    /** El [n]-ésimo [day] del mes de [month] (n negativo cuenta desde el final). */
    private fun nthWeekday(month: LocalDate, n: Int, day: DayOfWeek): LocalDate? {
        val date = if (n > 0) month.with(TemporalAdjusters.dayOfWeekInMonth(n, day))
        else month.with(TemporalAdjusters.lastInMonth(day)).minusWeeks((-n - 1).toLong())
        return date.takeIf { it.month == month.month }
    }

    /** Día [day] del mes (negativo: desde el final); en meses más cortos, el último día. */
    private fun monthDay(month: LocalDate, day: Int): LocalDate {
        val length = month.lengthOfMonth()
        val d = if (day > 0) day.coerceAtMost(length) else (length + day + 1).coerceAtLeast(1)
        return month.withDayOfMonth(d)
    }
}
