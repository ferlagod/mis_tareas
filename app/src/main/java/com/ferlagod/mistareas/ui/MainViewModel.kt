// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.TaskRepository
import com.ferlagod.mistareas.data.local.ListSummary
import com.ferlagod.mistareas.data.local.SortMode
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.local.TaskWithList
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.data.pref.AppSettings
import com.ferlagod.mistareas.data.pref.SyncMode
import com.ferlagod.mistareas.data.pref.ThemeMode
import com.ferlagod.mistareas.data.remote.DavException
import com.ferlagod.mistareas.data.sync.SyncError
import com.ferlagod.mistareas.data.sync.SharingService
import com.ferlagod.mistareas.data.sync.SharingUnavailable
import com.ferlagod.mistareas.data.sync.SyncRepository
import com.ferlagod.mistareas.data.sync.toSyncError
import com.ferlagod.mistareas.domain.QuickAddParser
import com.ferlagod.mistareas.util.LanguageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.ferlagod.mistareas.domain.ShopCategory
import com.ferlagod.mistareas.reminders.Reminders

/** Vistas que reúnen tareas de todas las listas. */
enum class SmartView { TODAY, IMPORTANT }

/** Avisos puntuales para la interfaz. */
sealed interface UiEvent {
    data class TasksDeleted(val tasks: List<TaskEntity>) : UiEvent
    data class Message(val text: String) : UiEvent
    /** Se ha completado una tarea que se repite: vuelve para esta fecha. */
    data class NextOccurrence(val previous: TaskEntity, val due: LocalDate?) : UiEvent
    /** Se ha puesto un aviso pero la app no puede mostrar notificaciones. */
    data object RequestNotificationPermission : UiEvent
    /** Sin permiso de alarmas exactas, Android puede retrasar los avisos mucho. */
    data object RequestExactAlarms : UiEvent
}

/** Abrir una tarea desde su notificación. */
data class OpenTask(val listId: String, val taskId: String)

/** Texto compartido desde otra app, pendiente de decidir a qué lista va. */
data class SharedText(val lines: List<String>)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository.getInstance(app)
    private val sync = SyncRepository.getInstance(app)
    private val appSettings = AppSettings.getInstance(app)
    private val sharing = SharingService.getInstance(app)

    val settings = appSettings.state
    val syncState = sync.state
    val account = sync.account
    val lastSync = sync.lastSync

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val summaries: StateFlow<List<ListSummary>> = repo.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<TaskListEntity>> = repo.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTasks: StateFlow<List<TaskWithList>> = _today.flatMapLatest { repo.observeToday(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importantTasks: StateFlow<List<TaskWithList>> = repo.observeImportant()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Texto recibido con "Compartir" o petición de "Nueva tarea" desde el acceso directo. */
    val pendingShare = MutableStateFlow<SharedText?>(null)
    val pendingNewTask = MutableStateFlow(false)
    val pendingOpenTask = MutableStateFlow<OpenTask?>(null)
    val pendingOpenToday = MutableStateFlow(false)

    /** Secciones del súper que el usuario ha elegido a mano (lo aprendido). */
    val categoryOverrides: StateFlow<Map<String, ShopCategory>> = repo.observeCategoryOverrides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        if (appSettings.claimOnce("shopping_sections_default")) {
            viewModelScope.launch { repo.applyShoppingSectionsDefault() }
        }
        if (sync.account.value == null && appSettings.claimStarterLists()) {
            viewModelScope.launch {
                val ctx = LanguageManager.localizedContext(app)
                repo.ensureStarterLists(ctx.getString(R.string.starter_list_shopping), ctx.getString(R.string.starter_list_tasks))
            }
        }
    }

    /** Al volver a la app: el día puede haber cambiado y conviene traer cambios de otros dispositivos. */
    fun onAppForeground() {
        refreshToday()
        sync.requestSync(delayMs = 0)
    }

    /** Actualiza "hoy" si ha cambiado el día (StateFlow no emite si es el mismo). */
    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    fun triggerSync() = sync.requestSync(delayMs = 0)

    fun listFlow(id: String): Flow<TaskListEntity?> = repo.observeList(id)
    fun tasksFlow(listId: String): Flow<List<TaskEntity>> = repo.observeTasks(listId)
    fun taskFlow(id: String): Flow<TaskEntity?> = repo.observeTask(id)
    fun searchFlow(query: String): Flow<List<TaskWithList>> = repo.search(query)

    /** Lista donde caen las tareas creadas desde Inicio, "Hoy", "Importantes" o "Compartir". */
    fun defaultListId(): String? {
        val all = lists.value.filter { !it.readOnly }
        return settings.value.defaultListId?.takeIf { id -> all.any { it.id == id } } ?: all.firstOrNull()?.id
    }

    // ------------------------------------------------------------------
    // Tareas
    // ------------------------------------------------------------------

    /**
     * Añade lo escrito en la barra rápida. Varias líneas (texto pegado) son varias tareas.
     * Con la entrada inteligente activada se entienden "mañana", "lunes", "15/11" o "!".
     */
    fun quickAdd(listId: String, text: String, smart: Boolean, forceDue: LocalDate? = null, forceImportant: Boolean = false) {
        val lines = splitLines(text)
        if (lines.isEmpty()) return
        viewModelScope.launch {
            if (lines.size > 1 && !smart && forceDue == null && !forceImportant) {
                repo.addTasks(listId, lines)
                return@launch
            }
            val now = LocalTime.now()
            var withReminder = false
            for (line in lines) {
                val parsed = if (smart && settings.value.smartInput) QuickAddParser.parse(line, today.value) else QuickAddParser.Result(line)
                // "a las 17": aviso a esa hora el día escrito o, si no se dijo, hoy (o mañana si ya pasó).
                val reminder = parsed.reminder(today.value, now)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                val due = parsed.effectiveDue(today.value, now) ?: forceDue
                repo.addTask(listId, parsed.title, due, parsed.important || forceImportant, reminderAt = reminder, rrule = parsed.rrule, tags = parsed.tags)
                if (reminder != null) withReminder = true
            }
            if (withReminder) checkNotificationPermission()
        }
    }

    fun setDone(id: String, done: Boolean) = viewModelScope.launch {
        // Una tarea que se repite no se completa: pasa a su siguiente fecha.
        repo.setDone(id, done)?.let { _events.emit(UiEvent.NextOccurrence(it.previous, it.nextDue)) }
    }
    fun undoAdvance(previous: TaskEntity) = viewModelScope.launch { repo.undoAdvance(previous) }
    fun addSubtask(parentId: String, title: String) = viewModelScope.launch { repo.addSubtask(parentId, title.trim()) }
    fun setTags(id: String, tags: List<String>) = viewModelScope.launch { repo.setTags(id, tags) }
    fun childrenFlow(task: TaskEntity): Flow<List<TaskEntity>> = repo.observeChildren(task)
    fun parentFlow(task: TaskEntity): Flow<TaskEntity?> = repo.observeParent(task)
    val allTags: StateFlow<List<String>> = repo.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setRepeat(id: String, rrule: String?) = viewModelScope.launch { repo.setRepeat(id, rrule) }
    fun setDueTime(id: String, minutes: Int?) = viewModelScope.launch {
        repo.setDueTime(id, minutes)
        if (minutes != null && settings.value.notifyAtDue) checkNotificationPermission()
    }
    fun setImportant(id: String, important: Boolean) = viewModelScope.launch { repo.setImportant(id, important) }
    fun setDue(id: String, due: LocalDate?) = viewModelScope.launch { repo.setDue(id, due) }
    fun setReminder(id: String, at: Long?) = viewModelScope.launch {
        repo.setReminder(id, at)
        if (at != null) checkNotificationPermission()
    }
    fun setShopCategory(title: String, category: ShopCategory) = viewModelScope.launch { repo.setShopCategory(title, category) }

    private var exactAlarmsAsked = false

    /** Tras permitir las notificaciones, se comprueba el permiso de alarmas exactas. */
    fun onNotificationsGranted() = viewModelScope.launch { checkNotificationPermission() }

    private suspend fun checkNotificationPermission() {
        if (!Reminders.notificationsAllowed(getApplication())) _events.emit(UiEvent.RequestNotificationPermission)
        else if (!exactAlarmsAsked && !Reminders.canScheduleExact(getApplication())) {
            // Una vez por sesión, para no insistir.
            exactAlarmsAsked = true
            _events.emit(UiEvent.RequestExactAlarms)
        }
    }
    fun saveTaskText(id: String, title: String, notes: String) = viewModelScope.launch {
        repo.getTask(id)?.let { repo.updateTask(it.copy(title = title, notes = notes)) }
    }
    fun moveTask(id: String, listId: String) = viewModelScope.launch { repo.moveTask(id, listId) }
    fun reorderTasks(ids: List<String>) = viewModelScope.launch { repo.reorderTasks(ids) }

    fun deleteTasks(ids: List<String>) = viewModelScope.launch {
        val removed = repo.deleteTasks(ids)
        if (removed.isNotEmpty()) _events.emit(UiEvent.TasksDeleted(removed))
    }

    fun clearCompleted(listId: String) = viewModelScope.launch {
        val removed = repo.clearCompleted(listId)
        if (removed.isNotEmpty()) _events.emit(UiEvent.TasksDeleted(removed))
    }

    fun restoreTasks(tasks: List<TaskEntity>) = viewModelScope.launch { repo.restoreTasks(tasks) }

    /** Añade el texto compartido (una tarea por línea) a una lista. */
    fun addShared(listId: String, lines: List<String>) = viewModelScope.launch {
        repo.addTasks(listId, lines)
        pendingShare.value = null
        val name = lists.value.firstOrNull { it.id == listId }?.name.orEmpty()
        val res = getApplication<Application>().let { LanguageManager.localizedContext(it).resources }
        _events.emit(UiEvent.Message(res.getQuantityString(R.plurals.tasks_added_to, lines.size, lines.size, name)))
    }

    fun receiveSharedText(text: String?) {
        val lines = splitLines(text.orEmpty())
        if (lines.isNotEmpty()) pendingShare.value = SharedText(lines)
    }

    // ------------------------------------------------------------------
    // Listas
    // ------------------------------------------------------------------

    fun createList(name: String, color: ListColor, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        onCreated(repo.createList(name, color))
    }

    fun updateList(id: String, name: String, color: ListColor) = viewModelScope.launch { repo.updateList(id, name, color) }
    fun deleteList(id: String) = viewModelScope.launch {
        repo.deleteList(id)
        if (settings.value.defaultListId == id) appSettings.setDefaultListId(null)
    }
    fun setSortMode(id: String, mode: SortMode) = viewModelScope.launch { repo.setSortMode(id, mode) }
    fun reorderLists(ids: List<String>) = viewModelScope.launch { repo.reorderLists(ids) }

    // ------------------------------------------------------------------
    // Ajustes y cuenta
    // ------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    fun setAmoled(enabled: Boolean) = appSettings.setAmoled(enabled)
    fun setSmartInput(enabled: Boolean) = appSettings.setSmartInput(enabled)
    fun setDefaultList(id: String?) = appSettings.setDefaultListId(id)
    fun setNotifyAtDue(enabled: Boolean) = appSettings.setNotifyAtDue(enabled)
    fun setDailyDigest(enabled: Boolean) {
        appSettings.setDailyDigest(enabled)
        if (enabled) viewModelScope.launch { checkNotificationPermission() }
    }
    fun setDigestMinutes(minutes: Int) = appSettings.setDigestMinutes(minutes)

    /** Conecta una cuenta. [onResult] recibe null si todo fue bien, o el tipo de error y el código HTTP. */
    fun connect(mode: SyncMode, server: String, user: String, password: String, folder: String, onResult: (SyncError?, Int?) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = sync.connect(mode, server, user, password, folder)
            _busy.value = false
            val error = result.exceptionOrNull()
            onResult(error?.toSyncError(), (error as? DavException)?.code)
        }
    }

    fun disconnect() = viewModelScope.launch { sync.disconnect() }

    // ------------------------------------------------------------------
    // Compartir listas (Nextcloud)
    // ------------------------------------------------------------------

    fun sharingUnavailable(list: TaskListEntity): SharingUnavailable? = sharing.unavailableReason(list)
    suspend fun loadSharees(listId: String) = runCatching { sharing.sharees(listId) }
    suspend fun searchPeople(query: String) = runCatching { sharing.search(query) }
    suspend fun shareList(listId: String, principal: String, canEdit: Boolean) = runCatching { sharing.share(listId, principal, canEdit) }
    suspend fun unshareList(listId: String, principal: String) = runCatching { sharing.unshare(listId, principal) }

    companion object {
        private val BULLET = Regex("""^\s*(?:[-*+•·▪◦]\s*(?:\[[ xX]]\s*)?|\d+[.)]\s+|☐\s*|☑\s*|✓\s*)""")

        /** Una tarea por línea, sin viñetas ni casillas de Markdown. */
        fun splitLines(text: String): List<String> =
            text.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }
    }
}
