// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddParserTest {

    // Lunes, 9 de noviembre de 2026.
    private val today = LocalDate.of(2026, 11, 9)
    private fun parse(text: String) = QuickAddParser.parse(text, today)

    @Test
    fun plainTextIsUntouched() {
        val r = parse("Leche")
        assertEquals("Leche", r.title)
        assertNull(r.due)
        assertFalse(r.important)
    }

    @Test
    fun relativeDays() {
        assertEquals(today, parse("Llamar a mamá hoy").due)
        assertEquals(today.plusDays(1), parse("Llamar a mamá mañana").due)
        assertEquals(today.plusDays(1), parse("Call mom tomorrow").due)
        assertEquals(today.plusDays(2), parse("Llamar a mamá pasado mañana").due)
        assertEquals("Llamar a mamá", parse("Llamar a mamá pasado mañana").title)
    }

    @Test
    fun weekdaysWithPrefixes() {
        val r = parse("Pagar alquiler para el viernes")
        assertEquals("Pagar alquiler", r.title)
        assertEquals(LocalDate.of(2026, 11, 13), r.due)
        // "lunes" siendo lunes = el lunes que viene.
        assertEquals(LocalDate.of(2026, 11, 16), parse("Reunión el lunes").due)
        assertEquals(LocalDate.of(2026, 11, 14), parse("Gym on Saturday").due)
        assertEquals(LocalDate.of(2026, 11, 11), parse("Dentista miercoles").due)
    }

    @Test
    fun numericDates() {
        assertEquals(LocalDate.of(2026, 11, 15), parse("Revisar informe 15/11").due)
        assertEquals(LocalDate.of(2027, 1, 3), parse("Renovar DNI 3/1").due)
        assertEquals(LocalDate.of(2027, 2, 1), parse("Seguro 1/2/27").due)
        assertNull(parse("Comprar 31/02").due)
    }

    @Test
    fun importance() {
        val r = parse("Pagar factura mañana !")
        assertTrue(r.important)
        assertEquals(today.plusDays(1), r.due)
        assertEquals("Pagar factura", r.title)
        assertTrue(parse("Pagar factura ! mañana").important)
        assertFalse(parse("¡Hola!").important)
    }

    @Test
    fun onlyLooksAtTheEnd() {
        assertNull(parse("Mañana de playa con los niños").due)
        assertEquals("Mañana de playa con los niños", parse("Mañana de playa con los niños").title)
    }

    @Test
    fun neverLeavesAnEmptyTitle() {
        assertEquals("mañana", parse("mañana").title)
        assertNull(parse("mañana").due)
        assertEquals("!", parse("!").title)
    }

    @Test
    fun otherLanguagesDates() {
        assertEquals(today.plusDays(1), parse("Appeler maman demain").due)
        assertEquals(today.plusDays(2), parse("Appeler maman après-demain").due)
        assertEquals(LocalDate.of(2026, 11, 13), parse("Payer le loyer vendredi").due)
        assertEquals(today.plusDays(1), parse("Mama anrufen morgen").due)
        assertEquals(today.plusDays(2), parse("Mama anrufen übermorgen").due)
        assertEquals(LocalDate.of(2026, 11, 13), parse("Miete zahlen am Freitag").due)
        assertEquals("Miete zahlen", parse("Miete zahlen am Freitag").title)
        assertEquals(today.plusDays(1), parse("Chiamare mamma domani").due)
        assertEquals(LocalDate.of(2026, 11, 13), parse("Pagare affitto venerdì").due)
        assertEquals(LocalDate.of(2026, 11, 15), parse("Bericht prüfen 15.11.").due)
    }

    @Test
    fun timesCreateReminders() {
        val now = LocalTime.of(10, 0)

        val es = parse("Llamar a Ana a las 17")
        assertEquals("Llamar a Ana", es.title)
        assertEquals(LocalTime.of(17, 0), es.time)
        assertEquals(today.atTime(17, 0), es.reminder(today, now))

        // Si la hora ya pasó hoy, mañana.
        assertEquals(today.plusDays(1).atTime(9, 0), parse("Regar las plantas a las 9").reminder(today, now))

        val withDate = parse("Dentista mañana a las 9:30 !")
        assertEquals("Dentista", withDate.title)
        assertTrue(withDate.important)
        assertEquals(today.plusDays(1).atTime(9, 30), withDate.reminder(today, now))
        assertEquals(today.plusDays(1).atTime(9, 30), parse("Dentista a las 9:30 mañana").reminder(today, now))

        assertEquals(LocalTime.of(18, 0), parse("Call mom at 6pm").time)
        assertEquals(LocalTime.of(18, 30), parse("Rendez-vous demain à 18h30").time)
        assertEquals(today.plusDays(1), parse("Rendez-vous demain à 18h30").due)
        assertEquals(LocalTime.of(9, 0), parse("Arzt morgen um 9 Uhr").time)
        assertEquals("Arzt", parse("Arzt morgen um 9 Uhr").title)
        assertEquals(LocalTime.of(21, 15), parse("Film alle 21:15").time)
    }

    @Test
    fun numbersThatAreNotTimes() {
        assertNull(parse("Pack de 12").time)
        assertNull(parse("Mesa para 4").time)
        assertNull(parse("Comprar 2 kg").time)
        assertNull(parse("Llamar al 112").time)
        assertEquals("Pack de 12", parse("Pack de 12").title)
    }
}
