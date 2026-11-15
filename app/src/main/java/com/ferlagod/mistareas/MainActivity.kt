// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ferlagod.mistareas.ui.OpenTask
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ferlagod.mistareas.data.pref.ThemeMode
import com.ferlagod.mistareas.ui.MainViewModel
import com.ferlagod.mistareas.ui.SmartView
import com.ferlagod.mistareas.ui.UiEvent
import com.ferlagod.mistareas.ui.components.ShareToListDialog
import com.ferlagod.mistareas.ui.screen.HomeScreen
import com.ferlagod.mistareas.ui.screen.ListScreen
import com.ferlagod.mistareas.ui.screen.SettingsScreen
import com.ferlagod.mistareas.ui.screen.SmartScreen
import com.ferlagod.mistareas.ui.theme.MisTareasTheme
import com.ferlagod.mistareas.util.LanguageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val LIST = "list/{id}"
    const val SMART = "smart/{view}"
    const val SETTINGS = "settings"

    fun list(id: String) = "list/$id"
    fun smart(view: SmartView) = "smart/${view.name}"
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        // Android 8-12: aplica el idioma elegido en Ajustes (en 13+ lo hace el sistema).
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        // Con la app abierta se sincroniza cada minuto: si alguien con quien compartes una lista
        // tacha algo en el súper, lo ves enseguida (con el CTag, si no hay cambios cuesta una petición).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(FOREGROUND_SYNC_MS)
                    viewModel.triggerSync()
                }
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MisTareasTheme(darkTheme = darkTheme, amoled = settings.amoled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModel = viewModel, activity = this)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Acceso directo "Nueva tarea" y texto compartido desde otras apps. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_NEW_TASK -> viewModel.pendingNewTask.value = true
            ACTION_OPEN_TASK -> {
                val listId = intent.getStringExtra(EXTRA_LIST_ID)
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (listId != null && taskId != null) viewModel.pendingOpenTask.value = OpenTask(listId, taskId)
                intent.action = null
            }
            ACTION_OPEN_TODAY -> {
                viewModel.pendingOpenToday.value = true
                intent.action = null
            }
            Intent.ACTION_SEND -> {
                viewModel.receiveSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
                intent.action = null
            }
        }
    }

    companion object {
        private const val FOREGROUND_SYNC_MS = 60_000L
        const val ACTION_NEW_TASK = "com.ferlagod.mistareas.action.NEW_TASK"
        const val ACTION_OPEN_TASK = "com.ferlagod.mistareas.action.OPEN_TASK"
        const val ACTION_OPEN_TODAY = "com.ferlagod.mistareas.action.OPEN_TODAY"
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_TASK_ID = "task_id"
    }
}

@Composable
private fun AppNavHost(viewModel: MainViewModel, activity: MainActivity) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val pendingShare by viewModel.pendingShare.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val pendingOpenTask by viewModel.pendingOpenTask.collectAsStateWithLifecycle()
    val pendingOpenToday by viewModel.pendingOpenToday.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    // Android 13+: permiso para mostrar los avisos (se pide la primera vez que se pone uno).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsGranted()
        if (!granted) scope.launch {
            val result = snackbarHostState.showSnackbar(
                resources.getString(R.string.notifications_denied),
                actionLabel = resources.getString(R.string.open_settings),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) openNotificationSettings()
        }
    }

    // Desde la notificación de un aviso: se abre su lista (y en ella, la tarea).
    // Desde el resumen diario: se abre la vista "Hoy".
    LaunchedEffect(pendingOpenToday) {
        if (!pendingOpenToday) return@LaunchedEffect
        viewModel.pendingOpenToday.value = false
        navController.navigate(Routes.smart(SmartView.TODAY)) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    LaunchedEffect(pendingOpenTask) {
        val target = pendingOpenTask ?: return@LaunchedEffect
        navController.navigate(Routes.list(target.listId)) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    // Avisos: "Tarea eliminada · Deshacer", "3 tareas añadidas a Compra"…
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (event) {
                    is UiEvent.TasksDeleted -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getQuantityString(R.plurals.tasks_deleted, event.tasks.size, event.tasks.size),
                            actionLabel = resources.getString(R.string.undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreTasks(event.tasks)
                    }
                    is UiEvent.Message -> snackbarHostState.showSnackbar(event.text, withDismissAction = true)
                    is UiEvent.NextOccurrence -> {
                        val locale = resources.configuration.locales[0]
                        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
                        val message = event.due?.let { resources.getString(R.string.repeat_next, java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(it)) }
                            ?: resources.getString(R.string.repeats)
                        val result = snackbarHostState.showSnackbar(
                            message,
                            actionLabel = resources.getString(R.string.undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoAdvance(event.previous)
                    }
                    UiEvent.RequestExactAlarms -> {
                        val result = snackbarHostState.showSnackbar(
                            resources.getString(R.string.exact_alarms_prompt),
                            actionLabel = resources.getString(R.string.allow),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName))
                            )
                        }
                    }
                    UiEvent.RequestNotificationPermission -> {
                        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (canAsk) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val result = snackbarHostState.showSnackbar(
                                resources.getString(R.string.notifications_denied),
                                actionLabel = resources.getString(R.string.open_settings),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) openNotificationSettings()
                        }
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onOpenList = { navController.navigate(Routes.list(it)) { launchSingleTop = true } },
                onOpenSmart = { navController.navigate(Routes.smart(it)) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }
        composable(Routes.LIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ListScreen(
                listId = entry.arguments?.getString("id").orEmpty(),
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
        composable(Routes.SMART, arguments = listOf(navArgument("view") { type = NavType.StringType })) { entry ->
            val view = SmartView.entries.firstOrNull { it.name == entry.arguments?.getString("view") } ?: SmartView.TODAY
            SmartScreen(view = view, viewModel = viewModel, snackbarHostState = snackbarHostState, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                currentLanguage = LanguageManager.current(activity),
                onLanguageSelected = { LanguageManager.set(activity, it) },
                onBack = { navController.popBackStack() }
            )
        }
    }

    pendingShare?.let { shared ->
        if (lists.isNotEmpty()) {
            ShareToListDialog(
                lines = shared.lines,
                lists = lists.filter { !it.readOnly },
                initialListId = viewModel.defaultListId(),
                onDismiss = { viewModel.pendingShare.value = null },
                onConfirm = { listId -> viewModel.addShared(listId, shared.lines) }
            )
        }
    }
}
