package com.example.timetable.ui

import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.timetable.model.Course
import com.example.timetable.data.TimetableEntity
import com.example.timetable.importer.TimetableImportSchool
import com.example.timetable.model.findWeekContainingDate
import com.example.timetable.model.effectiveEndMinutes
import com.example.timetable.model.effectiveStartMinutes
import com.example.timetable.model.formatActiveWeeks
import com.example.timetable.model.isActiveInWeek
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    foregroundEntry: Int = 0,
    onOpenSettings: () -> Unit = {},
    onJwLoginSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val courses by viewModel.courses.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val timetables by viewModel.timetables.collectAsState()
    val currentTimetable by viewModel.currentTimetable.collectAsState()
    val selectedTimetableId by viewModel.selectedTimetableId.collectAsState()
    val timetableImportState by viewModel.timetableImportState.collectAsState()
    val useCompactView by viewModel.compactTimetableView.collectAsState()
    var selectedImportSchool by remember { mutableStateOf<TimetableImportSchool?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val school = selectedImportSchool
        if (uri != null && school != null) {
            viewModel.parseTimetableFile(uri, school)
        }
        selectedImportSchool = null
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSectionCountDialog by remember { mutableStateOf(false) }
    var showTimeSettingsDialog by remember { mutableStateOf(false) }
    var showSemesterSettingsDialog by remember { mutableStateOf(false) }
    var showTimetableDialog by remember { mutableStateOf(false) }
    var showCreateTimetableDialog by remember { mutableStateOf(false) }
    var showOnlineImportDialog by remember { mutableStateOf(false) }
    var showOnlineImportWeb by remember { mutableStateOf(false) }
    var showLoginFailedDialog by remember { mutableStateOf(false) }
    var onlineImportStudentId by remember { mutableStateOf("") }
    var onlineImportPassword by remember { mutableStateOf("") }
    var onlineImportRemember by remember { mutableStateOf(false) }
    var onlineImportYear by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault()).year) }
    var onlineImportSemester by remember { mutableStateOf(1) }
    var timetablePendingRename by remember { mutableStateOf<TimetableEntity?>(null) }
    var timetablePendingDeletion by remember { mutableStateOf<TimetableEntity?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var pendingCourseSelection by remember { mutableStateOf<CourseSelection?>(null) }
    var dragCourseSelection by remember { mutableStateOf<CourseSelection?>(null) }
    var selectionAwaitingConfirmation by remember { mutableStateOf<CourseSelection?>(null) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var courseBeingEdited by remember { mutableStateOf<Course?>(null) }
    var coursePendingDeletion by remember { mutableStateOf<Course?>(null) }
    var currentWeek by rememberSaveable { mutableStateOf(1) }
    var lastWeekResetKey by rememberSaveable { mutableStateOf("") }
    val sectionCount = settings.sectionCount
    val semesterStart = settings.semesterStart
    val totalWeeks = settings.totalWeeks
    val classPeriods = settings.classPeriods
    val pagerState = rememberPagerState(initialPage = currentWeek - 1) { totalWeeks }
    val coroutineScope = rememberCoroutineScope()
    val localDate = LocalDate.now(ZoneId.systemDefault())
    val semesterEndExclusive = semesterStart.plusWeeks(totalWeeks.toLong())
    val isLocalDateInSemester =
        !localDate.isBefore(semesterStart) && localDate.isBefore(semesterEndExclusive)

    LaunchedEffect(selectedTimetableId, semesterStart, totalWeeks, foregroundEntry) {
        val resetKey = "$selectedTimetableId/$semesterStart/$totalWeeks/$foregroundEntry"
        if (lastWeekResetKey != resetKey) {
            val targetWeek = findWeekContainingDate(localDate, semesterStart, totalWeeks)
            pagerState.scrollToPage(targetWeek - 1)
            currentWeek = targetWeek
            lastWeekResetKey = resetKey
        }
        selectedCourse = null
    }

    LaunchedEffect(pagerState.currentPage) {
        currentWeek = pagerState.currentPage + 1
    }

    LaunchedEffect(currentWeek) {
        selectionAwaitingConfirmation = null
        dragCourseSelection = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTimetable?.name ?: "我的课表",
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Box {
                TextButton(
                    onClick = { showTopMenu = true },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "⋮",
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (useCompactView) {
                                    "切换为完整课表"
                                } else {
                                    "切换为今日课表（简洁版）"
                                }
                            )
                        },
                        onClick = {
                            viewModel.setCompactTimetableView(!useCompactView)
                            showTopMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("切换或新建课表") },
                        onClick = {
                            showTopMenu = false
                            showTimetableDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加课程") },
                        onClick = {
                            showTopMenu = false
                            pendingCourseSelection = null
                            showAddDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导入北化课表 PDF") },
                        onClick = {
                            showTopMenu = false
                            selectedImportSchool =
                                TimetableImportSchool.BEIJING_UNIVERSITY_OF_CHEMICAL_TECHNOLOGY
                            pdfPicker.launch(
                                TimetableImportSchool.BEIJING_UNIVERSITY_OF_CHEMICAL_TECHNOLOGY
                                    .acceptedMimeTypes
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("北化教务系统导入（测试中）") },
                        onClick = {
                            showTopMenu = false
                            showOnlineImportDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("设置每日节数") },
                        onClick = {
                            showTopMenu = false
                            showSectionCountDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("设置节次时间") },
                        onClick = {
                            showTopMenu = false
                            showTimeSettingsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("学期设置") },
                        onClick = {
                            showTopMenu = false
                            showSemesterSettingsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("关于与设置") },
                        onClick = {
                            showTopMenu = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }

        if (showTimetableDialog) {
            TimetableSelectionDialog(
                timetables = timetables,
                selectedTimetableId = selectedTimetableId,
                onDismiss = { showTimetableDialog = false },
                onSelect = { timetableId ->
                    viewModel.selectTimetable(timetableId)
                    showTimetableDialog = false
                },
                onCreate = {
                    showTimetableDialog = false
                    showCreateTimetableDialog = true
                },
                onRename = { timetable -> timetablePendingRename = timetable },
                onDelete = { timetable -> timetablePendingDeletion = timetable }
            )
        }

        timetablePendingRename?.let { timetable ->
            RenameTimetableDialog(
                timetable = timetable,
                onDismiss = { timetablePendingRename = null },
                onRename = { name ->
                    viewModel.renameTimetable(timetable.id, name)
                    timetablePendingRename = null
                }
            )
        }

        timetablePendingDeletion?.let { timetable ->
            DeleteTimetableDialog(
                timetable = timetable,
                onDismiss = { timetablePendingDeletion = null },
                onDelete = {
                    viewModel.deleteTimetable(timetable.id)
                    timetablePendingDeletion = null
                    showTimetableDialog = false
                }
            )
        }

        if (showCreateTimetableDialog) {
            CreateTimetableDialog(
                onDismiss = { showCreateTimetableDialog = false },
                onCreate = { name ->
                    viewModel.createTimetable(name)
                    showCreateTimetableDialog = false
                }
            )
        }

        when (val state = timetableImportState) {
            TimetableImportState.Idle -> Unit
            is TimetableImportState.Loading -> AlertDialog(
                onDismissRequest = {},
                title = { Text("正在获取课表") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                        Text(state.message)
                    }
                },
                confirmButton = {}
            )
            TimetableImportState.Saving -> AlertDialog(
                onDismissRequest = {},
                title = { Text("正在导入课程") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                        Text("正在保存所选课程，请稍候……")
                    }
                },
                confirmButton = {}
            )
            is TimetableImportState.Error -> AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                title = { Text("无法导入课表") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImportResult) { Text("确定") }
                }
            )
            is TimetableImportState.Success -> TimetableImportPreviewDialog(
                result = state.timetable,
                existingCourses = courses,
                onDismiss = viewModel::dismissImportResult,
                onImport = viewModel::importCourses
            )
            is TimetableImportState.Completed -> AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                title = { Text("导入完成") },
                text = { Text("已成功导入 ${state.importedCount} 门课程。") },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImportResult) { Text("完成") }
                }
            )
        }

        if (showOnlineImportDialog) {
            OnlineImportInfoDialog(
                hasSavedSession = viewModel.hasSavedJwSession(),
                savedStudentId = viewModel.savedJwStudentId().orEmpty(),
                savedPassword = viewModel.savedJwPassword().orEmpty(),
                onDismiss = { showOnlineImportDialog = false },
                onLogin = { studentId, year, semester, password, remember ->
                    onlineImportStudentId = studentId
                    onlineImportPassword = password
                    onlineImportRemember = remember
                    onlineImportYear = year
                    onlineImportSemester = semester
                    showOnlineImportDialog = false
                    showOnlineImportWeb = true
                },
                onUseSavedSession = { studentId, year, semester ->
                    showOnlineImportDialog = false
                    viewModel.savedJwSession()?.let { cookies ->
                        viewModel.importOnlineTimetable(cookies, studentId, year, semester)
                    }
                }
            )
        }

        if (showOnlineImportWeb) {
            JwLoginOverlay(
                studentId = onlineImportStudentId,
                password = onlineImportPassword,
                onCancel = {
                    showOnlineImportWeb = false
                    onlineImportPassword = ""
                },
                onLoginSuccess = { cookies ->
                    showOnlineImportWeb = false
                    val pwd = onlineImportPassword
                    val remember = onlineImportRemember
                    onlineImportPassword = ""
                    viewModel.importOnlineTimetable(
                        cookies,
                        onlineImportStudentId,
                        onlineImportYear,
                        onlineImportSemester,
                        password = pwd,
                        rememberCredentials = remember
                    )
                    onJwLoginSuccess()
                },
                onLoginFailed = {
                    showOnlineImportWeb = false
                    onlineImportPassword = ""
                    showLoginFailedDialog = true
                }
            )
        }

        if (showLoginFailedDialog) {
            AlertDialog(
                onDismissRequest = { showLoginFailedDialog = false },
                title = { Text("登录失败") },
                text = { Text("账号或密码错误，请检查后重试。") },
                confirmButton = {
                    TextButton(onClick = { showLoginFailedDialog = false }) { Text("确定") }
                }
            )
        }

        if (showAddDialog) {
            val selection = pendingCourseSelection
            AddCourseDialog(
                weekDays = weekDays,
                courses = courses,
                classPeriods = classPeriods,
                totalWeeks = totalWeeks,
                initialWeekDay = selection?.weekDay.orEmpty(),
                initialStartSection = selection?.startSection,
                initialEndSection = selection?.endSection,
                onDismiss = {
                    showAddDialog = false
                    pendingCourseSelection = null
                },
                onAddCourse = { course ->
                    viewModel.addCourse(course)
                    showAddDialog = false
                    pendingCourseSelection = null
                }
            )
        }

        if (showSectionCountDialog) {
            SectionCountDialog(
                currentSectionCount = sectionCount,
                minimumSectionCount = courses.maxOfOrNull { it.endSection } ?: 1,
                onDismiss = { showSectionCountDialog = false },
                onConfirm = { newSectionCount ->
                    val newPeriods = classPeriods.take(newSectionCount).toMutableList()
                    while (newPeriods.size < newSectionCount) {
                        val previousEnd = newPeriods.lastOrNull()?.endMinutes ?: (8 * 60 - 10)
                        val number = newPeriods.size + 1
                        val startMinutes = previousEnd + 10
                        newPeriods.add(
                            com.example.timetable.model.ClassPeriod(
                                number = number,
                                startMinutes = startMinutes,
                                endMinutes = startMinutes + 45
                            )
                        )
                    }
                    viewModel.saveSettings(
                        settings.copy(
                            sectionCount = newSectionCount,
                            classPeriods = newPeriods
                        )
                    )
                    showSectionCountDialog = false
                }
            )
        }

        if (showTimeSettingsDialog) {
            TimeSettingsDialog(
                currentPeriods = classPeriods,
                onDismiss = { showTimeSettingsDialog = false },
                onConfirm = { newPeriods ->
                    viewModel.saveSettings(
                        settings.copy(
                            sectionCount = newPeriods.size,
                            classPeriods = newPeriods
                        )
                    )
                    showTimeSettingsDialog = false
                }
            )
        }

        selectedCourse?.let { courseToView ->
            CourseDetailDialog(
                course = courseToView,
                onDismiss = { selectedCourse = null },
                onEdit = {
                    courseBeingEdited = courseToView
                    selectedCourse = null
                },
                onSaveNote = { note ->
                    val updatedCourse = courseToView.copy(note = note)
                    viewModel.updateCourse(updatedCourse)
                    selectedCourse = updatedCourse
                }
            )
        }

        courseBeingEdited?.let { courseToEdit ->
            EditCourseDialog(
                course = courseToEdit,
                weekDays = weekDays,
                courses = courses,
                classPeriods = classPeriods,
                totalWeeks = totalWeeks,
                onDismiss = { courseBeingEdited = null },
                onDeleteRequest = {
                    coursePendingDeletion = courseToEdit
                    courseBeingEdited = null
                },
                onSaveCourse = { updatedCourse ->
                    val courseIndex = courses.indexOf(courseToEdit)
                    if (courseIndex >= 0) {
                        viewModel.updateCourse(
                            updatedCourse.copy(id = courseToEdit.id)
                        )
                    }
                    courseBeingEdited = null
                }
            )
        }

        coursePendingDeletion?.let { courseToDelete ->
            DeleteCourseDialog(
                course = courseToDelete,
                onDismiss = { coursePendingDeletion = null },
                onConfirmDelete = {
                    viewModel.deleteCourse(courseToDelete)
                    coursePendingDeletion = null
                }
            )
        }

        if (showSemesterSettingsDialog) {
            SemesterSettingsDialog(
                currentStartDate = semesterStart,
                currentTotalWeeks = totalWeeks,
                minimumTotalWeeks = courses.maxOfOrNull { it.endWeek } ?: 1,
                onDismiss = { showSemesterSettingsDialog = false },
                onConfirm = { newStartDate, newTotalWeeks ->
                    viewModel.saveSettings(
                        settings.copy(
                            semesterStart = newStartDate,
                            totalWeeks = newTotalWeeks
                        )
                    )
                    currentWeek = currentWeek.coerceIn(1, newTotalWeeks)
                    showSemesterSettingsDialog = false
                }
            )
        }

        if (useCompactView) {
            CompactTodaySchedule(
                date = localDate,
                isDateInSemester = isLocalDateInSemester,
                semesterStart = semesterStart,
                totalWeeks = totalWeeks,
                courses = courses,
                classPeriods = classPeriods,
                onCourseClick = { selectedCourse = it },
                modifier = Modifier.weight(1f)
            )
        } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                enabled = pagerState.canScrollBackward,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            ) {
                Text("上一周", maxLines = 1)
            }
            BoxWithConstraints(
                modifier = Modifier.weight(0.8f),
                contentAlignment = Alignment.Center
            ) {
                val weekFontSize = when {
                    maxWidth < 56.dp -> 12.sp
                    maxWidth < 72.dp -> 14.sp
                    else -> 16.sp
                }
                AnimatedContent(
                    targetState = currentWeek,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { it * direction } + fadeIn(tween(180)))
                            .togetherWith(
                                slideOutHorizontally(tween(260)) { -it * direction } +
                                    fadeOut(tween(180))
                            )
                    },
                    label = "week-number"
                ) { week ->
                    Text(
                        text = "第 $week 周",
                        fontSize = weekFontSize,
                        lineHeight = weekFontSize * 1.15f,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            TextButton(
                enabled = pagerState.canScrollForward,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            ) {
                Text("下一周", maxLines = 1)
            }
        }

        Text(
            text = "课程数量：${courses.size} · 每日 $sectionCount 节 · 共 $totalWeeks 周",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { it }
        ) { page ->
            val displayedWeek = page + 1
            val displayedWeekStart = semesterStart.plusWeeks((displayedWeek - 1).toLong())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                TimetableHeaderCell(text = "节次", modifier = Modifier.weight(0.425f))
                for ((dayIndex, day) in weekDays.withIndex()) {
                    val date = displayedWeekStart.plusDays(dayIndex.toLong())
                    TimetableHeaderCell(
                        text = "$day\n${formatMonthDay(date)}",
                        isHighlighted = isLocalDateInSemester && date == localDate,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val sectionHeight = 88.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight * sectionCount)
            ) {
                Column(modifier = Modifier.weight(0.425f)) {
                    for (section in 1..sectionCount) {
                        val period = classPeriods[section - 1]
                        TimetableSectionCell(
                            section = section,
                            startTime = formatMinutesAsTime(period.startMinutes),
                            endTime = formatMinutesAsTime(period.endMinutes),
                            modifier = Modifier.height(sectionHeight)
                        )
                    }
                }
                for (day in weekDays) {
                    val occupiedSections = courses
                        .filter { it.weekDay == day && it.isActiveInWeek(displayedWeek) }
                        .flatMap { it.startSection..it.endSection }
                        .toSet()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .pointerInput(day, displayedWeek, occupiedSections, sectionCount) {
                                var anchorSection = 1
                                var latestSelection: CourseSelection? = null
                                fun sectionAt(y: Float): Int =
                                    ((y / size.height.coerceAtLeast(1)) * sectionCount)
                                        .toInt()
                                        .plus(1)
                                        .coerceIn(1, sectionCount)

                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        selectionAwaitingConfirmation = null
                                        anchorSection = sectionAt(offset.y)
                                        latestSelection = emptySectionSelection(
                                            day,
                                            anchorSection,
                                            anchorSection,
                                            occupiedSections
                                        )
                                        dragCourseSelection = latestSelection
                                    },
                                    onDrag = { change, _ ->
                                        latestSelection = emptySectionSelection(
                                            day,
                                            anchorSection,
                                            sectionAt(change.position.y),
                                            occupiedSections
                                        )
                                        dragCourseSelection = latestSelection
                                    },
                                    onDragEnd = {
                                        latestSelection?.let { selection ->
                                            selectionAwaitingConfirmation = selection
                                        }
                                        dragCourseSelection = null
                                    },
                                    onDragCancel = {
                                        dragCourseSelection = null
                                    }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            repeat(sectionCount) { sectionIndex ->
                                val section = sectionIndex + 1
                                TimetableEmptyCell(
                                    onClick = {
                                        if (selectionAwaitingConfirmation != null) {
                                            selectionAwaitingConfirmation = null
                                        } else if (section !in occupiedSections) {
                                            pendingCourseSelection = CourseSelection(
                                                weekDay = day,
                                                startSection = section,
                                                endSection = section
                                            )
                                            showAddDialog = true
                                        }
                                    },
                                    modifier = Modifier.height(sectionHeight)
                                )
                            }
                        }
                        (dragCourseSelection ?: selectionAwaitingConfirmation)
                            ?.takeIf { it.weekDay == day }
                            ?.let { selection ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(
                                            y = sectionHeight * (selection.startSection - 1)
                                        )
                                        .height(
                                            sectionHeight * (
                                                selection.endSection -
                                                    selection.startSection + 1
                                                )
                                        )
                                        .padding(2.dp)
                                        .background(
                                            if (dragCourseSelection != null) {
                                                Color(0x995C6BC0)
                                            } else {
                                                Color(0xCC66BB6A)
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable(
                                            enabled = dragCourseSelection == null,
                                            onClick = {
                                                pendingCourseSelection = selection
                                                selectionAwaitingConfirmation = null
                                                showAddDialog = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dragCourseSelection == null) {
                                        Text(
                                            text = "确认添加\n✓",
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            }
                        courses.filter {
                            it.weekDay == day && it.isActiveInWeek(displayedWeek)
                        }.forEach { course ->
                            val startOffset = courseStartRowOffset(course, classPeriods)
                            val endOffset = courseEndRowOffset(course, classPeriods)
                            TimetableCourseBlock(
                                course = course,
                                onCourseClick = {
                                    if (selectionAwaitingConfirmation != null) {
                                        selectionAwaitingConfirmation = null
                                    } else {
                                        selectedCourse = it
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = sectionHeight * startOffset)
                                    .height(
                                        sectionHeight * (endOffset - startOffset).coerceAtLeast(0.2f)
                                    )
                            )
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun CompactTodaySchedule(
    date: LocalDate,
    isDateInSemester: Boolean,
    semesterStart: LocalDate,
    totalWeeks: Int,
    courses: List<Course>,
    classPeriods: List<com.example.timetable.model.ClassPeriod>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDay = listOf(
        "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    )[date.dayOfWeek.value - 1]
    val currentWeek = findWeekContainingDate(date, semesterStart, totalWeeks)
    val todayCourses = if (isDateInSemester) {
        courses.filter { it.weekDay == weekDay && it.isActiveInWeek(currentWeek) }
            .sortedWith(
                compareBy<Course> { it.effectiveStartMinutes(classPeriods) }
                    .thenBy { it.name }
            )
    } else {
        emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 $weekDay",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isDateInSemester) {
                    Text(
                        text = "第 $currentWeek 周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isDateInSemester) {
                Text(
                    text = "${todayCourses.size} 门课程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!isDateInSemester) {
            CompactScheduleEmptyState("今天不在当前学期范围内")
        } else if (todayCourses.isEmpty()) {
            CompactScheduleEmptyState("今天没有课程")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                todayCourses.forEach { course ->
                    val startTime = formatMinutesAsTime(course.effectiveStartMinutes(classPeriods))
                    val endTime = formatMinutesAsTime(course.effectiveEndMinutes(classPeriods))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(courseColor(course), RoundedCornerShape(14.dp))
                            .clickable { onCourseClick(course) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(82.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(startTime, fontWeight = FontWeight.Bold)
                            Text(
                                text = endTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF555555)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = listOf(course.classroom, course.teacher)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · ")
                                    .ifBlank { "暂无教室或教师信息" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF555555),
                                maxLines = 1
                            )
                            Text(
                                text = "第 ${course.startSection}-${course.endSection} 节",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactScheduleEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun TimetableSelectionDialog(
    timetables: List<TimetableEntity>,
    selectedTimetableId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onRename: (TimetableEntity) -> Unit,
    onDelete: (TimetableEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择课表") },
        text = {
            Column {
                timetables.forEach { timetable ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = timetable.id == selectedTimetableId,
                            onCheckedChange = { checked ->
                                if (checked) onSelect(timetable.id)
                            }
                        )
                        Text(timetable.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRename(timetable) }) {
                            Text("重命名")
                        }
                        TextButton(
                            enabled = timetables.size > 1,
                            onClick = { onDelete(timetable) }
                        ) {
                            Text("删除")
                        }
                    }
                }
                TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text("新建课表")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameTimetableDialog(
    timetable: TimetableEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(timetable.id) { mutableStateOf(timetable.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名课表") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课表名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && name.trim() != timetable.name,
                onClick = { onRename(name.trim()) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteTimetableDialog(
    timetable: TimetableEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除课表") },
        text = { Text("确定删除“${timetable.name}”吗？其中的所有课程也会被永久删除。") },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("删除", color = Color.Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CreateTimetableDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建课表") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课表名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) }
            ) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OnlineImportInfoDialog(
    hasSavedSession: Boolean,
    savedStudentId: String,
    savedPassword: String,
    onDismiss: () -> Unit,
    onLogin: (studentId: String, year: Int, semester: Int, password: String, remember: Boolean) -> Unit,
    onUseSavedSession: (studentId: String, year: Int, semester: Int) -> Unit
) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberCredentials by remember { mutableStateOf(savedPassword.isNotEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var askFillConfirm by remember { mutableStateOf(false) }
    var fillAnsweredForId by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault()).year.toString()) }
    var semester by remember { mutableStateOf(1) }
    val yearValue = year.trim().toIntOrNull()
    val valid = studentId.isNotBlank() && password.isNotBlank() && yearValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在线导入课表") },
        text = {
            Column {
                Text("输入学号与密码后自动登录北化教务系统，拉取课表并同步成绩、绩点与考试。")
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { value ->
                        studentId = value
                        val trimmed = value.trim()
                        if (trimmed.isNotEmpty() &&
                            trimmed == savedStudentId &&
                            savedPassword.isNotEmpty() &&
                            fillAnsweredForId != trimmed
                        ) {
                            askFillConfirm = true
                        }
                    },
                    label = { Text("学号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("统一认证密码") },
                    singleLine = true,
                    visualTransformation =
                        if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏" else "显示", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberCredentials = !rememberCredentials }
                ) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = { rememberCredentials = it }
                    )
                    Text("记住账号密码（加密保存在本机，可在设置中删除）")
                }
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("学年起始年（如 2026）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1 to "秋冬", 2 to "春夏", 3 to "暑假").forEach { (code, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { semester = code }
                        ) {
                            RadioButton(selected = semester == code, onClick = { semester = code })
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (hasSavedSession) {
                    TextButton(
                        enabled = valid,
                        onClick = { onUseSavedSession(studentId.trim(), yearValue!!, semester) }
                    ) { Text("直接查询") }
                }
                TextButton(
                    enabled = valid,
                    onClick = {
                        onLogin(
                            studentId.trim(),
                            yearValue!!,
                            semester,
                            password,
                            rememberCredentials
                        )
                    }
                ) { Text(if (hasSavedSession) "重新登录" else "登录") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (askFillConfirm) {
        AlertDialog(
            onDismissRequest = { askFillConfirm = false },
            title = { Text("填充已保存的密码") },
            text = { Text("检测到账号 ${studentId.trim()} 已有保存的密码，是否自动填充？") },
            confirmButton = {
                TextButton(onClick = {
                    password = savedPassword
                    rememberCredentials = true
                    fillAnsweredForId = studentId.trim()
                    askFillConfirm = false
                }) { Text("填充") }
            },
            dismissButton = {
                TextButton(onClick = {
                    fillAnsweredForId = studentId.trim()
                    askFillConfirm = false
                }) { Text("不填充") }
            }
        )
    }
}

@Composable
private fun ImportSchoolDialog(
    schools: List<TimetableImportSchool>,
    onDismiss: () -> Unit,
    onSelect: (TimetableImportSchool) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学校") },
        text = {
            Column {
                Text("请选择课表所属学校，并按说明准备文件。")
                schools.forEach { school ->
                    TextButton(
                        onClick = { onSelect(school) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = school.displayName,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = school.importRequirement,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun TimetableImportPreviewDialog(
    result: com.example.timetable.importer.ParsedTimetable,
    existingCourses: List<Course>,
    onDismiss: () -> Unit,
    onImport: (List<Course>) -> Unit
) {
    var selectedGroupIndex by remember(result) { mutableStateOf(0) }
    val selectedGroup = result.groups.getOrNull(selectedGroupIndex)
    val displayedCourses = selectedGroup?.courses ?: result.courses
    val displayedWarnings = result.warnings + (selectedGroup?.warnings ?: emptyList())
    val duplicateIndices = remember(displayedCourses, existingCourses) {
        displayedCourses.indices.filterTo(mutableSetOf()) { index ->
            existingCourses.any { existing ->
                coursesAreDuplicates(displayedCourses[index], existing)
            }
        }
    }
    val conflictIndices = remember(displayedCourses, existingCourses) {
        displayedCourses.indices.filterTo(mutableSetOf()) { index ->
            val course = displayedCourses[index]
            existingCourses.any { existing -> coursesConflict(course, existing) } ||
                displayedCourses.indices.any { otherIndex ->
                    otherIndex != index && coursesConflict(course, displayedCourses[otherIndex])
                }
        }
    }
    var selectedIndices by remember(displayedCourses, duplicateIndices) {
        mutableStateOf(displayedCourses.indices.filterNot(duplicateIndices::contains).toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别到 ${displayedCourses.size} 门课程") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                result.semester?.let { Text(it, fontWeight = FontWeight.Bold) }
                if (result.groups.size > 1) {
                    Text("请选择班级", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    result.groups.forEachIndexed { index, group ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedGroupIndex == index,
                                onCheckedChange = { checked -> if (checked) selectedGroupIndex = index }
                            )
                            Text(group.name)
                        }
                    }
                }
                Text(
                    "请选择需要导入的课程。重复课程已自动取消选择。",
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                displayedCourses.forEachIndexed { index, course ->
                    val duplicate = index in duplicateIndices
                    val conflict = index in conflictIndices && !duplicate
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(
                            checked = index in selectedIndices,
                            enabled = !duplicate,
                            onCheckedChange = { checked ->
                                selectedIndices = if (checked) {
                                    selectedIndices + index
                                } else {
                                    selectedIndices - index
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${course.weekDay} ${course.startSection}-${course.endSection}节 · " +
                                    "${formatActiveWeeks(course.activeWeeks)}周" +
                                    (weekTypeLabel(course.activeWeeks)?.let { "（$it）" } ?: "")
                            )
                            Text("${course.classroom} · ${course.teacher}", color = Color.Gray)
                            when {
                                duplicate -> Text("已存在，跳过", color = Color.Gray, fontSize = 12.sp)
                                conflict -> Text("与其他课程时间冲突", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    }
                }
                displayedWarnings.forEach { warning ->
                    Text("提示：$warning", color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIndices.isNotEmpty(),
                onClick = {
                    onImport(selectedIndices.sorted().map(displayedCourses::get))
                }
            ) {
                Text("导入所选（${selectedIndices.size}）")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun coursesAreDuplicates(first: Course, second: Course): Boolean =
    first.name == second.name &&
        first.weekDay == second.weekDay &&
        first.startSection == second.startSection &&
        first.endSection == second.endSection &&
        first.activeWeeks == second.activeWeeks

private fun coursesConflict(first: Course, second: Course): Boolean =
    first.weekDay == second.weekDay &&
        first.startSection <= second.endSection &&
        first.endSection >= second.startSection &&
        first.activeWeeks.any(second.activeWeeks::contains) &&
        !coursesAreDuplicates(first, second)

@Composable
private fun TimetableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val backgroundColor = if (isHighlighted) Color(0xFFFFE0B2) else Color(0xFFE8EAF6)
    val contentColor = if (isHighlighted) Color(0xFFE65100) else Color.Black
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        val fontScale = LocalDensity.current.fontScale
        val widthScale = (maxWidth.value / 52f).coerceIn(0.72f, 1f)
        val accessibilityAdjustment = (1f / fontScale).coerceIn(0.78f, 1f)
        val titleSize = (11f * widthScale * accessibilityAdjustment).coerceAtLeast(8f).sp
        val dateSize = (9f * widthScale * accessibilityAdjustment).coerceAtLeast(7f).sp
        val lines = text.split('\n', limit = 2)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = lines.first(),
                fontSize = titleSize,
                lineHeight = titleSize * 1.1f,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
            lines.getOrNull(1)?.let { date ->
                Text(
                    text = date,
                    fontSize = dateSize,
                    lineHeight = dateSize * 1.1f,
                    color = if (isHighlighted) contentColor else Color.Gray,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TimetableSectionCell(
    section: Int,
    startTime: String,
    endTime: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        val fontScale = LocalDensity.current.fontScale
        val widthScale = (maxWidth.value / 28f).coerceIn(0.72f, 1.25f)
        val heightScale = (maxHeight.value / 72f).coerceIn(0.8f, 1.2f)
        val accessibilityAdjustment = (1f / fontScale).coerceIn(0.72f, 1f)
        val adaptiveScale = minOf(widthScale, heightScale) * accessibilityAdjustment
        val fontSize = (6.5f * adaptiveScale).coerceIn(5f, 7.5f).sp
        Text(
            text = "$section\n$startTime\n$endTime",
            fontSize = fontSize,
            lineHeight = fontSize * 1.18f,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            softWrap = false
        )
    }
}

@Composable
private fun TimetableEmptyCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
            .background(Color.White, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    )
}

@Composable
private fun TimetableCourseBlock(
    course: Course,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
            .background(
                courseColor(course),
                RoundedCornerShape(6.dp)
            )
            .clickable { onCourseClick(course) }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = course.name,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold
            )
            weekTypeLabel(course.activeWeeks)?.let { label ->
                Text(
                    text = label,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    color = Color(0xFF757575)
                )
            }
            Text(
                text = listOf(
                    course.classroom,
                    course.teacher,
                    course.customStartMinutes?.let { start ->
                        "${formatMinutesAsTime(start)}-${formatMinutesAsTime(requireNotNull(course.customEndMinutes))}"
                    } ?: "${course.startSection}-${course.endSection}节"
                ).filter(String::isNotBlank).joinToString("\n"),
                fontSize = 8.sp,
                lineHeight = 9.sp
            )
        }
    }
}

private fun weekTypeLabel(weeks: Set<Int>): String? {
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

private data class CourseSelection(
    val weekDay: String,
    val startSection: Int,
    val endSection: Int
)

private fun emptySectionSelection(
    weekDay: String,
    anchor: Int,
    current: Int,
    occupiedSections: Set<Int>
): CourseSelection? {
    if (anchor in occupiedSections) return null
    val direction = if (current >= anchor) 1 else -1
    var edge = anchor
    var candidate = anchor
    while (candidate != current) {
        candidate += direction
        if (candidate in occupiedSections) break
        edge = candidate
    }
    return CourseSelection(
        weekDay = weekDay,
        startSection = minOf(anchor, edge),
        endSection = maxOf(anchor, edge)
    )
}

private fun courseStartRowOffset(
    course: Course,
    periods: List<com.example.timetable.model.ClassPeriod>
): Float = course.customStartMinutes?.let { minuteToRowOffset(it, periods) }
    ?: (course.startSection - 1).toFloat()

private fun courseEndRowOffset(
    course: Course,
    periods: List<com.example.timetable.model.ClassPeriod>
): Float = course.customEndMinutes?.let { minuteToRowOffset(it, periods) }
    ?: course.endSection.toFloat()

private fun minuteToRowOffset(
    minutes: Int,
    periods: List<com.example.timetable.model.ClassPeriod>
): Float {
    periods.forEachIndexed { index, period ->
        if (minutes <= period.startMinutes) return index.toFloat()
        if (minutes <= period.endMinutes) {
            val progress = (minutes - period.startMinutes).toFloat() /
                (period.endMinutes - period.startMinutes)
            return index + progress
        }
    }
    return periods.size.toFloat()
}

private fun courseColor(course: Course): Color {
    val hue = Math.floorMod(course.name.hashCode(), 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.34f, lightness = 0.82f)
}
