// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.ui.components.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.domain.Recurrence
import com.ferlagod.mistareas.domain.ShopCategory
import com.ferlagod.mistareas.ui.components.dueLabel
import com.ferlagod.mistareas.ui.components.repeatLabel
import com.ferlagod.mistareas.ui.components.label
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Hora por defecto de "Mañana" en los avisos. */
private val MORNING = LocalTime.of(9, 0)

/**
 * Recordatorio de una tarea: sin aviso se ofrecen atajos ("Dentro de 1 h", "Mañana 9:00",
 * el día de vencimiento) y "Elegir fecha y hora"; con aviso se muestra y se puede cambiar o quitar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCard(reminderAt: Long?, dueDate: LocalDate?, today: LocalDate, onSet: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val current = reminderAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
    fun set(dateTime: LocalDateTime) = onSet(dateTime.atZone(zone).toInstant().toEpochMilli())

    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.reminder), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (current != null) {
                    val past = current.isBefore(LocalDateTime.now())
                    InputChip(
                        selected = true,
                        onClick = { pickDate = true },
                        label = {
                            Text(
                                dueLabel(current.toLocalDate(), current.hour * 60 + current.minute, today),
                                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (past) Icons.Rounded.NotificationsNone else Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                        shape = RoundedCornerShape(10.dp)
                    )
                    QuickChip(stringResource(R.string.remove_due)) { onSet(null) }
                } else {
                    QuickChip(stringResource(R.string.reminder_in_one_hour)) {
                        set(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
                    }
                    // El día que vence (si es futuro y no es mañana) a las 9:00.
                    if (dueDate != null && dueDate.isAfter(today.plusDays(1))) {
                        QuickChip(dueLabel(dueDate, MORNING.hour * 60, today)) { set(dueDate.atTime(MORNING)) }
                    }
                    QuickChip(dueLabel(today.plusDays(1), MORNING.hour * 60, today)) { set(today.plusDays(1).atTime(MORNING)) }
                    AssistChip(
                        onClick = { pickDate = true },
                        label = { Text(stringResource(R.string.pick_date_time)) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }

    if (pickDate) {
        val initial = current?.toLocalDate() ?: dueDate ?: today
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    pickedDate?.let { date ->
        val initialTime = current?.toLocalTime() ?: MORNING
        val state = rememberTimePickerState(initialTime.hour, initialTime.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            title = { Text(stringResource(R.string.reminder)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    set(date.atTime(state.hour, state.minute))
                    pickedDate = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface)
    )
}

/** Sección del súper de un producto, para corregir la que se ha deducido. */
@Composable
fun SectionCard(current: ShopCategory, onSelect: (ShopCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.shop_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${current.emoji}  ${current.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ShopCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text("${category.emoji}  ${category.label()}") },
                        trailingIcon = { RadioButton(selected = category == current, onClick = null) },
                        onClick = {
                            expanded = false
                            if (category != current) onSelect(category)
                        }
                    )
                }
            }
        }
    }
}

/** Chip para poner o cambiar la hora de la fecha límite (sin hora, vence "todo el día"). */
@Composable
fun DueTimeChip(minutes: Int?, onSet: (Int?) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AssistChip(
        onClick = { pick = true },
        label = { Text(stringResource(if (minutes == null) R.string.add_time else R.string.change_time)) },
        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(10.dp)
    )
    if (pick) {
        TimeDialog(
            title = stringResource(R.string.due_time),
            initial = minutes?.let { LocalTime.of(it / 60, it % 60) } ?: MORNING,
            is24 = DateFormat.is24HourFormat(context),
            onDismiss = { pick = false },
            onConfirm = { time ->
                onSet(time.hour * 60 + time.minute)
                pick = false
            },
            onRemove = if (minutes == null) null else ({
                onSet(null)
                pick = false
            })
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    title: String,
    initial: LocalTime,
    is24: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_time)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/**
 * Repetición de la tarea. Se ofrecen las sencillas (cada día, semana, mes o año); una regla
 * más compleja creada en otra app se muestra como "Personalizada" y se respeta.
 */
@Composable
fun RepeatCard(rrule: String?, onSet: (String?) -> Unit) {
    val selected = Recurrence.simpleFreq(rrule)
    val custom = rrule != null && selected == null
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.repeat), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (custom) {
                    FilterChip(selected = true, onClick = {}, label = { Text(repeatLabel(rrule)) }, shape = RoundedCornerShape(10.dp))
                }
                listOf(
                    Recurrence.Freq.DAILY to R.string.repeat_daily,
                    Recurrence.Freq.WEEKLY to R.string.repeat_weekly,
                    Recurrence.Freq.MONTHLY to R.string.repeat_monthly,
                    Recurrence.Freq.YEARLY to R.string.repeat_yearly
                ).forEach { (freq, label) ->
                    FilterChip(
                        selected = selected == freq,
                        onClick = { onSet(if (selected == freq) null else Recurrence.simple(freq)) },
                        label = { Text(stringResource(label)) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                if (rrule != null) {
                    AssistChip(onClick = { onSet(null) }, label = { Text(stringResource(R.string.remove_due)) }, shape = RoundedCornerShape(10.dp))
                }
            }
        }
    }
}

/** Enlace a la tarea madre ("Subtarea de: …"). */
@Composable
fun ParentLink(parent: TaskEntity, onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Rounded.SubdirectoryArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.subtask_of), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(parent.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Subtareas: se marcan aquí mismo, se abren al tocarlas y se añaden escribiendo debajo. */
@Composable
fun SubtasksCard(
    subtasks: List<TaskEntity>,
    accent: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onToggle: (TaskEntity) -> Unit,
    onOpen: (TaskEntity) -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.subtasks), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (subtasks.isNotEmpty()) {
                    Text(
                        "${subtasks.count { it.done }}/${subtasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            subtasks.forEach { sub ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(sub) }
                        .padding(end = 14.dp)
                ) {
                    CheckCircle(checked = sub.done, color = accent, onToggle = { if (enabled) onToggle(sub) }, size = 22.dp)
                    Text(
                        sub.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sub.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (enabled) {
                if (subtasks.isNotEmpty()) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 46.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 14.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    fun submit() {
                        if (text.isNotBlank()) onAdd(text.trim())
                        text = ""
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.replace("\n", " ") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text(stringResource(R.string.add_subtask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    )
                }
            }
        }
    }
}

/** Etiquetas de la tarea: se quitan con la ✕ y se añaden escribiendo (con sugerencias de las ya usadas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsCard(tags: List<String>, allTags: List<String>, enabled: Boolean, onSet: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun add(tag: String) {
        val clean = tag.trim().removePrefix("#").replace(",", " ").trim()
        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) onSet(tags + clean)
        text = ""
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.tags), style = MaterialTheme.typography.bodyLarge)
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { if (enabled) onSet(tags - tag) },
                            label = { Text("#$tag") },
                            trailingIcon = if (enabled) ({ Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove_due), modifier = Modifier.size(16.dp)) }) else null,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
            if (enabled) {
                BasicTextField(
                    value = text,
                    onValueChange = { value ->
                        // Coma o espacio al final: se añade la etiqueta escrita.
                        if (value.endsWith(",") || value.endsWith(" ") && value.isNotBlank()) add(value.dropLast(1)) else text = value
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(stringResource(R.string.add_tag), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
                val suggestions = allTags.filter { t -> tags.none { it.equals(t, ignoreCase = true) } && (text.isBlank() || t.contains(text.trim().removePrefix("#"), ignoreCase = true)) }.take(8)
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        suggestions.forEach { t -> QuickChip("#$t") { add(t) } }
                    }
                }
            }
        }
    }
}
