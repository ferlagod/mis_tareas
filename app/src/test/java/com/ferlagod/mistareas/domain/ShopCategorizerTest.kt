// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import com.ferlagod.mistareas.domain.ShopCategory.BAKERY
import com.ferlagod.mistareas.domain.ShopCategory.BREAKFAST
import com.ferlagod.mistareas.domain.ShopCategory.CLEANING
import com.ferlagod.mistareas.domain.ShopCategory.DAIRY
import com.ferlagod.mistareas.domain.ShopCategory.DELI
import com.ferlagod.mistareas.domain.ShopCategory.DRINKS
import com.ferlagod.mistareas.domain.ShopCategory.FISH
import com.ferlagod.mistareas.domain.ShopCategory.FROZEN
import com.ferlagod.mistareas.domain.ShopCategory.FRUIT_VEG
import com.ferlagod.mistareas.domain.ShopCategory.HYGIENE
import com.ferlagod.mistareas.domain.ShopCategory.MEAT
import com.ferlagod.mistareas.domain.ShopCategory.OTHER
import com.ferlagod.mistareas.domain.ShopCategory.PANTRY
import com.ferlagod.mistareas.domain.ShopCategory.PETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopCategorizerTest {

    private fun check(expected: ShopCategory, vararg items: String) =
        items.forEach { assertEquals(it, expected, ShopCategorizer.categorize(it)) }

    @Test
    fun spanish() {
        check(FRUIT_VEG, "Tomates", "2 kg de patatas", "Plátanos", "Lechuga", "Uvas", "Ajos")
        check(DAIRY, "Leche", "Yogures naturales", "Huevos x12", "Leche de avena")
        check(BAKERY, "Pan", "Pan de molde")
        check(MEAT, "Pechuga de pollo", "Carne picada")
        check(FISH, "Merluza", "Gambas")
        check(DELI, "Jamón serrano", "Queso manchego")
        check(PANTRY, "Arroz", "Aceite de oliva", "Tomate frito", "Pimienta")
        check(BREAKFAST, "Galletas", "Café", "Patatas fritas", "Colacao")
        check(DRINKS, "Cerveza", "Agua", "Zumo de naranja", "Coca cola")
        check(FROZEN, "Helado de chocolate", "Guisantes congelados")
        check(CLEANING, "Detergente", "Bolsas de basura", "Papel de cocina")
        check(HYGIENE, "Papel higiénico", "Pasta de dientes", "Champú")
        check(PETS, "Pienso para el perro", "Arena para gatos")
    }

    @Test
    fun otherLanguages() {
        check(FRUIT_VEG, "Strawberries", "Pommes de terre", "Karotten", "Pomodori")
        check(DAIRY, "Milk", "Œufs", "Vollmilch", "Uova")
        check(DELI, "Bergkäse", "Fromage râpé", "Prosciutto crudo")
        check(DRINKS, "Orange juice", "Jus d'orange", "Orangensaft", "Birra")
        check(FROZEN, "Ice cream", "Frozen peas", "Gelato")
        check(HYGIENE, "Toilet paper", "Zahnpasta", "Carta igienica")
        check(BAKERY, "Brötchen", "Baguette")
    }

    @Test
    fun unknownGoesToOther() {
        check(OTHER, "Regalo para Ana", "Cosas varias", "")
    }

    @Test
    fun learnedOverridesWin() {
        assertEquals("kefir de cabra", ShopCategorizer.termFor("2 Kéfir de cabra"))
        val overrides = mapOf(ShopCategorizer.termFor("Kéfir de cabra") to DRINKS)
        assertEquals(DRINKS, ShopCategorizer.categorize("kefir de cabra", overrides))
        assertEquals(DRINKS, ShopCategorizer.categorize("3 Kéfir de cabra", overrides))
    }

    @Test
    fun shoppingListNames() {
        assertTrue(ShopCategorizer.isShoppingListName("Compra semanal"))
        assertTrue(ShopCategorizer.isShoppingListName("Einkauf"))
        assertTrue(ShopCategorizer.isShoppingListName("Lista della spesa"))
        assertFalse(ShopCategorizer.isShoppingListName("Trabajo"))
    }
}
