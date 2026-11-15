// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.model.ListColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarkdownCodecTest {

    @Test
    fun parsesObsidianTasksFormat() {
        val doc = MarkdownCodec.parse(
            """
            ---
            color: teal
            tags: [casa]
            ---

            # Compra

            Para el sábado.

            - [ ] Leche ⏫ 📅 2026-11-10 🆔 abc123
                Sin lactosa
                De la marca blanca
            - [x] Pan ✅ 2026-11-09 🆔 def456
            * [ ] Huevos
            """.trimIndent()
        )

        assertEquals("Compra", doc.title)
        assertEquals(ListColor.TEAL, doc.color)
        assertEquals(listOf("color: teal", "tags: [casa]"), doc.frontMatter)
        assertEquals(listOf("Para el sábado."), doc.extra)
        assertEquals(3, doc.tasks.size)

        val milk = doc.tasks[0]
        assertEquals("abc123", milk.id)
        assertEquals("Leche", milk.title)
        assertEquals("Sin lactosa\nDe la marca blanca", milk.notes)
        assertEquals(LocalDate.of(2026, 11, 10), milk.due)
        assertEquals(1, milk.priority)
        assertFalse(milk.done)

        val bread = doc.tasks[1]
        assertTrue(bread.done)
        assertEquals(LocalDate.of(2026, 11, 9), bread.doneDate)

        val eggs = doc.tasks[2]
        assertNull(eggs.id)
        assertEquals("Huevos", eggs.title)
    }

    @Test
    fun roundTripKeepsEverything() {
        val original = """
            ---
            color: teal
            tags: [casa]
            ---

            # Compra

            Para el sábado.

            - [ ] Leche ⏫ 📅 2026-11-10 🆔 abc123
                Sin lactosa
            - [x] Pan ✅ 2026-11-09 🆔 def456

        """.trimIndent()
        val doc = MarkdownCodec.parse(original)
        val written = MarkdownCodec.write("Compra", ListColor.TEAL, doc.tasks, doc)
        assertEquals(original, written)
    }

    @Test
    fun defaultColorWithoutFrontMatterWritesNoFrontMatter() {
        val text = MarkdownCodec.write("Casa", ListColor.DEFAULT, listOf(MdTask(id = "x1", title = "Barrer")))
        assertEquals("# Casa\n\n- [ ] Barrer 🆔 x1\n", text)
    }

    @Test
    fun notesWithBlankLinesSurvive() {
        val text = MarkdownCodec.write(
            "Casa", ListColor.DEFAULT,
            listOf(MdTask(id = "a", title = "Pintar", notes = "Comprar pintura\n\nY rodillo"), MdTask(id = "b", title = "Limpiar"))
        )
        val doc = MarkdownCodec.parse(text)
        assertEquals("Comprar pintura\n\nY rodillo", doc.tasks[0].notes)
        assertEquals("Limpiar", doc.tasks[1].title)
    }

    @Test
    fun cancelledAndInProgressCheckboxes() {
        val doc = MarkdownCodec.parse("- [-] Cancelada\n- [/] En curso")
        assertTrue(doc.tasks[0].done)
        assertFalse(doc.tasks[1].done)
    }

    @Test
    fun safeFileNames() {
        assertEquals("Compra.md", MarkdownCodec.fileNameFor("Compra", emptySet()))
        assertEquals("Compra 2.md", MarkdownCodec.fileNameFor("Compra", setOf("compra.md")))
        assertEquals("Casa trabajo.md", MarkdownCodec.fileNameFor("Casa/trabajo", emptySet()))
        assertEquals("Lista.md", MarkdownCodec.fileNameFor("///", emptySet()))
    }

    @Test
    fun reminders() {
        val doc = MarkdownCodec.parse("- [ ] Dentista 📅 2026-11-10 ⏰ 2026-11-10 09:30 🆔 d1")
        val task = doc.tasks.single()
        assertEquals("Dentista", task.title)
        assertEquals(LocalDate.of(2026, 11, 10).atTime(9, 30), task.reminder)
        assertEquals(LocalDate.of(2026, 11, 10), task.due)
        assertEquals("# L\n\n- [ ] Dentista 📅 2026-11-10 ⏰ 2026-11-10 09:30 🆔 d1\n", MarkdownCodec.write("L", ListColor.DEFAULT, doc.tasks))
    }
}
