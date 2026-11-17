// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import androidx.room.withTransaction
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.data.remote.DavClient
import com.ferlagod.mistareas.data.remote.DavPreconditionException
import com.ferlagod.mistareas.data.remote.Ns
import com.ferlagod.mistareas.data.sync.markdown.MarkdownCodec
import com.ferlagod.mistareas.data.sync.markdown.MarkdownMerge
import com.ferlagod.mistareas.util.Ids

/**
 * Sincronización con una carpeta WebDAV: un fichero `Nombre de la lista.md` por lista.
 *
 * Cada fichero se compara por ETag: si no cambió en el servidor se sube la versión local;
 * si cambió, se descarga, se fusiona tarea a tarea ([MarkdownMerge]) y se sube el resultado
 * con `If-Match`, de modo que nunca se pisa un cambio hecho en otro dispositivo.
 */
class WebDavSyncEngine(
    private val db: AppDatabase,
    private val client: DavClient,
    folderUrl: String
) : SyncEngine {

    private val folderUrl = DavClient.ensureSlash(folderUrl)
    private val listDao = db.listDao()
    private val taskDao = db.taskDao()

    private class RemoteFile(val name: String, val etag: String?)

    override suspend fun sync() {
        if (client.propfind(folderUrl, 0, PROPFIND_FILES) == null) client.mkcol(folderUrl)

        val folderPath = DavClient.normalizePath(folderUrl)
        val remoteFiles = client.propfind(folderUrl, 1, PROPFIND_FILES).orEmpty()
            .filter { !it.isType(Ns.DAV, "collection") && DavClient.normalizePath(DavClient.resolve(folderUrl, it.href)) != folderPath }
            .map { RemoteFile(DavClient.normalizePath(it.href).substringAfterLast('/'), DavClient.normalizeEtag(it.text(Ns.DAV, "getetag"))) }
            .filter { it.name.endsWith(".md", ignoreCase = true) && !it.name.startsWith(".") }
            .associateBy { it.name.lowercase() }

        val handled = HashSet<String>()

        for (list in listDao.getAll()) {
            val href = list.remoteHref
            if (href == null) {
                if (list.deleted) {
                    deleteLocally(list.id)
                    continue
                }
                // Primera sincronización: si ya hay un fichero con el mismo nombre se fusionan.
                val sameName = remoteFiles[MarkdownCodec.fileNameFor(list.name, emptySet()).lowercase()]
                    ?.takeIf { it.name.lowercase() !in handled }
                if (sameName != null) {
                    handled += sameName.name.lowercase()
                    syncFile(list.copy(remoteHref = sameName.name, remoteTag = null), sameName.etag, adopt = true)
                } else {
                    val name = MarkdownCodec.fileNameFor(list.name, remoteFiles.keys + handled)
                    handled += name.lowercase()
                    syncFile(list.copy(remoteHref = name, remoteTag = null), remoteEtag = null, adopt = true)
                }
                continue
            }

            val remote = remoteFiles[href.lowercase()]
            handled += href.lowercase()
            when {
                list.deleted -> {
                    if (remote != null) client.delete(fileUrl(href))
                    deleteLocally(list.id)
                }
                remote == null -> {
                    val tasks = taskDao.getAllForList(list.id)
                    if (list.dirty || tasks.any { it.dirty }) {
                        // Borrada en otro dispositivo pero editada aquí: se vuelve a crear.
                        syncFile(list.copy(remoteTag = null), remoteEtag = null, adopt = false)
                    } else {
                        deleteLocally(list.id)
                    }
                }
                else -> syncFile(list, remote.etag, adopt = false)
            }
        }

        // Ficheros nuevos creados en otro dispositivo o a mano.
        var position = listDao.maxPosition()
        for ((key, remote) in remoteFiles) {
            if (key in handled) continue
            // Solo llegan ficheros ".md" (en cualquier combinación de mayúsculas).
            val name = remote.name.dropLast(3)
            val list = TaskListEntity(
                id = Ids.newId(),
                name = name,
                sortMode = TaskListEntity.defaultSortFor(name),
                color = ListColor.forIndex(++position).name,
                position = position,
                remoteHref = remote.name,
                remoteTag = null,
                dirty = false
            )
            listDao.upsert(list)
            syncFile(list, remote.etag, adopt = false)
        }
    }

    private fun fileUrl(name: String) = DavClient.childUrl(folderUrl, name)

    private suspend fun deleteLocally(listId: String) = db.withTransaction {
        taskDao.hardDeleteForList(listId)
        listDao.hardDelete(listId)
    }

    /**
     * Sincroniza una lista con su fichero. [remoteEtag] es el ETag del listado (null si el
     * fichero no existe). Con [adopt] la lista aún no estaba asociada al fichero.
     */
    private suspend fun syncFile(listSnapshot: TaskListEntity, remoteEtag: String?, adopt: Boolean) {
        var list = listSnapshot
        var etag = remoteEtag
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                syncFileOnce(list, etag, adopt)
                return
            } catch (e: DavPreconditionException) {
                if (attempt == MAX_ATTEMPTS - 1) throw e
                // Alguien cambió el fichero mientras tanto: se vuelve a leer y fusionar.
                list = listDao.get(list.id)?.copy(remoteHref = list.remoteHref, remoteTag = null) ?: return
                etag = client.get(fileUrl(list.remoteHref!!))?.etag
            }
        }
    }

    private suspend fun syncFileOnce(list: TaskListEntity, remoteEtag: String?, adopt: Boolean) {
        val href = list.remoteHref!!
        val url = fileUrl(href)
        val tasks = taskDao.getAllForList(list.id)
        val localChanged = list.dirty || tasks.any { it.dirty }

        if (remoteEtag != null && remoteEtag == list.remoteTag) {
            // El fichero no cambió en el servidor.
            if (!localChanged) return
            val active = tasks.filter { !it.deleted }.sortedBy { it.position }
            val previous = list.remoteRaw?.let(MarkdownCodec::parse)
            val content = MarkdownCodec.write(list.name, ListColor.fromKey(list.color), active.map { MarkdownMerge.toMd(it) }, previous)
            val newEtag = client.put(url, content, calendar = false, ifMatch = remoteEtag) ?: fetchEtag(url)
            apply(list, tasks, active.mapIndexed { i, t -> t.copy(position = i.toLong(), synced = true, dirty = false) }, list.name, list.color, content, newEtag)
            return
        }

        val file = if (remoteEtag != null) client.get(url) else null
        if (file == null) {
            // Fichero nuevo.
            val active = tasks.filter { !it.deleted }.sortedBy { it.position }
            val content = MarkdownCodec.write(list.name, ListColor.fromKey(list.color), active.map { MarkdownMerge.toMd(it) })
            val newEtag = client.put(url, content, calendar = false, ifNoneMatch = true) ?: fetchEtag(url)
            apply(list, tasks, active.mapIndexed { i, t -> t.copy(position = i.toLong(), synced = true, dirty = false) }, list.name, list.color, content, newEtag)
            return
        }

        val doc = MarkdownCodec.parse(file.body)
        // Al asociar por primera vez, las tareas locales cuentan como nuevas (se suman a las remotas).
        val localForMerge = if (adopt) tasks.map { it.copy(synced = false) } else tasks
        val merged = MarkdownMerge.merge(list.id, localForMerge, doc.tasks)
        val name = if (list.dirty && !adopt) list.name else doc.title?.takeIf { it.isNotBlank() } ?: list.name
        val color = if (list.dirty && !adopt) list.color else doc.color?.name ?: list.color
        val content = MarkdownCodec.write(name, ListColor.fromKey(color), merged.map { MarkdownMerge.toMd(it) }, doc)

        val newEtag = if (normalize(content) != normalize(file.body)) {
            client.put(url, content, calendar = false, ifMatch = file.etag ?: remoteEtag) ?: fetchEtag(url)
        } else {
            file.etag ?: remoteEtag
        }
        apply(list, tasks, merged, name, color, content, newEtag)
    }

    private suspend fun fetchEtag(url: String): String? =
        client.propfind(url, 0, PROPFIND_FILES)?.firstOrNull()?.text(Ns.DAV, "getetag")?.let(DavClient::normalizeEtag)

    private fun normalize(text: String) = text.replace("\r\n", "\n").trimEnd()

    /**
     * Guarda el resultado. Lo que el usuario haya tocado mientras se sincronizaba se respeta
     * (sigue pendiente y se subirá en la siguiente pasada).
     */
    private suspend fun apply(
        listSnapshot: TaskListEntity,
        tasksSnapshot: List<TaskEntity>,
        result: List<TaskEntity>,
        name: String,
        color: String,
        content: String,
        etag: String?
    ) = db.withTransaction {
        val current = listDao.get(listSnapshot.id) ?: return@withTransaction
        val untouched = current.updatedAt == listSnapshot.updatedAt && current.deleted == listSnapshot.deleted
        listDao.upsert(
            if (untouched) current.copy(name = name, color = color, remoteHref = listSnapshot.remoteHref, remoteTag = etag, remoteRaw = content, dirty = false)
            else current.copy(remoteHref = listSnapshot.remoteHref, remoteTag = etag, remoteRaw = content)
        )

        val before = tasksSnapshot.associateBy { it.id }
        val now = taskDao.getAllForList(listSnapshot.id).associateBy { it.id }
        val resultIds = result.mapTo(HashSet()) { it.id }

        for (task in result) {
            val snap = before[task.id]
            val cur = now[task.id]
            if (cur != null && (snap == null || cur.updatedAt != snap.updatedAt || cur.deleted != snap.deleted)) continue
            // Una tarea con este id en otra lista (movida aquí desde otro dispositivo) se sustituye.
            taskDao.upsert(task)
        }
        for (cur in now.values) {
            if (cur.id in resultIds) continue
            val snap = before[cur.id] ?: continue // Creada durante la sincronización.
            if (cur.updatedAt == snap.updatedAt && cur.deleted == snap.deleted) taskDao.hardDelete(cur.id)
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        private val PROPFIND_FILES = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:getetag/><d:resourcetype/><d:getlastmodified/></d:prop>
            </d:propfind>
        """.trimIndent()
    }
}
