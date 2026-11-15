// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private val today = LocalDate.of(2026, 11, 15) // domingo

    private fun next(rule: String, due: LocalDate, now: LocalDate = due) = Recurrence.next(rule, due, now)

    @Test
    fun simpleFrequencies() {
        val due = LocalDate.of(2026, 11, 16)
        assertEquals(LocalDate.of(2026, 11, 17), next("FREQ=DAILY", due)!!.due)
        assertEquals(LocalDate.of(2026, 11, 23), next("FREQ=WEEKLY", due)!!.due)
        assertEquals(LocalDate.of(2026, 12, 16), next("FREQ=MONTHLY", due)!!.due)
        assertEquals(LocalDate.of(2027, 11, 16), next("FREQ=YEARLY", due)!!.due)
        assertEquals(LocalDate.of(2026, 11, 19), next("FREQ=DAILY;INTERVAL=3", due)!!.due)
    }

    @Test
    fun weeklyByDay() {
        // Lunes y jueves: del lunes 16 pasa al jueves 19, y de ahí al lunes 23.
        assertEquals(LocalDate.of(2026, 11, 19), next("FREQ=WEEKLY;BYDAY=MO,TH", LocalDate.of(2026, 11, 16))!!.due)
        assertEquals(LocalDate.of(2026, 11, 23), next("FREQ=WEEKLY;BYDAY=MO,TH", LocalDate.of(2026, 11, 19))!!.due)
        // Cada dos semanas los lunes.
        assertEquals(LocalDate.of(2026, 11, 30), next("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO", LocalDate.of(2026, 11, 16))!!.due)
    }

    @Test
    fun monthlyRules() {
        // Último viernes de cada mes.
        assertEquals(LocalDate.of(2026, 12, 25), next("FREQ=MONTHLY;BYDAY=-1FR", LocalDate.of(2026, 11, 27))!!.due)
        // Primer lunes.
        assertEquals(LocalDate.of(2026, 12, 7), next("FREQ=MONTHLY;BYDAY=1MO", LocalDate.of(2026, 11, 2))!!.due)
        // Día 31: en meses cortos, el último día.
        assertEquals(LocalDate.of(2027, 2, 28), next("FREQ=MONTHLY;BYMONTHDAY=31", LocalDate.of(2027, 1, 31))!!.due)
    }

    @Test
    fun overdueSkipsPastOccurrences() {
        // Diaria vencida hace tres días: vuelve para hoy.
        assertEquals(today, next("FREQ=DAILY", today.minusDays(3), today)!!.due)
        // Semanal vencida: la siguiente que no haya pasado.
        assertEquals(LocalDate.of(2026, 11, 16), next("FREQ=WEEKLY", LocalDate.of(2026, 11, 2), today)!!.due)
    }

    @Test
    fun countAndUntilEndTheSeries() {
        val r = next("FREQ=DAILY;COUNT=3", today)!!
        assertEquals("FREQ=DAILY;COUNT=2", r.rrule)
        assertNull(next("FREQ=DAILY;COUNT=1", today))
        assertNull(next("FREQ=WEEKLY;UNTIL=20261120T000000Z", today))
        assertEquals(LocalDate.of(2026, 11, 22), next("FREQ=WEEKLY;UNTIL=20261130", today)!!.due)
    }

    @Test
    fun unknownRulesAreNotApplied() {
        assertNull(next("FREQ=HOURLY", today))
        assertNull(next("garbage", today))
        assertEquals(Recurrence.Freq.WEEKLY, Recurrence.simpleFreq("FREQ=WEEKLY"))
        assertNull(Recurrence.simpleFreq("FREQ=WEEKLY;BYDAY=MO"))
    }
}
