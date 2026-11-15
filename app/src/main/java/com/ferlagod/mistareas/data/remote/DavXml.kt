// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.remote

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Espacios de nombres de WebDAV / CalDAV. */
object Ns {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val CS = "http://calendarserver.org/ns/"
    const val APPLE = "http://apple.com/ns/ical/"
    const val OC = "http://owncloud.org/ns"
    const val NC = "http://nextcloud.com/ns"
}

/** Persona con acceso a un calendario compartido (Nextcloud). */
data class Sharee(
    /** `principal:principals/users/ana` o `principal:principals/groups/familia`. */
    val href: String,
    val name: String,
    val canEdit: Boolean,
    val isGroup: Boolean = href.contains("/groups/")
)

/** Un `<d:response>` de un multistatus: href y propiedades con estado 200. */
class DavResponse(
    val href: String,
    private val props: Map<String, Element>,
    /** `<d:status>` directo de la respuesta (p. ej. "HTTP/1.1 404 Not Found" en un sync-collection). */
    val status: String? = null
) {

    /** El recurso ya no existe (miembro borrado en un informe sync-collection). */
    val isGone: Boolean get() = status?.contains(" 404") == true

    fun prop(ns: String, name: String): Element? = props["{$ns}$name"]

    fun text(ns: String, name: String): String? = prop(ns, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    fun has(ns: String, name: String): Boolean = props.containsKey("{$ns}$name")

    /** ¿El `resourcetype` contiene `<ns:name/>`? */
    fun isType(ns: String, name: String): Boolean =
        prop(Ns.DAV, "resourcetype")?.children()?.any { it.namespaceURI == ns && it.localName == name } == true

    /** Primer `<d:href>` dentro de la propiedad (current-user-principal, calendar-home-set…). */
    fun hrefIn(ns: String, name: String): String? =
        prop(ns, name)?.let { el -> el.descendants().firstOrNull { it.namespaceURI == Ns.DAV && it.localName == "href" } }
            ?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** ¿Tengo permiso de escritura? (`current-user-privilege-set`; si el servidor no lo dice, sí) */
    fun canWrite(): Boolean {
        val set = prop(Ns.DAV, "current-user-privilege-set") ?: return true
        return set.descendants().any { it.namespaceURI == Ns.DAV && it.localName in setOf("write", "write-content", "all", "bind") }
    }

    /** Personas con las que está compartido (propiedad `oc:invite` de Nextcloud). */
    fun sharees(): List<Sharee> =
        prop(Ns.OC, "invite")?.children()?.filter { it.localName == "user" }?.mapNotNull { user ->
            val href = user.children().firstOrNull { it.localName == "href" }?.textContent?.trim() ?: return@mapNotNull null
            val name = user.children().firstOrNull { it.localName == "common-name" }?.textContent?.trim()
                ?.takeIf { it.isNotEmpty() } ?: href.trimEnd('/').substringAfterLast('/')
            val canEdit = user.children().firstOrNull { it.localName == "access" }?.children()?.any { it.localName == "read-write" } == true
            Sharee(href, name, canEdit)
        }.orEmpty()

    /** Componentes admitidos por un calendario (VEVENT, VTODO…); null si el servidor no lo dice. */
    fun supportedComponents(): Set<String>? =
        prop(Ns.CALDAV, "supported-calendar-component-set")?.children()
            ?.filter { it.localName == "comp" }
            ?.mapNotNull { it.getAttribute("name")?.uppercase() }
            ?.toSet()
}

object DavXml {

    /** Multistatus con el `<d:sync-token>` de la raíz (informe sync-collection). */
    class Multistatus(val responses: List<DavResponse>, val syncToken: String?)

    fun parseMultistatus(xml: String): List<DavResponse> = parseMultistatusWithToken(xml).responses

    fun parseMultistatusWithToken(xml: String): Multistatus {
        if (xml.isBlank()) return Multistatus(emptyList(), null)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement ?: return Multistatus(emptyList(), null)
        val token = root.children().firstOrNull { it.isDav("sync-token") }?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        val responses = root.children().filter { it.isDav("response") }.mapNotNull { response ->
            val href = response.children().firstOrNull { it.isDav("href") }?.textContent?.trim() ?: return@mapNotNull null
            val props = HashMap<String, Element>()
            response.children().filter { it.isDav("propstat") }.forEach { propstat ->
                val status = propstat.children().firstOrNull { it.isDav("status") }?.textContent.orEmpty()
                if (status.isNotBlank() && !status.contains(" 200")) return@forEach
                propstat.children().filter { it.isDav("prop") }.forEach { prop ->
                    prop.children().forEach { p -> props["{${p.namespaceURI}}${p.localName}"] = p }
                }
            }
            val status = response.children().firstOrNull { it.isDav("status") }?.textContent?.trim()
            DavResponse(href, props, status)
        }
        return Multistatus(responses, token)
    }

    private fun Element.isDav(name: String) = namespaceURI == Ns.DAV && localName == name

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

internal fun Element.children(): List<Element> {
    val list = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) list += node as Element
    }
    return list
}

internal fun Element.descendants(): Sequence<Element> = sequence {
    for (child in children()) {
        yield(child)
        yieldAll(child.descendants())
    }
}
