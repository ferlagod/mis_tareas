// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import com.ferlagod.mistareas.data.pref.SyncAccount
import com.ferlagod.mistareas.data.pref.SyncMode
import com.ferlagod.mistareas.data.remote.DavAuthException
import com.ferlagod.mistareas.data.remote.DavClient
import com.ferlagod.mistareas.data.remote.DavException
import com.ferlagod.mistareas.data.remote.Ns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/** La dirección del servidor no es válida. */
class InvalidUrlException : IllegalArgumentException("Invalid server URL")

/** No se encontró la carpeta o el servicio de calendarios. */
class ServiceNotFoundException : DavException(404, "Service not found")

/** Comprueba una cuenta y averigua la URL exacta con la que trabajar. */
object AccountConnector {

    suspend fun connect(mode: SyncMode, serverInput: String, username: String, password: String, folder: String): SyncAccount {
        val server = normalizeServer(serverInput)
        val client = DavClient(username.trim(), password.trim())
        val endpoint = when (mode) {
            SyncMode.WEBDAV -> resolveWebDavFolder(client, server, username.trim(), folder)
            SyncMode.CALDAV -> discoverCalendarHome(client, server)
            SyncMode.NONE -> throw IllegalArgumentException("No sync mode")
        }
        return SyncAccount(mode, server, username.trim(), password.trim(), folder.trim().trim('/'), endpoint)
    }

    fun normalizeServer(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) throw InvalidUrlException()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) url = "https://$url"
        url = url.trimEnd('/')
        // Si se pega la URL de la interfaz web de Nextcloud, nos quedamos con la base.
        url = url.substringBefore("/index.php").substringBefore("/apps/")
        if (url.toHttpUrlOrNull() == null) throw InvalidUrlException()
        return url
    }

    /** ¿Es Nextcloud u ownCloud? (tienen `status.php` público) */
    private suspend fun isNextcloud(client: DavClient, server: String): Boolean {
        if (server.contains("/remote.php/")) return false // Ya es una URL WebDAV/CalDAV completa.
        val status = client.probe("$server/status.php") ?: return false
        return status.contains("\"installed\"") && (status.contains("Nextcloud") || status.contains("ownCloud") || status.contains("versionstring"))
    }

    private suspend fun resolveWebDavFolder(client: DavClient, server: String, username: String, folder: String): String {
        val base = if (isNextcloud(client, server)) {
            "$server/remote.php/dav/files/${URLEncoder.encode(username, "UTF-8").replace("+", "%20")}/"
        } else {
            DavClient.ensureSlash(server)
        }
        if (client.propfind(base, 0, PROPFIND_MIN) == null) throw ServiceNotFoundException()

        var url = base
        folder.trim().trim('/').split('/').filter { it.isNotBlank() }.forEach { segment ->
            url = DavClient.ensureSlash(DavClient.childUrl(url, segment.trim()))
            if (client.propfind(url, 0, PROPFIND_MIN) == null) client.mkcol(url)
        }
        return url
    }

    /**
     * Descubrimiento CalDAV (RFC 6764): principal del usuario → `calendar-home-set`.
     * Se prueban la ruta de Nextcloud, `/.well-known/caldav` y la propia URL.
     */
    private suspend fun discoverCalendarHome(client: DavClient, server: String): String {
        val candidates = buildList {
            if (server.contains("/remote.php/")) add(DavClient.ensureSlash(server))
            else {
                add("$server/remote.php/dav/")
                add("$server/.well-known/caldav")
                add(DavClient.ensureSlash(server))
            }
        }
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                val responses = client.propfind(candidate, 0, PROPFIND_PRINCIPAL) ?: continue
                // ¿La URL ya es la colección de calendarios?
                responses.firstOrNull()?.hrefIn(Ns.CALDAV, "calendar-home-set")?.let { return DavClient.ensureSlash(DavClient.resolve(candidate, it)) }
                val principal = responses.firstOrNull()?.hrefIn(Ns.DAV, "current-user-principal") ?: continue
                val principalUrl = DavClient.resolve(candidate, principal)
                val home = client.propfind(principalUrl, 0, PROPFIND_PRINCIPAL)?.firstOrNull()?.hrefIn(Ns.CALDAV, "calendar-home-set") ?: continue
                return DavClient.ensureSlash(DavClient.resolve(principalUrl, home))
            } catch (e: DavAuthException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError is java.io.IOException && lastError !is DavException) throw lastError
        throw ServiceNotFoundException()
    }

    private val PROPFIND_MIN = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
    """.trimIndent()

    private val PROPFIND_PRINCIPAL = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop><d:current-user-principal/><c:calendar-home-set/></d:prop>
        </d:propfind>
    """.trimIndent()
}
