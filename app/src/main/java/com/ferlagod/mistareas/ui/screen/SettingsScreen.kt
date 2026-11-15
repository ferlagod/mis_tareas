// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.screen

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ferlagod.mistareas.reminders.Reminders
import com.ferlagod.mistareas.ui.theme.AccentGreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.mistareas.BuildConfig
import com.ferlagod.mistareas.R
import com.ferlagod.mistareas.data.pref.SyncAccount
import com.ferlagod.mistareas.data.pref.SyncMode
import com.ferlagod.mistareas.data.pref.SyncPreferences
import com.ferlagod.mistareas.data.pref.ThemeMode
import com.ferlagod.mistareas.data.sync.SyncError
import com.ferlagod.mistareas.data.sync.SyncState
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mistareas.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mistareas.ui.theme.AccentRed
import com.ferlagod.mistareas.util.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = READABLE_MAX_WIDTH)
                .padding(horizontal = 16.dp)
        ) {
            // --- Sincronización ---
            SectionTitle(stringResource(R.string.section_sync))
            SettingsCard {
                val current = account
                if (current != null) {
                    ConnectedAccount(
                        account = current,
                        syncState = syncState,
                        lastSync = lastSync,
                        onSyncNow = viewModel::triggerSync,
                        onDisconnect = { viewModel.disconnect() }
                    )
                } else {
                    ConnectForm(busy = busy, onConnect = viewModel::connect)
                }
            }

            // --- Tareas ---
            SectionTitle(stringResource(R.string.section_tasks))
            SettingsCard {
                SwitchRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.smart_input),
                    subtitle = stringResource(R.string.smart_input_desc),
                    checked = settings.smartInput,
                    onCheckedChange = viewModel::setSmartInput
                )
                Divider()
                DefaultListRow(
                    lists = lists.map { it.id to it.name },
                    selectedId = viewModel.defaultListId(),
                    onSelect = viewModel::setDefaultList
                )
            }

            // --- Recordatorios ---
            SectionTitle(stringResource(R.string.section_reminders))
            SettingsCard {
                RemindersSection()
                Divider()
                SwitchRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.notify_at_due),
                    subtitle = stringResource(R.string.notify_at_due_desc),
                    checked = settings.notifyAtDue,
                    onCheckedChange = viewModel::setNotifyAtDue
                )
                Divider()
                DigestRow(
                    enabled = settings.dailyDigest,
                    minutes = settings.digestMinutes,
                    onEnabledChange = viewModel::setDailyDigest,
                    onMinutesChange = viewModel::setDigestMinutes
                )
            }

            // --- Apariencia ---
            SectionTitle(stringResource(R.string.section_appearance))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    RowLabel(Icons.Rounded.DarkMode, stringResource(R.string.theme))
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                            ) {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        }
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Divider()
                SwitchRow(
                    icon = Icons.Rounded.Contrast,
                    title = stringResource(R.string.amoled_mode),
                    subtitle = stringResource(R.string.amoled_mode_desc),
                    checked = settings.amoled,
                    onCheckedChange = viewModel::setAmoled
                )
            }

            // --- Idioma ---
            SectionTitle(stringResource(R.string.section_language))
            SettingsCard { LanguageSection(currentLanguage, onLanguageSelected) }

            // --- Acerca de ---
            SectionTitle(stringResource(R.string.section_about))
            SettingsCard { AboutSection() }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ----------------------------------------------------------------------
// Sincronización
// ----------------------------------------------------------------------

@Composable
private fun ConnectedAccount(
    account: SyncAccount,
    syncState: SyncState,
    lastSync: Long?,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Rounded.CloudDone)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (account.mode == SyncMode.CALDAV) R.string.mode_caldav else R.string.mode_webdav),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    "${account.username} · ${account.serverUrl.removePrefix("https://").removePrefix("http://")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.mode == SyncMode.WEBDAV) {
                    Text(
                        stringResource(R.string.webdav_folder_value, account.folder.ifBlank { "/" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (syncState) {
            SyncState.Syncing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sync_in_progress), style = MaterialTheme.typography.bodySmall)
            }
            is SyncState.Failed -> Text(syncErrorText(syncState.error, syncState.code), style = MaterialTheme.typography.bodySmall, color = AccentRed)
            else -> Text(
                if (lastSync != null) stringResource(R.string.last_sync, formatTime(lastSync)) else stringResource(R.string.never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSyncNow, enabled = syncState != SyncState.Syncing, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sync_now))
            }
            TextButton(onClick = { confirmDisconnect = true }) {
                Text(stringResource(R.string.disconnect), color = AccentRed)
            }
        }
    }
    if (confirmDisconnect) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.disconnect_title),
            message = stringResource(R.string.disconnect_message),
            confirmLabel = stringResource(R.string.disconnect),
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                onDisconnect()
            }
        )
    }
}

@Composable
private fun ConnectForm(
    busy: Boolean,
    onConnect: (SyncMode, String, String, String, String, (SyncError?, Int?) -> Unit) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(SyncMode.CALDAV) }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf(SyncPreferences.DEFAULT_FOLDER) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<SyncError?>(null) }
    var errorCode by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Rounded.CloudOff)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(stringResource(R.string.local_only_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(SyncMode.CALDAV, SyncMode.WEBDAV).forEachIndexed { index, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { mode = m; error = null },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                ) {
                    Text(stringResource(if (m == SyncMode.CALDAV) R.string.mode_caldav_short else R.string.mode_webdav_short), maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(if (mode == SyncMode.CALDAV) R.string.mode_caldav_desc else R.string.mode_webdav_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        SettingsTextField(server, { server = it; error = null }, stringResource(R.string.server_hint), Icons.Rounded.Dns,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsTextField(user, { user = it; error = null }, stringResource(R.string.username), Icons.Rounded.Person,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsTextField(
            password, { password = it; error = null }, stringResource(R.string.app_password), Icons.Rounded.Key,
            isPassword = true, passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (mode == SyncMode.WEBDAV) ImeAction.Next else ImeAction.Done)
        )
        if (mode == SyncMode.WEBDAV) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(folder, { folder = it; error = null }, stringResource(R.string.webdav_folder), Icons.Rounded.Folder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.app_password_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        error?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(syncErrorText(it, errorCode), style = MaterialTheme.typography.bodySmall, color = AccentRed)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                error = null
                onConnect(mode, server, user, password, folder) { result, code ->
                    error = result
                    errorCode = code
                }
            },
            enabled = !busy && server.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.connecting))
            } else {
                Text(stringResource(R.string.connect), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun syncErrorText(error: SyncError, code: Int?): String = when (error) {
    SyncError.AUTH -> stringResource(R.string.error_auth)
    SyncError.NETWORK -> stringResource(R.string.error_network)
    SyncError.NOT_FOUND -> stringResource(R.string.error_not_found)
    SyncError.INVALID_URL -> stringResource(R.string.error_invalid_url)
    SyncError.SERVER -> stringResource(R.string.error_server, code ?: 0)
    SyncError.UNKNOWN -> stringResource(R.string.error_unknown)
}

private fun formatTime(timestamp: Long): String {
    val locale = Locale.getDefault()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMjmm"), locale).format(Date(timestamp))
}

// ----------------------------------------------------------------------
// Recordatorios
// ----------------------------------------------------------------------

/** Estado de los permisos de avisos, con acceso directo a los ajustes del sistema. Se refresca al volver. */
@Composable
private fun RemindersSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationsOn = remember(refresh) { Reminders.notificationsAllowed(context) }
    val exactOn = remember(refresh) { Reminders.canScheduleExact(context) }

    StatusRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.notifications),
        subtitle = stringResource(if (notificationsOn) R.string.notifications_on else R.string.notifications_off),
        ok = notificationsOn,
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Divider()
        StatusRow(
            icon = Icons.Rounded.Alarm,
            title = stringResource(R.string.exact_alarms),
            subtitle = stringResource(if (exactOn) R.string.exact_alarms_on else R.string.exact_alarms_off),
            ok = exactOn,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }
        )
    }
}

/** Resumen diario: interruptor y, al tocar la fila, la hora a la que llega. */
@Composable
private fun DigestRow(enabled: Boolean, minutes: Int, onEnabledChange: (Boolean) -> Unit, onMinutesChange: (Int) -> Unit) {
    val context = LocalContext.current
    var pick by remember { mutableStateOf(false) }
    val is24 = DateFormat.is24HourFormat(context)
    val time = java.time.LocalTime.of(minutes / 60, minutes % 60)
    val timeText = java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(time)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (enabled) pick = true else onEnabledChange(true) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(Icons.Rounded.WbSunny)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.daily_digest), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(
                stringResource(R.string.daily_digest_desc, timeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (pick) {
        TimeDialog(
            title = stringResource(R.string.daily_digest),
            initial = time,
            is24 = is24,
            onDismiss = { pick = false },
            onConfirm = {
                onMinutesChange(it.hour * 60 + it.minute)
                pick = false
            }
        )
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else AccentRed)
        }
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = if (ok) AccentGreen else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ----------------------------------------------------------------------
// Piezas comunes (iguales que en Mis Notas)
// ----------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun RowLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DefaultListRow(lists: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = false, role = Role.DropdownList, enabled = lists.isNotEmpty(), onClick = { expanded = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIcon(Icons.AutoMirrored.Rounded.ListAlt)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.default_list), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    lists.firstOrNull { it.first == selectedId }?.second ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { RadioButton(selected = id == selectedId, onClick = null) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageSection(current: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        AppLanguage.entries.forEachIndexed { index, language ->
            val label = if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = language == current, onClick = { if (language != current) onSelected(language) }, role = Role.RadioButton)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) SettingIcon(Icons.Rounded.Language) else Spacer(modifier = Modifier.width(26.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp))
                RadioButton(selected = language == current, onClick = null)
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_logo_round),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row {
            TextButton(onClick = { uriHandler.openUri(REPO_URL) }) { Text(stringResource(R.string.source_code)) }
            TextButton(onClick = { uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }) { Text(stringResource(R.string.license)) }
        }
    }
}

private const val REPO_URL = "https://github.com/ferlagod/mis_tareas"

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_password),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}
