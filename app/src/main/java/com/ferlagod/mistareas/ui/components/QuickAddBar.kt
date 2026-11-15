// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Label
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.domain.QuickAddParser
import com.ferlagod.mistareas.ui.theme.AccentStar
import java.time.LocalDate

/**
 * Barra para añadir tareas a toda velocidad: se escribe, se pulsa Intro y el teclado sigue
 * abierto para la siguiente. Si la entrada inteligente está activada, debajo se ve lo que se
 * ha entendido ("Mañana", "Importante") y se puede descartar con la ✕.
 *
 * @param onAdd recibe el texto y si se debe interpretar (false si el usuario descartó la sugerencia).
 */
@Composable
fun QuickAddBar(
    accent: Color,
    today: LocalDate,
    smartInput: Boolean,
    onAdd: (text: String, smart: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.quick_add_hint),
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var text by rememberSaveable { mutableStateOf("") }
    var ignoreSmart by rememberSaveable { mutableStateOf(false) }
    val preview = remember(text, today, smartInput) {
        if (smartInput && !text.contains('\n')) QuickAddParser.parse(text, today).takeIf { it.hasExtras } else null
    }

    fun submit() {
        if (text.isBlank()) return
        onAdd(text, !ignoreSmart)
        text = ""
        ignoreSmart = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = preview != null && !ignoreSmart,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            preview?.let { p ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 6.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    val now = remember(text) { java.time.LocalTime.now() }
                    val reminder = p.reminder(today, now)
                    if (reminder != null) {
                        SmartChip(
                            Icons.Rounded.NotificationsActive,
                            dueLabel(reminder.toLocalDate(), reminder.hour * 60 + reminder.minute, today),
                            MaterialTheme.colorScheme.secondary
                        ) { ignoreSmart = true }
                    } else {
                        p.due?.let { due ->
                            SmartChip(Icons.Rounded.Event, dueLabel(due, null, today), MaterialTheme.colorScheme.primary) { ignoreSmart = true }
                        }
                    }
                    if (p.important) {
                        SmartChip(Icons.Rounded.Star, stringResource(R.string.important), AccentStar) { ignoreSmart = true }
                    }
                    if (p.rrule != null) {
                        SmartChip(Icons.Rounded.Repeat, repeatLabel(p.rrule), MaterialTheme.colorScheme.primary) { ignoreSmart = true }
                    }
                    if (p.tags.isNotEmpty()) {
                        SmartChip(Icons.Rounded.Label, p.tags.joinToString(" ") { "#$it" }, MaterialTheme.colorScheme.primary) { ignoreSmart = true }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        // Intro de un teclado físico añade la tarea (un salto de línea tecleado al final).
                        // Pegar varias líneas de golpe sigue creando varias tareas.
                        if (new.length == text.length + 1 && new.endsWith('\n') && new.startsWith(text)) {
                            submit()
                        } else {
                            text = new
                            if (new.isEmpty()) ignoreSmart = false
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
                AnimatedVisibility(visible = text.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    FilledIconButton(
                        onClick = { submit() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.White),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.add), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onDismiss: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onDismiss,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) },
        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_ignore_suggestion), modifier = Modifier.size(16.dp)) },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.surface,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null,
        shape = RoundedCornerShape(10.dp)
    )
}
