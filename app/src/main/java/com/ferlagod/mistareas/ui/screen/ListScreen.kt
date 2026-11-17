// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.SortMode
import com.ferlagod.mistareas.data.local.TaskEntity
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.domain.ShopCategorizer
import com.ferlagod.mistareas.domain.ShopCategory
import com.ferlagod.mistareas.ui.components.label
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mistareas.ui.components.EmptyState
import com.ferlagod.mistareas.ui.components.GroupLabel
import com.ferlagod.mistareas.ui.components.ListDialog
import com.ferlagod.mistareas.ui.components.QuickAddBar
import com.ferlagod.mistareas.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mistareas.ui.components.TaskRow
import com.ferlagod.mistareas.ui.components.groupShape
import com.ferlagod.mistareas.ui.components.listIconFor
import com.ferlagod.mistareas.ui.components.rememberReorderState
import com.ferlagod.mistareas.ui.components.reorderable
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import java.text.Collator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    listId: String,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val loaded by produceState<Pair<Boolean, TaskListEntity?>>(false to null, listId) {
        viewModel.listFlow(listId).collect { value = true to it }
    }
    val list = loaded.second
    if (loaded.first && (list == null || list.deleted)) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    if (list == null) return

    // null mientras carga (para no confundir "cargando" con "lista vacía").
    val loadedTasks by remember(listId) { viewModel.tasksFlow(listId) }.collectAsStateWithLifecycle(null)
    val tasks = loadedTasks.orEmpty()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val overrides by viewModel.categoryOverrides.collectAsStateWithLifecycle()

    val isDark = isAppInDarkTheme()
    val listColor = ListColor.fromKey(list.color)
    val accent = listColor.toColor(isDark)
    val context = LocalContext.current

    var showCompleted by rememberSaveable(listId) { mutableStateOf(false) }
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    // Abierta desde la notificación de un aviso.
    LaunchedEffect(listId) {
        viewModel.pendingOpenTask.value?.takeIf { it.listId == listId }?.let {
            openTaskId = it.taskId
            viewModel.pendingOpenTask.value = null
        }
    }
    var showMenu by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    // Compartida conmigo sin permiso de edición.
    val readOnly = list.readOnly

    val collator = remember { Collator.getInstance().apply { strength = Collator.PRIMARY } }
    // Subtareas: van debajo de su madre (si la madre está pendiente en esta lista; si no, sueltas).
    val pendingAll = remember(tasks) { tasks.filter { !it.done } }
    val pendingUids = remember(pendingAll) { pendingAll.mapTo(HashSet()) { it.uid } }
    val childrenOf = remember(pendingAll, pendingUids) {
        pendingAll.filter { it.parentId != null && it.parentId in pendingUids && it.parentId != it.uid }
            .sortedWith(compareBy<TaskEntity> { it.position }.thenBy { it.createdAt })
            .groupBy { it.parentId!! }
    }
    val subtaskCounts = remember(tasks) {
        tasks.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { (_, c) -> c.count { it.done } to c.size }
    }
    val pendingSorted = remember(pendingAll, childrenOf, list.sort) {
        val childIds = childrenOf.values.flatten().mapTo(HashSet()) { it.id }
        val pending = if (list.sort == SortMode.CATEGORY) pendingAll else pendingAll.filter { it.id !in childIds }
        when (list.sort) {
            SortMode.MANUAL -> pending.sortedWith(compareBy<TaskEntity> { it.position }.thenBy { it.createdAt })
            SortMode.DUE -> pending.sortedWith(compareBy<TaskEntity, Long?>(nullsLast()) { it.dueDay }.thenBy { it.dueMinutes ?: -1 }.thenBy { it.position })
            SortMode.ALPHA -> pending.sortedWith { a, b -> collator.compare(a.title, b.title) }
            SortMode.CATEGORY -> pending.sortedWith(compareBy<TaskEntity> { it.position }.thenBy { it.createdAt })
        }
    }
    // Secciones del súper (solo con ese orden): en el orden de recorrido de la tienda.
    val sections = remember(pendingSorted, overrides, list.sort) {
        if (list.sort != SortMode.CATEGORY) emptyList()
        else pendingSorted.groupBy { ShopCategorizer.categorize(it.title, overrides) }.toSortedMap(compareBy { it.ordinal }).toList()
    }
    val completed = remember(tasks) { tasks.filter { it.done }.sortedByDescending { it.completedAt ?: it.updatedAt } }

    // Orden provisional mientras se arrastra una tarea.
    var dragOrder by remember { mutableStateOf<List<TaskEntity>?>(null) }
    val pending = dragOrder ?: pendingSorted
    // Filas a mostrar: cada tarea seguida de sus subtareas (con su nivel de sangría).
    val rows = remember(pending, childrenOf) {
        val out = mutableListOf<Pair<TaskEntity, Int>>()
        val seen = HashSet<String>()
        fun add(task: TaskEntity, depth: Int) {
            if (!seen.add(task.id)) return
            out += task to depth
            childrenOf[task.uid]?.forEach { add(it, depth + 1) }
        }
        pending.forEach { add(it, 0) }
        out
    }
    val listState = rememberLazyListState()
    val pendingIds = remember(pending) { pending.mapTo(HashSet()) { it.id } }
    val reorderState = rememberReorderState(
        listState = listState,
        canSwapWith = { it in pendingIds },
        onMove = { from, to ->
            val current = (dragOrder ?: pendingSorted).toMutableList()
            val i = current.indexOfFirst { it.id == from }
            val j = current.indexOfFirst { it.id == to }
            if (i >= 0 && j >= 0) {
                current.add(j, current.removeAt(i))
                dragOrder = current
            }
        }
    )
    LaunchedEffect(tasks) { if (reorderState.draggingKey == null) dragOrder = null }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = accent)
                    }
                },
                actions = {
                    IconButton(onClick = { showShare = true }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_list))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        ListMenu(
                            expanded = showMenu,
                            sort = list.sort,
                            hasCompleted = completed.isNotEmpty() && !readOnly,
                            canEdit = !readOnly,
                            isSharedWithMe = list.sharedBy != null,
                            onDismiss = { showMenu = false },
                            onEdit = { showEdit = true },
                            onSort = { viewModel.setSortMode(list.id, it) },
                            onClearCompleted = { viewModel.clearCompleted(list.id) },
                            onDelete = { confirmDelete = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                            0.35f to MaterialTheme.colorScheme.background
                        )
                    )
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (readOnly) {
                    ReadOnlyBanner(list.sharedBy.orEmpty(), Modifier.widthIn(max = READABLE_MAX_WIDTH))
                } else {
                    QuickAddBar(
                        accent = accent,
                        today = today,
                        smartInput = settings.smartInput,
                        onAdd = { text, smart -> viewModel.quickAdd(list.id, text, smart) },
                        modifier = Modifier.widthIn(max = READABLE_MAX_WIDTH)
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val side = maxOf(16.dp, (maxWidth - READABLE_MAX_WIDTH) / 2)
            if (loadedTasks == null) return@BoxWithConstraints
            if (pending.isEmpty() && completed.isEmpty()) {
                EmptyState(
                    icon = listIconFor(list.name),
                    title = stringResource(R.string.list_empty_title),
                    message = stringResource(R.string.list_empty_message),
                    tint = accent,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@BoxWithConstraints
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 24.dp)
            ) {
                if (pending.isEmpty()) {
                    item(key = "all_done") {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            EmptyState(icon = Icons.Rounded.Inbox, title = stringResource(R.string.all_done_title), tint = accent)
                        }
                    }
                }

                sections.forEach { (category, items) ->
                    item(key = "section:" + category.name) {
                        SectionHeader(category, items.size, Modifier.animateItem())
                    }
                    itemsIndexed(items, key = { _, task -> task.id }) { index, task ->
                        GroupedSwipeRow(
                            shape = groupShape(index, items.size),
                            showDivider = index < items.lastIndex,
                            onDelete = { viewModel.deleteTasks(listOf(task.id)) },
                        enabled = !readOnly,
                            modifier = Modifier.animateItem()
                        ) {
                            TaskRow(
                                task = task,
                                accent = accent,
                                today = today,
                                onToggleDone = { viewModel.setDone(task.id, it) },
                                onToggleImportant = { viewModel.setImportant(task.id, !task.important) },
                                onClick = { openTaskId = task.id },
                                enabled = !readOnly
                            )
                        }
                    }
                }

                if (list.sort != SortMode.CATEGORY) itemsIndexed(rows, key = { _, row -> row.first.id }) { index, (task, depth) ->
                    val dragging = reorderState.draggingKey == task.id
                    GroupedSwipeRow(
                        shape = groupShape(index, rows.size),
                        showDivider = index < rows.lastIndex,
                        onDelete = { viewModel.deleteTasks(listOf(task.id)) },
                        enabled = !readOnly,
                        modifier = (if (dragging) Modifier else Modifier.animateItem())
                            .reorderable(
                                state = reorderState,
                                key = task.id,
                                // Se arrastran las tareas principales (sus subtareas van con ellas).
                                enabled = list.sort == SortMode.MANUAL && !readOnly && depth == 0,
                                onDragEnd = { dragOrder?.let { order -> viewModel.reorderTasks(order.map { it.id }) } }
                            )
                    ) {
                        TaskRow(
                            task = task,
                            accent = accent,
                            today = today,
                            onToggleDone = { viewModel.setDone(task.id, it) },
                            onToggleImportant = { viewModel.setImportant(task.id, !task.important) },
                            onClick = { openTaskId = task.id },
                            enabled = !readOnly,
                            indent = depth,
                            subtasks = subtaskCounts[task.uid]
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item(key = "completed_header") {
                        CompletedHeader(
                            count = completed.size,
                            expanded = showCompleted,
                            onToggle = { showCompleted = !showCompleted },
                            onClear = if (readOnly) null else ({ viewModel.clearCompleted(list.id) }),
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (showCompleted) {
                        itemsIndexed(completed, key = { _, task -> "done:" + task.id }) { index, task ->
                            GroupedSwipeRow(
                                shape = groupShape(index, completed.size),
                                showDivider = index < completed.lastIndex,
                                onDelete = { viewModel.deleteTasks(listOf(task.id)) },
                                enabled = !readOnly,
                                modifier = Modifier.animateItem()
                            ) {
                                TaskRow(
                                    task = task,
                                    accent = accent,
                                    today = today,
                                    onToggleDone = { viewModel.setDone(task.id, it) },
                                    onToggleImportant = { viewModel.setImportant(task.id, !task.important) },
                                    onClick = { openTaskId = task.id },
                                    enabled = !readOnly
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    openTaskId?.let { id ->
        val task = tasks.firstOrNull { it.id == id }
        if (task == null) {
            if (loadedTasks != null) LaunchedEffect(id) { openTaskId = null }
        } else {
            TaskSheet(
                task = task,
                lists = lists,
                today = today,
                actions = viewModel.taskSheetActions(),
                onDismiss = { openTaskId = null },
                section = if (list.sort == SortMode.CATEGORY) ShopCategorizer.categorize(task.title, overrides) else null
            )
        }
    }

    if (showEdit) {
        ListDialog(
            title = stringResource(R.string.edit_list),
            initialName = list.name,
            initialColor = listColor,
            onDismiss = { showEdit = false },
            onConfirm = { name, color ->
                viewModel.updateList(list.id, name, color)
                showEdit = false
            }
        )
    }

    if (showShare) {
        ShareSheet(
            list = list,
            viewModel = viewModel,
            onSendAsText = {
                showShare = false
                shareList(context, list.name, rows.map { it.first })
            },
            onDismiss = { showShare = false }
        )
    }

    if (confirmDelete) {
        val sharedBy = list.sharedBy
        ConfirmDestructiveDialog(
            title = stringResource(if (sharedBy != null) R.string.leave_list_title else R.string.delete_list_title),
            message = if (sharedBy != null) stringResource(R.string.leave_list_message, list.name, sharedBy)
            else pluralStringResource(R.plurals.delete_list_message, tasks.size, list.name, tasks.size),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteList(list.id)
                onBack()
            }
        )
    }
}

@Composable
private fun ListMenu(
    expanded: Boolean,
    sort: SortMode,
    hasCompleted: Boolean,
    canEdit: Boolean,
    isSharedWithMe: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSort: (SortMode) -> Unit,
    onClearCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_list)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { onDismiss(); onEdit() }
            )
            HorizontalDivider()
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (mode) {
                                SortMode.MANUAL -> R.string.sort_manual
                                SortMode.DUE -> R.string.sort_due
                                SortMode.ALPHA -> R.string.sort_alpha
                                SortMode.CATEGORY -> R.string.sort_category
                            }
                        )
                    )
                },
                leadingIcon = { RadioButton(selected = mode == sort, onClick = null) },
                onClick = { onSort(mode); onDismiss() }
            )
        }
        HorizontalDivider()
        if (hasCompleted) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_completed)) },
                leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                onClick = { onDismiss(); onClearCompleted() }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (isSharedWithMe) R.string.leave_list else R.string.delete_list), color = AccentRed) },
            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = AccentRed) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, onClear: (() -> Unit)?, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp)
            .clickable(onClick = onToggle)
    ) {
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(20.dp)
                .rotate(rotation)
        )
        GroupLabel(
            pluralStringResource(R.plurals.completed_count, count, count),
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        )
        // En una lista compartida de solo lectura no se pueden borrar.
        if (onClear != null) {
            TextButton(onClick = onClear, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.clear), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun SectionHeader(category: ShopCategory, count: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)
    ) {
        Text(category.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            category.label(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadOnlyBanner(owner: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.read_only_banner, owner), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Comparte las tareas pendientes como texto (WhatsApp, Telegram, correo…). */
private fun shareList(context: android.content.Context, name: String, pending: List<TaskEntity>) {
    val text = buildString {
        append(name).append('\n')
        pending.forEach { append("☐ ").append(it.title).append('\n') }
    }.trimEnd()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
