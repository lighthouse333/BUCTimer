package com.example.timetable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.model.Course
import com.example.timetable.model.ClassPeriod
import com.example.timetable.model.effectiveEndMinutes
import com.example.timetable.model.effectiveStartMinutes
import com.example.timetable.model.formatActiveWeeks
import com.example.timetable.model.parseActiveWeeks
import com.example.timetable.model.weekSchedulesOverlap

@Composable
fun AddCourseDialog(
    weekDays: List<String>,
    courses: List<Course>,
    classPeriods: List<ClassPeriod>,
    totalWeeks: Int,
    initialWeekDay: String = "",
    initialStartSection: Int? = null,
    initialEndSection: Int? = null,
    onDismiss: () -> Unit,
    onAddCourse: (Course) -> Unit
) {
    CourseEditorDialog(
        title = "添加课程",
        originalCourse = null,
        weekDays = weekDays,
        courses = courses,
        classPeriods = classPeriods,
        totalWeeks = totalWeeks,
        initialWeekDay = initialWeekDay,
        initialStartSection = initialStartSection,
        initialEndSection = initialEndSection,
        onDismiss = onDismiss,
        onSaveCourse = onAddCourse
    )
}

@Composable
fun EditCourseDialog(
    course: Course,
    weekDays: List<String>,
    courses: List<Course>,
    classPeriods: List<ClassPeriod>,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onSaveCourse: (Course) -> Unit
) {
    CourseEditorDialog(
        title = "编辑课程",
        originalCourse = course,
        weekDays = weekDays,
        courses = courses,
        classPeriods = classPeriods,
        totalWeeks = totalWeeks,
        onDismiss = onDismiss,
        onDeleteRequest = onDeleteRequest,
        onSaveCourse = onSaveCourse
    )
}

@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSaveNote: (String) -> Unit
) {
    var note by remember(course.id, course.note) { mutableStateOf(course.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CourseDetailRow("教师", course.teacher)
                CourseDetailRow("教室", course.classroom)
                CourseDetailRow("星期", course.weekDay)
                CourseDetailRow(
                    "时间",
                    course.customStartMinutes?.let { start ->
                        "${formatMinutesAsTime(start)}-${formatMinutesAsTime(requireNotNull(course.customEndMinutes))}"
                    } ?: "第 ${course.startSection}-${course.endSection} 节"
                )
                CourseDetailRow("周次", buildString {
                    append(formatActiveWeeks(course.activeWeeks))
                    append(" 周")
                    courseOddEvenLabel(course.activeWeeks)?.let { append("（$it）") }
                })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备忘") },
                    minLines = 3,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = note != course.note,
                    onClick = { onSaveNote(note) }
                ) { Text("保存备忘") }
                TextButton(onClick = onEdit) { Text("编辑课程") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun CourseDetailRow(label: String, value: String) {
    Text(
        text = "$label：${value.ifBlank { "无" }}",
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CourseEditorDialog(
    title: String,
    originalCourse: Course?,
    weekDays: List<String>,
    courses: List<Course>,
    classPeriods: List<ClassPeriod>,
    totalWeeks: Int,
    initialWeekDay: String = "",
    initialStartSection: Int? = null,
    initialEndSection: Int? = null,
    onDismiss: () -> Unit,
    onDeleteRequest: (() -> Unit)? = null,
    onSaveCourse: (Course) -> Unit
) {
    var name by remember(originalCourse) { mutableStateOf(originalCourse?.name.orEmpty()) }
    var teacher by remember(originalCourse) { mutableStateOf(originalCourse?.teacher.orEmpty()) }
    var classroom by remember(originalCourse) { mutableStateOf(originalCourse?.classroom.orEmpty()) }
    val maxSection = classPeriods.size
    var weekDay by remember(originalCourse, initialWeekDay) {
        mutableStateOf(originalCourse?.weekDay ?: initialWeekDay)
    }
    var startSection by remember(originalCourse) {
        mutableStateOf(
            originalCourse?.startSection?.toString()
                ?: initialStartSection?.toString().orEmpty()
        )
    }
    var endSection by remember(originalCourse) {
        mutableStateOf(
            originalCourse?.endSection?.toString()
                ?: initialEndSection?.toString().orEmpty()
        )
    }
    var useCustomTime by remember(originalCourse) {
        mutableStateOf(originalCourse?.customStartMinutes != null)
    }
    var startTimeText by remember(originalCourse, initialStartSection, classPeriods) {
        mutableStateOf(
            originalCourse?.customStartMinutes?.let(::formatMinutesAsTime)
                ?: initialStartSection
                    ?.let { classPeriods.getOrNull(it - 1)?.startMinutes }
                    ?.let(::formatMinutesAsTime)
                .orEmpty()
        )
    }
    var endTimeText by remember(originalCourse, initialEndSection, classPeriods) {
        mutableStateOf(
            originalCourse?.customEndMinutes?.let(::formatMinutesAsTime)
                ?: initialEndSection
                    ?.let { classPeriods.getOrNull(it - 1)?.endMinutes }
                    ?.let(::formatMinutesAsTime)
                .orEmpty()
        )
    }
    var activeWeeksText by remember(originalCourse, totalWeeks) {
        mutableStateOf(
            originalCourse?.let { formatActiveWeeks(it.activeWeeks) } ?: "1-$totalWeeks"
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CourseTextField(name, { name = it }, "课程名称")
                CourseTextField(teacher, { teacher = it }, "教师")
                CourseTextField(classroom, { classroom = it }, "教室")
                CourseTextField(weekDay, { weekDay = it }, "星期（例如：周一）")
                Row {
                    Checkbox(
                        checked = useCustomTime,
                        onCheckedChange = { useCustomTime = it }
                    )
                    Text("自定义上课时间", modifier = Modifier.padding(top = 12.dp))
                }
                if (useCustomTime) {
                    CourseTextField(
                        startTimeText,
                        { startTimeText = it.filter { char -> char.isDigit() || char == ':' } },
                        "开始时间（HH:mm）"
                    )
                    CourseTextField(
                        endTimeText,
                        { endTimeText = it.filter { char -> char.isDigit() || char == ':' } },
                        "结束时间（HH:mm）"
                    )
                } else {
                    CourseTextField(
                        startSection,
                        { startSection = it.filter(Char::isDigit) },
                        "开始节次"
                    )
                    CourseTextField(
                        endSection,
                        { endSection = it.filter(Char::isDigit) },
                        "结束节次"
                    )
                }
                CourseTextField(
                    activeWeeksText,
                    { activeWeeksText = it },
                    "上课周次（如 1-9,11-18）"
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val customStart = startTimeText.toMinutesOrNull().takeIf { useCustomTime }
                    val customEnd = endTimeText.toMinutesOrNull().takeIf { useCustomTime }
                    val customSections = if (customStart != null && customEnd != null) {
                        sectionsOverlapping(customStart, customEnd, classPeriods)
                    } else null
                    val start = customSections?.first ?: startSection.toIntOrNull()
                    val end = customSections?.last ?: endSection.toIntOrNull()
                    val activeWeeks = parseActiveWeeks(activeWeeksText, totalWeeks)
                    val editingIndex = originalCourse?.let(courses::indexOf) ?: -1

                    errorMessage = when {
                        name.isBlank() -> "请输入课程名称"
                        weekDay !in weekDays -> "星期只能填写周一至周日"
                        useCustomTime && (customStart == null || customEnd == null) ->
                            "时间格式不正确，请按 HH:mm 填写"
                        useCustomTime && customStart != null && customEnd != null &&
                            customEnd <= customStart -> "结束时间必须晚于开始时间"
                        useCustomTime && customStart != null && customEnd != null && (
                            customStart < classPeriods.first().startMinutes ||
                                customEnd > classPeriods.last().endMinutes
                            ) -> "自定义时间需在当天课表时间范围内"
                        useCustomTime && customSections == null -> "自定义时间未覆盖任何节次"
                        start == null || start !in 1..maxSection ->
                            "开始节次必须在 1 到 $maxSection 之间"
                        end == null || end !in start..maxSection ->
                            "结束节次必须在开始节次到第 $maxSection 节之间"
                        activeWeeks == null ->
                            "周次格式不正确，请填写 1-$totalWeeks 范围内的周次"
                        courses.withIndex().any { (index, existing) ->
                            index != editingIndex &&
                                existing.weekDay == weekDay &&
                                (customStart ?: classPeriods[start - 1].startMinutes) <
                                    existing.effectiveEndMinutes(classPeriods) &&
                                (customEnd ?: classPeriods[end - 1].endMinutes) >
                                    existing.effectiveStartMinutes(classPeriods) &&
                                weekSchedulesOverlap(activeWeeks, existing)
                        } -> "该时间已经有其他课程"
                        else -> {
                            onSaveCourse(
                                Course(
                                    id = originalCourse?.id ?: 0,
                                    name = name.trim(),
                                    teacher = teacher.trim(),
                                    classroom = classroom.trim(),
                                    weekDay = weekDay,
                                    startSection = start,
                                    endSection = end,
                                    activeWeeks = activeWeeks,
                                    customStartMinutes = customStart,
                                    customEndMinutes = customEnd,
                                    note = originalCourse?.note.orEmpty()
                                )
                            )
                            null
                        }
                    }
                }
            ) {
                Text(if (originalCourse == null) "添加" else "保存")
            }
        },
        dismissButton = {
            Row {
                onDeleteRequest?.let { delete ->
                    TextButton(onClick = delete) {
                        Text("删除课程", color = Color.Red)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

private fun String.toMinutesOrNull(): Int? {
    val parts = split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun sectionsOverlapping(
    startMinutes: Int,
    endMinutes: Int,
    periods: List<ClassPeriod>
): IntRange? {
    val first = periods.indexOfFirst { it.endMinutes > startMinutes }
    val last = periods.indexOfLast { it.startMinutes < endMinutes }
    if (first < 0 || last < first) return null
    return (first + 1)..(last + 1)
}

@Composable
private fun CourseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
fun DeleteCourseDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除课程") },
        text = { Text("确定要删除“${course.name}”吗？删除后无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text("确认删除", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun courseOddEvenLabel(weeks: Set<Int>): String? {
    if (weeks.isEmpty()) return null
    val sorted = weeks.sorted()
    val isConsecutive = sorted.size == sorted.last() - sorted.first() + 1
    if (!isConsecutive) return null
    return when {
        sorted.all { it % 2 == 1 } -> "单周"
        sorted.all { it % 2 == 0 } -> "双周"
        else -> null
    }
}
