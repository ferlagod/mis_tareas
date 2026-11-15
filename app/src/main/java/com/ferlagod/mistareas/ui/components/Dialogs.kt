// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import java.time.LocalDate

/** Crear o editar una lista: nombre y color. */
@Composable
fun ListDialog(
    title: String,
    initialName: String,
    initialColor: ListColor,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: ListColor) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "") },
                    label = { Text(stringResource(R.string.list_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim(), color) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                Spacer(modifier = Modifier.height(18.dp))
                ColorPalette(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ConfirmDestructiveDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AccentRed, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Selector horizontal de lista con chips de color. */
@Composable
fun ListChips(lists: List<TaskListEntity>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val isDark = isAppInDarkTheme()
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lists, key = { it.id }) { list ->
            val color = ListColor.fromKey(list.color).toColor(isDark)
            FilterChip(
                selected = list.id == selectedId,
                onClick = { onSelect(list.id) },
                label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(color)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

/**
 * Hoja para añadir tareas desde Inicio o el acceso directo: se elige la lista y se escriben
 * tareas seguidas sin cerrar la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    lists: List<TaskListEntity>,
    initialListId: String?,
    today: LocalDate,
    smartInput: Boolean,
    onAdd: (listId: String, text: String, smart: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var listId by remember { mutableStateOf(initialListId ?: lists.firstOrNull()?.id) }
    var added by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val isDark = isAppInDarkTheme()
    val accent = lists.firstOrNull { it.id == listId }?.let { ListColor.fromKey(it.color).toColor(isDark) } ?: MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        ) {
            Text(stringResource(R.string.new_task), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))
            ListChips(lists = lists, selectedId = listId, onSelect = { listId = it })
            Spacer(modifier = Modifier.height(12.dp))
            QuickAddBar(
                accent = accent,
                today = today,
                smartInput = smartInput,
                focusRequester = focus,
                onAdd = { text, smart ->
                    listId?.let {
                        onAdd(it, text, smart)
                        added++
                    }
                }
            )
            if (added > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val listName = lists.firstOrNull { it.id == listId }?.name.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = com.ferlagod.mistareas.ui.theme.AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    SuccessText(pluralStringResource(R.plurals.tasks_added_to, added, added, listName))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Texto compartido desde otra app: vista previa y elección de lista. */
@Composable
fun ShareToListDialog(
    lines: List<String>,
    lists: List<TaskListEntity>,
    initialListId: String?,
    onDismiss: () -> Unit,
    onConfirm: (listId: String) -> Unit
) {
    var listId by remember { mutableStateOf(initialListId ?: lists.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.share_add_title, lines.size, lines.size), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    lines.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.share_choose_list), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                ListChips(lists = lists, selectedId = listId, onSelect = { listId = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { listId?.let(onConfirm) }, enabled = listId != null) {
                Text(stringResource(R.string.add), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Fila de opción de menú con icono, para hojas inferiores. */
@Composable
fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
