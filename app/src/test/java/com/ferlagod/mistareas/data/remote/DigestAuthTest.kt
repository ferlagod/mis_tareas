// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestAuthTest {

    private val challenge = "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
        "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""

    private fun unauthorized(request: Request, header: String = challenge) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
        .header("WWW-Authenticate", header).build()

    @Test
    fun answersRfc2617Example() {
        val auth = DigestAuthenticator("Mufasa", "Circle Of Life") { "0a4f113b" }
        val request = Request.Builder().url("http://www.nowhere.org/dir/index.html").build()
        val retry = auth.authenticate(null, unauthorized(request))!!
        val header = retry.header("Authorization")!!
        assertTrue(header.startsWith("Digest "))
        // Respuesta del ejemplo de RFC 2617 §3.5.
        assertTrue(header, header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(header.contains("nc=00000001"))
    }

    @Test
    fun givesUpWhenDigestIsRejected() {
        val auth = DigestAuthenticator("u", "wrong") { "1" }
        val request = Request.Builder().url("http://x/dav/").header("Authorization", "Digest username=\"u\"").build()
        assertNull(auth.authenticate(null, unauthorized(request)))
    }

    @Test
    fun ignoresBasicChallenges() {
        val auth = DigestAuthenticator("u", "p")
        val request = Request.Builder().url("http://x/dav/").build()
        assertNull(auth.authenticate(null, unauthorized(request, "Basic realm=\"x\"")))
    }

    @Test
    fun parsesParams() {
        val p = DigestAuthenticator.parseParams("realm=\"a, b\", nonce=xyz, stale=TRUE")
        assertEquals("a, b", p["realm"])
        assertEquals("xyz", p["nonce"])
        assertEquals("TRUE", p["stale"])
    }
}
