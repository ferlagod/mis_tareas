// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Autenticación HTTP Digest (RFC 7616), la que usan por defecto Baïkal y algunos Radicale o
 * SabreDAV. Se envía Basic de entrada; si el servidor pide Digest, se responde al reto y a partir
 * de ahí cada petición lleva ya la cabecera Digest (sin pasar otra vez por el 401).
 */
internal class DigestAuthenticator(
    private val username: String,
    private val password: String,
    private val newCnonce: () -> String = { ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) } }
) : Authenticator {

    private class Challenge(val realm: String, val nonce: String, val qop: String?, val algorithm: String, val opaque: String?)

    private var challenge: Challenge? = null
    private var nonceCount = 0

    val active: Boolean @Synchronized get() = challenge != null

    override fun authenticate(route: Route?, response: Response): Request? {
        val header = response.headers("WWW-Authenticate").firstOrNull { it.trim().startsWith("Digest", ignoreCase = true) }
            ?: return null
        val params = parseParams(header.trim().substring(6))
        val stale = params["stale"].equals("true", ignoreCase = true)
        val previous = response.request.header("Authorization")
        // Ya se respondió a un reto y el servidor lo rechaza: la contraseña no es correcta.
        if (previous != null && previous.startsWith("Digest", ignoreCase = true) && !stale) return null
        if (priorResponses(response) >= 3) return null

        val algorithm = params["algorithm"] ?: "MD5"
        if (hashName(algorithm) == null) return null
        val qop = params["qop"]?.split(',')?.map { it.trim().lowercase() }?.let { if ("auth" in it) "auth" else return null }
        synchronized(this) {
            challenge = Challenge(
                realm = params["realm"] ?: return null,
                nonce = params["nonce"] ?: return null,
                qop = qop,
                algorithm = algorithm,
                opaque = params["opaque"]
            )
            nonceCount = 0
        }
        return response.request.newBuilder().header("Authorization", authorization(response.request.method, response.request.url) ?: return null).build()
    }

    /** Cabecera Digest para una petición, o null si aún no hay reto. */
    @Synchronized
    fun authorization(method: String, url: HttpUrl): String? {
        val c = challenge ?: return null
        val hash = hashName(c.algorithm) ?: return null
        val uri = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val cnonce = newCnonce()
        val nc = "%08x".format(++nonceCount)
        var ha1 = h(hash, "$username:${c.realm}:$password")
        if (c.algorithm.endsWith("-sess", ignoreCase = true)) ha1 = h(hash, "$ha1:${c.nonce}:$cnonce")
        val ha2 = h(hash, "$method:$uri")
        val digest = if (c.qop != null) h(hash, "$ha1:${c.nonce}:$nc:$cnonce:${c.qop}:$ha2") else h(hash, "$ha1:${c.nonce}:$ha2")
        return buildString {
            append("Digest username=\"").append(quote(username)).append('"')
            append(", realm=\"").append(quote(c.realm)).append('"')
            append(", nonce=\"").append(quote(c.nonce)).append('"')
            append(", uri=\"").append(uri).append('"')
            append(", algorithm=").append(c.algorithm)
            append(", response=\"").append(digest).append('"')
            if (c.qop != null) append(", qop=").append(c.qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce).append('"')
            c.opaque?.let { append(", opaque=\"").append(quote(it)).append('"') }
        }
    }

    private fun priorResponses(response: Response): Int {
        var count = 0
        var r = response.priorResponse
        while (r != null) { count++; r = r.priorResponse }
        return count
    }

    private fun quote(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private fun hashName(algorithm: String): String? = when (algorithm.uppercase().removeSuffix("-SESS")) {
            "MD5" -> "MD5"
            "SHA-256" -> "SHA-256"
            else -> null
        }

        private fun h(algorithm: String, value: String): String =
            MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /** `realm="x", nonce="y", qop="auth,auth-int"` → mapa (claves en minúsculas). */
        fun parseParams(text: String): Map<String, String> {
            val result = HashMap<String, String>()
            var i = 0
            while (i < text.length) {
                while (i < text.length && (text[i] == ',' || text[i].isWhitespace())) i++
                val eq = text.indexOf('=', i)
                if (eq < 0) break
                val key = text.substring(i, eq).trim().lowercase()
                i = eq + 1
                val value = StringBuilder()
                if (i < text.length && text[i] == '"') {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\' && i + 1 < text.length) i++
                        value.append(text[i++])
                    }
                    i++
                } else {
                    while (i < text.length && text[i] != ',') value.append(text[i++])
                }
                result[key] = value.toString().trim()
            }
            return result
        }
    }
}
