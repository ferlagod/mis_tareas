// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ICalCodecTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    /** VTODO como los que crea Nextcloud Tasks, con etiquetas, subtarea y aviso. */
    private val nextcloudTodo = listOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Nextcloud Tasks v0.16.1",
        "BEGIN:VTODO",
        "UID:2b5c7e9a-1f3d-4a8b-9c2e-7d6f5e4a3b21",
        "CREATED:20261101T090000Z",
        "LAST-MODIFIED:20261102T100000Z",
        "DTSTAMP:20261102T100000Z",
        "SUMMARY:Comprar regalo\\, envolverlo",
        "DESCRIPTION:Primera línea\\nSegunda línea",
        "CATEGORIES:Personal,Navidad",
        "RELATED-TO:aaaa-bbbb",
        "DUE;TZID=Europe/Madrid:20261124T183000",
        "PRIORITY:5",
        "STATUS:NEEDS-ACTION",
        "X-APPLE-SORT-ORDER:42",
        "BEGIN:VALARM",
        "ACTION:DISPLAY",
        "DESCRIPTION:Recordatorio",
        "TRIGGER:-PT15M",
        "END:VALARM",
        "END:VTODO",
        "END:VCALENDAR"
    ).joinToString("\r\n")

    @Test
    fun parsesNextcloudTodo() {
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        assertEquals("2b5c7e9a-1f3d-4a8b-9c2e-7d6f5e4a3b21", todo.uid)
        assertEquals("Comprar regalo, envolverlo", todo.summary)
        assertEquals("Primera línea\nSegunda línea", todo.description) // No coge la DESCRIPTION del VALARM.
        assertEquals(LocalDate.of(2026, 11, 24).toEpochDay(), todo.dueDay)
        assertEquals(18 * 60 + 30, todo.dueMinutes)
        assertEquals(5, todo.priority)
        assertEquals(42L, todo.sortOrder)
        assertFalse(todo.isDone)
    }

    @Test
    fun patchingKeepsUnknownPropertiesAndAlarm() {
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        val done = todo.copy(status = "COMPLETED", completed = 1_800_000_000_000L)
        val out = ICalCodec.write(done, nextcloudTodo, now = 1_800_000_000_000L, zone = madrid)

        assertTrue(out.contains("CATEGORIES:Personal,Navidad"))
        assertTrue(out.contains("RELATED-TO:aaaa-bbbb"))
        assertTrue(out.contains("DUE;TZID=Europe/Madrid:20261124T183000")) // Sin tocar: no cambió.
        assertTrue(out.contains("STATUS:COMPLETED"))
        assertTrue(out.contains("PERCENT-COMPLETE:100"))
        assertTrue(out.contains("TRIGGER:-PT15M"))
        assertFalse(out.contains("STATUS:NEEDS-ACTION"))

        // Las propiedades nuevas van antes del VALARM (RFC 5545).
        assertTrue(out.indexOf("STATUS:COMPLETED") < out.indexOf("BEGIN:VALARM"))

        val reparsed = ICalCodec.parse(out, madrid)!!
        assertTrue(reparsed.isDone)
        assertEquals("Comprar regalo, envolverlo", reparsed.summary)
    }

    @Test
    fun reopeningRemovesCompletion() {
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        val done = ICalCodec.write(todo.copy(status = "COMPLETED", completed = 1_800_000_000_000L), nextcloudTodo, zone = madrid)
        val reopened = ICalCodec.write(todo.copy(status = "NEEDS-ACTION", completed = null), done, zone = madrid)
        val parsed = ICalCodec.parse(reopened, madrid)!!
        assertFalse(parsed.isDone)
        assertNull(parsed.completed)
        assertFalse(reopened.contains("PERCENT-COMPLETE:100"))
    }

    @Test
    fun buildsNewTodoWithAllDayDue() {
        val todo = VTodo(uid = "abc", summary = "Leche", dueDay = LocalDate.of(2026, 11, 10).toEpochDay(), priority = 1, sortOrder = 3)
        val out = ICalCodec.write(todo, null, now = 0L)
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(out.contains("DUE;VALUE=DATE:20261110"))
        assertTrue(out.contains("STATUS:NEEDS-ACTION"))
        assertTrue(out.contains("PRIORITY:1"))
        val parsed = ICalCodec.parse(out)!!
        assertEquals("Leche", parsed.summary)
        assertNull(parsed.dueMinutes)
    }

    @Test
    fun removingDueDateDropsProperty() {
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        val out = ICalCodec.write(todo.copy(dueDay = null, dueMinutes = null), nextcloudTodo, zone = madrid)
        assertFalse(out.contains("DUE"))
    }

    @Test
    fun foldsLongLinesWithoutBreakingUtf8() {
        val long = "SUMMARY:" + "ñ".repeat(60)
        val folded = ICalCodec.fold(long)
        folded.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertEquals(long, ICalCodec.unfold(folded).single())
    }

    @Test
    fun parsesUtcAndFloatingDates() {
        val ics = "BEGIN:VCALENDAR\nBEGIN:VTODO\nUID:x\nDUE:20261110T230000Z\nCOMPLETED:20261110T080000Z\nEND:VTODO\nEND:VCALENDAR"
        val todo = ICalCodec.parse(ics, madrid)!!
        // 23:00 UTC = 00:00 del día siguiente en Madrid.
        assertEquals(LocalDate.of(2026, 11, 11).toEpochDay(), todo.dueDay)
        assertEquals(0, todo.dueMinutes)
        assertNotNull(todo.completed)
        assertTrue(todo.isDone) // COMPLETED sin STATUS.
    }

    @Test
    fun ignoresEventsWithoutTodo() {
        assertNull(ICalCodec.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:e\nEND:VEVENT\nEND:VCALENDAR"))
    }

    @Test
    fun readsRelativeAndAbsoluteAlarms() {
        // El VALARM de Nextcloud del ejemplo es 15 minutos antes del inicio; sin DTSTART se usa DUE.
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        val due = java.time.ZonedDateTime.of(2026, 11, 24, 18, 30, 0, 0, madrid).toInstant().toEpochMilli()
        assertEquals(due - 15 * 60_000, todo.reminder)

        val absolute = nextcloudTodo.replace("TRIGGER:-PT15M", "TRIGGER;VALUE=DATE-TIME:20261124T080000Z")
        assertEquals(java.time.Instant.parse("2026-11-24T08:00:00Z").toEpochMilli(), ICalCodec.parse(absolute, madrid)!!.reminder)

        val related = nextcloudTodo.replace("TRIGGER:-PT15M", "TRIGGER;RELATED=END:-P1D")
        assertEquals(due - 86_400_000, ICalCodec.parse(related, madrid)!!.reminder)
    }

    @Test
    fun changingReminderReplacesOnlyFirstAlarm() {
        val todo = ICalCodec.parse(nextcloudTodo, madrid)!!
        val at = java.time.Instant.parse("2026-11-20T09:00:00Z").toEpochMilli()
        val out = ICalCodec.write(todo.copy(reminder = at), nextcloudTodo, zone = madrid)
        assertFalse(out.contains("TRIGGER:-PT15M"))
        assertTrue(out.contains("TRIGGER;VALUE=DATE-TIME:20261120T090000Z"))
        assertEquals(1, Regex("BEGIN:VALARM").findAll(out).count())
        assertEquals(at, ICalCodec.parse(out, madrid)!!.reminder)
        assertTrue(out.indexOf("BEGIN:VALARM") < out.indexOf("END:VTODO"))

        // Sin cambiar el aviso, el VALARM original queda intacto.
        val untouched = ICalCodec.write(todo.copy(summary = "Otro"), nextcloudTodo, zone = madrid)
        assertTrue(untouched.contains("TRIGGER:-PT15M"))

        // Quitar el aviso borra el VALARM.
        val removed = ICalCodec.write(todo.copy(reminder = null), nextcloudTodo, zone = madrid)
        assertFalse(removed.contains("VALARM"))
        assertNull(ICalCodec.parse(removed, madrid)!!.reminder)
    }

    @Test
    fun newTodoWithReminder() {
        val at = java.time.Instant.parse("2026-11-10T17:00:00Z").toEpochMilli()
        val out = ICalCodec.write(VTodo(uid = "r", summary = "Llamar", reminder = at), null, now = 0L)
        assertTrue(out.contains("BEGIN:VALARM\r\nACTION:DISPLAY"))
        assertEquals(at, ICalCodec.parse(out)!!.reminder)
    }

    @Test
    fun durations() {
        assertEquals(-15 * 60_000L, ICalCodec.parseDuration("-PT15M"))
        assertEquals(86_400_000L, ICalCodec.parseDuration("P1D"))
        assertEquals(-7 * 86_400_000L, ICalCodec.parseDuration("-P1W"))
        assertEquals(0L, ICalCodec.parseDuration("PT0S"))
    }
}
