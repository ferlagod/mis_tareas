// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.TaskWithList
import com.ferlagod.mistareas.data.model.ListColor
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.SmartView
import com.ferlagod.mistareas.ui.components.EmptyState
import com.ferlagod.mistareas.ui.components.GroupLabel
import com.ferlagod.mistareas.ui.components.QuickAddBar
import com.ferlagod.mistareas.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mistareas.ui.components.TaskRow
import com.ferlagod.mistareas.ui.components.groupShape
import com.ferlagod.mistareas.ui.theme.AccentStar
import com.ferlagod.mistareas.ui.theme.isAppInDarkTheme
import com.ferlagod.mistareas.ui.theme.toColor
import java.time.LocalDate

/**
 * "Hoy" (vence hoy o vencidas) e "Importantes", con tareas de todas las listas.
 * Lo que se escribe abajo va a la lista predeterminada, para hoy o marcado como importante.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScreen(
    view: SmartView,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val tasks by (if (view == SmartView.TODAY) viewModel.todayTasks else viewModel.importantTasks).collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val accent = if (view == SmartView.TODAY) MaterialTheme.colorScheme.primary else AccentStar
    val title = stringResource(if (view == SmartView.TODAY) R.string.smart_today else R.string.smart_important)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
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
            if (lists.isNotEmpty()) {
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
                    QuickAddBar(
                        accent = accent,
                        today = today,
                        smartInput = settings.smartInput,
                        placeholder = stringResource(if (view == SmartView.TODAY) R.string.quick_add_today_hint else R.string.quick_add_important_hint),
                        onAdd = { text, smart ->
                            viewModel.defaultListId()?.let { listId ->
                                viewModel.quickAdd(
                                    listId, text, smart,
                                    forceDue = if (view == SmartView.TODAY) today else null,
                                    forceImportant = view == SmartView.IMPORTANT
                                )
                            }
                        },
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
            if (tasks.isEmpty()) {
                EmptyState(
                    icon = if (view == SmartView.TODAY) Icons.Rounded.WbSunny else Icons.Rounded.StarBorder,
                    title = stringResource(if (view == SmartView.TODAY) R.string.today_empty_title else R.string.important_empty_title),
                    message = stringResource(if (view == SmartView.TODAY) R.string.today_empty_message else R.string.important_empty_message),
                    tint = accent,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@BoxWithConstraints
            }

            val overdueLabel = stringResource(R.string.group_overdue)
            val todayLabel = stringResource(R.string.group_today)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 24.dp)
            ) {
                if (view == SmartView.TODAY) {
                    val overdue = tasks.filter { (it.task.dueDay ?: 0) < today.toEpochDay() }
                    val dueToday = tasks.filter { it.task.dueDay == today.toEpochDay() }
                    taskGroup("overdue", overdueLabel, overdue, today, viewModel) { openTaskId = it }
                    taskGroup("today", todayLabel, dueToday, today, viewModel) { openTaskId = it }
                } else {
                    taskGroup("important", null, tasks, today, viewModel) { openTaskId = it }
                }
            }
        }
    }

    openTaskId?.let { id ->
        val task = tasks.firstOrNull { it.task.id == id }?.task
        if (task == null) {
            LaunchedEffect(id) { openTaskId = null }
        } else {
            TaskSheet(task = task, lists = lists, today = today, actions = viewModel.taskSheetActions(), onDismiss = { openTaskId = null })
        }
    }
}

private fun LazyListScope.taskGroup(
    key: String,
    label: String?,
    tasks: List<TaskWithList>,
    today: LocalDate,
    viewModel: MainViewModel,
    onOpen: (String) -> Unit
) {
    if (tasks.isEmpty()) return
    if (label != null) {
        item(key = "label_$key") {
            Spacer(modifier = Modifier.height(8.dp))
            GroupLabel(label, modifier = Modifier.animateItem())
        }
    }
    itemsIndexed(tasks, key = { _, it -> "$key:${it.task.id}" }) { index, item ->
        val color = ListColor.fromKey(item.listColor).toColor(isAppInDarkTheme())
        GroupedSwipeRow(
            shape = groupShape(index, tasks.size),
            showDivider = index < tasks.lastIndex,
            onDelete = { viewModel.deleteTasks(listOf(item.task.id)) },
            modifier = Modifier.animateItem()
        ) {
            TaskRow(
                task = item.task,
                accent = color,
                today = today,
                onToggleDone = { viewModel.setDone(item.task.id, it) },
                onToggleImportant = { viewModel.setImportant(item.task.id, !item.task.important) },
                onClick = { onOpen(item.task.id) },
                listName = item.listName,
                listColor = color
            )
        }
    }
}
