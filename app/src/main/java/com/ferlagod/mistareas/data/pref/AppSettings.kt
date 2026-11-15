// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema de la interfaz. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias de la app que no son secretas (aspecto y comportamiento). */
data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** En modo oscuro, negro puro en lugar de gris (pantallas OLED). */
    val amoled: Boolean = false,
    /** Entender "mañana", "lunes" o "!" al escribir una tarea. */
    val smartInput: Boolean = true,
    /** Lista donde caen las tareas creadas desde Inicio, "Hoy" o "Compartir". */
    val defaultListId: String? = null,
    /** Las tareas con hora límite avisan a esa hora aunque no tengan recordatorio. */
    val notifyAtDue: Boolean = true,
    /** Aviso diario con las tareas de hoy y las vencidas. */
    val dailyDigest: Boolean = true,
    /** Hora del aviso diario, en minutos desde medianoche. */
    val digestMinutes: Int = 9 * 60
)

/**
 * Preferencias no sensibles en SharedPreferences normales, expuestas como [StateFlow]
 * para que la interfaz reaccione al instante. Comparte fichero con el idioma.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private fun read() = AppSettingsState(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
        amoled = prefs.getBoolean(KEY_AMOLED, false),
        smartInput = prefs.getBoolean(KEY_SMART_INPUT, true),
        defaultListId = prefs.getString(KEY_DEFAULT_LIST, null),
        notifyAtDue = prefs.getBoolean(KEY_NOTIFY_AT_DUE, true),
        dailyDigest = prefs.getBoolean(KEY_DAILY_DIGEST, true),
        digestMinutes = prefs.getInt(KEY_DIGEST_MINUTES, 9 * 60).coerceIn(0, 24 * 60 - 1)
    )

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _state.value = read()
    }

    fun setThemeMode(mode: ThemeMode) = update { putString(KEY_THEME, mode.name) }
    fun setAmoled(enabled: Boolean) = update { putBoolean(KEY_AMOLED, enabled) }
    fun setSmartInput(enabled: Boolean) = update { putBoolean(KEY_SMART_INPUT, enabled) }
    fun setNotifyAtDue(enabled: Boolean) = update { putBoolean(KEY_NOTIFY_AT_DUE, enabled) }
    fun setDailyDigest(enabled: Boolean) = update { putBoolean(KEY_DAILY_DIGEST, enabled) }
    fun setDigestMinutes(minutes: Int) = update { putInt(KEY_DIGEST_MINUTES, minutes) }
    fun setDefaultListId(id: String?) = update { if (id == null) remove(KEY_DEFAULT_LIST) else putString(KEY_DEFAULT_LIST, id) }

    /** Devuelve true solo la primera vez que se llama con [key] (para ajustes que se aplican una vez). */
    fun claimOnce(key: String): Boolean {
        if (prefs.getBoolean("once_$key", false)) return false
        prefs.edit { putBoolean("once_$key", true) }
        return true
    }

    /** Las listas de ejemplo se crean una sola vez (si el usuario las borra, no vuelven). */
    fun claimStarterLists(): Boolean {
        if (prefs.getBoolean(KEY_STARTER_CREATED, false)) return false
        prefs.edit { putBoolean(KEY_STARTER_CREATED, true) }
        return true
    }

    companion object {
        const val PREFS = "mistareas_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_SMART_INPUT = "smart_input"
        private const val KEY_DEFAULT_LIST = "default_list"
        private const val KEY_NOTIFY_AT_DUE = "notify_at_due"
        private const val KEY_DAILY_DIGEST = "daily_digest"
        private const val KEY_DIGEST_MINUTES = "digest_minutes"
        private const val KEY_STARTER_CREATED = "starter_lists_created"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }
    }
}
