// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.util.Ids
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fusión de una lista local con su fichero Markdown remoto, tarea a tarea:
 *
 * - Tarea editada en el móvil desde la última sincronización → gana la versión local.
 * - Tarea sin cambios locales → se toma la del servidor.
 * - Borrada en el móvil → se borra también en el fichero.
 * - Desaparecida del fichero y sin cambios locales → se borró en otro dispositivo.
 * - Nueva en el móvil (nunca subida) → se añade al final.
 */
object MarkdownMerge {

    fun merge(
        listId: String,
        local: List<TaskEntity>,
        remote: List<MdTask>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
        newId: () -> String = Ids::newId
    ): List<TaskEntity> {
        // Las tareas movidas a otra lista dejan aquí una marca de borrado "id#moved…".
        // Si hay marca y tarea con el mismo id (se movió y volvió), manda la tarea.
        val localById = HashMap<String, TaskEntity>()
        local.sortedBy { !it.deleted }.forEach { localById[Ids.originalId(it.id)] = it }

        val result = mutableListOf<TaskEntity>()
        val usedLocal = HashSet<String>()
        val seenRemote = HashSet<String>()

        val idByIndex = HashMap<Int, String>()
        for ((index, task) in remote.withIndex()) {
            if (task.title.isBlank() && task.notes.isBlank()) continue
            val id = task.id?.takeIf { seenRemote.add(it) } ?: newId().also { seenRemote.add(it) }
            idByIndex[index] = id
            // La madre de una subtarea escrita a mano se conoce por su posición en el fichero.
            val r = task.copy(parentId = task.parentId ?: task.parentIndex?.let(idByIndex::get))
            val l = localById[id]
            when {
                l == null -> result += fromRemote(listId, id, null, r, zone, now)
                else -> {
                    usedLocal += l.id
                    when {
                        l.deleted && l.dirty -> Unit
                        l.dirty -> result += l.copy(id = id)
                        else -> result += fromRemote(listId, id, l, r, zone, now)
                    }
                }
            }
        }

        local.filter { it.id !in usedLocal && !it.deleted && (!it.synced || it.dirty) }
            .sortedBy { it.position }
            .forEach { result += it }

        return result.mapIndexed { index, task ->
            task.copy(listId = listId, position = index.toLong(), synced = true, dirty = false, deleted = false)
        }
    }

    private fun fromRemote(listId: String, id: String, l: TaskEntity?, r: MdTask, zone: ZoneId, now: Long): TaskEntity {
        val dueDay = r.due?.toEpochDay()
        val completedAt = if (!r.done) null else {
            l?.completedAt?.takeIf { l.done && (r.doneDate == null || r.doneDate == toDate(it, zone)) }
                ?: r.doneDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                ?: now
        }
        val priority = if (l != null && bucket(l.priority) == bucket(r.priority)) l.priority else r.priority
        val base = l ?: TaskEntity(id = id, listId = listId, title = r.title, createdAt = now, updatedAt = now)
        val merged = base.copy(
            id = id,
            listId = listId,
            title = r.title,
            notes = r.notes,
            done = r.done,
            completedAt = completedAt,
            dueDay = dueDay,
            // El Markdown solo guarda la fecha: se conserva la hora local si la fecha no cambió.
            dueMinutes = if (l?.dueDay == dueDay) l?.dueMinutes else null,
            priority = priority,
            // El Markdown guarda el aviso al minuto: se conserva el local si es el mismo minuto.
            reminderAt = r.reminder?.atZone(zone)?.toInstant()?.toEpochMilli()?.let { remote ->
                l?.reminderAt?.takeIf { Math.abs(it - remote) < 60_000 } ?: remote
            },
            // Si el fichero no puede expresar la regla local (personalizada), se conserva la local.
            rrule = r.rrule ?: l?.rrule?.takeIf { MarkdownRecurrence.toText(it) == null },
            parentId = r.parentId,
            tags = TaskEntity.joinTags(r.tags)
        )
        return if (l != null && merged.copy(updatedAt = l.updatedAt) != l) merged.copy(updatedAt = now) else merged
    }

    /** Prioridad tal y como se ve en Markdown (alta, media, baja o ninguna). */
    private fun bucket(priority: Int) = when (priority) {
        in 1..4 -> 1
        5 -> 5
        in 6..9 -> 9
        else -> 0
    }

    fun toMd(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()) = MdTask(
        id = Ids.originalId(task.id),
        title = task.title,
        notes = task.notes,
        done = task.done,
        doneDate = task.completedAt?.let { toDate(it, zone) },
        due = task.dueDate,
        priority = task.priority,
        reminder = task.reminderAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().withSecond(0).withNano(0) },
        // Una regla que el Markdown no sabe escribir se conserva en el móvil.
        rrule = task.rrule,
        tags = task.tagList,
        parentId = task.parentId
    )

    private fun toDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
