// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DavXmlTest {

    @Test
    fun parsesNextcloudCalendarHome() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:cal="urn:ietf:params:xml:ns:caldav"
                xmlns:cs="http://calendarserver.org/ns/" xmlns:x1="http://apple.com/ns/ical/">
              <d:response>
                <d:href>/remote.php/dav/calendars/ana/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/calendars/ana/tasks/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>
                    <d:displayname>Tareas</d:displayname>
                    <cs:getctag>http://sabre.io/ns/sync/12</cs:getctag>
                    <x1:calendar-color>#0082c9</x1:calendar-color>
                    <cal:supported-calendar-component-set><cal:comp name="VTODO"/></cal:supported-calendar-component-set>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:sync-token/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/calendars/ana/personal/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>
                    <d:displayname>Personal</d:displayname>
                    <cal:supported-calendar-component-set><cal:comp name="VEVENT"/></cal:supported-calendar-component-set>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val responses = DavXml.parseMultistatus(xml)
        assertEquals(3, responses.size)
        assertFalse(responses[0].isType(Ns.CALDAV, "calendar"))

        val tasks = responses[1]
        assertTrue(tasks.isType(Ns.CALDAV, "calendar"))
        assertEquals("Tareas", tasks.text(Ns.DAV, "displayname"))
        assertEquals("http://sabre.io/ns/sync/12", tasks.text(Ns.CS, "getctag"))
        assertEquals("#0082c9", tasks.text(Ns.APPLE, "calendar-color"))
        assertEquals(setOf("VTODO"), tasks.supportedComponents())
        assertNull(tasks.text(Ns.DAV, "sync-token")) // Viene con 404: se ignora.

        assertEquals(setOf("VEVENT"), responses[2].supportedComponents())
    }

    @Test
    fun readsPrincipalHref() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/</d:href>
                <d:propstat><d:prop><d:current-user-principal><d:href>/remote.php/dav/principals/users/ana/</d:href></d:current-user-principal></d:prop>
                <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        assertEquals("/remote.php/dav/principals/users/ana/", DavXml.parseMultistatus(xml).single().hrefIn(Ns.DAV, "current-user-principal"))
    }

    @Test
    fun readsSharingInfo() {
        val xml = """
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.com/ns" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:response><d:href>/remote.php/dav/calendars/ana/compra/</d:href>
                <d:propstat><d:prop>
                  <d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>
                  <oc:owner-principal>principals/users/ana</oc:owner-principal>
                  <d:current-user-privilege-set><d:privilege><d:read/></d:privilege><d:privilege><d:write/></d:privilege></d:current-user-privilege-set>
                  <oc:invite>
                    <oc:user><d:href>principal:principals/users/bob</d:href><oc:common-name>Bob</oc:common-name><oc:invite-accepted/><oc:access><oc:read-write/></oc:access></oc:user>
                    <oc:user><d:href>principal:principals/groups/familia</d:href><oc:invite-accepted/><oc:access><oc:read/></oc:access></oc:user>
                  </oc:invite>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response><d:href>/remote.php/dav/calendars/ana/super_shared_by_carla/</d:href>
                <d:propstat><d:prop>
                  <d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>
                  <oc:owner-principal>principals/users/carla</oc:owner-principal>
                  <nc:owner-displayname>Carla</nc:owner-displayname>
                  <d:current-user-privilege-set><d:privilege><d:read/></d:privilege></d:current-user-privilege-set>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val (mine, shared) = DavXml.parseMultistatus(xml)
        assertTrue(mine.canWrite())
        assertEquals(listOf("Bob", "familia"), mine.sharees().map { it.name })
        assertTrue(mine.sharees()[0].canEdit)
        assertFalse(mine.sharees()[1].canEdit)
        assertTrue(mine.sharees()[1].isGroup)

        assertFalse(shared.canWrite())
        assertEquals("Carla", shared.text(Ns.NC, "owner-displayname"))
        assertEquals("principals/users/carla", shared.text(Ns.OC, "owner-principal"))
    }
}
