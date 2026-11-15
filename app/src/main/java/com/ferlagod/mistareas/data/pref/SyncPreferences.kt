// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

@file:Suppress("DEPRECATION") // security-crypto está deprecado pero no hay sustituto estable equivalente

package com.ferlagod.mistareas.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde se guardan las tareas además de en el dispositivo. */
enum class SyncMode {
    /** Solo en este dispositivo. */
    NONE,

    /** Nextcloud Tasks u otro servidor CalDAV: cada lista es un calendario de tareas. */
    CALDAV,

    /** Una carpeta WebDAV con un fichero Markdown por lista (compatible con Obsidian Tasks). */
    WEBDAV
}

/** Cuenta configurada. [endpoint] es la URL ya resuelta (carpeta WebDAV o raíz de calendarios CalDAV). */
data class SyncAccount(
    val mode: SyncMode,
    val serverUrl: String,
    val username: String,
    val password: String,
    val folder: String,
    val endpoint: String
)

/**
 * Configuración de sincronización en EncryptedSharedPreferences (clave en el Android Keystore).
 */
class SyncPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<SyncAccount?> = _account.asStateFlow()

    private val _lastSync = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 })
    val lastSync: StateFlow<Long?> = _lastSync.asStateFlow()

    private fun readAccount(): SyncAccount? {
        val mode = SyncMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: return null
        if (mode == SyncMode.NONE) return null
        return SyncAccount(
            mode = mode,
            serverUrl = prefs.getString(KEY_SERVER, null) ?: return null,
            username = prefs.getString(KEY_USER, null) ?: return null,
            password = prefs.getString(KEY_PASSWORD, null) ?: return null,
            folder = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER,
            endpoint = prefs.getString(KEY_ENDPOINT, null) ?: return null
        )
    }

    fun saveAccount(account: SyncAccount) {
        prefs.edit {
            putString(KEY_MODE, account.mode.name)
            putString(KEY_SERVER, account.serverUrl)
            putString(KEY_USER, account.username)
            putString(KEY_PASSWORD, account.password)
            putString(KEY_FOLDER, account.folder)
            putString(KEY_ENDPOINT, account.endpoint)
            remove(KEY_LAST_SYNC)
        }
        _account.value = readAccount()
        _lastSync.value = null
    }

    fun clearAccount() {
        prefs.edit {
            remove(KEY_MODE); remove(KEY_SERVER); remove(KEY_USER); remove(KEY_PASSWORD)
            remove(KEY_FOLDER); remove(KEY_ENDPOINT); remove(KEY_LAST_SYNC)
        }
        _account.value = null
        _lastSync.value = null
    }

    fun setLastSync(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC, timestamp) }
        _lastSync.value = timestamp
    }

    companion object {
        private const val TAG = "SyncPreferences"
        private const val PREFS_FILENAME = "mistareas_secure_sync"
        private const val KEY_MODE = "mode"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "app_password"
        private const val KEY_FOLDER = "folder"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_LAST_SYNC = "last_sync"
        const val DEFAULT_FOLDER = "MisTareas"

        @Volatile
        private var INSTANCE: SyncPreferences? = null

        fun getInstance(context: Context): SyncPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncPreferences(context).also { INSTANCE = it }
            }

        /**
         * Si el Keystore se ha perdido (p. ej. tras restaurar en otro dispositivo), el fichero cifrado
         * es ilegible: se borra y se crea de nuevo (habrá que volver a conectar la cuenta).
         */
        private fun createPrefs(context: Context): SharedPreferences {
            fun build(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILENAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
            return try {
                build()
            } catch (e: Exception) {
                Log.w(TAG, "Preferencias seguras ilegibles; se reinician", e)
                context.deleteSharedPreferences(PREFS_FILENAME)
                build()
            }
        }
    }
}
