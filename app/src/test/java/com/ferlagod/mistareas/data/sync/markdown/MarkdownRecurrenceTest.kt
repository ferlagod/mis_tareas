// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.model.ListColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRecurrenceTest {

    @Test
    fun parsesObsidianRecurrence() {
        val doc = MarkdownCodec.parse("- [ ] Regar plantas 🔁 every 2 days 📅 2026-11-16 🆔 abc\n- [ ] Basura 🔁 every week on Monday, Thursday 🆔 def\n")
        assertEquals("Regar plantas", doc.tasks[0].title)
        assertEquals("FREQ=DAILY;INTERVAL=2", doc.tasks[0].rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH", doc.tasks[1].rrule)
    }

    @Test
    fun unknownRecurrenceStaysInTitle() {
        val task = MarkdownCodec.parse("- [ ] Pagar 🔁 every month on the last Friday 🆔 abc\n").tasks[0]
        assertNull(task.rrule)
        assertTrue(task.title.contains("every month on the last Friday"))
    }

    @Test
    fun roundTrip() {
        val text = MarkdownCodec.write("Casa", ListColor.DEFAULT, listOf(MdTask(id = "a1", title = "Basura", rrule = "FREQ=WEEKLY;BYDAY=MO")))
        assertTrue(text.contains("- [ ] Basura 🔁 every week on Monday 🆔 a1"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", MarkdownCodec.parse(text).tasks[0].rrule)
        assertNull(MarkdownRecurrence.toText("FREQ=MONTHLY;BYDAY=-1FR"))
    }
}
