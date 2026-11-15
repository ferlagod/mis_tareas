// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** RRULE, DTSTART y DURATION al editar VTODO de otras apps. */
class ICalRecurrenceTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun ics(vararg props: String) = (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:abc") + props +
        listOf("END:VTODO", "END:VCALENDAR")).joinToString("\r\n")

    private fun lines(ics: String) = ICalCodec.unfold(ics)

    @Test
    fun readsAndKeepsRrule() {
        val original = ics("SUMMARY:Basura", "DUE;VALUE=DATE:20261116", "RRULE:FREQ=WEEKLY;BYDAY=MO")
        val todo = ICalCodec.parse(original, madrid)!!
        assertEquals("FREQ=WEEKLY;BYDAY=MO", todo.rrule)
        // Cambiar solo el título no toca la regla.
        val out = ICalCodec.write(todo.copy(summary = "Sacar la basura"), original, zone = madrid)
        assertTrue(lines(out).contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
    }

    @Test
    fun writesNewAndRemovedRrule() {
        val built = ICalCodec.write(VTodo(uid = "x", summary = "Regar", dueDay = LocalDate.of(2026, 11, 16).toEpochDay(), rrule = "FREQ=DAILY"), null, zone = madrid)
        assertTrue(lines(built).contains("RRULE:FREQ=DAILY"))
        val todo = ICalCodec.parse(built, madrid)!!
        val removed = ICalCodec.write(todo.copy(rrule = null), built, zone = madrid)
        assertFalse(lines(removed).any { it.startsWith("RRULE") })
    }

    @Test
    fun startMovesWithDue() {
        val original = ics("SUMMARY:Informe", "DTSTART;VALUE=DATE:20261110", "DUE;VALUE=DATE:20261116")
        val todo = ICalCodec.parse(original, madrid)!!
        // Siguiente repetición: una semana después. El inicio también se mueve una semana.
        val out = ICalCodec.write(todo.copy(dueDay = LocalDate.of(2026, 11, 23).toEpochDay()), original, zone = madrid)
        assertTrue(lines(out).contains("DTSTART;VALUE=DATE:20261117"))
        assertTrue(lines(out).contains("DUE;VALUE=DATE:20261123"))
    }

    @Test
    fun startNeverAfterDue() {
        // La tarea no tenía fecha límite y se le pone una anterior al inicio.
        val original = ics("SUMMARY:Algo", "DTSTART;VALUE=DATE:20261201")
        val todo = ICalCodec.parse(original, madrid)!!
        val out = ICalCodec.write(todo.copy(dueDay = LocalDate.of(2026, 11, 20).toEpochDay()), original, zone = madrid)
        assertTrue(lines(out).contains("DTSTART;VALUE=DATE:20261120"))
    }

    @Test
    fun startTakesDueValueType() {
        val original = ics("SUMMARY:Algo", "DTSTART;VALUE=DATE:20261116", "DUE;VALUE=DATE:20261116")
        val todo = ICalCodec.parse(original, madrid)!!
        val out = ICalCodec.write(todo.copy(dueMinutes = 18 * 60), original, zone = madrid)
        // 00:00 en Madrid (UTC+1) = 23:00Z del día anterior; ambos con hora.
        assertTrue(lines(out).contains("DTSTART:20261115T230000Z"))
        assertTrue(lines(out).contains("DUE:20261116T170000Z"))
    }

    @Test
    fun durationDroppedWhenDueIsSet() {
        val original = ics("SUMMARY:Algo", "DTSTART:20261116T090000Z", "DURATION:PT1H")
        val todo = ICalCodec.parse(original, madrid)!!
        val out = ICalCodec.write(todo.copy(dueDay = LocalDate.of(2026, 11, 17).toEpochDay()), original, zone = madrid)
        assertFalse(lines(out).any { it.startsWith("DURATION") })
    }

    @Test
    fun relativeAlarmFollowsDue() {
        val original = ics(
            "SUMMARY:Cita", "DUE:20261116T100000Z",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:x", "TRIGGER;RELATED=END:-PT15M", "END:VALARM"
        )
        val todo = ICalCodec.parse(original, madrid)!!
        assertEquals(java.time.Instant.parse("2026-11-16T09:45:00Z").toEpochMilli(), todo.reminder)
        val moved = todo.copy(dueDay = LocalDate.of(2026, 11, 17).toEpochDay())
        val out = ICalCodec.write(moved, original, zone = madrid)
        // El VALARM relativo se conserva y el aviso leído del resultado se mueve con la fecha.
        assertTrue(lines(out).contains("TRIGGER;RELATED=END:-PT15M"))
        assertEquals(java.time.Instant.parse("2026-11-17T09:45:00Z").toEpochMilli(), ICalCodec.parse(out, madrid)!!.reminder)
    }
}
