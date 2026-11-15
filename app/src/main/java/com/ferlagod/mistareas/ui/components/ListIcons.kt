// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer

private val ICON_RULES: List<Pair<List<String>, ImageVector>> = listOf(
    listOf("compra", "super", "mercado", "shopping", "grocer", "despensa") to Icons.Rounded.ShoppingCart,
    listOf("casa", "hogar", "home", "house", "limpieza", "chores") to Icons.Rounded.Home,
    listOf("trabajo", "oficina", "work", "office", "proyecto", "project") to Icons.Rounded.Work,
    listOf("viaje", "vacacion", "travel", "trip", "maleta") to Icons.Rounded.Flight,
    listOf("receta", "comida", "menu", "cocina", "recipe", "meal", "food") to Icons.Rounded.Restaurant,
    listOf("farmacia", "medic", "salud", "pharmacy", "health") to Icons.Rounded.LocalPharmacy,
    listOf("gym", "gimnasio", "deporte", "entreno", "workout", "sport") to Icons.Rounded.FitnessCenter,
    listOf("regalo", "navidad", "cumple", "gift", "christmas", "birthday") to Icons.Rounded.CardGiftcard,
    listOf("libro", "leer", "lectura", "book", "reading") to Icons.AutoMirrored.Rounded.MenuBook,
    listOf("cole", "clase", "estudio", "examen", "school", "study") to Icons.Rounded.School,
    listOf("perro", "gato", "mascota", "pet", "dog", "cat") to Icons.Rounded.Pets
)

/** Icono según el nombre de la lista ("Compra" → carrito, "Casa" → casa…). */
fun listIconFor(name: String): ImageVector {
    val normalized = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
    return ICON_RULES.firstOrNull { (keys, _) -> keys.any { normalized.contains(it) } }?.second
        ?: Icons.AutoMirrored.Rounded.FormatListBulleted
}
