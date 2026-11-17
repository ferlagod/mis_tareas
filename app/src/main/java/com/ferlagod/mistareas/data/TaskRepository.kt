// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data

import android.content.Context
import androidx.room.withTransaction
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.local.CategoryOverride
import com.ferlagod.mistareas.domain.ShopCategorizer
import com.ferlagod.mistareas.domain.ShopCategory
import kotlinx.coroutines.flow.map
import com.ferlagod.mistareas.data.local.ListSummary
import com.ferlagod.mistareas.data.local.SortMode
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.local.TaskWithList
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.data.sync.SyncRepository
import com.ferlagod.mistareas.util.Ids
import kotlinx.coroutines.flow.Flow
import com.ferlagod.mistareas.domain.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Operaciones locales sobre listas y tareas. Todo se guarda al instante en el dispositivo;
 * la sincronización con el servidor va detrás, sin bloquear nunca la interfaz.
 */
class TaskRepository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val listDao = db.listDao()
    private val taskDao = db.taskDao()
    private val sync = SyncRepository.getInstance(context)

    private fun now() = System.currentTimeMillis()

    /**
     * Las listas compartidas conmigo sin permiso de edición no se modifican: el servidor
     * rechazaría los cambios y se quedarían pendientes para siempre, distintos de lo que hay allí.
     */
    private suspend fun isReadOnly(listId: String): Boolean = listDao.get(listId)?.readOnly == true

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    fun observeSummaries(): Flow<List<ListSummary>> = listDao.observeSummaries()
    fun observeLists(): Flow<List<TaskListEntity>> = listDao.observeActive()
    fun observeList(id: String): Flow<TaskListEntity?> = listDao.observe(id)
    fun observeTasks(listId: String): Flow<List<TaskEntity>> = taskDao.observeForList(listId)
    fun observeTask(id: String): Flow<TaskEntity?> = taskDao.observe(id)
    fun observeToday(today: LocalDate): Flow<List<TaskWithList>> = taskDao.observeDueUntil(today.toEpochDay())
    fun observeImportant(): Flow<List<TaskWithList>> = taskDao.observeImportant()
    /** Búsqueda literal: `%` y `_` no actúan como comodines de LIKE. */
    fun search(query: String): Flow<List<TaskWithList>> =
        taskDao.search(query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))

    suspend fun getTask(id: String): TaskEntity? = taskDao.get(id)
    suspend fun getActiveLists(): List<TaskListEntity> = listDao.getActive()

    // ------------------------------------------------------------------
    // Listas
    // ------------------------------------------------------------------

    suspend fun createList(name: String, color: ListColor? = null): String {
        val position = listDao.maxPosition() + 1
        val list = TaskListEntity(
            id = Ids.newId(),
            name = name.trim(),
            color = (color ?: ListColor.forIndex(position)).name,
            position = position,
            sortMode = TaskListEntity.defaultSortFor(name)
        )
        listDao.upsert(list)
        sync.requestSync()
        return list.id
    }

    suspend fun updateList(id: String, name: String, color: ListColor) {
        val list = listDao.get(id) ?: return
        if (list.name == name.trim() && list.color == color.name) return
        listDao.upsert(list.copy(name = name.trim(), color = color.name, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /** El orden es una preferencia de este dispositivo: no se sincroniza. */
    suspend fun setSortMode(id: String, mode: SortMode) {
        val list = listDao.get(id) ?: return
        listDao.upsert(list.copy(sortMode = mode.name))
    }

    suspend fun reorderLists(orderedIds: List<String>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id ->
            listDao.get(id)?.let { if (it.position != index) listDao.upsert(it.copy(position = index)) }
        }
    }

    suspend fun deleteList(id: String) {
        db.withTransaction {
            val list = listDao.get(id) ?: return@withTransaction
            taskDao.hardDeleteForList(id)
            if (list.remoteHref == null) listDao.hardDelete(id)
            else listDao.upsert(list.copy(deleted = true, dirty = true, updatedAt = now()))
        }
        sync.requestSync()
    }

    /**
     * Listas de la compra creadas antes de existir las secciones del súper: se pasan a ese
     * orden una sola vez (si luego el usuario elige otro orden, se respeta).
     */
    suspend fun applyShoppingSectionsDefault() = db.withTransaction {
        listDao.getAll().filter { it.sort == SortMode.MANUAL && ShopCategorizer.isShoppingListName(it.name) }
            .forEach { listDao.upsert(it.copy(sortMode = SortMode.CATEGORY.name)) }
    }

    /** Crea las listas de ejemplo la primera vez que se abre la app. */
    suspend fun ensureStarterLists(shopping: String, tasks: String) {
        if (listDao.getAll().isNotEmpty()) return
        listDao.upsertAll(
            listOf(
                TaskListEntity(id = Ids.newId(), name = shopping, color = ListColor.GREEN.name, position = 0, sortMode = SortMode.CATEGORY.name),
                TaskListEntity(id = Ids.newId(), name = tasks, color = ListColor.ORANGE.name, position = 1)
            )
        )
    }

    // ------------------------------------------------------------------
    // Tareas
    // ------------------------------------------------------------------

    suspend fun addTask(
        listId: String,
        title: String,
        due: LocalDate? = null,
        important: Boolean = false,
        notes: String = "",
        reminderAt: Long? = null,
        rrule: String? = null,
        tags: List<String> = emptyList(),
        parentId: String? = null
    ): String {
        val id = Ids.newId()
        db.withTransaction {
            val position = taskDao.maxPosition(listId) + 1
            taskDao.upsert(
                TaskEntity(
                    id = id,
                    listId = listId,
                    title = title.trim(),
                    notes = notes,
                    dueDay = due?.toEpochDay(),
                    priority = if (important) 1 else 0,
                    reminderAt = reminderAt,
                    rrule = rrule,
                    tags = TaskEntity.joinTags(tags),
                    parentId = parentId,
                    position = position
                )
            )
        }
        sync.requestSync()
        return id
    }

    /** Añade varias tareas de golpe (texto pegado o compartido, una por línea). */
    suspend fun addTasks(listId: String, titles: List<String>) {
        if (titles.isEmpty() || isReadOnly(listId)) return
        db.withTransaction {
            var position = taskDao.maxPosition(listId)
            taskDao.upsertAll(titles.map { TaskEntity(id = Ids.newId(), listId = listId, title = it.trim(), position = ++position) })
        }
        sync.requestSync()
    }

    /** Guarda cambios hechos en el detalle de la tarea. */
    suspend fun updateTask(task: TaskEntity) {
        val current = taskDao.get(task.id) ?: return
        if (isReadOnly(current.listId)) return
        val updated = task.copy(
            listId = current.listId, // Cambiar de lista es otra operación (ver moveTask).
            completedAt = when {
                task.done && !current.done -> now()
                !task.done -> null
                else -> current.completedAt
            }
        )
        if (updated == current) return
        taskDao.upsert(updated.copy(updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /**
     * Marca o desmarca una tarea. Si se repite, en vez de completarse pasa a la siguiente fecha
     * (y su aviso se mueve con ella). Devuelve la nueva fecha límite en ese caso.
     */
    suspend fun setDone(id: String, done: Boolean): Advanced? {
        val task = taskDao.get(id) ?: return null
        if (task.done == done || isReadOnly(task.listId)) return null
        if (done && task.repeats) {
            advanceRecurrence(task)?.let { next ->
                taskDao.upsert(next.copy(updatedAt = now(), dirty = true))
                sync.requestSync(delayMs = 3000)
                return Advanced(task, next.dueDate ?: next.reminderAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() })
            }
        }
        db.withTransaction {
            taskDao.upsert(task.copy(done = done, completedAt = if (done) now() else null, updatedAt = now(), dirty = true))
            // Completar una tarea completa también sus subtareas pendientes.
            if (done) descendants(task).filter { !it.done }.forEach {
                taskDao.upsert(it.copy(done = true, completedAt = now(), updatedAt = now(), dirty = true))
            }
        }
        sync.requestSync(delayMs = 3000)
        return null
    }

    /** Se completó una repetición: [previous] es la tarea antes de pasar a [nextDue] (para deshacer). */
    data class Advanced(val previous: TaskEntity, val nextDue: LocalDate?)

    /** Deshace el paso a la siguiente repetición. */
    suspend fun undoAdvance(previous: TaskEntity) {
        val current = taskDao.get(previous.id) ?: return
        taskDao.upsert(previous.copy(remoteHref = current.remoteHref, remoteEtag = current.remoteEtag, remoteRaw = current.remoteRaw, synced = current.synced, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /** Subtareas, nietas… de [task] (en la misma lista). */
    private suspend fun descendants(task: TaskEntity): List<TaskEntity> {
        val out = mutableListOf<TaskEntity>()
        val seen = hashSetOf(task.id)
        var level = listOf(task)
        while (level.isNotEmpty() && out.size < 500) {
            level = level.flatMap { taskDao.getChildren(it.listId, it.uid) }.filter { seen.add(it.id) }
            out += level
        }
        return out
    }

    suspend fun addSubtask(parentId: String, title: String): String? {
        val parent = taskDao.get(parentId) ?: return null
        if (title.isBlank() || isReadOnly(parent.listId)) return null
        return addTask(parent.listId, title, parentId = parent.uid)
    }

    suspend fun setTags(id: String, tags: List<String>) {
        val task = taskDao.get(id) ?: return
        val joined = TaskEntity.joinTags(tags)
        if (task.tags == joined || isReadOnly(task.listId)) return
        taskDao.upsert(task.copy(tags = joined, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /** Todas las etiquetas usadas (para sugerirlas). */
    fun observeAllTags(): Flow<List<String>> = taskDao.observeTagStrings().map { rows ->
        rows.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
    }

    fun observeChildren(task: TaskEntity): Flow<List<TaskEntity>> = taskDao.observeChildren(task.listId, task.uid)
    fun observeParent(task: TaskEntity): Flow<TaskEntity?> =
        task.parentId?.let { taskDao.observeByUid(task.listId, it) } ?: kotlinx.coroutines.flow.flowOf(null)

    /** La tarea en su siguiente repetición, o null si la serie ha terminado. */
    private fun advanceRecurrence(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): TaskEntity? {
        val rule = task.rrule ?: return null
        val today = LocalDate.now(zone)
        val reminder = task.reminderAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val base = task.dueDate ?: reminder?.toLocalDate() ?: today
        val next = Recurrence.next(rule, base, today) ?: return null
        val days = ChronoUnit.DAYS.between(base, next.due)
        return task.copy(
            dueDay = if (task.dueDay != null || reminder == null) next.due.toEpochDay() else null,
            reminderAt = reminder?.plusDays(days)?.toInstant()?.toEpochMilli(),
            rrule = next.rrule
        )
    }

    /** Repetición de la tarea (null = no se repite). Sin fecha límite, se pone hoy como primera. */
    suspend fun setRepeat(id: String, rrule: String?) {
        val task = taskDao.get(id) ?: return
        if (task.rrule == rrule || isReadOnly(task.listId)) return
        val due = if (rrule != null && task.dueDay == null && task.reminderAt == null) LocalDate.now().toEpochDay() else task.dueDay
        taskDao.upsert(task.copy(rrule = rrule, dueDay = due, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /** Hora de la fecha límite en minutos desde medianoche (null = todo el día). */
    suspend fun setDueTime(id: String, minutes: Int?) {
        val task = taskDao.get(id) ?: return
        if (task.dueDay == null || task.dueMinutes == minutes || isReadOnly(task.listId)) return
        taskDao.upsert(task.copy(dueMinutes = minutes, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    suspend fun setImportant(id: String, important: Boolean) {
        val task = taskDao.get(id) ?: return
        if (task.important == important || isReadOnly(task.listId)) return
        taskDao.upsert(task.copy(priority = if (important) 1 else 0, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    suspend fun setDue(id: String, due: LocalDate?) {
        val task = taskDao.get(id) ?: return
        val day = due?.toEpochDay()
        if (task.dueDay == day || isReadOnly(task.listId)) return
        // Al cambiar de día se conserva la hora; sin fecha no hay hora ni repetición.
        taskDao.upsert(
            task.copy(
                dueDay = day,
                dueMinutes = if (day == null) null else task.dueMinutes,
                rrule = if (day == null && task.reminderAt == null) null else task.rrule,
                updatedAt = now(),
                dirty = true
            )
        )
        sync.requestSync()
    }

    suspend fun setReminder(id: String, at: Long?) {
        val task = taskDao.get(id) ?: return
        if (task.reminderAt == at || isReadOnly(task.listId)) return
        taskDao.upsert(task.copy(reminderAt = at, updatedAt = now(), dirty = true))
        sync.requestSync()
    }

    /** Recuerda la sección del súper elegida a mano para este producto. */
    suspend fun setShopCategory(title: String, category: ShopCategory) {
        val term = ShopCategorizer.termFor(title)
        if (term.isNotEmpty()) db.categoryDao().upsert(CategoryOverride(term, category.name))
    }

    fun observeCategoryOverrides(): Flow<Map<String, ShopCategory>> = db.categoryDao().observeAll().map { list ->
        list.mapNotNull { o -> ShopCategory.entries.firstOrNull { it.name == o.category }?.let { o.term to it } }.toMap()
    }

    /**
     * Mueve una tarea a otra lista. En el servidor equivale a borrarla de una y crearla en la
     * otra, así que en la lista de origen queda una marca de borrado.
     */
    suspend fun moveTask(id: String, targetListId: String) {
        db.withTransaction {
            val task = taskDao.get(id) ?: return@withTransaction
            if (task.listId == targetListId || isReadOnly(task.listId) || isReadOnly(targetListId)) return@withTransaction
            // Las subtareas viajan con su madre; una subtarea movida sola deja de serlo.
            val family = listOf(task.copy(parentId = null)) + descendants(task)
            family.forEach { moveOne(it, targetListId) }
        }
        sync.requestSync()
    }

    private suspend fun moveOne(task: TaskEntity, targetListId: String) {
        if (task.synced) {
            taskDao.upsert(task.copy(id = Ids.movedTombstoneId(task.id), deleted = true, dirty = true, updatedAt = now()))
        }
        taskDao.upsert(
            task.copy(
                listId = targetListId,
                position = taskDao.maxPosition(targetListId) + 1,
                remoteHref = null,
                remoteEtag = null,
                synced = false,
                dirty = true,
                updatedAt = now()
            )
        )
    }

    /** Borra tareas; devuelve sus versiones anteriores para poder deshacer. */
    suspend fun deleteTasks(ids: List<String>): List<TaskEntity> {
        val removed = db.withTransaction {
            val roots = taskDao.getAll(ids).filter { !it.deleted && !isReadOnly(it.listId) }
            // Al borrar una tarea se borran también sus subtareas.
            val tasks = (roots + roots.flatMap { descendants(it) }).distinctBy { it.id }.filter { !it.deleted }
            tasks.forEach { task ->
                if (task.synced) taskDao.upsert(task.copy(deleted = true, dirty = true, updatedAt = now()))
                else taskDao.hardDelete(task.id)
            }
            tasks
        }
        sync.requestSync(delayMs = 4000)
        return removed
    }

    suspend fun clearCompleted(listId: String): List<TaskEntity> =
        deleteTasks(taskDao.getCompleted(listId).map { it.id })

    /** Deshace un borrado. */
    suspend fun restoreTasks(tasks: List<TaskEntity>) {
        db.withTransaction {
            tasks.forEach { taskDao.upsert(it.copy(deleted = false, dirty = true, updatedAt = now())) }
        }
        sync.requestSync()
    }

    /** Guarda el nuevo orden manual de las tareas pendientes de una lista. */
    suspend fun reorderTasks(orderedIds: List<String>) {
        db.withTransaction {
            val tasks = taskDao.getAll(orderedIds).filter { !isReadOnly(it.listId) }.associateBy { it.id }
            val positions = orderedIds.mapNotNull { tasks[it]?.position }.sorted()
            orderedIds.forEachIndexed { index, id ->
                val task = tasks[id] ?: return@forEachIndexed
                val position = positions.getOrElse(index) { task.position }
                if (task.position != position) taskDao.upsert(task.copy(position = position, updatedAt = now(), dirty = true))
            }
        }
        sync.requestSync(delayMs = 3000)
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskRepository? = null

        fun getInstance(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
