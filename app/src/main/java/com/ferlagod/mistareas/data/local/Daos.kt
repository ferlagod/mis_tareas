// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Query(
        """
        SELECT l.*,
            (SELECT COUNT(*) FROM tasks t WHERE t.list_id = l.id AND t.deleted = 0 AND t.done = 0) AS pending,
            (SELECT COUNT(*) FROM tasks t WHERE t.list_id = l.id AND t.deleted = 0) AS total
        FROM lists l
        WHERE l.deleted = 0
        ORDER BY l.position, l.created_at
        """
    )
    fun observeSummaries(): Flow<List<ListSummary>>

    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY position, created_at")
    fun observeActive(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observe(id: String): Flow<TaskListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: String): TaskListEntity?

    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY position, created_at")
    suspend fun getActive(): List<TaskListEntity>

    /** Todas, incluidas las borradas pendientes de propagar (para sincronizar). */
    @Query("SELECT * FROM lists")
    suspend fun getAll(): List<TaskListEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM lists WHERE deleted = 0")
    suspend fun maxPosition(): Int

    @Upsert
    suspend fun upsert(list: TaskListEntity)

    @Upsert
    suspend fun upsertAll(lists: List<TaskListEntity>)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE list_id = :listId AND deleted = 0")
    fun observeForList(listId: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT t.*, l.name AS list_name, l.color AS list_color, l.read_only AS list_read_only
        FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE t.deleted = 0 AND l.deleted = 0 AND t.done = 0
          AND t.due_day IS NOT NULL AND t.due_day <= :today
        ORDER BY t.due_day, t.due_minutes IS NULL, t.due_minutes, l.position, t.position
        """
    )
    fun observeDueUntil(today: Long): Flow<List<TaskWithList>>

    @Query(
        """
        SELECT t.*, l.name AS list_name, l.color AS list_color, l.read_only AS list_read_only
        FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE t.deleted = 0 AND l.deleted = 0 AND t.done = 0 AND t.priority BETWEEN 1 AND 4
        ORDER BY t.priority, t.due_day IS NULL, t.due_day, l.position, t.position
        """
    )
    fun observeImportant(): Flow<List<TaskWithList>>

    @Query(
        """
        SELECT t.*, l.name AS list_name, l.color AS list_color, l.read_only AS list_read_only
        FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE t.deleted = 0 AND l.deleted = 0
          AND (t.title LIKE '%' || :query || '%' ESCAPE '\' OR t.notes LIKE '%' || :query || '%' ESCAPE '\'
               OR t.tags LIKE '%' || LTRIM(:query, '#') || '%' ESCAPE '\')
        ORDER BY t.done, l.position, t.position
        LIMIT 200
        """
    )
    /** [query] debe llevar escapados `%`, `_` y `\` (ver TaskRepository.search). */
    fun search(query: String): Flow<List<TaskWithList>>

    /**
     * Tareas pendientes que pueden avisar: con recordatorio o con hora límite
     * (para programar las alarmas).
     */
    @Query(
        """
        SELECT t.* FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE (t.reminder_at IS NOT NULL OR (t.due_day IS NOT NULL AND t.due_minutes IS NOT NULL))
          AND t.done = 0 AND t.deleted = 0 AND l.deleted = 0
        """
    )
    fun observeWithReminder(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT t.* FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE (t.reminder_at IS NOT NULL OR (t.due_day IS NOT NULL AND t.due_minutes IS NOT NULL))
          AND t.done = 0 AND t.deleted = 0 AND l.deleted = 0
        """
    )
    suspend fun getWithReminder(): List<TaskEntity>

    /** Pendientes que vencen hoy o antes (resumen diario). */
    @Query(
        """
        SELECT t.*, l.name AS list_name, l.color AS list_color, l.read_only AS list_read_only
        FROM tasks t JOIN lists l ON l.id = t.list_id
        WHERE t.deleted = 0 AND l.deleted = 0 AND t.done = 0
          AND t.due_day IS NOT NULL AND t.due_day <= :today
        ORDER BY t.due_day, t.due_minutes IS NULL, t.due_minutes, l.position, t.position
        """
    )
    suspend fun getDueUntil(today: Long): List<TaskWithList>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    /** Subtareas de una tarea (por el UID de la madre), pendientes primero. */
    @Query("SELECT * FROM tasks WHERE list_id = :listId AND parent_id = :parentUid AND deleted = 0 ORDER BY done, position, created_at")
    fun observeChildren(listId: String, parentUid: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE list_id = :listId AND parent_id = :parentUid AND deleted = 0")
    suspend fun getChildren(listId: String, parentUid: String): List<TaskEntity>

    @Query("SELECT DISTINCT tags FROM tasks WHERE tags IS NOT NULL AND deleted = 0")
    fun observeTagStrings(): Flow<List<String>>

    @Query("SELECT * FROM tasks WHERE list_id = :listId AND id = :uid AND deleted = 0")
    fun observeByUid(listId: String, uid: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<TaskEntity>

    /** Todas las tareas de la lista, incluidas las borradas pendientes de propagar. */
    @Query("SELECT * FROM tasks WHERE list_id = :listId")
    suspend fun getAllForList(listId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE list_id = :listId AND deleted = 0 AND done = 1")
    suspend fun getCompleted(listId: String): List<TaskEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE list_id = :listId")
    suspend fun maxPosition(listId: String): Long

    @Query("SELECT COUNT(*) FROM tasks WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM tasks WHERE list_id = :listId")
    suspend fun hardDeleteForList(listId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category_overrides")
    fun observeAll(): Flow<List<CategoryOverride>>

    @Upsert
    suspend fun upsert(override: CategoryOverride)
}
