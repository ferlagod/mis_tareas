// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ICalSubtaskTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun ics(vararg props: String) = (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:hija") + props +
        listOf("END:VTODO", "END:VCALENDAR")).joinToString("\r\n")

    @Test
    fun readsParentAndCategories() {
        val todo = ICalCodec.parse(
            ics("SUMMARY:x", "RELATED-TO;RELTYPE=SIBLING:hermana", "RELATED-TO:madre", "CATEGORIES:Casa,Urgente", "CATEGORIES:Coma\\, sí"),
            zone
        )!!
        assertEquals("madre", todo.parentUid)
        assertEquals(listOf("Casa", "Urgente", "Coma, sí"), todo.categories)
    }

    @Test
    fun changingParentKeepsOtherRelations() {
        val original = ics("SUMMARY:x", "RELATED-TO;RELTYPE=SIBLING:hermana", "RELATED-TO;RELTYPE=PARENT:madre")
        val todo = ICalCodec.parse(original, zone)!!
        val out = ICalCodec.unfold(ICalCodec.write(todo.copy(parentUid = "otra"), original, zone = zone))
        assertTrue(out.contains("RELATED-TO;RELTYPE=SIBLING:hermana"))
        assertTrue(out.contains("RELATED-TO:otra"))
        assertFalse(out.any { it.endsWith(":madre") })
        val orphan = ICalCodec.write(todo.copy(parentUid = null), original, zone = zone)
        assertNull(ICalCodec.parse(orphan, zone)!!.parentUid)
        assertTrue(ICalCodec.unfold(orphan).contains("RELATED-TO;RELTYPE=SIBLING:hermana"))
    }

    @Test
    fun writesCategoriesOnlyWhenChanged() {
        val original = ics("SUMMARY:x", "CATEGORIES:B", "CATEGORIES:A")
        val todo = ICalCodec.parse(original, zone)!!
        // Mismo conjunto en otro orden: no se reescribe.
        val same = ICalCodec.unfold(ICalCodec.write(todo.copy(summary = "y", categories = listOf("A", "B")), original, zone = zone))
        assertTrue(same.contains("CATEGORIES:B") && same.contains("CATEGORIES:A"))
        val changed = ICalCodec.unfold(ICalCodec.write(todo.copy(categories = listOf("A", "C, D")), original, zone = zone))
        assertEquals(listOf("CATEGORIES:A,C\\, D"), changed.filter { it.startsWith("CATEGORIES") })
    }
}
