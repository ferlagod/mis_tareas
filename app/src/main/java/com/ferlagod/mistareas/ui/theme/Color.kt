// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.theme

import androidx.compose.ui.graphics.Color
import com.ferlagod.mistareas.data.model.ListColor

// ==========================================
// Identidad de marca (familia "Mis Apps")
// ==========================================
// Mis Podcast usa el azul de su logo y Mis Notas el verde del suyo; Mis Tareas usa el naranja
// (#FF914D), oscurecido en modo claro para que texto e iconos tengan buen contraste.
val LogoOrange = Color(0xFFFF914D)           // Naranja exacto del logo
val BrandOrange = Color(0xFFB8501A)          // Primario en modo claro (contraste 5:1 sobre blanco)
val BrandOrangeLight = LogoOrange            // Primario en modo oscuro (7,6:1 sobre la superficie)
val BrandOrangeContainer = Color(0xFFFFE3D2)
val BrandOrangeOnContainer = Color(0xFF3A1600)
val BrandOrangeDarkContainer = Color(0xFF7A3510)
val BrandCream = Color(0xFFFFFBF5)           // Fondo del logo
val LogoTeal = Color(0xFF4A9BB4)             // Camiseta del personaje del logo
val LogoMustard = Color(0xFFF2A73B)          // Pantalón del personaje del logo

// ==========================================
// Tema claro (fondos agrupados estilo iOS, compartidos con Mis Podcast y Mis Notas)
// ==========================================
val BackgroundLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF8E8E93)
val TextTertiaryLight = Color(0xFFC7C7CC)
val OutlineLight = Color(0xFFD1D1D6)

// ==========================================
// Tema oscuro (gris elegante) y AMOLED (negro puro)
// ==========================================
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val BackgroundAmoled = Color(0xFF000000)
// Fondo negro puro; las tarjetas apenas se elevan para que se distingan los grupos.
val SurfaceAmoled = Color(0xFF111113)
val SurfaceVariantAmoled = Color(0xFF1C1C1E)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark = Color(0xFF48484A)
val OutlineDark = Color(0xFF38383A)

// ==========================================
// Acentos semánticos
// ==========================================
val AccentGreen = Color(0xFF34C759)
val AccentRed = Color(0xFFFF3B30)
val AccentStar = Color(0xFFF2A73B)

/** Color de una lista en pantalla. En oscuro se aclara un poco para que no se apague. */
fun ListColor.toColor(isDark: Boolean): Color {
    val base = Color(argb)
    return if (!isDark) base else Color(
        red = base.red + (1f - base.red) * 0.15f,
        green = base.green + (1f - base.green) * 0.15f,
        blue = base.blue + (1f - base.blue) * 0.15f
    )
}
