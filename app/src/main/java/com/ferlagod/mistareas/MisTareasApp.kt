// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas

import android.app.Application
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.pref.AppSettings
import com.ferlagod.mistareas.data.pref.SyncPreferences
import com.ferlagod.mistareas.data.sync.SyncWorker
import com.ferlagod.mistareas.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MisTareasApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        // Cada cambio en las tareas con aviso (desde la app, una notificación o la sincronización)
        // reprograma las alarmas.
        val settings = AppSettings.getInstance(this).state
        scope.launch {
            combine(
                AppDatabase.getInstance(this@MisTareasApp).taskDao().observeWithReminder(),
                settings.map { it.notifyAtDue }.distinctUntilChanged()
            ) { tasks, _ -> tasks }
                .debounce(300)
                .collect { Reminders.schedule(this@MisTareasApp, it) }
        }
        // Resumen diario: se reprograma al arrancar y al cambiar su ajuste.
        scope.launch {
            settings.map { it.dailyDigest to it.digestMinutes }.distinctUntilChanged()
                .collect { Reminders.scheduleDigest(this@MisTareasApp) }
        }
        // Sincronización periódica en segundo plano si hay una cuenta conectada.
        if (SyncPreferences.getInstance(this).account.value != null) SyncWorker.schedulePeriodic(this)
    }
}
