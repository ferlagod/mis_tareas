// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.util

import java.security.SecureRandom

object Ids {
    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** Separador de las marcas de borrado que deja una tarea al moverse a otra lista. */
    const val MOVED_SEPARATOR = "#moved-"

    /**
     * Identificador corto (12 caracteres, ~62 bits): se lee bien en el Markdown (`🆔 k3j9x0a2qe4m`)
     * y sirve como UID de VTODO y nombre del `.ics`.
     */
    fun newId(): String = buildString(12) { repeat(12) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun movedTombstoneId(taskId: String): String = taskId + MOVED_SEPARATOR + newId()

    /** Id real de una tarea (sin el sufijo de las marcas de borrado por movimiento). */
    fun originalId(id: String): String = id.substringBefore(MOVED_SEPARATOR)
}
