package com.example.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private enum class AppDestination(val label: String, path: String) {
    TODO("待办", "M4,4 L20,4 L20,20 L4,20 Z M7,12 L10,15 L17,8"),
    TIMETABLE("课表", "M4,5 L20,5 L20,21 L4,21 Z M8,3 L8,7 M16,3 L16,7 M4,10 L20,10 M9,10 L9,21 M15,10 L15,21 M4,15 L20,15"),
    POMODORO("番茄钟", "M9,5 L20,5 M9,12 L20,12 M9,19 L20,19 M3,4 L5,6 L7,3 M3,11 L5,13 L7,10 M3,18 L5,20 L7,17"),
    ACADEMICS("学业", "M2,8 L12,3 L22,8 L12,13 Z M6,10 L6,17 Q12,22 18,17 L18,10 M22,8 L22,17"),
    SETTINGS("设置", "M4,6 L20,6 M4,12 L20,12 M4,18 L20,18 M8,3 L8,9 M16,9 L16,15 M10,15 L10,21");

    val icon = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f
    ).build()
}

@Composable
fun ClassScheduleApp(
    viewModel: TimetableViewModel,
    todoViewModel: TodoViewModel,
    pomodoroViewModel: PomodoroViewModel,
    academicsViewModel: AcademicsViewModel,
    foregroundEntry: Int
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.TIMETABLE) }
    val pageState = rememberSaveableStateHolder()
    val updateState by viewModel.updateState.collectAsState()
    val automaticUpdateChecks by viewModel.automaticUpdateChecks.collectAsState()
    val updatePopupReminders by viewModel.updatePopupReminders.collectAsState()
    val lastUpdateCheck by viewModel.lastUpdateCheck.collectAsState()
    val updatePrompt by viewModel.updatePrompt.collectAsState()
    val todoItems by todoViewModel.items.collectAsState()
    val pomodoroState by pomodoroViewModel.state.collectAsState()

    BackHandler(enabled = destination != AppDestination.TIMETABLE) {
        destination = AppDestination.TIMETABLE
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            pageState.SaveableStateProvider(destination.name) {
                when (destination) {
                    AppDestination.TODO -> TodoScreen(
                        items = todoItems,
                        onAdd = todoViewModel::add,
                        onUpdateTitle = todoViewModel::updateTitle,
                        onCompletedChange = todoViewModel::setCompleted,
                        onDelete = todoViewModel::delete
                    )
                    AppDestination.TIMETABLE -> TimetableScreen(
                        viewModel = viewModel,
                        foregroundEntry = foregroundEntry,
                        onOpenSettings = { destination = AppDestination.SETTINGS },
                        onJwLoginSuccess = academicsViewModel::refresh
                    )
                    AppDestination.POMODORO -> PomodoroScreen(
                        state = pomodoroState,
                        onToggleRunning = pomodoroViewModel::toggleRunning,
                        onReset = pomodoroViewModel::reset,
                        onUpdateDurations = pomodoroViewModel::updateDurations,
                        onUpdateGoal = pomodoroViewModel::updateGoal,
                        onDeleteGoal = pomodoroViewModel::deleteGoal
                    )
                    AppDestination.ACADEMICS -> AcademicsScreen(viewModel = academicsViewModel)
                    AppDestination.SETTINGS -> AboutSettingsScreen(
                        updateState = updateState,
                        automaticUpdateChecks = automaticUpdateChecks,
                        updatePopupReminders = updatePopupReminders,
                        lastUpdateCheck = lastUpdateCheck,
                        savedJwStudentId = viewModel.savedJwStudentId(),
                        onDeleteJwCredentials = viewModel::clearSavedJwCredentials,
                        onSaveJwCredentials = viewModel::saveJwCredentials,
                        onAutomaticUpdateChecksChange = viewModel::setAutomaticUpdateChecks,
                        onUpdatePopupRemindersChange = viewModel::setUpdatePopupReminders,
                        onCheckForUpdate = viewModel::checkForAppUpdate,
                        onDownloadUpdate = viewModel::downloadAppUpdate,
                        onInstallUpdate = viewModel::installDownloadedUpdate,
                        onBack = { destination = AppDestination.TIMETABLE }
                    )
                }
            }
        }
    }

    updatePrompt?.let { info ->
        UpdateAvailableDialog(
            info = info,
            onUpdateNow = {
                destination = AppDestination.SETTINGS
                viewModel.downloadAppUpdate(info)
            },
            onUpdateLater = viewModel::dismissUpdatePrompt
        )
    }
}
