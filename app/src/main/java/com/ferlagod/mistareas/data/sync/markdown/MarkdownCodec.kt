// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync.markdown

import com.ferlagod.mistareas.data.model.ListColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Tarea tal como aparece en un fichero Markdown. */
data class MdTask(
    val id: String?,
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    val doneDate: LocalDate? = null,
    val due: LocalDate? = null,
    /** Escala iCalendar (0 = sin prioridad, 1 = máxima, 5 = media, 9 = mínima). */
    val priority: Int = 0,
    /** Aviso, con el formato del plugin Reminder de Obsidian: `⏰ 2026-11-10 09:00`. */
    val reminder: LocalDateTime? = null,
    /** Repetición como RRULE; en el fichero, `🔁 every week` (plugin Tasks de Obsidian). */
    val rrule: String? = null,
    /** Etiquetas (`#compra` en el título, como en Obsidian). */
    val tags: List<String> = emptyList(),
    /** Id de la tarea madre (al escribir). */
    val parentId: String? = null,
    /** Posición en [MdDocument.tasks] de la tarea madre (al leer: la tarea menos sangrada de encima). */
    val parentIndex: Int? = null
)

/**
 * Fichero de una lista.
 *
 * @param frontMatter líneas de la cabecera YAML (sin los `---`), se conservan tal cual.
 * @param extra texto que no son tareas (párrafos, subtítulos…), se conserva al reescribir.
 */
data class MdDocument(
    val frontMatter: List<String> = emptyList(),
    val title: String? = null,
    val extra: List<String> = emptyList(),
    val tasks: List<MdTask> = emptyList()
) {
    val color: ListColor?
        get() = frontMatter.firstNotNullOfOrNull { line ->
            COLOR_LINE.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.trim('"', '\'')
        }?.let(ListColor::fromKey)

    private companion object {
        val COLOR_LINE = Regex("""color\s*:\s*(.+)""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Formato Markdown de las listas, compatible con el plugin Tasks de Obsidian:
 *
 * ```
 * ---
 * color: teal
 * ---
 *
 * # Compra
 *
 * - [ ] Leche ⏫ 📅 2026-11-10 ⏰ 2026-11-10 09:00 🆔 k3j9x0a2qe
 *     Sin lactosa
 * - [x] Pan ✅ 2026-11-09 🆔 a81bd0c4f2
 * ```
 *
 * Las notas son las líneas sangradas bajo cada tarea. El `🆔` permite reconocer la misma
 * tarea en varios dispositivos; las líneas escritas a mano sin él reciben uno nuevo.
 */
object MarkdownCodec {

    private const val VS = "️?"
    private val TASK_LINE = Regex("""^\s*[-*+]\s+\[([ xX/\-])]\s?(.*)$""")
    private val HEADING = Regex("""^#\s+(.+?)\s*#*\s*$""")
    private val ID = Regex("""\s*🆔$VS\s*(\S+)""")
    private val DUE = Regex("""\s*📅$VS\s*(\d{4}-\d{2}-\d{2})""")
    private val DONE = Regex("""\s*✅$VS\s*(\d{4}-\d{2}-\d{2})""")
    private val REMINDER = Regex("""\s*⏰$VS\s*(\d{4}-\d{2}-\d{2})(?:\s+(\d{1,2}:\d{2}))?""")
    private val RECURRENCE = Regex("""\s*🔁$VS\s*(every\b[^📅⏰✅🆔⏫🔼🔽⏬🔺➕⏳🛫]*)""", RegexOption.IGNORE_CASE)
    /** `#etiqueta` de Obsidian: no puede ser solo números ("#2" no es etiqueta). */
    private val TAG = Regex("""(?<=^|\s)#([\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*)""")
    private val PRIORITY = Regex("""\s*(🔺|⏫|🔼|🔽|⏬)$VS""")
    private const val NOTE_INDENT = "    "
    private val REMINDER_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    fun parse(text: String): MdDocument {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var index = 0
        var frontMatter = emptyList<String>()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) {
                frontMatter = lines.subList(1, end).toList()
                index = end + 1
            }
        }

        var title: String? = null
        val extra = mutableListOf<String>()
        val tasks = mutableListOf<MdTask>()
        var current: MdTask? = null
        val currentNotes = mutableListOf<String>()
        // Tareas abiertas por nivel de sangría (para saber de quién es cada subtarea).
        val stack = ArrayDeque<Pair<Int, Int>>()

        fun flush() {
            current?.let { task ->
                tasks += task.copy(notes = currentNotes.joinToString("\n").trimEnd())
            }
            current = null
            currentNotes.clear()
        }

        while (index < lines.size) {
            val line = lines[index++]
            val taskMatch = TASK_LINE.matchEntire(line)
            when {
                taskMatch != null -> {
                    flush()
                    val indent = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 4 else 1 }
                    while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                    val parent = stack.lastOrNull()?.second
                    stack.addLast(indent to tasks.size)
                    current = parseTask(taskMatch.groupValues[1], taskMatch.groupValues[2]).copy(parentIndex = parent)
                }
                current != null && line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t') -> {
                    currentNotes += line.trimStart()
                }
                title == null && tasks.isEmpty() && current == null && HEADING.matches(line) -> {
                    title = HEADING.matchEntire(line)!!.groupValues[1]
                }
                else -> {
                    flush()
                    stack.clear()
                    extra += line
                }
            }
        }
        flush()

        return MdDocument(
            frontMatter = frontMatter,
            title = title,
            extra = extra.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() },
            tasks = tasks
        )
    }

    private fun parseTask(mark: String, rest: String): MdTask {
        var text = rest
        fun take(regex: Regex): String? {
            val match = regex.find(text) ?: return null
            text = text.removeRange(match.range)
            return match.groupValues[1]
        }

        val id = take(ID)
        var reminder: LocalDateTime? = null
        REMINDER.find(text)?.let { match ->
            text = text.removeRange(match.range)
            val date = parseDate(match.groupValues[1])
            val time = match.groupValues[2].takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() }
            reminder = date?.atTime(time ?: LocalTime.of(9, 0))
        }
        // Solo se entiende lo que se puede pasar a RRULE; lo demás se queda en el título y no se pierde.
        val rrule = RECURRENCE.find(text)?.let { match ->
            MarkdownRecurrence.toRrule(match.groupValues[1])?.also { text = text.removeRange(match.range) }
        }
        val due = take(DUE)?.let(::parseDate)
        val tags = TAG.findAll(text).map { it.groupValues[1] }.distinct().toList()
        if (tags.isNotEmpty()) text = TAG.replace(text, "")
        val doneDate = take(DONE)?.let(::parseDate)
        val priority = when (take(PRIORITY)) {
            "🔺", "⏫" -> 1
            "🔼" -> 5
            "🔽", "⏬" -> 9
            else -> 0
        }
        return MdTask(
            id = id,
            title = text.trim().replace(Regex("""\s{2,}"""), " "),
            done = mark == "x" || mark == "X" || mark == "-",
            doneDate = doneDate,
            due = due,
            priority = priority,
            reminder = reminder,
            rrule = rrule,
            tags = tags
        )
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Escribe el fichero de una lista. [previous] es la versión del servidor (si la hay) de la
     * que se conservan la cabecera YAML y el texto que no son tareas.
     */
    fun write(name: String, color: ListColor, tasks: List<MdTask>, previous: MdDocument? = null): String {
        val sb = StringBuilder()

        val frontMatter = previous?.frontMatter.orEmpty().toMutableList()
        val colorLine = "color: ${color.name.lowercase()}"
        val colorIndex = frontMatter.indexOfFirst { it.trim().startsWith("color:", ignoreCase = true) }
        when {
            colorIndex >= 0 -> frontMatter[colorIndex] = colorLine
            color != ListColor.DEFAULT || frontMatter.isNotEmpty() -> frontMatter += colorLine
        }
        if (frontMatter.isNotEmpty()) {
            sb.append("---\n")
            frontMatter.forEach { sb.append(it).append('\n') }
            sb.append("---\n\n")
        }

        sb.append("# ").append(singleLine(name)).append("\n\n")

        val extra = previous?.extra.orEmpty()
        if (extra.isNotEmpty()) {
            extra.forEach { sb.append(it).append('\n') }
            sb.append('\n')
        }

        // Subtareas sangradas bajo su tarea madre; las huérfanas, arriba del todo.
        val ids = tasks.mapNotNull { it.id }.toSet()
        val children = tasks.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
        val written = HashSet<MdTask>()
        fun writeTask(task: MdTask, depth: Int) {
            if (!written.add(task) || depth > 8) return
            val indent = NOTE_INDENT.repeat(depth)
            sb.append(indent).append(if (task.done) "- [x] " else "- [ ] ").append(singleLine(task.title))
            task.tags.forEach { sb.append(" #").append(it.replace(Regex("""\s+"""), "_")) }
            when {
                task.priority in 1..4 -> sb.append(" ⏫")
                task.priority == 5 -> sb.append(" 🔼")
                task.priority in 6..9 -> sb.append(" 🔽")
            }
            task.rrule?.let(MarkdownRecurrence::toText)?.let { sb.append(" 🔁 ").append(it) }
            task.due?.let { sb.append(" 📅 ").append(it) }
            task.reminder?.let { sb.append(" ⏰ ").append(it.toLocalDate()).append(' ').append(REMINDER_TIME.format(it)) }
            if (task.done) task.doneDate?.let { sb.append(" ✅ ").append(it) }
            task.id?.let { sb.append(" 🆔 ").append(it) }
            sb.append('\n')
            if (task.notes.isNotBlank()) {
                task.notes.trimEnd().split('\n').forEach { note -> sb.append(indent).append(NOTE_INDENT).append(note).append('\n') }
            }
            task.id?.let { id -> children[id]?.forEach { writeTask(it, depth + 1) } }
        }
        tasks.filter { it.parentId == null || it.parentId !in ids }.forEach { writeTask(it, 0) }
        tasks.forEach { writeTask(it, 0) } // Por si hubiera un ciclo de madres.
        return sb.toString()
    }

    private fun singleLine(text: String) = text.replace(Regex("""\s*\n\s*"""), " ").trim()

    /**
     * Nombre de fichero seguro a partir del nombre de la lista (sin extensión ni caracteres
     * problemáticos en Windows, Nextcloud o URLs).
     */
    fun fileNameFor(listName: String, taken: Set<String>): String {
        val base = listName
            .replace(Regex("""[\\/:*?"<>|#%{}\u0000-\u001F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
            .take(60)
            .ifBlank { "Lista" }
        val lowerTaken = taken.map { it.lowercase() }.toSet()
        var candidate = "$base.md"
        var n = 2
        while (candidate.lowercase() in lowerTaken) candidate = "$base $n.md".also { n++ }
        return candidate
    }
}

/** Repeticiones del plugin Tasks de Obsidian ("every 2 weeks on Monday, Friday") ⇄ RRULE. */
object MarkdownRecurrence {

    private val DAYS = listOf("MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday", "TH" to "Thursday", "FR" to "Friday", "SA" to "Saturday", "SU" to "Sunday")
    private val UNITS = mapOf("day" to "DAILY", "week" to "WEEKLY", "month" to "MONTHLY", "year" to "YEARLY")
    private val TEXT = Regex("""^every\s+(?:(\d+)\s+)?(day|week|month|year)s?(?:\s+on\s+(.+?))?(?:\s+when done)?\s*$""", RegexOption.IGNORE_CASE)

    fun toRrule(text: String): String? {
        val m = TEXT.matchEntire(text.trim()) ?: return null
        val interval = m.groupValues[1].toIntOrNull()?.takeIf { it > 1 }
        val freq = UNITS[m.groupValues[2].lowercase()] ?: return null
        val on = m.groupValues[3].trim()
        val byDay = if (on.isEmpty()) null else {
            if (freq != "WEEKLY") return null
            on.split(',', '&').map { it.trim().removePrefix("and ").trim() }.map { name ->
                DAYS.firstOrNull { it.second.equals(name, ignoreCase = true) }?.first ?: return null
            }.joinToString(",")
        }
        return buildString {
            append("FREQ=").append(freq)
            interval?.let { append(";INTERVAL=").append(it) }
            byDay?.let { append(";BYDAY=").append(it) }
        }
    }

    /** Texto para el fichero, o null si la regla no se puede escribir así (se pierde solo en Markdown). */
    fun toText(rrule: String): String? {
        val parts = rrule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
        if (parts.keys.any { it !in setOf("FREQ", "INTERVAL", "BYDAY", "WKST") }) return null
        val unit = UNITS.entries.firstOrNull { it.value == parts["FREQ"]?.uppercase() }?.key ?: return null
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val days = parts["BYDAY"]?.split(',')?.map { code -> DAYS.firstOrNull { it.first == code.trim().uppercase() }?.second ?: return null }
        if (days != null && unit != "week") return null
        return buildString {
            append("every ")
            if (interval > 1) append(interval).append(' ').append(unit).append('s') else append(unit)
            if (!days.isNullOrEmpty()) append(" on ").append(days.joinToString(", "))
        }
    }
}
