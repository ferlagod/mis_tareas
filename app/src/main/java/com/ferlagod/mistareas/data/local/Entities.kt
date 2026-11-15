// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Cómo se ordenan las tareas pendientes de una lista (las completadas van siempre aparte).
 * [CATEGORY] agrupa por secciones del supermercado (para listas de la compra).
 */
enum class SortMode { MANUAL, DUE, ALPHA, CATEGORY }

/**
 * Una lista de tareas ("Compra", "Casa", "Trabajo"…).
 *
 * Campos de sincronización:
 * - [remoteHref]: fichero `.md` (WebDAV) o colección de calendario (CalDAV) asociada.
 * - [remoteTag]: ETag del fichero (WebDAV) o CTag del calendario (CalDAV) en la última sincronización.
 * - [dirty]: nombre o color cambiados localmente y pendientes de subir.
 * - [deleted]: borrada localmente; se conserva hasta propagar el borrado al servidor.
 */
@Entity(tableName = "lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String = "ORANGE",
    val position: Int = 0,
    @ColumnInfo(name = "sort_mode") val sortMode: String = SortMode.MANUAL.name,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "remote_href") val remoteHref: String? = null,
    @ColumnInfo(name = "remote_tag") val remoteTag: String? = null,
    /** WebDAV: último contenido conocido del fichero (para conservar lo que no son tareas). */
    @ColumnInfo(name = "remote_raw") val remoteRaw: String? = null,
    /** Lista compartida conmigo: nombre de quien la comparte (null si es mía). */
    @ColumnInfo(name = "shared_by") val sharedBy: String? = null,
    /** Lista mía compartida: nombres de las personas con acceso, separados por comas. */
    @ColumnInfo(name = "shared_with") val sharedWith: String? = null,
    /** Compartida conmigo sin permiso de edición. */
    @ColumnInfo(name = "read_only", defaultValue = "0") val readOnly: Boolean = false,
    /** CalDAV: token de `sync-collection` (RFC 6578) para bajar solo lo que cambió. */
    @ColumnInfo(name = "sync_token") val syncToken: String? = null,
    val dirty: Boolean = true,
    val deleted: Boolean = false
) {
    val sort: SortMode get() = SortMode.entries.firstOrNull { it.name == sortMode } ?: SortMode.MANUAL

    companion object {
        /** Las listas de la compra se agrupan por secciones del súper; el resto, en orden manual. */
        fun defaultSortFor(name: String): String =
            if (com.ferlagod.mistareas.domain.ShopCategorizer.isShoppingListName(name)) SortMode.CATEGORY.name else SortMode.MANUAL.name
    }
}

/**
 * Una tarea. El [id] es también el UID del VTODO en CalDAV y el `🆔` en Markdown,
 * por eso es una cadena libre (las tareas creadas en otras apps traen su propio UID).
 *
 * - [dueDay]: fecha límite como día de época (`LocalDate.toEpochDay()`), sin zona horaria.
 * - [dueMinutes]: hora de la fecha límite en minutos desde medianoche; null = todo el día.
 * - [priority]: escala iCalendar (0 = sin prioridad, 1 = máxima … 9 = mínima).
 * - [reminderAt]: aviso (instante en milisegundos); se sincroniza como VALARM o `⏰`.
 * - [parentId]: UID de la tarea madre si es una subtarea (RELATED-TO en CalDAV, sangría en Markdown).
 * - [tags]: etiquetas separadas por comas (CATEGORIES en CalDAV, `#etiqueta` en Markdown).
 * - [rrule]: regla de repetición iCalendar ("FREQ=WEEKLY;BYDAY=MO"); se sincroniza como RRULE o `🔁`.
 * - [remoteRaw]: VTODO original del servidor; al subir solo se tocan las propiedades que
 *   gestiona la app, así no se pierden etiquetas, subtareas o avisos creados en otras apps.
 * - [synced]: la tarea llegó a existir en el servidor (distingue "nueva" de "borrada en remoto").
 */
@Entity(
    tableName = "tasks",
    indices = [Index("list_id"), Index("due_day")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "due_day") val dueDay: Long? = null,
    @ColumnInfo(name = "due_minutes") val dueMinutes: Int? = null,
    val priority: Int = 0,
    @ColumnInfo(name = "reminder_at") val reminderAt: Long? = null,
    val rrule: String? = null,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    val tags: String? = null,
    val position: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "remote_href") val remoteHref: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @ColumnInfo(name = "remote_raw") val remoteRaw: String? = null,
    val synced: Boolean = false,
    val dirty: Boolean = true,
    val deleted: Boolean = false
) {
    val important: Boolean get() = priority in 1..4
    val repeats: Boolean get() = !rrule.isNullOrBlank()
    val tagList: List<String> get() = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    /** UID con el que la referencian sus subtareas. */
    val uid: String get() = com.ferlagod.mistareas.util.Ids.originalId(id)

    companion object {
        fun joinTags(tags: Collection<String>): String? =
            tags.map { it.replace(",", " ").trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().joinToString(",").ifEmpty { null }
    }
    val dueDate: LocalDate? get() = dueDay?.let(LocalDate::ofEpochDay)
}

/** Tarea con el nombre y color de su lista (para las vistas "Hoy" e "Importantes"). */
data class TaskWithList(
    @androidx.room.Embedded val task: TaskEntity,
    @ColumnInfo(name = "list_name") val listName: String,
    @ColumnInfo(name = "list_color") val listColor: String
)

/** Resumen de una lista para la pantalla de inicio. */
data class ListSummary(
    @androidx.room.Embedded val list: TaskListEntity,
    val pending: Int,
    val total: Int
)

/**
 * Sección del súper elegida a mano para un producto. [term] es el nombre normalizado
 * ("kefir", "leche de avena"); así la próxima vez que se apunte va a la misma sección.
 */
@Entity(tableName = "category_overrides")
data class CategoryOverride(
    @PrimaryKey val term: String,
    val category: String
)
