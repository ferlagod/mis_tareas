// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddRecurrenceTest {

    private val today = LocalDate.of(2026, 11, 15) // domingo

    private fun parse(text: String) = QuickAddParser.parse(text, today)

    @Test
    fun weekdayInFiveLanguages() {
        listOf(
            "Sacar la basura cada lunes", "Sacar la basura todos los lunes", "Sacar la basura los lunes",
            "Take out trash every monday", "Take out trash on mondays", "Sortir les poubelles tous les lundis",
            "Sortir les poubelles chaque lundi", "Müll rausbringen jeden Montag", "Müll rausbringen montags",
            "Portare fuori la spazzatura ogni lunedì"
        ).forEach { text ->
            val r = parse(text)
            assertEquals(text, "FREQ=WEEKLY;BYDAY=MO", r.rrule)
            assertEquals(text, LocalDate.of(2026, 11, 16), r.due)
            assertEquals(text, text.substringBefore(" cada").substringBefore(" todos").substringBefore(" los ").substringBefore(" every")
                .substringBefore(" on ").substringBefore(" tous").substringBefore(" chaque").substringBefore(" jeden").substringBefore(" montags")
                .substringBefore(" ogni"), r.title)
        }
    }

    @Test
    fun frequencies() {
        assertEquals("FREQ=DAILY", parse("Tomar pastilla todos los días").rrule)
        assertEquals("FREQ=DAILY", parse("Take pill daily").rrule)
        assertEquals("FREQ=WEEKLY", parse("Regar plantas cada semana").rrule)
        assertEquals("FREQ=MONTHLY", parse("Pagar alquiler cada mes").rrule)
        assertEquals("FREQ=MONTHLY", parse("Miete zahlen monatlich").rrule)
        assertEquals("FREQ=YEARLY", parse("Revisar coche cada año").rrule)
        assertEquals(today, parse("Tomar pastilla todos los días").due)
    }

    @Test
    fun withTime() {
        val r = parse("Sacar la basura cada lunes a las 21")
        assertEquals("Sacar la basura", r.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", r.rrule)
        assertEquals(LocalTime.of(21, 0), r.time)
        // "cada día a las 8" escrito a las 10: empieza mañana.
        val daily = parse("Pastilla cada día a las 8")
        assertEquals(today.plusDays(1), daily.effectiveDue(today, LocalTime.of(10, 0)))
        assertEquals(today, daily.effectiveDue(today, LocalTime.of(7, 0)))
    }

    @Test
    fun singleDateIsNotARepeat() {
        val r = parse("Dentista el lunes")
        assertNull(r.rrule)
        assertEquals(LocalDate.of(2026, 11, 16), r.due)
        assertNull(parse("Gym on monday").rrule)
    }

    @Test
    fun tags() {
        val r = parse("Comprar pilas #casa mañana #urgente")
        assertEquals("Comprar pilas", r.title)
        assertEquals(listOf("casa", "urgente"), r.tags)
        assertEquals(today.plusDays(1), r.due)
        // "#2" no es una etiqueta; y una tarea que solo es etiqueta se queda como título.
        assertEquals("Comprar #2 pilas", parse("Comprar #2 pilas").title)
        assertEquals("#casa", parse("#casa").title)
    }
}
