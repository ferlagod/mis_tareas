// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.util.Ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class MarkdownMergeTest {

    private val zone = ZoneOffset.UTC
    private var counter = 0
    private fun merge(local: List<TaskEntity>, remote: List<MdTask>) =
        MarkdownMerge.merge("L", local, remote, zone, now = 1000L, newId = { "new${counter++}" })

    private fun task(id: String, title: String, synced: Boolean = true, dirty: Boolean = false, deleted: Boolean = false, position: Long = 0) =
        TaskEntity(id = id, listId = "L", title = title, synced = synced, dirty = dirty, deleted = deleted, position = position, updatedAt = 1L, createdAt = 1L)

    @Test
    fun remoteChangesApplyToUntouchedTasks() {
        val result = merge(listOf(task("a", "Leche")), listOf(MdTask("a", "Leche entera")))
        assertEquals(listOf("Leche entera"), result.map { it.title })
        assertTrue(result.all { it.synced && !it.dirty })
    }

    @Test
    fun localEditsWin() {
        val result = merge(listOf(task("a", "Leche desnatada", dirty = true)), listOf(MdTask("a", "Leche entera")))
        assertEquals("Leche desnatada", result.single().title)
    }

    @Test
    fun localDeletionWins() {
        val result = merge(listOf(task("a", "Leche", dirty = true, deleted = true)), listOf(MdTask("a", "Leche")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun remoteDeletionRemovesSyncedTasks() {
        val result = merge(listOf(task("a", "Leche"), task("b", "Pan")), listOf(MdTask("a", "Leche")))
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun newLocalTasksAreKeptAndAppended() {
        val result = merge(
            listOf(task("a", "Leche"), task("n", "Huevos", synced = false, dirty = true, position = 5)),
            listOf(MdTask("a", "Leche"), MdTask(null, "Pan"))
        )
        assertEquals(listOf("Leche", "Pan", "Huevos"), result.map { it.title })
        assertEquals(listOf(0L, 1L, 2L), result.map { it.position })
        assertEquals("new0", result[1].id)
    }

    @Test
    fun movedTombstoneHidesTaskInOldList() {
        val tombstone = task(Ids.movedTombstoneId("a"), "Leche", dirty = true, deleted = true)
        val result = merge(listOf(tombstone), listOf(MdTask("a", "Leche"), MdTask("b", "Pan")))
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun duplicatedRemoteIdsGetNewIds() {
        val result = merge(emptyList(), listOf(MdTask("a", "Leche"), MdTask("a", "Leche (copia)")))
        assertEquals(2, result.map { it.id }.toSet().size)
    }

    @Test
    fun keepsTimeOfDayWhenDateUnchanged() {
        val local = task("a", "Médico").copy(dueDay = 20000, dueMinutes = 600)
        val result = merge(listOf(local), listOf(MdTask("a", "Médico", due = java.time.LocalDate.ofEpochDay(20000))))
        assertEquals(600, result.single().dueMinutes)
    }
}
