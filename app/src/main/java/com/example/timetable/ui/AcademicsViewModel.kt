package com.example.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.jw.AcademicsCacheStore
import com.example.timetable.jw.BuctExam
import com.example.timetable.jw.BuctGrade
import com.example.timetable.jw.BuctStudentInfo
import com.example.timetable.jw.JwApiClient
import com.example.timetable.jw.JwParsers
import com.example.timetable.jw.JwSessionExpiredException
import com.example.timetable.jw.JwSessionStore
import com.example.timetable.jw.computeWeightedGpa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface AcademicsState {
    data object LoggedOut : AcademicsState

    data class Loading(val message: String) : AcademicsState

    data class Ready(
        val student: BuctStudentInfo?,
        val year: Int,
        val semester: Int,
        val grades: List<BuctGrade>,
        val exams: List<BuctExam>,
        val officialGpa: Double?,
        val computedGpa: Double?,
        val computedGpaCredits: Double,
        /** 数据获取时间；来自缓存时为缓存保存时间，正在联网查询后为查询完成时间。 */
        val loadedAt: Long?,
        /** 是否为上次查询保存的本地缓存（而非本次联网请求的结果）。 */
        val fromCache: Boolean
    ) : AcademicsState

    data class Failed(
        val message: String,
        val sessionExpired: Boolean
    ) : AcademicsState
}

/**
 * 学业页（成绩 / 考试 / 绩点）状态编排：复用课表在线导入保存的教务会话，
 * 拉取所选学期成绩与考试，并从学业情况页面提取官方 GPA。
 */
class AcademicsViewModel(application: Application) : AndroidViewModel(application) {
    private val jwSessionStore = JwSessionStore(application)
    private val jwCredentialStore = com.example.timetable.jw.JwCredentialStore(application)
    private val cacheStore = AcademicsCacheStore(application)
    private val _state = MutableStateFlow<AcademicsState>(AcademicsState.LoggedOut)
    val state: StateFlow<AcademicsState> = _state.asStateFlow()
    private val _selectedSemester = MutableStateFlow(defaultSemesterSelection())
    val selectedSemester: StateFlow<Pair<Int, Int>> = _selectedSemester.asStateFlow()

    init {
        // 启动即恢复上次关闭时的学年/学期选择及其缓存，无需再次请求教务系统
        val studentId = jwSessionStore.loadStudentId()
        if (studentId != null) {
            cacheStore.loadSelection(studentId)?.let { _selectedSemester.value = it }
            val (year, semester) = _selectedSemester.value
            cacheStore.loadEntry(studentId, year, semester)?.let { entry ->
                _state.value = readyFromCache(entry, year, semester)
            }
        }
    }

    fun savedStudentId(): String? = jwSessionStore.loadStudentId()

    fun savedJwStudentId(): String? = jwCredentialStore.loadStudentId()

    fun savedJwPassword(): String? = jwCredentialStore.loadPassword()

    fun clearSavedJwCredentials() = jwCredentialStore.clear()

    fun onLoginSuccess(cookies: String, studentId: String) {
        if (cookies.isBlank()) {
            _state.value = AcademicsState.Failed(
                message = "未获取到登录会话，请重新登录",
                sessionExpired = true
            )
            return
        }
        // 只保存会话与学号；账号密码凭据仅在设置页手动管理
        jwSessionStore.saveCookies(cookies)
        jwSessionStore.saveStudentId(studentId)
        // 恢复该账号上次关闭时的学年/学期选择，无记录时回到默认学期
        _selectedSemester.value =
            cacheStore.loadSelection(studentId) ?: defaultSemesterSelection()
        refresh()
    }

    fun onLoginFailed() {
        _state.value = AcademicsState.Failed(
            message = "账号或密码错误，请检查后重试",
            sessionExpired = true
        )
    }

    /** 用已保存的会话重新拉取当前所选学期数据；无会话时回到未登录态。 */
    fun refresh() {
        val cookies = jwSessionStore.loadCookies()
        val studentId = jwSessionStore.loadStudentId()
        if (cookies.isNullOrBlank() || studentId.isNullOrBlank()) {
            _state.value = AcademicsState.LoggedOut
            return
        }
        val (year, semester) = _selectedSemester.value
        android.util.Log.i("Academics", "refresh requested year=$year semester=$semester")
        viewModelScope.launch {
            _state.value = AcademicsState.Loading("正在查询成绩与考试安排……")
            _state.value = try {
                val data = withContext(Dispatchers.IO) {
                    val xqm = requireNotNull(JwApiClient.xqmForSemester(semester)) {
                        "无效的学期代码：$semester"
                    }
                    val gradesJson = JwApiClient.fetchGrades(cookies, studentId, year, xqm)
                    val student = JwParsers.parseStudentInfo(gradesJson)
                    if (student != null && student.studentId.isNotBlank() &&
                        student.studentId != studentId
                    ) {
                        throw IllegalStateException(
                            "登录账号（${student.studentId}）与所填学号（$studentId）不一致，请重新登录"
                        )
                    }
                    val examsJson = runCatching {
                        JwApiClient.fetchExams(cookies, studentId, year, xqm)
                    }.getOrNull()
                    val gpaHtml = runCatching {
                        JwApiClient.fetchGpaPage(cookies, studentId)
                    }.getOrNull()
                    GradeExamsResult(
                        student = student,
                        grades = JwParsers.parseGrades(gradesJson),
                        exams = examsJson?.let { json ->
                            runCatching { JwParsers.parseExams(json) }.getOrDefault(emptyList())
                        } ?: emptyList(),
                        officialGpa = gpaHtml?.let { JwParsers.extractOfficialGpa(it) }
                    )
                }
                val local = computeWeightedGpa(data.grades)
                val savedAt = System.currentTimeMillis()
                cacheStore.saveEntry(
                    studentId, year, semester,
                    AcademicsCacheStore.Entry(
                        student = data.student,
                        grades = data.grades,
                        exams = data.exams,
                        officialGpa = data.officialGpa,
                        savedAt = savedAt
                    )
                )
                cacheStore.saveSelection(studentId, year, semester)
                AcademicsState.Ready(
                    student = data.student,
                    year = year,
                    semester = semester,
                    grades = data.grades,
                    exams = data.exams,
                    officialGpa = data.officialGpa,
                    computedGpa = local?.first,
                    computedGpaCredits = local?.second ?: 0.0,
                    loadedAt = savedAt,
                    fromCache = false
                )
            } catch (error: JwSessionExpiredException) {
                AcademicsState.Failed(
                    message = error.message ?: "登录会话已过期",
                    sessionExpired = true
                )
            } catch (error: Exception) {
                AcademicsState.Failed(
                    message = error.message ?: "查询失败，请稍后重试",
                    sessionExpired = false
                )
            }
        }
    }

    /**
     * 「查询」所选学年/学期：已有该学期缓存时直接展示缓存（不联网），
     * 没有缓存才联网查询；成功后写入缓存并记住该选择。
     */
    fun selectSemester(year: Int, semester: Int) {
        _selectedSemester.value = year to semester
        val studentId = jwSessionStore.loadStudentId()
        if (studentId != null) cacheStore.saveSelection(studentId, year, semester)
        val cached = studentId?.let { cacheStore.loadEntry(it, year, semester) }
        if (cached != null) {
            _state.value = readyFromCache(cached, year, semester)
            return
        }
        refresh()
    }

    /** 「刷新」：先提交屏幕上的学年/学期选择，再强制联网重新拉取最新数据。 */
    fun refreshSelection(year: Int, semester: Int) {
        _selectedSemester.value = year to semester
        jwSessionStore.loadStudentId()?.let { cacheStore.saveSelection(it, year, semester) }
        refresh()
    }

    /**
     * 进入学业页时调用：已有可展示数据（含缓存恢复的）就直接复用，
     * 仅在没有任何数据时自动查询一次。需要重新拉取时由用户点「刷新」。
     */
    fun autoLoadIfNeeded() {
        val current = _state.value
        if (current is AcademicsState.Ready || current is AcademicsState.Loading) return
        refresh()
    }

    fun logout() {
        jwSessionStore.loadStudentId()?.let { cacheStore.clearFor(it) }
        jwSessionStore.clear()
        _selectedSemester.value = defaultSemesterSelection()
        _state.value = AcademicsState.LoggedOut
    }

    private fun readyFromCache(
        entry: AcademicsCacheStore.Entry,
        year: Int,
        semester: Int
    ): AcademicsState.Ready {
        val local = computeWeightedGpa(entry.grades)
        return AcademicsState.Ready(
            student = entry.student,
            year = year,
            semester = semester,
            grades = entry.grades,
            exams = entry.exams,
            officialGpa = entry.officialGpa,
            computedGpa = local?.first,
            computedGpaCredits = local?.second ?: 0.0,
            loadedAt = entry.savedAt,
            fromCache = true
        )
    }

    private data class GradeExamsResult(
        val student: BuctStudentInfo?,
        val grades: List<BuctGrade>,
        val exams: List<BuctExam>,
        val officialGpa: Double?
    )

    private companion object {
        /**
         * 默认查询的学年/学期：9-2 月处于秋冬学期（学年起始年 = 当前年，1-2 月减一），
         * 3-8 月处于春夏学期。
         */
        fun defaultSemesterSelection(): Pair<Int, Int> {
            val now = LocalDate.now()
            return if (now.monthValue <= 2 || now.monthValue >= 9) {
                (if (now.monthValue <= 2) now.year - 1 else now.year) to 1
            } else {
                (now.year - 1) to 2
            }
        }
    }
}