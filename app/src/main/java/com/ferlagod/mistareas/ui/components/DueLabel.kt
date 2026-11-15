// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ferlagod.mistareas.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Estado de una fecha límite respecto a hoy (para el color). */
enum class DueState { OVERDUE, TODAY, FUTURE }

fun dueState(day: LocalDate, today: LocalDate): DueState = when {
    day.isBefore(today) -> DueState.OVERDUE
    day == today -> DueState.TODAY
    else -> DueState.FUTURE
}

/** "Hoy", "Mañana", "Ayer", "vie" (esta semana), "15 nov" o "15 nov 2027", con la hora si la hay. */
@Composable
fun dueLabel(day: LocalDate, minutes: Int?, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val date = when {
        day == today -> stringResource(R.string.due_today)
        day == today.plusDays(1) -> stringResource(R.string.due_tomorrow)
        day == today.minusDays(1) -> stringResource(R.string.due_yesterday)
        day.isAfter(today) && day.isBefore(today.plusDays(7)) ->
            day.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        day.year == today.year -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(day)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale).format(day)
    }
    if (minutes == null) return date
    val time = LocalTime.of(minutes / 60, minutes % 60)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return "$date · " + DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** "Cada semana", "Cada mes"… o "Se repite" para reglas personalizadas de otras apps. */
@Composable
fun repeatLabel(rrule: String?): String {
    // "Cada lunes" (lo que crea la entrada rápida).
    val rule = com.ferlagod.mistareas.domain.Recurrence.parse(rrule)
    if (rule != null && rule.freq == com.ferlagod.mistareas.domain.Recurrence.Freq.WEEKLY && rule.interval == 1 &&
        rule.byDay.size == 1 && rule.byDay[0].first == 0 && rule.count == null && rule.until == null
    ) {
        val locale = LocalConfiguration.current.locales[0]
        return stringResource(R.string.repeat_every_weekday, rule.byDay[0].second.getDisplayName(TextStyle.FULL, locale))
    }
    return simpleRepeatLabel(rrule)
}

@Composable
private fun simpleRepeatLabel(rrule: String?): String = when (com.ferlagod.mistareas.domain.Recurrence.simpleFreq(rrule)) {
    com.ferlagod.mistareas.domain.Recurrence.Freq.DAILY -> stringResource(R.string.repeat_daily)
    com.ferlagod.mistareas.domain.Recurrence.Freq.WEEKLY -> stringResource(R.string.repeat_weekly)
    com.ferlagod.mistareas.domain.Recurrence.Freq.MONTHLY -> stringResource(R.string.repeat_monthly)
    com.ferlagod.mistareas.domain.Recurrence.Freq.YEARLY -> stringResource(R.string.repeat_yearly)
    null -> stringResource(R.string.repeats)
}
