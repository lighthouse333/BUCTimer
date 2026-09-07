package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    onUpdateDurations: (Int, Int) -> Unit,
    onUpdateGoal: (String) -> Unit,
    onDeleteGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDurationDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    val progress = if (state.totalSeconds > 0) {
        1f - state.remainingSeconds.toFloat() / state.totalSeconds
    } else {
        0f
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("番茄钟", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { showDurationDialog = true }) {
                Text("时长设置")
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 28.dp)
                .size(250.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize().padding(8.dp),
                strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.phase.displayName,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDuration(state.remainingSeconds),
                    fontSize = 52.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isRunning) "进行中" else "已暂停",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(16.dp)
                )
                .clickable { showGoalDialog = true }
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "本次专注目标",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = state.goal.ifBlank { "点击设置，例如：完成数学作业" },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (state.goal.isBlank()) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (state.goal.isBlank() && state.phase == PomodoroPhase.FOCUS) {
            Text(
                text = "设置目标后即可开始专注",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("重置")
            }
            Button(
                onClick = onToggleRunning,
                enabled = state.isRunning || state.phase == PomodoroPhase.BREAK || state.goal.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isRunning) "暂停" else "开始")
            }
        }

        Text(
            text = "专注 ${state.focusMinutes} 分钟 · 休息 ${state.breakMinutes} 分钟",
            modifier = Modifier.padding(top = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (showDurationDialog) {
        DurationSettingsDialog(
            focusMinutes = state.focusMinutes,
            breakMinutes = state.breakMinutes,
            onDismiss = { showDurationDialog = false },
            onConfirm = { focus, rest ->
                onUpdateDurations(focus, rest)
                showDurationDialog = false
            }
        )
    }

    if (showGoalDialog) {
        GoalDialog(
            currentGoal = state.goal,
            onDismiss = { showGoalDialog = false },
            onSave = {
                onUpdateGoal(it)
                showGoalDialog = false
            },
            onDelete = {
                onDeleteGoal()
                showGoalDialog = false
            }
        )
    }
}

@Composable
private fun DurationSettingsDialog(
    focusMinutes: Int,
    breakMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var focusText by remember(focusMinutes) { mutableStateOf(focusMinutes.toString()) }
    var breakText by remember(breakMinutes) { mutableStateOf(breakMinutes.toString()) }
    val focus = focusText.toIntOrNull()
    val rest = breakText.toIntOrNull()
    val valid = focus in PomodoroViewModel.MIN_FOCUS_MINUTES..PomodoroViewModel.MAX_FOCUS_MINUTES &&
        rest in PomodoroViewModel.MIN_BREAK_MINUTES..PomodoroViewModel.MAX_BREAK_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置番茄钟时长") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = focusText,
                    onValueChange = { focusText = it.filter(Char::isDigit) },
                    label = { Text("专注时间（分钟）") },
                    supportingText = { Text("1–180 分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = breakText,
                    onValueChange = { breakText = it.filter(Char::isDigit) },
                    label = { Text("休息时间（分钟）") },
                    supportingText = { Text("1–60 分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text(
                    text = "修改时长会重置当前计时。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(requireNotNull(focus), requireNotNull(rest)) },
                enabled = valid
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun GoalDialog(
    currentGoal: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var goal by remember(currentGoal) { mutableStateOf(currentGoal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentGoal.isBlank()) "设置专注目标" else "编辑专注目标") },
        text = {
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text("目标名称") },
                placeholder = { Text("例如：完成数学作业") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(goal) },
                enabled = goal.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (currentGoal.isNotBlank()) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return "%02d:%02d".format(minutes, seconds)
}
