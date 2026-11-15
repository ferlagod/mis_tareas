// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.model

/**
 * Colores de lista. [hex] es el color que se guarda en el servidor (CalDAV `calendar-color`
 * o la propiedad `color` del Markdown); los colores de otras apps se aproximan al más cercano.
 */
enum class ListColor(val hex: String) {
    ORANGE("#FF914D"),
    MUSTARD("#F2A73B"),
    RED("#E5484D"),
    PINK("#E05A9B"),
    PURPLE("#8E6BD8"),
    BLUE("#3E7BE6"),
    TEAL("#4A9BB4"),
    GREEN("#4E9F5D"),
    GRAPHITE("#7A7F87");

    val argb: Long get() = 0xFF000000L or hex.removePrefix("#").toLong(16)

    companion object {
        val DEFAULT = ORANGE

        fun fromKey(key: String?): ListColor =
            entries.firstOrNull { it.name.equals(key?.trim(), ignoreCase = true) } ?: fromHex(key) ?: DEFAULT

        /** Color de la paleta más parecido a un `#RRGGBB` o `#RRGGBBAA` arbitrario. */
        fun fromHex(hex: String?): ListColor? {
            val clean = hex?.trim()?.removePrefix("#") ?: return null
            if (clean.length != 6 && clean.length != 8) return null
            val rgb = clean.take(6).toLongOrNull(16) ?: return null
            val r = (rgb shr 16 and 0xFF).toInt()
            val g = (rgb shr 8 and 0xFF).toInt()
            val b = (rgb and 0xFF).toInt()
            return entries.minBy { c ->
                val v = c.argb
                val dr = r - (v shr 16 and 0xFF).toInt()
                val dg = g - (v shr 8 and 0xFF).toInt()
                val db = b - (v and 0xFF).toInt()
                // Distancia ponderada según la sensibilidad del ojo.
                2 * dr * dr + 4 * dg * dg + 3 * db * db
            }
        }

        /** Colores que se van asignando a las listas nuevas. */
        fun forIndex(index: Int): ListColor = entries[Math.floorMod(index, entries.size)]
    }
}
