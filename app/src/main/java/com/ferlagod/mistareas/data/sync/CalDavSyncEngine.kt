// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.data.remote.DavAuthException
import com.ferlagod.mistareas.data.remote.DavClient
import com.ferlagod.mistareas.data.remote.DavException
import com.ferlagod.mistareas.data.remote.DavInvalidSyncTokenException
import com.ferlagod.mistareas.data.remote.DavPreconditionException
import com.ferlagod.mistareas.data.remote.DavXml
import com.ferlagod.mistareas.data.remote.Ns
import com.ferlagod.mistareas.data.sync.ical.ICalCodec
import com.ferlagod.mistareas.data.sync.ical.VTodo
import com.ferlagod.mistareas.util.Ids
import java.text.Normalizer

/**
 * Sincronización CalDAV (Nextcloud Tasks, Radicale, Baïkal, Fastmail…).
 *
 * - Cada calendario que admite tareas (VTODO) es una lista; las listas nuevas crean un calendario.
 * - El CTag del calendario evita descargar nada si no ha cambiado.
 * - Se descargan solo los `.ics` cuyo ETag cambió (calendar-multiget).
 * - Conflictos: si una tarea cambió en ambos lados gana la modificación más reciente.
 */
class CalDavSyncEngine(
    private val db: AppDatabase,
    private val client: DavClient,
    homeUrl: String
) : SyncEngine {

    private val homeUrl = DavClient.ensureSlash(homeUrl)
    private val listDao = db.listDao()
    private val taskDao = db.taskDao()

    private class RemoteCalendar(
        val url: String,
        val name: String,
        val color: ListColor?,
        val ctag: String?,
        /** Token actual de sync-collection, si el servidor lo admite (RFC 6578). */
        val syncToken: String? = null,
        /** Nombre de quien me la comparte (null si es mía). */
        val sharedBy: String? = null,
        val readOnly: Boolean = false,
        /** Personas con las que la comparto yo. */
        val sharees: List<String> = emptyList()
    ) {
        val sharedWith: String? get() = sharees.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Usuario dueño de la colección de calendarios (".../calendars/<usuario>/"). */
    private val homeUser: String? = DavClient.normalizePath(this.homeUrl).trimEnd('/').substringAfterLast('/').takeIf { it.isNotEmpty() }

    /** Primer error "suave" (p. ej. un calendario de solo lectura); se informa al terminar. */
    private var softError: DavException? = null

    override suspend fun sync() {
        val calendars = client.propfind(homeUrl, 1, PROPFIND_CALENDARS)
            ?: throw DavException(404, "Calendar home not found")
        val remote = calendars
            .filter { it.isType(Ns.CALDAV, "calendar") && (it.supportedComponents()?.contains("VTODO") ?: true) }
            .map { r ->
                val url = DavClient.ensureSlash(DavClient.resolve(homeUrl, r.href))
                // Nextcloud: los calendarios compartidos conmigo tienen otro propietario.
                val owner = r.text(Ns.OC, "owner-principal")?.trimEnd('/')?.substringAfterLast('/')
                val sharedWithMe = owner != null && homeUser != null && !owner.equals(homeUser, ignoreCase = true)
                RemoteCalendar(
                    url = url,
                    name = r.text(Ns.DAV, "displayname") ?: DavClient.normalizePath(url).substringAfterLast('/'),
                    color = ListColor.fromHex(r.text(Ns.APPLE, "calendar-color")),
                    ctag = r.text(Ns.CS, "getctag") ?: r.text(Ns.DAV, "sync-token"),
                    syncToken = r.text(Ns.DAV, "sync-token"),
                    sharedBy = if (sharedWithMe) r.text(Ns.NC, "owner-displayname") ?: owner else null,
                    readOnly = !r.canWrite(),
                    sharees = if (sharedWithMe) emptyList() else r.sharees().map { it.name }
                )
            }
        val byPath = remote.associateBy { DavClient.normalizePath(it.url) }
        val handled = HashSet<String>()

        for (list in listDao.getAll()) {
            val href = list.remoteHref
            if (href == null) {
                if (list.deleted) {
                    deleteLocally(list.id)
                    continue
                }
                // Primera sincronización: un calendario propio con el mismo nombre se reutiliza
                // (nunca uno compartido por otra persona: no se mezclan sus tareas con las mías).
                val sameName = remote.firstOrNull {
                    it.sharedBy == null && !it.readOnly &&
                        it.name.trim().equals(list.name.trim(), ignoreCase = true) && DavClient.normalizePath(it.url) !in handled
                }
                val target = sameName ?: createCalendar(list)
                handled += DavClient.normalizePath(target.url)
                val linked = list.copy(remoteHref = target.url, remoteTag = null, color = target.color?.name ?: list.color)
                saveListMeta(list, linked.copy(dirty = false))
                syncCalendar(linked, target, firstLink = true)
                continue
            }

            val path = DavClient.normalizePath(href)
            val cal = byPath[path]
            handled += path
            when {
                list.deleted -> {
                    if (cal != null) client.delete(cal.url)
                    deleteLocally(list.id)
                }
                // El calendario se borró en el servidor: se borra la lista.
                cal == null -> deleteLocally(list.id)
                else -> {
                    val sharing = list.copy(sharedBy = cal.sharedBy, sharedWith = cal.sharedWith, readOnly = cal.readOnly)
                    if (list.dirty && !cal.readOnly) {
                        client.updateCalendar(cal.url, list.name, ListColor.fromKey(list.color).hex)
                        saveListMeta(list, sharing.copy(dirty = false))
                    } else if (list.dirty || cal.name != list.name || (cal.color != null && cal.color.name != list.color)) {
                        saveListMeta(list, sharing.copy(name = cal.name, color = cal.color?.name ?: list.color, dirty = false))
                    } else if (sharing != list) {
                        saveListMeta(list, sharing)
                    }
                    syncCalendar(listDao.get(list.id) ?: continue, cal, firstLink = false)
                }
            }
        }

        // Calendarios de tareas que aún no están en el dispositivo.
        var position = listDao.maxPosition()
        for ((path, cal) in byPath) {
            if (path in handled) continue
            position++
            val list = TaskListEntity(
                id = Ids.newId(),
                name = cal.name,
                color = (cal.color ?: ListColor.forIndex(position)).name,
                position = position,
                sortMode = TaskListEntity.defaultSortFor(cal.name),
                remoteHref = cal.url,
                sharedBy = cal.sharedBy,
                sharedWith = cal.sharedWith,
                readOnly = cal.readOnly,
                dirty = false
            )
            listDao.upsert(list)
            syncCalendar(list, cal, firstLink = true)
        }

        softError?.let { throw it }
    }

    private suspend fun createCalendar(list: TaskListEntity): RemoteCalendar {
        val base = slug(list.name)
        var url = DavClient.ensureSlash(DavClient.childUrl(homeUrl, base))
        repeat(3) { attempt ->
            try {
                client.mkcalendar(url, list.name, ListColor.fromKey(list.color).hex)
                return RemoteCalendar(url, list.name, ListColor.fromKey(list.color), null)
            } catch (e: DavException) {
                // 405: ya existe un recurso con ese nombre.
                if (e is DavAuthException || e.code != 405 || attempt == 2) throw e
                url = DavClient.ensureSlash(DavClient.childUrl(homeUrl, base + "-" + Ids.newId().take(4)))
            }
        }
        error("unreachable")
    }

    private suspend fun deleteLocally(listId: String) = db.withTransaction {
        taskDao.hardDeleteForList(listId)
        listDao.hardDelete(listId)
    }

    /** Guarda nombre/color/enlace sin pisar un cambio hecho por el usuario durante la sincronización. */
    private suspend fun saveListMeta(snapshot: TaskListEntity, updated: TaskListEntity) = db.withTransaction {
        val current = listDao.get(snapshot.id) ?: return@withTransaction
        if (current.updatedAt == snapshot.updatedAt && current.deleted == snapshot.deleted) {
            listDao.upsert(updated.copy(sortMode = current.sortMode, position = current.position))
        } else {
            listDao.upsert(current.copy(remoteHref = updated.remoteHref))
        }
    }

    private suspend fun syncCalendar(list: TaskListEntity, cal: RemoteCalendar, firstLink: Boolean) {
        // 1) Bajar cambios (si el CTag cambió o la lista acaba de enlazarse).
        var pulled = false
        var token: String? = null
        if (firstLink || cal.ctag == null || cal.ctag != list.remoteTag) {
            token = pull(list, cal, firstLink)
            pulled = true
        }
        // 2) Subir cambios locales (en una lista de solo lectura no hay nada que subir).
        if (!cal.readOnly) push(list, cal)
        // 3) Recordar el CTag y el token vistos (lo que hayamos subido vendrá en el próximo informe
        //    con el ETag que ya conocemos, así que no se vuelve a descargar).
        db.withTransaction {
            listDao.get(list.id)?.let { listDao.upsert(it.copy(remoteTag = cal.ctag, syncToken = if (pulled) token else it.syncToken)) }
        }
    }

    /**
     * Baja los cambios del calendario. Con token de sync-collection solo se pregunta por lo que
     * cambió desde la última vez; si no lo hay o el servidor lo rechaza, se comparan todos los ETags.
     * Devuelve el token desde el que preguntar la próxima vez (null = siempre completa).
     */
    private suspend fun pull(list: TaskListEntity, cal: RemoteCalendar, firstLink: Boolean): String? {
        val previous = list.syncToken
        if (!firstLink && previous != null && cal.syncToken != null) {
            try {
                return pullIncremental(list, cal, previous)
            } catch (e: DavInvalidSyncTokenException) {
                Log.i(TAG, "Token de sincronización caducado; sincronización completa", e)
            } catch (e: DavException) {
                if (e is DavAuthException || e.code !in setOf(400, 403, 404, 405, 501)) throw e
                Log.i(TAG, "sync-collection no disponible; sincronización completa", e)
            }
        }
        pullFull(list, cal, firstLink)
        // El token leído antes de la descarga completa: lo que cambie después saldrá en el próximo informe.
        return cal.syncToken
    }

    private suspend fun pullFull(list: TaskListEntity, cal: RemoteCalendar, firstLink: Boolean) {
        val calPath = DavClient.normalizePath(cal.url)
        val remoteEtags = client.report(cal.url, 1, CALENDAR_QUERY_ETAGS)
            .map { DavClient.resolve(cal.url, it.href) to DavClient.normalizeEtag(it.text(Ns.DAV, "getetag")) }
            .filter { (url, _) -> DavClient.normalizePath(url) != calPath }
            .associate { (url, etag) -> DavClient.normalizePath(url) to (url to etag) }

        val local = taskDao.getAllForList(list.id)
        val localByPath = local.filter { it.remoteHref != null }.associateBy { DavClient.normalizePath(it.remoteHref!!) }

        // Borradas en el servidor.
        removeDeletedRemotely(localByPath.filterKeys { it !in remoteEtags }.values)
        fetchChanged(list, cal, local, localByPath, remoteEtags, firstLink)
    }

    private suspend fun pullIncremental(list: TaskListEntity, cal: RemoteCalendar, startToken: String): String? {
        val calPath = DavClient.normalizePath(cal.url)
        val changed = LinkedHashMap<String, Pair<String, String?>>()
        val gone = HashSet<String>()
        var token: String? = startToken
        // Un servidor puede trocear la respuesta (507 sobre la propia colección): se sigue pidiendo.
        for (round in 0 until 20) {
            val result = client.syncCollection(cal.url, token)
            var truncated = false
            for (r in result.responses) {
                val url = DavClient.resolve(cal.url, r.href)
                val path = DavClient.normalizePath(url)
                if (path == calPath) {
                    if (r.status?.contains(" 507") == true) truncated = true
                    continue
                }
                if (r.isGone) {
                    gone += path
                    changed.remove(path)
                } else {
                    gone -= path
                    changed[path] = url to DavClient.normalizeEtag(r.text(Ns.DAV, "getetag"))
                }
            }
            val next = result.syncToken
            if (next == null) {
                // Servidor que no devuelve token: no se puede seguir en incremental.
                pullFull(list, cal, firstLink = false)
                return null
            }
            token = next
            if (!truncated) break
        }
        if (changed.isEmpty() && gone.isEmpty()) return token

        val local = taskDao.getAllForList(list.id)
        val localByPath = local.filter { it.remoteHref != null }.associateBy { DavClient.normalizePath(it.remoteHref!!) }
        removeDeletedRemotely(gone.mapNotNull { localByPath[it] })
        fetchChanged(list, cal, local, localByPath, changed, firstLink = false)
        return token
    }

    /** Tareas que ya no están en el servidor. */
    private suspend fun removeDeletedRemotely(tasks: Collection<TaskEntity>) {
        for (task in tasks) {
            updateIfUnchanged(task) { current ->
                if (current.dirty && !current.deleted) {
                    // Editada aquí y borrada allí: se vuelve a crear.
                    taskDao.upsert(current.copy(remoteHref = null, remoteEtag = null, synced = false))
                } else {
                    taskDao.hardDelete(current.id)
                }
            }
        }
    }

    /** Descarga (calendar-multiget) los `.ics` cuyo ETag no coincide con el que se tiene. */
    private suspend fun fetchChanged(
        list: TaskListEntity,
        cal: RemoteCalendar,
        local: List<TaskEntity>,
        localByPath: Map<String, TaskEntity>,
        remoteEtags: Map<String, Pair<String, String?>>,
        firstLink: Boolean
    ) {
        val toFetch = remoteEtags.filter { (path, pair) -> localByPath[path]?.remoteEtag != pair.second || pair.second == null }
        if (toFetch.isEmpty()) return

        val localById = local.associateBy { Ids.originalId(it.id) }
        toFetch.values.map { it.first }.chunked(MULTIGET_BATCH).forEach { urls ->
            val body = buildString {
                append("""<?xml version="1.0" encoding="utf-8"?>""")
                append("""<c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">""")
                append("<d:prop><d:getetag/><c:calendar-data/></d:prop>")
                urls.forEach { append("<d:href>").append(DavXml.escape(it.toHttpPath())).append("</d:href>") }
                append("</c:calendar-multiget>")
            }
            for (response in client.report(cal.url, 1, body)) {
                val data = response.text(Ns.CALDAV, "calendar-data") ?: continue
                val todo = ICalCodec.parse(data) ?: continue
                val url = DavClient.resolve(cal.url, response.href)
                val etag = DavClient.normalizeEtag(response.text(Ns.DAV, "getetag"))
                val existing = localByPath[DavClient.normalizePath(url)] ?: localById[todo.uid]
                applyRemote(list.id, existing, todo, url, etag, data, preferRemoteOnTie = !firstLink)
            }
        }
    }

    /** Guarda una tarea descargada, resolviendo conflictos con la versión local. */
    private suspend fun applyRemote(
        listId: String,
        existing: TaskEntity?,
        todo: VTodo,
        url: String,
        etag: String?,
        raw: String,
        preferRemoteOnTie: Boolean
    ) = db.withTransaction {
        val current = existing?.let { taskDao.get(it.id) }?.takeIf { it.listId == listId }
        if (existing == null || current == null) {
            // Nueva. Si otra lista ya usa ese UID (mismo VTODO en dos calendarios), se usa un alias.
            val clash = taskDao.get(todo.uid)
            val id = if (clash == null) todo.uid else Ids.movedTombstoneId(todo.uid)
            taskDao.upsert(fromRemote(listId, id, null, todo, url, etag, raw))
            return@withTransaction
        }
        if (existing.updatedAt != current.updatedAt) return@withTransaction // Se está editando ahora mismo.

        if (current.dirty) {
            val remoteNewer = (todo.lastModified ?: 0L) > current.updatedAt ||
                (preferRemoteOnTie && (todo.lastModified ?: 0L) == current.updatedAt)
            if (current.deleted || !remoteNewer) {
                // Gana la versión local: se sube después sobre el VTODO más reciente.
                taskDao.upsert(current.copy(remoteHref = url, remoteEtag = etag, remoteRaw = raw))
                return@withTransaction
            }
        }
        taskDao.upsert(fromRemote(listId, current.id, current, todo, url, etag, raw))
    }

    private suspend fun push(list: TaskListEntity, cal: RemoteCalendar) {
        for (task in taskDao.getAllForList(list.id).filter { it.dirty }) {
            try {
                pushTask(list, cal, task)
            } catch (e: DavException) {
                if (e is DavAuthException) throw e
                Log.w(TAG, "No se pudo subir la tarea ${task.id}", e)
                if (softError == null) softError = e
            }
        }
    }

    private suspend fun pushTask(list: TaskListEntity, cal: RemoteCalendar, task: TaskEntity) {
        if (task.deleted) {
            task.remoteHref?.let { href ->
                val url = DavClient.resolve(cal.url, href)
                val tombstone = Ids.originalId(task.id) != task.id // Marca de una tarea movida a otra lista.
                try {
                    client.delete(url, ifMatch = task.remoteEtag)
                } catch (e: DavPreconditionException) {
                    // Otro dispositivo la cambió después de que la viéramos: ese cambio gana y la tarea vuelve.
                    val remote = if (tombstone) null else client.get(url)
                    val remoteTodo = remote?.body?.let(ICalCodec::parse)
                    if (remote != null && remoteTodo != null) {
                        updateIfUnchanged(task) { current ->
                            taskDao.upsert(fromRemote(list.id, current.id, current, remoteTodo, url, remote.etag, remote.body))
                        }
                        return
                    }
                    // Marca de movimiento o versión remota ilegible: se borra sin condiciones
                    // (si el GET dio 404, ya no existe y no hay nada que borrar).
                    if (tombstone || remote != null) client.delete(url)
                }
            }
            updateIfUnchanged(task) { taskDao.hardDelete(it.id) }
            return
        }

        val isNew = task.remoteHref == null
        val url = task.remoteHref?.let { DavClient.resolve(cal.url, it) }
            ?: DavClient.childUrl(cal.url, Ids.originalId(task.id) + ".ics")
        var raw = ICalCodec.write(toVTodo(task), task.remoteRaw)
        val etag = try {
            client.put(url, raw, calendar = true, ifMatch = if (isNew) null else task.remoteEtag, ifNoneMatch = isNew)
        } catch (e: DavPreconditionException) {
            // Cambió en el servidor después de leerlo: gana lo más reciente.
            val remote = client.get(url)
            val remoteTodo = remote?.body?.let(ICalCodec::parse)
            if (remote != null && remoteTodo != null && (remoteTodo.lastModified ?: 0L) > task.updatedAt) {
                db.withTransaction {
                    val current = taskDao.get(task.id)
                    if (current != null && current.updatedAt == task.updatedAt) {
                        taskDao.upsert(fromRemote(list.id, task.id, current, remoteTodo, url, remote.etag, remote.body))
                    }
                }
                return
            }
            raw = ICalCodec.write(toVTodo(task), remote?.body ?: task.remoteRaw)
            client.put(url, raw, calendar = true, ifMatch = remote?.etag, ifNoneMatch = remote == null)
        }

        // Un aviso relativo de otra app ("15 min antes") se ha movido con la fecha límite: se recalcula
        // aquí para que la alarma del móvil coincida con la del servidor.
        val written = ICalCodec.parse(raw)
        updateIfUnchanged(task) { current ->
            taskDao.upsert(
                current.copy(
                    remoteHref = url, remoteEtag = etag, remoteRaw = raw, synced = true, dirty = false,
                    reminderAt = if (written != null) written.reminder else current.reminderAt
                )
            )
        }
    }

    /** Ejecuta [block] solo si la tarea no se ha tocado desde [snapshot]. */
    private suspend fun updateIfUnchanged(snapshot: TaskEntity, block: suspend (TaskEntity) -> Unit) = db.withTransaction {
        val current = taskDao.get(snapshot.id) ?: return@withTransaction
        if (current.updatedAt == snapshot.updatedAt && current.deleted == snapshot.deleted) block(current)
    }

    private fun toVTodo(task: TaskEntity) = VTodo(
        uid = Ids.originalId(task.id),
        summary = task.title,
        description = task.notes,
        status = if (task.done) "COMPLETED" else "NEEDS-ACTION",
        completed = if (task.done) task.completedAt ?: task.updatedAt else null,
        dueDay = task.dueDay,
        dueMinutes = task.dueMinutes,
        priority = task.priority,
        sortOrder = task.position,
        created = task.createdAt,
        lastModified = task.updatedAt,
        reminder = task.reminderAt,
        rrule = task.rrule,
        parentUid = task.parentId,
        categories = task.tagList
    )

    private fun fromRemote(
        listId: String,
        id: String,
        local: TaskEntity?,
        todo: VTodo,
        url: String,
        etag: String?,
        raw: String
    ): TaskEntity {
        val now = System.currentTimeMillis()
        return TaskEntity(
            id = id,
            listId = listId,
            title = todo.summary,
            notes = todo.description,
            done = todo.isDone,
            completedAt = if (todo.isDone) todo.completed ?: local?.completedAt ?: todo.lastModified ?: now else null,
            dueDay = todo.dueDay,
            dueMinutes = todo.dueMinutes,
            priority = todo.priority,
            reminderAt = todo.reminder,
            rrule = todo.rrule,
            parentId = todo.parentUid,
            tags = TaskEntity.joinTags(todo.categories),
            position = todo.sortOrder ?: local?.position ?: ((todo.created ?: now) / 1000),
            createdAt = todo.created ?: local?.createdAt ?: now,
            updatedAt = todo.lastModified ?: now,
            remoteHref = url,
            remoteEtag = etag,
            remoteRaw = raw,
            synced = true,
            dirty = false,
            deleted = false
        )
    }

    private fun String.toHttpPath(): String =
        runCatching { java.net.URI(this).rawPath }.getOrNull() ?: this

    private fun slug(name: String): String =
        Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(32)
            .ifBlank { "lista" }

    companion object {
        private const val TAG = "CalDavSync"
        private const val MULTIGET_BATCH = 40

        private val PROPFIND_CALENDARS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/"
                xmlns:a="http://apple.com/ns/ical/" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.com/ns">
              <d:prop>
                <d:resourcetype/>
                <d:current-user-privilege-set/>
                <oc:owner-principal/>
                <nc:owner-displayname/>
                <oc:invite/>
                <d:displayname/>
                <d:sync-token/>
                <cs:getctag/>
                <a:calendar-color/>
                <c:supported-calendar-component-set/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val CALENDAR_QUERY_ETAGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/></d:prop>
              <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VTODO"/></c:comp-filter></c:filter>
            </c:calendar-query>
        """.trimIndent()
    }
}
