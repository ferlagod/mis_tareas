// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.ferlagod.mistareas.MainActivity
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.AppDatabase
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.pref.AppSettings
import com.ferlagod.mistareas.util.LanguageManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Avisos de tareas con AlarmManager.
 *
 * - Una tarea avisa a la hora de su recordatorio o, si no tiene, a su hora límite
 *   (ajuste "Avisar a la hora límite").
 * - Con permiso de "alarmas exactas" suenan al minuto; sin él, Android puede retrasarlos unos minutos.
 * - Las alarmas se recalculan cada vez que cambian las tareas (ver [com.ferlagod.mistareas.MisTareasApp]),
 *   al reiniciar el móvil y al cambiar la hora o la zona horaria ([SystemEventsReceiver]).
 * - Los avisos que tocaban con el móvil apagado se muestran al encenderlo.
 * - Además hay un resumen diario con las tareas de hoy y las vencidas.
 */
object Reminders {

    const val CHANNEL_ID = "reminders"
    const val DIGEST_CHANNEL_ID = "daily_digest"
    private const val PREFS = "mistareas_reminders"
    private const val KEY_SCHEDULED = "scheduled_ids"
    private const val KEY_FIRED = "fired"
    private const val DIGEST_NOTIFICATION_ID = 0x4d54
    /** Al encender el móvil se recuperan los avisos perdidos de las últimas horas. */
    private const val MISSED_WINDOW_MS = 24 * 60 * 60 * 1000L

    fun createChannel(context: Context) {
        val localized = LanguageManager.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, localized.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = localized.getString(R.string.notif_channel_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(DIGEST_CHANNEL_ID, localized.getString(R.string.digest_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = localized.getString(R.string.digest_channel_desc)
            }
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Cuándo avisa una tarea: su recordatorio o, si no tiene y está activado, su hora límite. */
    fun triggerAt(task: TaskEntity, notifyAtDue: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (task.done || task.deleted) return null
        task.reminderAt?.let { return it }
        if (!notifyAtDue) return null
        val day = task.dueDay ?: return null
        val minutes = task.dueMinutes ?: return null
        return LocalDate.ofEpochDay(day).atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
    }

    fun triggerAt(context: Context, task: TaskEntity): Long? =
        triggerAt(task, AppSettings.getInstance(context).state.value.notifyAtDue)

    private fun fireIntent(context: Context, taskId: String, at: Long? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData("mistareas://reminder/$taskId".toUri())
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        if (at != null) intent.putExtra(ReminderReceiver.EXTRA_AT, at)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            // Permiso de alarmas exactas retirado justo ahora.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /**
     * Programa los avisos futuros de [tasks] y anula los que ya no corresponden
     * (tarea completada, borrada o aviso quitado).
     */
    fun schedule(context: Context, tasks: List<TaskEntity>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val notifyAtDue = AppSettings.getInstance(context).state.value.notifyAtDue
        val upcoming = tasks.mapNotNull { task -> triggerAt(task, notifyAtDue)?.takeIf { it > now }?.let { task to it } }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val ids = upcoming.mapTo(HashSet()) { it.first.id }

        (previous - ids).forEach { alarms.cancel(fireIntent(context, it)) }
        upcoming.forEach { (task, at) -> setAlarm(context, at, fireIntent(context, task.id, at)) }
        // Solo se recuerdan los avisos ya mostrados de tareas que siguen pendientes.
        val alive = tasks.mapTo(HashSet()) { it.id }
        val fired = prefs.getStringSet(KEY_FIRED, emptySet()).orEmpty().filterTo(HashSet()) { it.substringBefore('@') in alive }
        prefs.edit {
            putStringSet(KEY_SCHEDULED, ids)
            putStringSet(KEY_FIRED, fired)
        }
    }

    /**
     * Vuelve a programar todo desde la base de datos (arranque del móvil, cambio de hora o de permisos).
     * Con [showMissed], muestra los avisos que tocaban mientras el móvil estaba apagado.
     */
    suspend fun rescheduleAll(context: Context, showMissed: Boolean = false) {
        val db = AppDatabase.getInstance(context)
        val tasks = db.taskDao().getWithReminder()
        if (showMissed) {
            val now = System.currentTimeMillis()
            val notifyAtDue = AppSettings.getInstance(context).state.value.notifyAtDue
            tasks.forEach { task ->
                val at = triggerAt(task, notifyAtDue) ?: return@forEach
                if (at <= now && now - at < MISSED_WINDOW_MS && !wasShown(context, task.id, at)) {
                    val list = db.listDao().get(task.listId) ?: return@forEach
                    show(context, task, list.name, list.readOnly, at)
                }
            }
        }
        schedule(context, tasks)
        scheduleDigest(context)
    }

    private fun firedKey(taskId: String, at: Long) = "$taskId@${at / 60_000}"

    fun wasShown(context: Context, taskId: String, at: Long): Boolean =
        firedKey(taskId, at) in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_FIRED, emptySet()).orEmpty()

    private fun markShown(context: Context, taskId: String, at: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fired = prefs.getStringSet(KEY_FIRED, emptySet()).orEmpty().toMutableSet()
        fired.removeAll { it.substringBefore('@') == taskId }
        fired += firedKey(taskId, at)
        prefs.edit { putStringSet(KEY_FIRED, fired) }
    }

    fun notificationId(taskId: String) = taskId.hashCode()

    /** Muestra el aviso de una tarea con las acciones "Hecha" y "Posponer". */
    fun show(context: Context, task: TaskEntity, listName: String, readOnly: Boolean, at: Long) {
        if (!notificationsAllowed(context)) return
        markShown(context, task.id, at)
        val ctx = LanguageManager.localizedContext(context)
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TASK)
            .setData("mistareas://task/${task.id}".toUri())
            .putExtra(MainActivity.EXTRA_LIST_ID, task.listId)
            .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun action(action: String) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData("mistareas://$action/${task.id}".toUri())
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = task.notes.lineSequence().firstOrNull { it.isNotBlank() }?.let { "$listName · $it" } ?: listName
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB8501A.toInt())
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (task.notes.isBlank()) listName else "$listName\n${task.notes}"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(at)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        // En una lista compartida de solo lectura no se puede completar.
        if (!readOnly) builder.addAction(0, ctx.getString(R.string.notif_action_done), action(ReminderReceiver.ACTION_DONE))
        builder.addAction(0, ctx.getString(R.string.notif_action_snooze_short), action(ReminderReceiver.ACTION_SNOOZE_SHORT))
        builder.addAction(0, ctx.getString(R.string.notif_action_snooze), action(ReminderReceiver.ACTION_SNOOZE))
        try {
            NotificationManagerCompat.from(context).notify(notificationId(task.id), builder.build())
        } catch (_: SecurityException) {
            // Permiso retirado entre la comprobación y el aviso.
        }
    }

    fun dismiss(context: Context, taskId: String) = NotificationManagerCompat.from(context).cancel(notificationId(taskId))

    // ------------------------------------------------------------------
    // Resumen diario
    // ------------------------------------------------------------------

    private fun digestIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DIGEST).setData("mistareas://digest".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Programa (o anula) el próximo resumen diario según los ajustes. */
    fun scheduleDigest(context: Context) {
        val settings = AppSettings.getInstance(context).state.value
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = digestIntent(context)
        if (!settings.dailyDigest) {
            alarms.cancel(pi)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atStartOfDay().plusMinutes(settings.digestMinutes.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        setAlarm(context, next.atZone(zone).toInstant().toEpochMilli(), pi)
    }

    /** Muestra el resumen con las tareas pendientes de hoy y las vencidas (si hay alguna). */
    suspend fun showDigest(context: Context) {
        if (!AppSettings.getInstance(context).state.value.dailyDigest || !notificationsAllowed(context)) return
        val tasks = AppDatabase.getInstance(context).taskDao().getDueUntil(LocalDate.now().toEpochDay())
        if (tasks.isEmpty()) return
        val ctx = LanguageManager.localizedContext(context)
        val today = LocalDate.now().toEpochDay()
        val overdue = tasks.count { (it.task.dueDay ?: today) < today }
        val title = ctx.resources.getQuantityString(R.plurals.digest_title, tasks.size, tasks.size)
        val summary = if (overdue > 0) ctx.resources.getQuantityString(R.plurals.digest_overdue, overdue, overdue) else null
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        tasks.take(6).forEach { style.addLine("${it.task.title} · ${it.listName}") }
        if (tasks.size > 6) style.setSummaryText("+${tasks.size - 6}") else summary?.let(style::setSummaryText)

        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB8501A.toInt())
            .setContentTitle(title)
            .setContentText(summary ?: tasks.take(3).joinToString(", ") { it.task.title })
            .setStyle(style)
            .setNumber(tasks.size)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
