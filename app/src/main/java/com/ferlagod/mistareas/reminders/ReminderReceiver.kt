// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ferlagod.mistareas.data.TaskRepository
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Llega la hora de un aviso o del resumen diario, o se pulsa "Hecha" / "Posponer" en la notificación. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (action != ACTION_DIGEST && taskId == null) return
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_DIGEST -> {
                        Reminders.showDigest(context)
                        Reminders.scheduleDigest(context)
                    }
                    ACTION_FIRE -> fire(context, taskId!!, intent.getLongExtra(EXTRA_AT, -1L).takeIf { it > 0 })
                    ACTION_DONE -> {
                        Reminders.dismiss(context, taskId!!)
                        TaskRepository.getInstance(context).setDone(taskId, true)
                        SyncWorker.enqueueWhenOnline(context)
                    }
                    ACTION_SNOOZE, ACTION_SNOOZE_SHORT -> {
                        Reminders.dismiss(context, taskId!!)
                        val delay = if (action == ACTION_SNOOZE_SHORT) SNOOZE_SHORT_MS else SNOOZE_MS
                        TaskRepository.getInstance(context).setReminder(taskId, System.currentTimeMillis() + delay)
                        SyncWorker.enqueueWhenOnline(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, taskId: String, scheduledAt: Long?) {
        val db = AppDatabase.getInstance(context)
        val task = db.taskDao().get(taskId) ?: return
        val at = Reminders.triggerAt(context, task) ?: return
        // Alarma antigua (la tarea cambió desde que se programó): no se avisa.
        if (scheduledAt != null && Math.abs(scheduledAt - at) >= 60_000) return
        if (Math.abs(System.currentTimeMillis() - at) > STALE_MS) return
        val list = db.listDao().get(task.listId) ?: return
        if (list.deleted) return
        Reminders.show(context, task, list.name, list.readOnly, at)
    }

    companion object {
        const val ACTION_FIRE = "com.ferlagod.mistareas.reminder.FIRE"
        const val ACTION_DONE = "com.ferlagod.mistareas.reminder.DONE"
        const val ACTION_SNOOZE = "com.ferlagod.mistareas.reminder.SNOOZE"
        const val ACTION_SNOOZE_SHORT = "com.ferlagod.mistareas.reminder.SNOOZE_SHORT"
        const val ACTION_DIGEST = "com.ferlagod.mistareas.reminder.DIGEST"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_AT = "at"
        private const val SNOOZE_MS = 60 * 60 * 1000L
        private const val SNOOZE_SHORT_MS = 10 * 60 * 1000L
        private const val STALE_MS = 6 * 60 * 60 * 1000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * Tras reiniciar el móvil, actualizar la app, cambiar la hora o la zona horaria, o cambiar el
 * permiso de alarmas exactas, se reprograman los avisos (y al encender, se muestran los perdidos).
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.rescheduleAll(context, showMissed = intent.action == Intent.ACTION_BOOT_COMPLETED)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
