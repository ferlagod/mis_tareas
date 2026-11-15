// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.domain.ShopCategory

/** Nombre traducido de cada sección del súper. */
@Composable
fun ShopCategory.label(): String = stringResource(
    when (this) {
        ShopCategory.FRUIT_VEG -> R.string.cat_fruit_veg
        ShopCategory.BAKERY -> R.string.cat_bakery
        ShopCategory.MEAT -> R.string.cat_meat
        ShopCategory.FISH -> R.string.cat_fish
        ShopCategory.DELI -> R.string.cat_deli
        ShopCategory.DAIRY -> R.string.cat_dairy
        ShopCategory.PANTRY -> R.string.cat_pantry
        ShopCategory.BREAKFAST -> R.string.cat_breakfast
        ShopCategory.DRINKS -> R.string.cat_drinks
        ShopCategory.FROZEN -> R.string.cat_frozen
        ShopCategory.CLEANING -> R.string.cat_cleaning
        ShopCategory.HYGIENE -> R.string.cat_hygiene
        ShopCategory.PETS -> R.string.cat_pets
        ShopCategory.OTHER -> R.string.cat_other
    }
)
