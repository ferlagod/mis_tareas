// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Error HTTP devuelto por el servidor. */
open class DavException(val code: Int, message: String) : IOException(message)

/** 401: usuario o contraseña de aplicación incorrectos. */
class DavAuthException(code: Int) : DavException(code, "Authentication failed ($code)")

/** 412: el recurso cambió en el servidor desde la última lectura. */
class DavPreconditionException(url: String) : DavException(412, "Precondition failed: $url")

/** 403/409/412 a un sync-collection: el token ya no vale y hay que hacer una sincronización completa. */
class DavInvalidSyncTokenException(code: Int) : DavException(code, "Invalid sync token ($code)")

/** Resultado de un GET: contenido y ETag. */
class DavFile(val body: String, val etag: String?)

/**
 * Cliente WebDAV/CalDAV mínimo sobre OkHttp (RFC 4918, RFC 4791).
 *
 * Las subidas usan `If-Match` / `If-None-Match` para no pisar nunca un cambio remoto
 * que no se haya visto todavía.
 */
class DavClient(
    username: String,
    password: String,
    shared: OkHttpClient = sharedHttpClient
) {
    private val authHeader = Credentials.basic(username, password, Charsets.UTF_8)
    private val digest = DigestAuthenticator(username, password)
    /** Mismo pool de conexiones que [shared], con respuesta a retos Digest. */
    private val http: OkHttpClient = shared.newBuilder().authenticator(digest).build()

    companion object {
        private const val USER_AGENT = "MisTareas-Android"
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val ICS = "text/calendar; charset=utf-8".toMediaType()
        private val MARKDOWN = "text/markdown; charset=utf-8".toMediaType()

        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun normalizeEtag(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.trim().removePrefix("W/").trim('"').takeIf { it.isNotBlank() }
        }

        /** Resuelve un href del servidor (normalmente una ruta absoluta) contra una URL base. */
        fun resolve(base: String, href: String): String =
            base.toHttpUrl().resolve(href)?.toString() ?: href

        /** Ruta decodificada y sin barra final, para comparar hrefs que llegan con distinta codificación. */
        fun normalizePath(hrefOrUrl: String): String {
            val path = hrefOrUrl.toHttpUrlOrNull()?.encodedPath ?: hrefOrUrl
            return runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path).trimEnd('/')
        }

        fun childUrl(collectionUrl: String, name: String): String {
            val base = collectionUrl.toHttpUrl()
            val builder: HttpUrl.Builder = base.newBuilder()
            // Quita el segmento vacío final de "…/carpeta/" antes de añadir el nombre.
            if (base.pathSegments.lastOrNull() == "") builder.removePathSegment(base.pathSegments.size - 1)
            return builder.addPathSegment(name).build().toString()
        }

        fun ensureSlash(url: String) = if (url.endsWith("/")) url else "$url/"
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", authHeader)
        .header("User-Agent", USER_AGENT)

    private fun Response.checkAuth() {
        if (code == 401) throw DavAuthException(code)
    }

    private suspend fun <T> call(builder: Request.Builder, block: (Response) -> T): T = withContext(Dispatchers.IO) {
        var request = builder.build()
        // Servidor con Digest: se responde directamente sin volver a enviar Basic.
        if (digest.active) digest.authorization(request.method, request.url)?.let { request = request.newBuilder().header("Authorization", it).build() }
        http.newCall(request).execute().use { response ->
            response.checkAuth()
            block(response)
        }
    }

    /** PROPFIND; devuelve null si el recurso no existe (404). */
    suspend fun propfind(url: String, depth: Int, body: String): List<DavResponse>? =
        call(request(url).method("PROPFIND", body.toRequestBody(XML)).header("Depth", depth.toString())) { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> throw DavException(r.code, "PROPFIND $url → ${r.code}")
                else -> DavXml.parseMultistatus(r.body?.string().orEmpty())
            }
        }

    suspend fun report(url: String, depth: Int, body: String): List<DavResponse> =
        call(request(url).method("REPORT", body.toRequestBody(XML)).header("Depth", depth.toString())) { r ->
            if (!r.isSuccessful) throw DavException(r.code, "REPORT $url → ${r.code}")
            DavXml.parseMultistatus(r.body?.string().orEmpty())
        }

    /**
     * Informe `sync-collection` (RFC 6578): cambios desde [token] (null = todo).
     * Devuelve los miembros cambiados (con ETag) o borrados ([DavResponse.isGone]) y el nuevo token.
     */
    suspend fun syncCollection(url: String, token: String?): DavXml.Multistatus {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token>${token?.let(DavXml::escape).orEmpty()}</d:sync-token>
              <d:sync-level>1</d:sync-level>
              <d:prop><d:getetag/></d:prop>
            </d:sync-collection>
        """.trimIndent()
        return call(request(url).method("REPORT", body.toRequestBody(XML)).header("Depth", "0")) { r ->
            when {
                token != null && r.code in setOf(403, 409, 412) -> throw DavInvalidSyncTokenException(r.code)
                !r.isSuccessful -> throw DavException(r.code, "REPORT sync-collection $url → ${r.code}")
                else -> DavXml.parseMultistatusWithToken(r.body?.string().orEmpty())
            }
        }
    }

    /** GET; null si no existe. */
    suspend fun get(url: String): DavFile? = call(request(url).get()) { r ->
        when {
            r.code == 404 -> null
            !r.isSuccessful -> throw DavException(r.code, "GET $url → ${r.code}")
            else -> DavFile(r.body?.string().orEmpty(), normalizeEtag(r.header("OC-ETag") ?: r.header("ETag")))
        }
    }

    /** GET sin autenticación para detectar el tipo de servidor; nunca lanza. */
    suspend fun probe(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
    }

    /**
     * PUT con control de concurrencia.
     * @param ifMatch solo sobrescribe si el ETag remoto coincide.
     * @param ifNoneMatch solo crea si no existe.
     * @return nuevo ETag si el servidor lo devuelve.
     */
    suspend fun put(url: String, content: String, calendar: Boolean, ifMatch: String? = null, ifNoneMatch: Boolean = false): String? {
        val builder = request(url).put(content.toByteArray(Charsets.UTF_8).toRequestBody(if (calendar) ICS else MARKDOWN))
        if (ifMatch != null) builder.header("If-Match", "\"$ifMatch\"") else if (ifNoneMatch) builder.header("If-None-Match", "*")
        return call(builder) { r ->
            if (r.code == 412) throw DavPreconditionException(url)
            if (!r.isSuccessful) throw DavException(r.code, "PUT $url → ${r.code}")
            normalizeEtag(r.header("OC-ETag") ?: r.header("ETag"))
        }
    }

    /** DELETE idempotente (404 cuenta como éxito). */
    suspend fun delete(url: String, ifMatch: String? = null) {
        val builder = request(url).delete()
        if (ifMatch != null) builder.header("If-Match", "\"$ifMatch\"")
        call(builder) { r ->
            if (r.code == 412) throw DavPreconditionException(url)
            if (!r.isSuccessful && r.code != 404) throw DavException(r.code, "DELETE $url → ${r.code}")
        }
    }

    /** Crea una carpeta; 405 (ya existe) cuenta como éxito. */
    suspend fun mkcol(url: String) = call(request(url).method("MKCOL", null)) { r ->
        if (!r.isSuccessful && r.code != 405) throw DavException(r.code, "MKCOL $url → ${r.code}")
    }

    /** Crea un calendario de tareas (solo VTODO). */
    suspend fun mkcalendar(url: String, displayName: String, color: String) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">
              <d:set><d:prop>
                <d:displayname>${DavXml.escape(displayName)}</d:displayname>
                <a:calendar-color>$color</a:calendar-color>
                <c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set>
              </d:prop></d:set>
            </c:mkcalendar>
        """.trimIndent()
        call(request(url).method("MKCALENDAR", body.toRequestBody(XML))) { r ->
            if (!r.isSuccessful) throw DavException(r.code, "MKCALENDAR $url → ${r.code}")
        }
    }

    /**
     * Comparte un calendario (extensión de Nextcloud/ownCloud, la que usa su app Calendario).
     * [principal] es `principal:principals/users/ana` o `principal:principals/groups/familia`.
     */
    suspend fun share(calendarUrl: String, principal: String, canEdit: Boolean) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <o:share xmlns:d="DAV:" xmlns:o="http://owncloud.org/ns">
              <o:set>
                <d:href>${DavXml.escape(principal)}</d:href>
                ${if (canEdit) "<o:read-write/>" else ""}
              </o:set>
            </o:share>
        """.trimIndent()
        call(request(calendarUrl).post(body.toRequestBody(XML))) { r ->
            if (!r.isSuccessful) throw DavException(r.code, "SHARE $calendarUrl → ${r.code}")
        }
    }

    suspend fun unshare(calendarUrl: String, principal: String) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <o:share xmlns:d="DAV:" xmlns:o="http://owncloud.org/ns">
              <o:remove><d:href>${DavXml.escape(principal)}</d:href></o:remove>
            </o:share>
        """.trimIndent()
        call(request(calendarUrl).post(body.toRequestBody(XML))) { r ->
            if (!r.isSuccessful) throw DavException(r.code, "UNSHARE $calendarUrl → ${r.code}")
        }
    }

    /** GET con la cabecera de la API OCS de Nextcloud (búsqueda de usuarios para compartir). */
    suspend fun ocsGet(url: String): String = call(request(url).get().header("OCS-APIRequest", "true").header("Accept", "application/json")) { r ->
        if (!r.isSuccessful) throw DavException(r.code, "OCS $url → ${r.code}")
        r.body?.string().orEmpty()
    }

    /** Cambia nombre y color de un calendario. */
    suspend fun updateCalendar(url: String, displayName: String, color: String) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propertyupdate xmlns:d="DAV:" xmlns:a="http://apple.com/ns/ical/">
              <d:set><d:prop>
                <d:displayname>${DavXml.escape(displayName)}</d:displayname>
                <a:calendar-color>$color</a:calendar-color>
              </d:prop></d:set>
            </d:propertyupdate>
        """.trimIndent()
        call(request(url).method("PROPPATCH", body.toRequestBody(XML))) { r ->
            if (!r.isSuccessful) throw DavException(r.code, "PROPPATCH $url → ${r.code}")
        }
    }
}
