// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import android.content.Context
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.pref.SyncAccount
import com.ferlagod.mistareas.data.pref.SyncMode
import com.ferlagod.mistareas.data.pref.SyncPreferences
import com.ferlagod.mistareas.data.remote.DavClient
import com.ferlagod.mistareas.data.remote.DavException
import com.ferlagod.mistareas.data.remote.Sharee
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Persona o grupo de Nextcloud con quien se puede compartir. */
data class ShareCandidate(
    /** `principal:principals/users/ana` o `principal:principals/groups/familia`. */
    val principal: String,
    val name: String,
    /** Dato para distinguir homónimos (correo o usuario). */
    val detail: String?,
    val isGroup: Boolean
)

/** Por qué no se puede compartir una lista. */
enum class SharingUnavailable { NOT_CONNECTED, NOT_NEXTCLOUD, NOT_OWNER }

/**
 * Compartir listas con otras personas del mismo Nextcloud.
 *
 * Cada lista es un calendario de tareas; se usa la compartición de calendarios de Nextcloud
 * (la misma que su app Calendario). La otra persona ve la lista en su Mis Tareas y en
 * Nextcloud Tasks, y los cambios de ambos se sincronizan.
 */
class SharingService private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = SyncPreferences.getInstance(context)
    private val sync = SyncRepository.getInstance(context)

    private fun account(): SyncAccount? = prefs.account.value

    fun unavailableReason(list: TaskListEntity): SharingUnavailable? {
        val account = account() ?: return SharingUnavailable.NOT_CONNECTED
        if (account.mode != SyncMode.CALDAV || !account.endpoint.contains("/remote.php/dav/calendars/")) return SharingUnavailable.NOT_NEXTCLOUD
        if (list.sharedBy != null) return SharingUnavailable.NOT_OWNER
        return null
    }

    private fun client(account: SyncAccount) = DavClient(account.username, account.password)

    /** URL del calendario de la lista; si aún no se ha subido, se sincroniza antes. */
    private suspend fun calendarUrl(listId: String): String {
        db.listDao().get(listId)?.remoteHref?.let { return it }
        sync.synchronize().getOrThrow()
        return db.listDao().get(listId)?.remoteHref ?: throw DavException(404, "List not synced")
    }

    suspend fun sharees(listId: String): List<Sharee> {
        val account = account() ?: return emptyList()
        val url = calendarUrl(listId)
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:prop><oc:invite/></d:prop></d:propfind>
        """.trimIndent()
        val sharees = client(account).propfind(url, 0, body)?.firstOrNull()?.sharees().orEmpty()
        saveSharedWith(listId, sharees)
        return sharees
    }

    suspend fun search(query: String): List<ShareCandidate> {
        val account = account() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        val base = account.serverUrl.substringBefore("/remote.php").trimEnd('/')
        val url = "$base/ocs/v2.php/apps/files_sharing/api/v1/sharees?format=json&perPage=20&itemType=principals&lookup=false&search=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        return parseCandidates(client(account).ocsGet(url), account.username)
    }

    suspend fun share(listId: String, principal: String, canEdit: Boolean): List<Sharee> {
        val account = account() ?: return emptyList()
        client(account).share(calendarUrl(listId), principal, canEdit)
        return sharees(listId)
    }

    suspend fun unshare(listId: String, principal: String): List<Sharee> {
        val account = account() ?: return emptyList()
        client(account).unshare(calendarUrl(listId), principal)
        return sharees(listId)
    }

    private suspend fun saveSharedWith(listId: String, sharees: List<Sharee>) {
        val list = db.listDao().get(listId) ?: return
        val names = sharees.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
        if (list.sharedWith != names) db.listDao().upsert(list.copy(sharedWith = names))
    }

    companion object {
        @Volatile
        private var INSTANCE: SharingService? = null

        fun getInstance(context: Context): SharingService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingService(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * Respuesta de la API de Nextcloud `sharees` (usuarios y grupos, exactos y parecidos),
         * sin repetidos y sin el propio usuario.
         */
        fun parseCandidates(json: String, myUser: String): List<ShareCandidate> {
            val data = JSONObject(json).optJSONObject("ocs")?.optJSONObject("data") ?: return emptyList()
            val result = LinkedHashMap<String, ShareCandidate>()
            fun read(array: JSONArray?, group: Boolean) {
                if (array == null) return
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optJSONObject("value")?.optString("shareWith").orEmpty()
                    if (id.isEmpty() || (!group && id.equals(myUser, ignoreCase = true))) continue
                    val principal = "principal:principals/${if (group) "groups" else "users"}/$id"
                    val label = item.optString("label").ifBlank { id }
                    val detail = item.optString("shareWithDisplayNameUnique").takeIf { it.isNotBlank() && it != label } ?: id.takeIf { it != label }
                    result.putIfAbsent(principal, ShareCandidate(principal, label, detail, group))
                }
            }
            val exact = data.optJSONObject("exact")
            read(exact?.optJSONArray("users"), false)
            read(exact?.optJSONArray("groups"), true)
            read(data.optJSONArray("users"), false)
            read(data.optJSONArray("groups"), true)
            return result.values.toList()
        }
    }
}
