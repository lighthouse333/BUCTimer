package com.example.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.jw.BuctExam
import com.example.timetable.jw.BuctGrade
import com.example.timetable.jw.BuctStudentInfo
import java.text.DecimalFormat

@Composable
fun AcademicsScreen(viewModel: AcademicsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val selection by viewModel.selectedSemester.collectAsState()
    var showLoginInfo by remember { mutableStateOf(false) }
    var showWebLogin by remember { mutableStateOf(false) }
    var loginStudentId by remember { mutableStateOf(viewModel.savedStudentId().orEmpty()) }
    var loginPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.autoLoadIfNeeded()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is AcademicsState.LoggedOut -> LoggedOutPanel(
                modifier = Modifier.fillMaxSize(),
                onLogin = { showLoginInfo = true }
            )
            is AcademicsState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AcademicsState.Failed -> FailedPanel(
                message = current.message,
                sessionExpired = current.sessionExpired,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::refresh,
                onRelogin = { showLoginInfo = true }
            )
            is AcademicsState.Ready -> AcademicsContent(
                current = current,
                modifier = Modifier.fillMaxSize(),
                onRefresh = viewModel::refresh,
                onLogout = viewModel::logout,
                onSelectSemester = viewModel::selectSemester
            )
        }
    }

    if (showLoginInfo) {
        AcademicLoginDialog(
            initialStudentId = loginStudentId,
            onDismiss = { showLoginInfo = false },
            onConfirm = { studentId, password ->
                loginStudentId = studentId
                loginPassword = password
                showLoginInfo = false
                showWebLogin = true
            }
        )
    }

    if (showWebLogin) {
        JwLoginOverlay(
            studentId = loginStudentId,
            password = loginPassword,
            onCancel = {
                showWebLogin = false
                loginPassword = ""
            },
            onLoginSuccess = { cookies ->
                showWebLogin = false
                loginPassword = ""
                viewModel.onLoginSuccess(cookies, loginStudentId)
            },
            onLoginFailed = {
                showWebLogin = false
                loginPassword = ""
                viewModel.onLoginFailed()
            }
        )
    }
}

@Composable
private fun AcademicLoginDialog(
    initialStudentId: String,
    onDismiss: () -> Unit,
    onConfirm: (studentId: String, password: String) -> Unit
) {
    var studentId by remember { mutableStateOf(initialStudentId) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录北化教务系统") },
        text = {
            Column {
                Text("输入学号与统一认证密码后自动登录，查询成绩、绩点与考试安排。密码仅用于本次登录，App 不读取也不保存。")
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = { Text("学号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("统一认证密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = studentId.isNotBlank() && password.isNotBlank(),
                onClick = { onConfirm(studentId.trim(), password) }
            ) { Text("登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LoggedOutPanel(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("教务·学业查询", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "登录北化教务系统后，可查询你的成绩、绩点与考试安排。数据仅在本地展示，不会上传。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) { Text("登录北化教务系统") }
    }
}

@Composable
private fun FailedPanel(
    message: String,
    sessionExpired: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onRelogin: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (sessionExpired) "登录会话已过期" else "查询失败",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        if (sessionExpired) {
            Button(onClick = onRelogin) { Text("重新登录") }
        } else {
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun AcademicsContent(
    current: AcademicsState.Ready,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSelectSemester: (year: Int, semester: Int) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var yearText by remember(current.year) {
        mutableStateOf(current.year.toString())
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StudentCard(
                student = current.student,
                onRefresh = onRefresh,
                onLogout = onLogout
            )
        }
        item {
            GpaCard(
                officialGpa = current.officialGpa,
                computedGpa = current.computedGpa
            )
        }
        item {
            SemesterSelector(
                yearText = yearText,
                semester = current.semester,
                onYearChange = { yearText = it },
                onYearConfirm = { year ->
                    yearText = year
                    year.toIntOrNull()?.let { onSelectSemester(it, current.semester) }
                },
                onSemesterChange = { semester ->
                    yearText.toIntOrNull()?.let { onSelectSemester(it, semester) }
                }
            )
        }
        item {
            PrimaryTabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("成绩") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("考试") }
                )
            }
        }
        when (activeTab) {
            0 -> {
                if (current.grades.isEmpty()) {
                    item { EmptyHint("该学期暂无成绩记录") }
                } else {
                    items(current.grades, key = { it.courseCode + it.score }) { grade ->
                        GradeRow(grade)
                    }
                }
            }
            else -> {
                if (current.exams.isEmpty()) {
                    item { EmptyHint("该学期暂无考试安排") }
                } else {
                    items(current.exams, key = { it.courseName + it.timeText }) { exam ->
                        ExamCard(exam)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: BuctStudentInfo?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                student?.name ?: "北化学生",
                style = MaterialTheme.typography.titleLarge
            )
            val detail = buildString {
                if (!student?.major.isNullOrBlank()) append(student.major)
                if (!student?.className.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(student.className)
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val studentId = student?.studentId.orEmpty()
            if (studentId.isNotBlank()) {
                Text(
                    "学号 $studentId",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRefresh) { Text("刷新") }
                TextButton(onClick = onLogout) { Text("退出登录") }
            }
        }
    }
}

@Composable
private fun GpaCard(officialGpa: Double?, computedGpa: Double?) {
    val gpaValue = officialGpa ?: computedGpa
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "平均学分绩点（GPA）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    gpaValue?.let { String.format("%.2f", it) } ?: "暂无数据",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (officialGpa != null) "教务官方" else "本地计算",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (officialGpa == null || (computedGpa != null &&
                        kotlin.math.abs(officialGpa - computedGpa) >= 0.005)
                ) {
                    Text(
                        "仅供参考",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterSelector(
    yearText: String,
    semester: Int,
    onYearChange: (String) -> Unit,
    onYearConfirm: (String) -> Unit,
    onSemesterChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = yearText,
            onValueChange = onYearChange,
            label = { Text("学年起始年") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onYearConfirm(yearText) }),
            modifier = Modifier.width(160.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(1 to "秋冬", 2 to "春夏", 3 to "暑假").forEach { (code, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSemesterChange(code) }
                ) {
                    RadioButton(
                        selected = semester == code,
                        onClick = { onSemesterChange(code) }
                    )
                    Text(label)
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

private val GRADE_POINT_FORMAT = DecimalFormat("0.##")

@Composable
private fun GradeRow(grade: BuctGrade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    grade.courseName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val info = listOf(
                    grade.courseNature,
                    grade.courseCategory,
                    grade.examMethod
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (info.isNotEmpty()) {
                    Text(
                        info,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    grade.score,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = buildString {
                    grade.credits?.let { append("${GRADE_POINT_FORMAT.format(it)} 学分") }
                    val jd = grade.gradePoint
                    if (jd != null && jd > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("绩点 ${GRADE_POINT_FORMAT.format(jd)}")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!grade.countedInGpa) {
                    Text(
                        "不计绩点",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamCard(exam: BuctExam) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exam.courseName,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exam.examName.isNotBlank()) {
                    Text(
                        exam.examName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (exam.timeText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    exam.timeText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val detail = buildString {
                if (exam.location.isNotBlank()) append(exam.location)
                if (exam.seat.isNotBlank()) append(" · 座位 ${exam.seat}")
                if (exam.invigilator.isNotBlank()) append(" · 监考 ${exam.invigilator}")
                if (exam.method.isNotBlank()) append(" · ${exam.method}")
            }
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}