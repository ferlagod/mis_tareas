// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Lo que la app entiende de un VTODO. */
data class VTodo(
    val uid: String,
    val summary: String = "",
    val description: String = "",
    /** NEEDS-ACTION, IN-PROCESS, COMPLETED o CANCELLED. */
    val status: String? = null,
    val completed: Long? = null,
    val percentComplete: Int? = null,
    val dueDay: Long? = null,
    val dueMinutes: Int? = null,
    val priority: Int = 0,
    val sortOrder: Long? = null,
    val created: Long? = null,
    val lastModified: Long? = null,
    /** Aviso (instante absoluto) calculado a partir del primer VALARM. */
    val reminder: Long? = null,
    /** Regla de repetición (RRULE sin el prefijo), p. ej. "FREQ=WEEKLY;BYDAY=MO". */
    val rrule: String? = null,
    /** UID de la tarea madre (RELATED-TO sin RELTYPE o con RELTYPE=PARENT). */
    val parentUid: String? = null,
    /** Etiquetas (todas las líneas CATEGORIES). */
    val categories: List<String> = emptyList()
) {
    val isDone: Boolean get() = status == "COMPLETED" || status == "CANCELLED" || (status == null && completed != null)
}

/**
 * Lectura y escritura de VTODO (RFC 5545) sin dependencias externas.
 *
 * Al escribir sobre un VTODO existente solo se modifican las propiedades que han cambiado;
 * el resto (CATEGORIES, RELATED-TO, VALARM, X-…) se conserva tal cual.
 */
object ICalCodec {

    private const val PRODID = "-//ferlagod//Mis Tareas//EN"
    private const val PARENT_KEY = "RELATED-TO;PARENT"
    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** Una propiedad: nombre en mayúsculas, parámetros en bruto (`;TZID=…`) y valor. */
    data class Property(val name: String, val params: String, val value: String) {
        fun param(key: String): String? = splitParams(params)
            .firstOrNull { it.substringBefore('=').equals(key, ignoreCase = true) }
            ?.substringAfter('=')?.trim('"')

        fun toLine(): String = "$name$params:$value"
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    fun unfold(ics: String): List<String> {
        val out = mutableListOf<String>()
        ics.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else if (line.isNotEmpty()) {
                out += line
            }
        }
        return out
    }

    fun parseProperty(line: String): Property? {
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) { colon = i; break }
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val semi = head.indexOf(';')
        val name = (if (semi >= 0) head.substring(0, semi) else head).uppercase()
        val params = if (semi >= 0) head.substring(semi) else ""
        return Property(name, params, line.substring(colon + 1))
    }

    private fun splitParams(params: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in params) {
            when {
                c == '"' -> { inQuotes = !inQuotes; current.append(c) }
                c == ';' && !inQuotes -> { if (current.isNotEmpty()) parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    /** TRIGGER del primer VALARM del primer VTODO (o null si no tiene avisos). */
    private fun firstAlarmTrigger(lines: List<String>): Property? {
        var depth = 0
        var todoDepth = -1
        var alarmDepth = -1
        for (line in lines) {
            val prop = parseProperty(line) ?: continue
            when (prop.name) {
                "BEGIN" -> {
                    depth++
                    if (todoDepth < 0 && prop.value.equals("VTODO", ignoreCase = true)) todoDepth = depth
                    else if (todoDepth > 0 && alarmDepth < 0 && depth == todoDepth + 1 && prop.value.equals("VALARM", ignoreCase = true)) alarmDepth = depth
                }
                "END" -> {
                    if (alarmDepth > 0 && depth == alarmDepth) return null // Alarma sin TRIGGER.
                    if (todoDepth > 0 && depth == todoDepth) return null
                    depth--
                }
                "TRIGGER" -> if (alarmDepth > 0 && depth == alarmDepth) return prop
            }
        }
        return null
    }

    private val DURATION = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    /** Duración iCalendar ("-PT15M", "P1D", "-P1W") en milisegundos. */
    fun parseDuration(value: String): Long? {
        val m = DURATION.matchEntire(value.trim().uppercase()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val seconds = g(2) * 7 * 86_400 + g(3) * 86_400 + g(4) * 3_600 + g(5) * 60 + g(6)
        return (if (m.groupValues[1] == "-") -seconds else seconds) * 1000
    }

    /** Propiedades del primer VTODO, sin entrar en sus subcomponentes (VALARM). */
    private fun todoProperties(lines: List<String>): List<Property>? {
        var depth = 0
        var inTodo = false
        var todoDepth = -1
        val props = mutableListOf<Property>()
        for (line in lines) {
            val prop = parseProperty(line) ?: continue
            when (prop.name) {
                "BEGIN" -> {
                    depth++
                    if (!inTodo && todoDepth < 0 && prop.value.equals("VTODO", ignoreCase = true)) {
                        inTodo = true
                        todoDepth = depth
                    }
                }
                "END" -> {
                    if (inTodo && depth == todoDepth) return props
                    depth--
                }
                else -> if (inTodo && depth == todoDepth) props += prop
            }
        }
        return if (inTodo) props else null
    }

    fun parse(ics: String, zone: ZoneId = ZoneId.systemDefault()): VTodo? {
        val lines = unfold(ics)
        val props = todoProperties(lines) ?: return null
        fun first(name: String) = props.firstOrNull { it.name == name }

        val uid = first("UID")?.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val due = first("DUE")?.let { parseDue(it, zone) }
        return VTodo(
            uid = uid,
            summary = first("SUMMARY")?.value?.let(::unescape).orEmpty(),
            description = first("DESCRIPTION")?.value?.let(::unescape).orEmpty(),
            status = first("STATUS")?.value?.trim()?.uppercase(),
            completed = first("COMPLETED")?.let { parseInstant(it, zone) },
            percentComplete = first("PERCENT-COMPLETE")?.value?.trim()?.toIntOrNull(),
            dueDay = due?.first,
            dueMinutes = due?.second,
            priority = first("PRIORITY")?.value?.trim()?.toIntOrNull()?.coerceIn(0, 9) ?: 0,
            sortOrder = first("X-APPLE-SORT-ORDER")?.value?.trim()?.toLongOrNull(),
            created = first("CREATED")?.let { parseInstant(it, zone) },
            lastModified = first("LAST-MODIFIED")?.let { parseInstant(it, zone) },
            rrule = first("RRULE")?.value?.trim()?.takeIf { it.isNotEmpty() },
            parentUid = props.firstOrNull { it.name == "RELATED-TO" && isParentRelation(it) }?.value?.trim()?.takeIf { it.isNotEmpty() },
            categories = props.filter { it.name == "CATEGORIES" }.flatMap { splitList(it.value) }.map { unescape(it).trim() }
                .filter { it.isNotEmpty() }.distinct(),
            reminder = firstAlarmTrigger(lines)?.let { trigger ->
                val value = trigger.value.trim()
                if (trigger.param("VALUE").equals("DATE-TIME", ignoreCase = true) || !value.contains('P')) {
                    parseInstant(trigger, zone)
                } else {
                    // Relativo al inicio (por defecto) o al vencimiento (RELATED=END).
                    val offset = parseDuration(value) ?: return@let null
                    val base = if (trigger.param("RELATED").equals("END", ignoreCase = true)) first("DUE")
                    else first("DTSTART") ?: first("DUE")
                    base?.let { parseInstant(it, zone) }?.plus(offset)
                }
            }
        )
    }

    /** DUE como (día de época, minutos o null si es de todo el día) en la zona local. */
    private fun parseDue(prop: Property, zone: ZoneId): Pair<Long, Int?>? {
        val value = prop.value.trim()
        return try {
            if (prop.param("VALUE").equals("DATE", ignoreCase = true) || value.length == 8) {
                LocalDate.parse(value.take(8), DATE_FORMAT).toEpochDay() to null
            } else {
                val local = toZoned(prop, zone)?.withZoneSameInstant(zone) ?: return null
                local.toLocalDate().toEpochDay() to (local.hour * 60 + local.minute)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstant(prop: Property, zone: ZoneId): Long? = try {
        val value = prop.value.trim()
        if (value.length == 8) LocalDate.parse(value, DATE_FORMAT).atStartOfDay(zone).toInstant().toEpochMilli()
        else toZoned(prop, zone)?.toInstant()?.toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun toZoned(prop: Property, zone: ZoneId): java.time.ZonedDateTime? {
        val value = prop.value.trim()
        return if (value.endsWith("Z", ignoreCase = true)) {
            LocalDateTime.parse(value.dropLast(1).take(15), LOCAL_FORMAT).atZone(ZoneOffset.UTC)
        } else {
            val tz = prop.param("TZID")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
            LocalDateTime.parse(value.take(15), LOCAL_FORMAT).atZone(tz)
        }
    }

    private fun isParentRelation(prop: Property): Boolean =
        prop.param("RELTYPE")?.equals("PARENT", ignoreCase = true) ?: true

    /** Separa por comas sin escapar ("a\,b,c" → ["a\,b", "c"]). */
    private fun splitList(value: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' && i + 1 < value.length -> { sb.append(c).append(value[i + 1]); i++ }
                c == ',' -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    fun unescape(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val n = text[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\n", "\\n")

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    /**
     * Genera el `.ics` de una tarea. Si [original] no es null, se parte de él y solo se cambian
     * las propiedades cuyo valor difiere; si es null se crea un VCALENDAR nuevo.
     */
    fun write(
        todo: VTodo,
        original: String?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val previous = original?.let { parse(it, zone) }
        val lines = original?.let(::unfold)?.takeIf { previous != null }
        return if (lines == null) build(todo, now, zone) else patch(lines, previous!!, todo, now, zone)
    }

    private fun managedLines(todo: VTodo, previous: VTodo?, zone: ZoneId): Map<String, List<String>> {
        val map = LinkedHashMap<String, List<String>>()
        map["SUMMARY"] = listOf("SUMMARY:" + escape(todo.summary))
        map["DESCRIPTION"] = if (todo.description.isBlank()) emptyList() else listOf("DESCRIPTION:" + escape(todo.description))

        // Estado: se respetan IN-PROCESS y CANCELLED de otras apps mientras encajen.
        val status = when {
            todo.isDone && previous?.status == "CANCELLED" -> "CANCELLED"
            todo.isDone -> "COMPLETED"
            previous?.status == "IN-PROCESS" -> "IN-PROCESS"
            else -> "NEEDS-ACTION"
        }
        map["STATUS"] = listOf("STATUS:$status")
        map["COMPLETED"] = if (todo.isDone) listOf("COMPLETED:" + UTC_FORMAT.format(Instant.ofEpochMilli(todo.completed ?: System.currentTimeMillis()))) else emptyList()
        map["PERCENT-COMPLETE"] = when {
            todo.isDone -> listOf("PERCENT-COMPLETE:100")
            previous?.percentComplete != null && previous.percentComplete < 100 -> listOf("PERCENT-COMPLETE:${previous.percentComplete}")
            else -> emptyList()
        }
        map["DUE"] = when {
            todo.dueDay == null -> emptyList()
            todo.dueMinutes == null -> listOf("DUE;VALUE=DATE:" + LocalDate.ofEpochDay(todo.dueDay).format(DATE_FORMAT))
            else -> {
                val instant = LocalDate.ofEpochDay(todo.dueDay).atStartOfDay(zone)
                    .plusMinutes(todo.dueMinutes.toLong()).toInstant()
                listOf("DUE:" + UTC_FORMAT.format(instant))
            }
        }
        map["PRIORITY"] = if (todo.priority > 0) listOf("PRIORITY:${todo.priority}") else emptyList()
        map["X-APPLE-SORT-ORDER"] = todo.sortOrder?.let { listOf("X-APPLE-SORT-ORDER:$it") } ?: emptyList()
        map["RRULE"] = todo.rrule?.takeIf { it.isNotBlank() }?.let { listOf("RRULE:$it") } ?: emptyList()
        map["CATEGORIES"] = if (todo.categories.isEmpty()) emptyList() else listOf("CATEGORIES:" + todo.categories.joinToString(",") { escape(it) })
        // Solo la relación con la madre; RELATED-TO de otros tipos (hermanas, hijas) se conservan.
        map[PARENT_KEY] = todo.parentUid?.let { listOf("RELATED-TO:$it") } ?: emptyList()
        return map
    }

    /** VALARM con hora absoluta, como los que crea Nextcloud Tasks al elegir fecha y hora. */
    private fun alarmLines(todo: VTodo): List<String> {
        val at = todo.reminder ?: return emptyList()
        return listOf(
            "BEGIN:VALARM",
            "ACTION:DISPLAY",
            "DESCRIPTION:" + escape(todo.summary.ifBlank { "Reminder" }),
            "TRIGGER;VALUE=DATE-TIME:" + UTC_FORMAT.format(Instant.ofEpochMilli(at)),
            "END:VALARM"
        )
    }

    private fun reminderChanged(todo: VTodo, previous: VTodo): Boolean {
        val a = todo.reminder
        val b = previous.reminder
        return (a == null) != (b == null) || (a != null && b != null && Math.abs(a - b) >= 60_000)
    }

    /** ¿Ha cambiado lo que representa cada propiedad gestionada? (para no reescribir lo que no se tocó) */
    private fun changed(name: String, todo: VTodo, previous: VTodo): Boolean = when (name) {
        "SUMMARY" -> todo.summary != previous.summary
        "DESCRIPTION" -> todo.description.trimEnd() != previous.description.trimEnd()
        "STATUS", "COMPLETED", "PERCENT-COMPLETE" ->
            todo.isDone != previous.isDone || (todo.isDone && todo.completed != null && previous.completed != null &&
                Math.abs(todo.completed - previous.completed) >= 1000)
        "DUE" -> todo.dueDay != previous.dueDay || todo.dueMinutes != previous.dueMinutes
        "PRIORITY" -> todo.priority != previous.priority
        "X-APPLE-SORT-ORDER" -> todo.sortOrder != null && todo.sortOrder != previous.sortOrder
        "RRULE" -> todo.rrule?.trim() != previous.rrule?.trim()
        "CATEGORIES" -> todo.categories.toSet() != previous.categories.toSet()
        PARENT_KEY -> todo.parentUid != previous.parentUid
        else -> false
    }

    private fun patch(lines: List<String>, previous: VTodo, todo: VTodo, now: Long, zone: ZoneId): String {
        val managed = managedLines(todo, previous, zone)
        val replace = managed.keys.filter { changed(it, todo, previous) }.toSet() + setOf("LAST-MODIFIED", "DTSTAMP")
        val stamp = UTC_FORMAT.format(Instant.ofEpochMilli(now))
        // Si cambió el aviso se sustituye el primer VALARM (el que se leyó); los demás se respetan.
        val replaceAlarm = reminderChanged(todo, previous)
        val dueChanged = "DUE" in replace

        val out = mutableListOf<String>()
        var depth = 0
        var todoDepth = -1
        var inserted = false
        var alarmSeen = false
        var skipDepth = -1

        fun insertManaged() {
            if (inserted) return
            inserted = true
            replace.forEach { name ->
                when (name) {
                    "LAST-MODIFIED" -> out += "LAST-MODIFIED:$stamp"
                    "DTSTAMP" -> out += "DTSTAMP:$stamp"
                    else -> out += managed[name].orEmpty()
                }
            }
        }

        for (line in lines) {
            val prop = parseProperty(line)
            if (skipDepth > 0) {
                // Dentro del VALARM que se sustituye.
                if (prop?.name == "BEGIN") depth++
                if (prop?.name == "END") {
                    if (depth == skipDepth) skipDepth = -1
                    depth--
                }
                continue
            }
            if (prop == null) { out += line; continue }
            val atTodoLevel = todoDepth > 0 && depth == todoDepth
            when {
                prop.name == "BEGIN" -> {
                    if (atTodoLevel) insertManaged() // Las propiedades van antes de los VALARM.
                    depth++
                    if (todoDepth == -1 && prop.value.equals("VTODO", ignoreCase = true)) todoDepth = depth
                    if (atTodoLevel && !alarmSeen && prop.value.equals("VALARM", ignoreCase = true)) {
                        alarmSeen = true
                        if (replaceAlarm) {
                            skipDepth = depth
                            continue
                        }
                    }
                    out += line
                }
                prop.name == "END" -> {
                    if (atTodoLevel) {
                        insertManaged()
                        if (replaceAlarm) out += alarmLines(todo)
                        todoDepth = -2 // Solo se modifica el primer VTODO.
                    }
                    depth--
                    out += line
                }
                atTodoLevel && prop.name in replace -> Unit // Se sustituye por la nueva versión.
                atTodoLevel && prop.name == "RELATED-TO" && PARENT_KEY in replace && isParentRelation(prop) -> Unit
                // DUE y DURATION no pueden ir juntos (RFC 5545 §3.6.2).
                atTodoLevel && prop.name == "DURATION" && todo.dueDay != null -> Unit
                // El inicio acompaña a la fecha límite para que nunca quede detrás de ella.
                atTodoLevel && prop.name == "DTSTART" && dueChanged -> shiftStart(prop, previous, todo, zone)?.let { out += it }
                else -> out += line
            }
        }
        return out.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    /**
     * Nuevo DTSTART cuando cambia la fecha límite: se mueve los mismos días que DUE, con su mismo
     * tipo (fecha o fecha y hora) y nunca después de DUE. Sin fecha límite se deja como estaba.
     */
    private fun shiftStart(start: Property, previous: VTodo, todo: VTodo, zone: ZoneId): String? {
        val newDay = todo.dueDay ?: return start.toLine()
        val startIsDate = start.param("VALUE").equals("DATE", ignoreCase = true) || start.value.trim().length == 8
        val oldStart: LocalDateTime = try {
            if (startIsDate) LocalDate.parse(start.value.trim().take(8), DATE_FORMAT).atStartOfDay()
            else toZoned(start, zone)?.withZoneSameInstant(zone)?.toLocalDateTime() ?: return start.toLine()
        } catch (_: Exception) {
            return start.toLine()
        }
        val newDue = LocalDate.ofEpochDay(newDay).atStartOfDay().plusMinutes((todo.dueMinutes ?: 0).toLong())
        var shifted = previous.dueDay?.let { oldDay -> oldStart.plusDays(newDay - oldDay) } ?: oldStart
        if (shifted.isAfter(newDue)) shifted = newDue
        return if (todo.dueMinutes == null) {
            "DTSTART;VALUE=DATE:" + shifted.toLocalDate().format(DATE_FORMAT)
        } else {
            "DTSTART:" + UTC_FORMAT.format(shifted.atZone(zone).toInstant())
        }
    }

    private fun build(todo: VTodo, now: Long, zone: ZoneId): String {
        val stamp = UTC_FORMAT.format(Instant.ofEpochMilli(now))
        val created = UTC_FORMAT.format(Instant.ofEpochMilli(todo.created ?: now))
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:$PRODID",
            "BEGIN:VTODO",
            "UID:${todo.uid}",
            "DTSTAMP:$stamp",
            "CREATED:$created",
            "LAST-MODIFIED:$stamp"
        )
        managedLines(todo, null, zone).values.forEach { lines += it }
        lines += alarmLines(todo)
        lines += "END:VTODO"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    /** Pliega una línea a 75 octetos sin partir caracteres UTF-8 (RFC 5545 §3.1). */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val sb = StringBuilder()
        var bytes = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > limit) {
                sb.append("\r\n ")
                bytes = 0
                limit = 74 // El espacio inicial cuenta.
            }
            sb.appendCodePoint(cp)
            bytes += size
            i += chars
        }
        return sb.toString()
    }
}
