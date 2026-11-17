// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.ListSummary
import com.ferlagod.mistareas.data.local.TaskWithList
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.SmartView
import com.ferlagod.mistareas.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mistareas.ui.components.EmptyState
import com.ferlagod.mistareas.ui.components.GroupCard
import com.ferlagod.mistareas.ui.components.GroupLabel
import com.ferlagod.mistareas.ui.components.ListBadge
import com.ferlagod.mistareas.ui.components.ListDialog
import com.ferlagod.mistareas.ui.components.NewTaskSheet
import com.ferlagod.mistareas.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mistareas.ui.components.RowDivider
import com.ferlagod.mistareas.ui.components.SyncStatusIconButton
import com.ferlagod.mistareas.ui.components.TaskRow
import com.ferlagod.mistareas.ui.components.groupShape
import com.ferlagod.mistareas.ui.components.listIconFor
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.ui.theme.AccentStar
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import java.time.LocalTime

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenList: (String) -> Unit,
    onOpenSmart: (SmartView) -> Unit,
    onOpenSettings: () -> Unit
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val importantTasks by viewModel.importantTasks.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingNewTask by viewModel.pendingNewTask.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var showNewList by remember { mutableStateOf(false) }
    var showNewTask by rememberSaveable { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<ListSummary?>(null) }
    var listToDelete by remember { mutableStateOf<ListSummary?>(null) }
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingNewTask) {
        if (pendingNewTask) {
            showNewTask = true
            viewModel.pendingNewTask.value = false
        }
    }

    val searchResults by remember(query) {
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else viewModel.searchFlow(query.trim())
    }.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        floatingActionButton = {
            if (lists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showNewTask = true },
                    icon = { Icon(Icons.Rounded.AddTask, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_task), fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    expanded = fabExpanded
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val side = maxOf(16.dp, (maxWidth - READABLE_MAX_WIDTH) / 2)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 104.dp)
            ) {
                item(key = "header") {
                    HomeHeader(
                        pendingToday = todayTasks.size,
                        listCount = summaries.size,
                        actions = {
                            SyncStatusIconButton(syncState) { if (account != null) viewModel.triggerSync() else onOpenSettings() }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                item(key = "search") {
                    SearchField(query = query, onQueryChange = { query = it }, onSearch = { focusManager.clearFocus() })
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (query.isNotBlank()) {
                    if (searchResults.isEmpty()) {
                        item(key = "no_results") {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = stringResource(R.string.search_no_results),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp)
                            )
                        }
                    }
                    searchResultItems(searchResults, viewModel, today) { openTaskId = it }
                    return@LazyColumn
                }

                item(key = "smart") {
                    GroupLabel(stringResource(R.string.smart_views))
                    GroupCard {
                        SmartRow(
                            title = stringResource(R.string.smart_today),
                            icon = Icons.Rounded.WbSunny,
                            tint = MaterialTheme.colorScheme.primary,
                            count = todayTasks.size,
                            highlight = todayTasks.any { (it.task.dueDay ?: 0) < today.toEpochDay() },
                            onClick = { onOpenSmart(SmartView.TODAY) }
                        )
                        RowDivider(start = 62.dp)
                        SmartRow(
                            title = stringResource(R.string.smart_important),
                            icon = Icons.Rounded.Star,
                            tint = AccentStar,
                            count = importantTasks.size,
                            highlight = false,
                            onClick = { onOpenSmart(SmartView.IMPORTANT) }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(key = "lists_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupLabel(stringResource(R.string.my_lists), modifier = Modifier.weight(1f))
                        TextButton(onClick = { showNewList = true }) {
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.new_list), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (summaries.isEmpty()) {
                    item(key = "no_lists") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            onClick = { showNewList = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.no_lists), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    itemsIndexed(summaries, key = { _, it -> it.list.id }) { index, summary ->
                        Surface(
                            shape = groupShape(index, summaries.size),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Column {
                                ListRow(
                                    summary = summary,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < summaries.lastIndex,
                                    onClick = { onOpenList(summary.list.id) },
                                    onEdit = { listToEdit = summary },
                                    onMove = { delta ->
                                        val ids = summaries.map { it.list.id }.toMutableList()
                                        val target = (index + delta).coerceIn(0, ids.lastIndex)
                                        ids.add(target, ids.removeAt(index))
                                        viewModel.reorderLists(ids)
                                    },
                                    onDelete = { listToDelete = summary }
                                )
                                if (index < summaries.lastIndex) RowDivider(start = 62.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewList) {
        ListDialog(
            title = stringResource(R.string.new_list),
            initialName = "",
            initialColor = ListColor.forIndex(summaries.size),
            onDismiss = { showNewList = false },
            onConfirm = { name, color ->
                showNewList = false
                viewModel.createList(name, color, onCreated = onOpenList)
            }
        )
    }

    listToEdit?.let { summary ->
        ListDialog(
            title = stringResource(R.string.edit_list),
            initialName = summary.list.name,
            initialColor = ListColor.fromKey(summary.list.color),
            onDismiss = { listToEdit = null },
            onConfirm = { name, color ->
                viewModel.updateList(summary.list.id, name, color)
                listToEdit = null
            }
        )
    }

    listToDelete?.let { summary ->
        val sharedBy = summary.list.sharedBy
        ConfirmDestructiveDialog(
            title = stringResource(if (sharedBy != null) R.string.leave_list_title else R.string.delete_list_title),
            message = if (sharedBy != null) stringResource(R.string.leave_list_message, summary.list.name, sharedBy)
            else pluralStringResource(R.plurals.delete_list_message, summary.total, summary.list.name, summary.total),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { listToDelete = null },
            onConfirm = {
                viewModel.deleteList(summary.list.id)
                listToDelete = null
            }
        )
    }

    if (showNewTask && lists.isNotEmpty()) {
        NewTaskSheet(
            lists = lists.filter { !it.readOnly },
            initialListId = viewModel.defaultListId(),
            today = today,
            smartInput = settings.smartInput,
            onAdd = { listId, text, smart -> viewModel.quickAdd(listId, text, smart) },
            onDismiss = { showNewTask = false }
        )
    }

    openTaskId?.let { id ->
        val task = searchResults.firstOrNull { it.task.id == id }?.task
        if (task == null) {
            LaunchedEffect(id) { openTaskId = null }
        } else {
            TaskSheet(task = task, lists = lists, today = today, actions = viewModel.taskSheetActions(), onDismiss = { openTaskId = null })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResultItems(
    results: List<TaskWithList>,
    viewModel: MainViewModel,
    today: java.time.LocalDate,
    onOpen: (String) -> Unit
) {
    itemsIndexed(results, key = { _, it -> "s:" + it.task.id }) { index, item ->
        val color = ListColor.fromKey(item.listColor).toColor(isAppInDarkTheme())
        Surface(shape = groupShape(index, results.size), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                TaskRow(
                    task = item.task,
                    accent = color,
                    today = today,
                    onToggleDone = { viewModel.setDone(item.task.id, it) },
                    onToggleImportant = { viewModel.setImportant(item.task.id, !item.task.important) },
                    onClick = { onOpen(item.task.id) },
                    listName = item.listName,
                    listColor = color,
                    enabled = !item.listReadOnly
                )
                if (index < results.lastIndex) RowDivider(start = 48.dp)
            }
        }
    }
}

/**
 * Cabecera común a la familia "Mis Apps": nombre de la app en pequeño, saludo según la hora,
 * resumen y logo redondo.
 */
@Composable
private fun HomeHeader(pendingToday: Int, listCount: Int, actions: @Composable () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 6..12 -> R.string.greeting_morning
            in 13..20 -> R.string.greeting_afternoon
            else -> R.string.greeting_night
        }
    )
    val summary = (if (pendingToday == 0) stringResource(R.string.home_nothing_today)
    else pluralStringResource(R.plurals.home_today_count, pendingToday, pendingToday)) +
        "  ·  " + pluralStringResource(R.plurals.home_lists_count, listCount, listCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_logo_round),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SmartRow(title: String, icon: ImageVector, tint: Color, count: Int, highlight: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ListRow(
    summary: ListSummary,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListBadge(ListColor.fromKey(summary.list.color), listIconFor(summary.list.name))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.list.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Listas compartidas: con quién o por quién.
            val sharing = summary.list.sharedBy?.let { stringResource(R.string.shared_by, it) }
                ?: summary.list.sharedWith?.let { stringResource(R.string.shared_with, it) }
            if (sharing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.People, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        sharing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(text = summary.pending.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_list_options, summary.list.name), tint = MaterialTheme.colorScheme.outlineVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_list)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { showMenu = false; onEdit() }
                )
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) },
                        onClick = { showMenu = false; onMove(-1) }
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                        onClick = { showMenu = false; onMove(1) }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (summary.list.sharedBy != null) R.string.leave_list else R.string.delete_list), color = AccentRed) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = AccentRed) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
