// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.local.TaskListEntity
import com.ferlagod.mistareas.data.remote.Sharee
import com.ferlagod.mistareas.data.sync.ShareCandidate
import com.ferlagod.mistareas.data.sync.SharingUnavailable
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.components.GroupLabel
import com.ferlagod.mistareas.ui.components.RowDivider
import com.ferlagod.mistareas.ui.components.SheetAction
import com.ferlagod.mistareas.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compartir una lista con otras personas del mismo Nextcloud, o enviarla como texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(list: TaskListEntity, viewModel: MainViewModel, onSendAsText: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val unavailable = remember(list) { viewModel.sharingUnavailable(list) }

    var sharees by remember { mutableStateOf<List<Sharee>?>(null) }
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<ShareCandidate>>(emptyList()) }
    var canEdit by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(list.id, unavailable) {
        if (unavailable != null) return@LaunchedEffect
        busy = true
        viewModel.loadSharees(list.id).onSuccess { sharees = it }.onFailure { error = true }
        busy = false
    }
    // Búsqueda con una pequeña espera para no lanzar una petición por cada letra.
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            candidates = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        viewModel.searchPeople(query).onSuccess { found ->
            val already = sharees.orEmpty().map { it.href.removePrefix("principal:").trimEnd('/') }.toSet()
            candidates = found.filter { it.principal.removePrefix("principal:") !in already }
        }
    }

    fun run(block: suspend () -> Result<List<Sharee>>) = scope.launch {
        busy = true
        error = false
        block().onSuccess {
            sharees = it
            query = ""
        }.onFailure { error = true }
        busy = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    stringResource(R.string.share_title, list.name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (unavailable != null) {
                InfoCard(
                    when (unavailable) {
                        SharingUnavailable.NOT_CONNECTED -> stringResource(R.string.share_unavailable_not_connected)
                        SharingUnavailable.NOT_NEXTCLOUD -> stringResource(R.string.share_unavailable_not_nextcloud)
                        SharingUnavailable.NOT_OWNER -> stringResource(R.string.share_unavailable_not_owner, list.sharedBy.orEmpty())
                    }
                )
            } else {
                // Buscador
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(stringResource(R.string.share_search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            )
                        }
                        candidates.forEach { candidate ->
                            RowDivider(start = 14.dp)
                            PersonRow(
                                icon = if (candidate.isGroup) Icons.Rounded.Group else Icons.Rounded.Person,
                                name = candidate.name,
                                detail = candidate.detail,
                                trailing = { Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.primary) },
                                onClick = { run { viewModel.shareList(list.id, candidate.principal, canEdit) } }
                            )
                        }
                        RowDivider(start = 14.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                            .fillMaxWidth()
                            .clickable { canEdit = !canEdit }
                            .padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.share_can_edit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = canEdit, onCheckedChange = { canEdit = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                GroupLabel(stringResource(R.string.share_people_with_access))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        val current = sharees
                        when {
                            current == null -> Spacer(modifier = Modifier.height(48.dp))
                            current.isEmpty() -> Text(
                                stringResource(R.string.share_nobody),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                            else -> current.forEachIndexed { index, sharee ->
                                if (index > 0) RowDivider(start = 62.dp)
                                PersonRow(
                                    icon = if (sharee.isGroup) Icons.Rounded.Group else Icons.Rounded.Person,
                                    name = sharee.name,
                                    detail = stringResource(if (sharee.canEdit) R.string.share_role_edit else R.string.share_role_view),
                                    trailing = {
                                        IconButton(onClick = { run { viewModel.unshareList(list.id, sharee.href) } }) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.share_remove_cd, sharee.name), tint = AccentRed)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (error) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.share_error), style = MaterialTheme.typography.bodySmall, color = AccentRed, modifier = Modifier.padding(start = 6.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SheetAction(Icons.Rounded.Share, stringResource(R.string.share_send_text), onClick = onSendAsText)
        }
    }
}

@Composable
private fun PersonRow(icon: ImageVector, name: String, detail: String?, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
