// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.domain.ShopCategory
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.components.CheckCircle
import com.ferlagod.mistareas.ui.components.DueState
import com.ferlagod.mistareas.ui.components.ListChips
import com.ferlagod.mistareas.ui.components.SheetAction
import com.ferlagod.mistareas.ui.components.dueLabel
import com.ferlagod.mistareas.ui.components.dueState
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.ui.theme.AccentStar
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** Acciones del detalle de una tarea. */
class TaskSheetActions(
    val onSaveText: (id: String, title: String, notes: String) -> Unit,
    val onSetDone: (id: String, done: Boolean) -> Unit,
    val onSetImportant: (id: String, important: Boolean) -> Unit,
    val onSetDue: (id: String, due: LocalDate?) -> Unit,
    val onMove: (id: String, listId: String) -> Unit,
    val onDelete: (id: String) -> Unit,
    val onSetReminder: (id: String, at: Long?) -> Unit,
    val onSetDueTime: (id: String, minutes: Int?) -> Unit,
    val onSetRepeat: (id: String, rrule: String?) -> Unit,
    val onSetTags: (id: String, tags: List<String>) -> Unit,
    val onAddSubtask: (parentId: String, title: String) -> Unit,
    val children: (TaskEntity) -> Flow<List<TaskEntity>>,
    val parent: (TaskEntity) -> Flow<TaskEntity?>,
    val observeTask: (id: String) -> Flow<TaskEntity?>,
    val allTags: StateFlow<List<String>>,
    val onSetSection: (title: String, section: ShopCategory) -> Unit
)

fun MainViewModel.taskSheetActions() = TaskSheetActions(
    onSaveText = { id, title, notes -> saveTaskText(id, title, notes) },
    onSetDone = { id, done -> setDone(id, done) },
    onSetImportant = { id, important -> setImportant(id, important) },
    onSetDue = { id, due -> setDue(id, due) },
    onMove = { id, listId -> moveTask(id, listId) },
    onDelete = { id -> deleteTasks(listOf(id)) },
    onSetReminder = { id, at -> setReminder(id, at) },
    onSetDueTime = { id, minutes -> setDueTime(id, minutes) },
    onSetRepeat = { id, rrule -> setRepeat(id, rrule) },
    onSetTags = { id, tags -> setTags(id, tags) },
    onAddSubtask = { parentId, title -> addSubtask(parentId, title) },
    children = ::childrenFlow,
    parent = ::parentFlow,
    observeTask = ::taskFlow,
    allTags = allTags,
    onSetSection = { title, section -> setShopCategory(title, section) }
)

/**
 * Detalle de una tarea. El título y las notas se guardan al cerrar la hoja; el resto
 * (fecha, importante, lista) se aplica al momento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    task: TaskEntity,
    lists: List<TaskListEntity>,
    today: LocalDate,
    actions: TaskSheetActions,
    onDismiss: () -> Unit,
    /** Sección del súper (solo en listas ordenadas por secciones). */
    section: ShopCategory? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var showDatePicker by remember { mutableStateOf(false) }
    val isDark = isAppInDarkTheme()
    val accent = ListColor.fromKey(lists.firstOrNull { it.id == task.listId }?.color).toColor(isDark)

    val listReadOnly = lists.firstOrNull { it.id == task.listId }?.readOnly == true
    val subtasks by remember(task.id, task.listId) { actions.children(task) }.collectAsStateWithLifecycle(emptyList())
    val parent by remember(task.id, task.listId, task.parentId) { actions.parent(task) }.collectAsStateWithLifecycle(null)
    val allTags by actions.allTags.collectAsStateWithLifecycle()
    // Subtarea o tarea madre abierta encima de esta.
    var nestedId by remember(task.id) { mutableStateOf<String?>(null) }

    val latestTitle by rememberUpdatedState(title)
    val latestNotes by rememberUpdatedState(notes)
    fun saveAndClose() {
        if (latestTitle.isNotBlank() && (latestTitle != task.title || latestNotes != task.notes)) {
            actions.onSaveText(task.id, latestTitle.trim(), latestNotes.trimEnd())
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::saveAndClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Título con su casilla
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 14.dp, top = 4.dp, bottom = 4.dp)) {
                        CheckCircle(checked = task.done, color = accent, onToggle = { actions.onSetDone(task.id, !task.done) }, size = 26.dp)
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it.replace("\n", " ") },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (task.done) TextDecoration.LineThrough else null
                            ),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 11.dp, bottom = 8.dp)
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 44.dp)
                    )
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 12.dp, end = 14.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .padding(vertical = 14.dp),
                            decorationBox = { inner ->
                                if (notes.isEmpty()) {
                                    Text(stringResource(R.string.add_notes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            parent?.let { p ->
                Spacer(modifier = Modifier.height(14.dp))
                ParentLink(p) { nestedId = p.id }
            }

            Spacer(modifier = Modifier.height(14.dp))
            SubtasksCard(
                subtasks = subtasks,
                accent = accent,
                enabled = !listReadOnly,
                onToggle = { actions.onSetDone(it.id, !it.done) },
                onOpen = { nestedId = it.id },
                onAdd = { actions.onAddSubtask(task.id, it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fecha límite
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.due_date), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        val due = task.dueDate
                        if (due != null) {
                            val color = when (dueState(due, today)) {
                                DueState.OVERDUE -> AccentRed
                                DueState.TODAY -> MaterialTheme.colorScheme.primary
                                DueState.FUTURE -> MaterialTheme.colorScheme.onSurface
                            }
                            InputChip(
                                selected = true,
                                onClick = { showDatePicker = true },
                                label = { Text(dueLabel(due, task.dueMinutes, today), color = color) },
                                leadingIcon = { Icon(Icons.Rounded.EditCalendar, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
                                colors = InputChipDefaults.inputChipColors(selectedContainerColor = accent.copy(alpha = 0.16f)),
                                shape = RoundedCornerShape(10.dp)
                            )
                            DueTimeChip(minutes = task.dueMinutes, onSet = { actions.onSetDueTime(task.id, it) })
                            AssistChip(
                                onClick = { actions.onSetDue(task.id, null) },
                                label = { Text(stringResource(R.string.remove_due)) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            DateChip(stringResource(R.string.due_today)) { actions.onSetDue(task.id, today) }
                            DateChip(stringResource(R.string.due_tomorrow)) { actions.onSetDue(task.id, today.plusDays(1)) }
                            DateChip(stringResource(R.string.due_next_week)) {
                                actions.onSetDue(task.id, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))
                            }
                            AssistChip(
                                onClick = { showDatePicker = true },
                                label = { Text(stringResource(R.string.pick_date)) },
                                leadingIcon = { Icon(Icons.Rounded.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Repetición
            RepeatCard(rrule = task.rrule, onSet = { actions.onSetRepeat(task.id, it) })

            Spacer(modifier = Modifier.height(14.dp))

            // Recordatorio
            ReminderCard(
                reminderAt = task.reminderAt,
                dueDate = task.dueDate,
                today = today,
                onSet = { actions.onSetReminder(task.id, it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Importante
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                onClick = { actions.onSetImportant(task.id, !task.important) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Icon(
                        if (task.important) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        tint = if (task.important) AccentStar else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.important), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = task.important, onCheckedChange = { actions.onSetImportant(task.id, it) })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            TagsCard(tags = task.tagList, allTags = allTags, enabled = !listReadOnly, onSet = { actions.onSetTags(task.id, it) })

            if (section != null) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionCard(current = section, onSelect = { actions.onSetSection(title.ifBlank { task.title }, it) })
            }

            if (lists.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    stringResource(R.string.in_list),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )
                ListChips(lists = lists.filter { !it.readOnly || it.id == task.listId }, selectedId = task.listId, onSelect = { actions.onMove(task.id, it) })
            }

            Spacer(modifier = Modifier.height(10.dp))
            SheetAction(Icons.Rounded.DeleteOutline, stringResource(R.string.delete_task), tint = AccentRed, onClick = {
                actions.onDelete(task.id)
                onDismiss()
            })
        }
    }

    nestedId?.let { id ->
        val nested by remember(id) { actions.observeTask(id) }.collectAsStateWithLifecycle(null)
        nested?.let { TaskSheet(task = it, lists = lists, today = today, actions = actions, onDismiss = { nestedId = null }) }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (task.dueDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        actions.onSetDue(task.id, Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface)
    )
}
