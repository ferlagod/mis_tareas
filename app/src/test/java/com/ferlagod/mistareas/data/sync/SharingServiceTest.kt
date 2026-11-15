// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingServiceTest {

    /** Respuesta real (simplificada) de /ocs/v2.php/apps/files_sharing/api/v1/sharees?itemType=principals */
    private val json = """
        {"ocs":{"meta":{"status":"ok","statuscode":200},"data":{
          "exact":{"users":[{"label":"Bob","shareWithDisplayNameUnique":"bob@example.com","value":{"shareType":0,"shareWith":"bob"}}],"groups":[]},
          "users":[
            {"label":"Bob","value":{"shareType":0,"shareWith":"bob"}},
            {"label":"Ana","value":{"shareType":0,"shareWith":"ana"}},
            {"label":"Bobby","value":{"shareType":0,"shareWith":"bobby"}}
          ],
          "groups":[{"label":"Familia","value":{"shareType":1,"shareWith":"familia"}}]
        }}}
    """.trimIndent()

    @Test
    fun parsesUsersAndGroupsWithoutDuplicatesOrMyself() {
        val result = SharingService.parseCandidates(json, myUser = "ana")
        assertEquals(listOf("Bob", "Bobby", "Familia"), result.map { it.name })
        assertEquals("principal:principals/users/bob", result[0].principal)
        assertEquals("bob@example.com", result[0].detail)
        assertEquals("principal:principals/groups/familia", result[2].principal)
        assertTrue(result[2].isGroup)
    }

    @Test
    fun emptyOrBrokenResponses() {
        assertTrue(SharingService.parseCandidates("""{"ocs":{"data":[]}}""", "ana").isEmpty())
    }
}
