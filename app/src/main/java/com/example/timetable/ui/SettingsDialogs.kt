package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.model.ClassPeriod
import com.example.timetable.model.MAX_SECTION
import com.example.timetable.model.TimePreset
import com.example.timetable.model.createPresetPeriods
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SemesterSettingsDialog(
    currentStartDate: LocalDate,
    currentTotalWeeks: Int,
    minimumTotalWeeks: Int,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int) -> Unit
) {
    var startDateText by remember(currentStartDate) {
        mutableStateOf(currentStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var totalWeeksText by remember(currentTotalWeeks) {
        mutableStateOf(currentTotalWeeks.toString())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期设置") },
        text = {
            Column {
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text("第一周周一（yyyy-MM-dd）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = totalWeeksText,
                    onValueChange = { totalWeeksText = it.filter(Char::isDigit) },
                    label = { Text("学期总周数") },
                    singleLine = true
                )
                ValidationMessage(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startDate = runCatching {
                        LocalDate.parse(startDateText.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                    }.getOrNull()
                    val weeks = totalWeeksText.toIntOrNull()
                    errorMessage = when {
                        startDate == null -> "日期格式应为 yyyy-MM-dd，例如 2026-09-07"
                        startDate.dayOfWeek != DayOfWeek.MONDAY -> "学期开始日期必须是周一"
                        weeks == null || weeks !in 1..30 -> "学期总周数必须在 1 到 30 之间"
                        weeks < minimumTotalWeeks ->
                            "已有课程持续到第 $minimumTotalWeeks 周，无法减少"
                        else -> {
                            onConfirm(startDate, weeks)
                            null
                        }
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

@Composable
fun SectionCountDialog(
    currentSectionCount: Int,
    minimumSectionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedCount by remember(currentSectionCount) { mutableStateOf(currentSectionCount) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置每日总节数") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("所有星期将统一显示相同的课程节数")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        enabled = selectedCount > 1,
                        onClick = { selectedCount--; errorMessage = null }
                    ) { Text("−") }
                    Text(
                        text = "$selectedCount 节",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(
                        enabled = selectedCount < MAX_SECTION,
                        onClick = { selectedCount++; errorMessage = null }
                    ) { Text("+") }
                }
                ValidationMessage(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedCount < minimumSectionCount) {
                        errorMessage = "已有课程占用第 $minimumSectionCount 节，无法减少"
                    } else {
                        onConfirm(selectedCount)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

@Composable
fun TimeSettingsDialog(
    currentPeriods: List<ClassPeriod>,
    onDismiss: () -> Unit,
    onConfirm: (List<ClassPeriod>) -> Unit
) {
    var periodCount by remember { mutableIntStateOf(currentPeriods.size) }
    val startTimes = remember {
        mutableStateListOf<String>().apply {
            addAll(currentPeriods.map { formatMinutesAsTime(it.startMinutes) })
        }
    }
    val endTimes = remember {
        mutableStateListOf<String>().apply {
            addAll(currentPeriods.map { formatMinutesAsTime(it.endMinutes) })
        }
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置每节课时间") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    TimePreset.entries.forEach { preset ->
                        TextButton(onClick = {
                            val presetPeriods = createPresetPeriods(preset)
                            periodCount = presetPeriods.size
                            startTimes.clear()
                            endTimes.clear()
                            presetPeriods.forEach {
                                startTimes += formatMinutesAsTime(it.startMinutes)
                                endTimes += formatMinutesAsTime(it.endMinutes)
                            }
                            errorMessage = null
                        }) { Text(preset.displayName) }
                    }
                }
                (0 until periodCount).forEach { index ->
                    Text(
                        text = "第${index + 1}节",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PeriodTimeField(
                            startTimes[index],
                            { startTimes[index] = it; errorMessage = null },
                            "开始",
                            Modifier.weight(1f)
                        )
                        PeriodTimeField(
                            endTimes[index],
                            { endTimes[index] = it; errorMessage = null },
                            "结束",
                            Modifier.weight(1f)
                        )
                    }
                }
                ValidationMessage(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedPeriods = mutableListOf<ClassPeriod>()
                    var validationError: String? = null
                    for (index in 0 until periodCount) {
                        val start = parseTimeToMinutes(startTimes[index])
                        val end = parseTimeToMinutes(endTimes[index])
                        validationError = when {
                            start == null || end == null -> "第${index + 1}节时间格式应为 HH:mm"
                            end <= start -> "第${index + 1}节结束时间必须晚于开始时间"
                            index > 0 && start < parsedPeriods[index - 1].endMinutes ->
                                "第${index + 1}节与上一节时间重叠"
                            else -> null
                        }
                        if (validationError != null) break
                        parsedPeriods.add(ClassPeriod(index + 1, start ?: break, end ?: break))
                    }
                    if (validationError == null) onConfirm(parsedPeriods)
                    else errorMessage = validationError
                }
            ) { Text("保存") }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

@Composable
private fun PeriodTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("08:00") },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun ValidationMessage(message: String?) {
    message?.let {
        Text(
            text = it,
            color = Color.Red,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CancelButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss) { Text("取消") }
}

fun parseTimeToMinutes(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size != 2 || parts[1].length != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun formatMinutesAsTime(totalMinutes: Int): String =
    "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))

fun formatMonthDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M/d"))
