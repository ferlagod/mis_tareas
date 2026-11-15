// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCollectionTest {

    @Test
    fun parsesChangedDeletedAndToken() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/cal/a.ics</d:href>
                <d:propstat><d:prop><d:getetag>"e1"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/cal/b.ics</d:href>
                <d:status>HTTP/1.1 404 Not Found</d:status>
              </d:response>
              <d:sync-token>http://sabre.io/ns/sync/42</d:sync-token>
            </d:multistatus>
        """.trimIndent()
        val ms = DavXml.parseMultistatusWithToken(xml)
        assertEquals("http://sabre.io/ns/sync/42", ms.syncToken)
        assertEquals(2, ms.responses.size)
        assertFalse(ms.responses[0].isGone)
        assertEquals("\"e1\"", ms.responses[0].text(Ns.DAV, "getetag"))
        assertTrue(ms.responses[1].isGone)
    }
}
