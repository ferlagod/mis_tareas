// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.data.sync.SyncState
import com.ferlagod.mistareas.ui.theme.AccentGreen
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.ui.theme.AccentStar
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Ancho máximo cómodo de lectura en tablets y plegables. */
val READABLE_MAX_WIDTH = 720.dp

/** Etiqueta de grupo en mayúsculas, como en Mis Podcast y Mis Notas. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
fun RowDivider(start: Dp) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = start)
    )
}

/** Forma de cada fila dentro de un grupo (esquinas redondeadas solo en los extremos). */
fun groupShape(index: Int, count: Int, radius: Dp = 14.dp) = when {
    count == 1 -> RoundedCornerShape(radius)
    index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
    index == count - 1 -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    else -> RoundedCornerShape(0.dp)
}

/** Estado vacío con icono grande, título y explicación. */
@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier, message: String? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier = modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = tint.copy(alpha = 0.10f), modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Icono del estado de sincronización (gira mientras sincroniza). */
@Composable
fun SyncStatusIconButton(state: SyncState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sync")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    val (icon, tint, description) = when (state) {
        SyncState.Disabled -> Triple(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), stringResource(R.string.sync_configure))
        SyncState.Idle -> Triple(Icons.Rounded.CloudDone, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.sync_now))
        SyncState.Syncing -> Triple(Icons.Rounded.Sync, MaterialTheme.colorScheme.primary, stringResource(R.string.sync_in_progress))
        is SyncState.Failed -> Triple(Icons.Rounded.ErrorOutline, AccentRed, stringResource(R.string.sync_error))
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (state == SyncState.Syncing) angle else 0f)
        )
    }
}

/**
 * Casilla redonda del color de la lista. Al marcarla se rellena con un pequeño rebote.
 */
@Composable
fun CheckCircle(checked: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val fill by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "fill")
    val doneLabel = stringResource(R.string.cd_done)
    val pendingLabel = stringResource(R.string.cd_pending)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (checked) doneLabel else pendingLabel },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, color), CircleShape)
                .background(color.copy(alpha = fill)),
            contentAlignment = Alignment.Center
        ) {
            if (fill > 0.01f) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.6f) Color.Black else Color.White,
                    modifier = Modifier
                        .size(size * 0.7f)
                        .scale(fill)
                )
            }
        }
    }
}

/**
 * Fila de una tarea.
 *
 * Al marcarla como hecha se ve la casilla rellenarse y el texto tacharse un instante
 * antes de pasar a "Completadas", para que el gesto se note.
 *
 * @param listName si no es null se muestra (vistas "Hoy", "Importantes" y búsqueda).
 */
@Composable
fun TaskRow(
    task: TaskEntity,
    accent: Color,
    today: LocalDate,
    onToggleDone: (Boolean) -> Unit,
    onToggleImportant: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    listName: String? = null,
    listColor: Color? = null,
    /** false en listas compartidas conmigo sin permiso de edición. */
    enabled: Boolean = true,
    /** Nivel de subtarea (0 = tarea normal). */
    indent: Int = 0,
    /** Subtareas (hechas, total), si las tiene. */
    subtasks: Pair<Int, Int>? = null
) {
    // La fecha forma parte de la clave: una tarea que se repite no se completa, cambia de fecha.
    var optimisticDone by remember(task.id, task.done, task.dueDay, task.reminderAt) { mutableStateOf(task.done) }
    val latestDone by rememberUpdatedState(optimisticDone)
    val latestOnToggle by rememberUpdatedState(onToggleDone)
    LaunchedEffect(optimisticDone) {
        val target = optimisticDone
        if (target == task.done) return@LaunchedEffect
        try {
            delay(450)
        } finally {
            // Se guarda aunque la fila desaparezca antes (p. ej. al salir de la pantalla),
            // salvo que el usuario la haya vuelto a desmarcar.
            if (latestDone == target) latestOnToggle(target)
        }
    }
    val done = optimisticDone
    val titleColor by animateColorAsState(
        if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        label = "title"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 4.dp + 28.dp * indent.coerceAtMost(3), end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(checked = done, color = accent, onToggle = { if (enabled) optimisticDone = !optimisticDone })
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = task.title.ifBlank { " " },
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            TaskMetaLine(task, today, done, listName, listColor, subtasks)
        }
        if (enabled && (task.important || !done)) {
            val starLabel = stringResource(if (task.important) R.string.cd_unmark_important else R.string.cd_mark_important)
            IconButton(onClick = onToggleImportant, modifier = Modifier.semantics { contentDescription = starLabel }) {
                Icon(
                    if (task.important) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (task.important) AccentStar else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMetaLine(task: TaskEntity, today: LocalDate, done: Boolean, listName: String?, listColor: Color?, subtasks: Pair<Int, Int>?) {
    val due = task.dueDate
    val reminder = task.reminderAt?.takeIf { !done }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
    val tags = task.tagList
    if (due == null && task.notes.isBlank() && listName == null && reminder == null && !task.repeats && tags.isEmpty() && subtasks == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        // Si hay aviso el mismo día, basta con el aviso (que ya lleva la fecha).
        if (due != null && reminder?.toLocalDate() != due) {
            val color = when {
                done -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> when (dueState(due, today)) {
                    DueState.OVERDUE -> AccentRed
                    DueState.TODAY -> MaterialTheme.colorScheme.primary
                    DueState.FUTURE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            MetaItem(Icons.Rounded.Event, dueLabel(due, task.dueMinutes, today), color)
        }
        if (reminder != null) {
            val past = reminder.isBefore(java.time.LocalDateTime.now())
            MetaItem(
                if (past) Icons.Rounded.NotificationsNone else Icons.Rounded.NotificationsActive,
                dueLabel(reminder.toLocalDate(), reminder.hour * 60 + reminder.minute, today),
                if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
            )
        }
        if (subtasks != null) {
            MetaItem(Icons.Rounded.Checklist, "${subtasks.first}/${subtasks.second}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tags.isNotEmpty()) {
            Text(
                tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (task.repeats && !done) {
            MetaItem(Icons.Rounded.Repeat, repeatLabel(task.rrule), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.notes.isNotBlank()) {
            MetaItem(Icons.AutoMirrored.Rounded.Notes, task.notes.lineSequence().first().take(40), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (listName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(listColor ?: MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(listName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icono redondo de una lista con su color. */
@Composable
fun ListBadge(color: ListColor, icon: ImageVector, size: Dp = 32.dp) {
    val tint = color.toColor(isAppInDarkTheme())
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Nombre legible de cada color (accesibilidad). */
@Composable
fun ListColor.label(): String = stringResource(
    when (this) {
        ListColor.ORANGE -> R.string.color_orange
        ListColor.MUSTARD -> R.string.color_mustard
        ListColor.RED -> R.string.color_red
        ListColor.PINK -> R.string.color_pink
        ListColor.PURPLE -> R.string.color_purple
        ListColor.BLUE -> R.string.color_blue
        ListColor.TEAL -> R.string.color_teal
        ListColor.GREEN -> R.string.color_green
        ListColor.GRAPHITE -> R.string.color_graphite
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPalette(selected: ListColor, onSelect: (ListColor) -> Unit, modifier: Modifier = Modifier) {
    val isDark = isAppInDarkTheme()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ListColor.entries.forEach { listColor ->
            val color = listColor.toColor(isDark)
            val isSelected = selected == listColor
            val label = listColor.label()
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        BorderStroke(if (isSelected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 0.85f else 0f)),
                        CircleShape
                    )
                    .clickable(role = Role.RadioButton) { onSelect(listColor) }
                    .semantics {
                        contentDescription = label
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Pequeño aviso verde ("Conectado", "Añadida a Compra"…). */
@Composable
fun SuccessText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = AccentGreen)
}
