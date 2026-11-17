// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.pref.SyncAccount
import com.ferlagod.mistareas.data.pref.SyncMode
import com.ferlagod.mistareas.data.pref.SyncPreferences
import com.ferlagod.mistareas.data.remote.DavAuthException
import com.ferlagod.mistareas.data.remote.DavClient
import com.ferlagod.mistareas.data.remote.DavException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Una forma de sincronizar (CalDAV o WebDAV + Markdown). */
interface SyncEngine {
    suspend fun sync()
}

/** Tipo de error, para mostrar un mensaje comprensible. */
enum class SyncError { AUTH, NETWORK, NOT_FOUND, INVALID_URL, SERVER, UNKNOWN }

sealed interface SyncState {
    data object Disabled : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Failed(val error: SyncError, val code: Int? = null) : SyncState
}

fun Throwable.toSyncError(): SyncError = when (this) {
    is DavAuthException -> SyncError.AUTH
    is ServiceNotFoundException -> SyncError.NOT_FOUND
    is InvalidUrlException -> SyncError.INVALID_URL
    is DavException -> if (code == 404 || code == 409) SyncError.NOT_FOUND else SyncError.SERVER
    is javax.net.ssl.SSLException -> SyncError.NETWORK
    is IOException -> SyncError.NETWORK
    else -> SyncError.UNKNOWN
}

/**
 * Punto único de sincronización: una sola a la vez (mutex), con estado observable,
 * agrupando los cambios rápidos (al tachar varias tareas seguidas se sincroniza una vez).
 */
class SyncRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = SyncPreferences.getInstance(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private val _state = MutableStateFlow<SyncState>(if (prefs.account.value == null) SyncState.Disabled else SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val account: StateFlow<SyncAccount?> get() = prefs.account
    val lastSync: StateFlow<Long?> get() = prefs.lastSync

    private fun engineFor(account: SyncAccount): SyncEngine {
        val client = DavClient(account.username, account.password)
        return when (account.mode) {
            SyncMode.CALDAV -> CalDavSyncEngine(db, client, account.endpoint)
            SyncMode.WEBDAV -> WebDavSyncEngine(db, client, account.endpoint)
            SyncMode.NONE -> error("No sync")
        }
    }

    suspend fun synchronize(onLocked: () -> Unit = {}): Result<Unit> = mutex.withLock {
        onLocked()
        val account = prefs.account.value ?: run {
            _state.value = SyncState.Disabled
            return Result.success(Unit)
        }
        _state.value = SyncState.Syncing
        try {
            engineFor(account).sync()
            prefs.setLastSync(System.currentTimeMillis())
            _state.value = SyncState.Idle
            Result.success(Unit)
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error de sincronización", e)
            _state.value = SyncState.Failed(e.toSyncError(), (e as? DavException)?.code)
            Result.failure(e)
        }
    }

    /**
     * Pide una sincronización en breve. Las peticiones seguidas se agrupan; si no hay red,
     * WorkManager lo reintenta cuando vuelva.
     *
     * Una petición nueva solo anula la espera de la anterior: una sincronización ya empezada
     * termina (cortarla a medias repetiría el trabajo y, con cambios cada pocos segundos, podría
     * no completarse nunca). La nueva espera al mutex y recoge lo que haya cambiado mientras;
     * si ya hay otra esperando, basta con esa.
     */
    fun requestSync(delayMs: Long = 1500) {
        if (prefs.account.value == null) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            if (!queued.compareAndSet(false, true)) return@launch
            withContext(NonCancellable) {
                val result = try {
                    synchronize(onLocked = { queued.set(false) })
                } finally {
                    queued.set(false)
                }
                if (result.exceptionOrNull()?.toSyncError() == SyncError.NETWORK) SyncWorker.enqueueWhenOnline(context)
            }
        }
    }

    /** Hay una sincronización pedida esperando a que acabe la que está en curso. */
    private val queued = AtomicBoolean(false)

    /** Comprueba la cuenta, la guarda y lanza la primera sincronización. */
    suspend fun connect(mode: SyncMode, server: String, username: String, password: String, folder: String): Result<Unit> {
        val account = try {
            AccountConnector.connect(mode, server, username, password, folder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo conectar", e)
            return Result.failure(e)
        }
        mutex.withLock {
            val previous = prefs.account.value
            val sameTarget = previous != null && previous.mode == account.mode && previous.endpoint == account.endpoint &&
                previous.username == account.username
            if (!sameTarget) {
                resetRemoteState()
                removePlaceholderLists()
            }
            prefs.saveAccount(account)
            _state.value = SyncState.Idle
        }
        SyncWorker.schedulePeriodic(context)
        return synchronize()
    }

    /** Desconecta la cuenta; las tareas se quedan en el dispositivo. */
    suspend fun disconnect() = mutex.withLock {
        pending?.cancel()
        prefs.clearAccount()
        SyncWorker.cancelAll(context)
        resetRemoteState()
        _state.value = SyncState.Disabled
    }

    /**
     * Olvida todo lo relacionado con el servidor anterior: las listas y tareas quedan como
     * nuevas y se subirán a la próxima cuenta. Los borrados pendientes se descartan.
     */
    private suspend fun resetRemoteState() = db.withTransaction {
        val listDao = db.listDao()
        val taskDao = db.taskDao()
        for (list in listDao.getAll()) {
            if (list.deleted) {
                taskDao.hardDeleteForList(list.id)
                listDao.hardDelete(list.id)
                continue
            }
            listDao.upsert(list.copy(remoteHref = null, remoteTag = null, remoteRaw = null, syncToken = null, dirty = true))
            for (task in taskDao.getAllForList(list.id)) {
                if (task.deleted) taskDao.hardDelete(task.id)
                else taskDao.upsert(task.copy(remoteHref = null, remoteEtag = null, synced = false, dirty = true))
            }
        }
    }

    /** Las listas de ejemplo vacías no se suben al servidor (evita crear calendarios sin pedirlo). */
    private suspend fun removePlaceholderLists() = db.withTransaction {
        val lists = db.listDao().getAll()
        val allEmpty = lists.all { db.taskDao().getAllForList(it.id).isEmpty() }
        if (allEmpty) lists.forEach { db.listDao().hardDelete(it.id) }
    }

    companion object {
        private const val TAG = "SyncRepository"

        @Volatile
        private var INSTANCE: SyncRepository? = null

        fun getInstance(context: Context): SyncRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
