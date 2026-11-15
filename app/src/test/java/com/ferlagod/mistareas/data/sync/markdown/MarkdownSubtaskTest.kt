// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.model.ListColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownSubtaskTest {

    private val text = """
        # Casa

        - [ ] Mudanza #casa 🆔 m1
            Llamar antes
            - [ ] Cajas 🆔 c1
                Del súper
            - [x] Furgoneta
        - [ ] Otra cosa
    """.trimIndent() + "\n"

    @Test
    fun parsesIndentedSubtasksAndTags() {
        val tasks = MarkdownCodec.parse(text).tasks
        assertEquals(listOf("Mudanza", "Cajas", "Furgoneta", "Otra cosa"), tasks.map { it.title })
        assertEquals(listOf("casa"), tasks[0].tags)
        assertEquals("Llamar antes", tasks[0].notes)
        assertEquals("Del súper", tasks[1].notes)
        assertEquals(listOf(null, 0, 0, null), tasks.map { it.parentIndex })
    }

    @Test
    fun mergeLinksHandwrittenSubtasks() {
        var n = 0
        val merged = MarkdownMerge.merge("l", emptyList(), MarkdownCodec.parse(text).tasks, newId = { "new${n++}" })
        val byTitle = merged.associateBy { it.title }
        assertEquals("m1", byTitle["Cajas"]!!.parentId)
        assertEquals("m1", byTitle["Furgoneta"]!!.parentId)
        assertNull(byTitle["Otra cosa"]!!.parentId)
        assertEquals("casa", byTitle["Mudanza"]!!.tags)
    }

    @Test
    fun writesChildrenUnderParent() {
        val out = MarkdownCodec.write(
            "Casa", ListColor.DEFAULT,
            listOf(
                MdTask(id = "c1", title = "Cajas", parentId = "m1", notes = "Del súper"),
                MdTask(id = "m1", title = "Mudanza", tags = listOf("casa")),
                MdTask(id = "o1", title = "Otra")
            )
        )
        assertEquals(
            "# Casa\n\n- [ ] Mudanza #casa 🆔 m1\n    - [ ] Cajas 🆔 c1\n        Del súper\n- [ ] Otra 🆔 o1\n",
            out
        )
        val again = MarkdownCodec.parse(out).tasks
        assertEquals(listOf(null, 0, null), again.map { it.parentIndex })
    }
}
