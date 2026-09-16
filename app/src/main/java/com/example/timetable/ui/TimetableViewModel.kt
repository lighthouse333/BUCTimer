package com.example.timetable.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.CourseRepository
import com.example.timetable.data.ScheduleSettingsRepository
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.TimetableEntity
import com.example.timetable.importer.BuctPdfTimetableParser
import com.example.timetable.importer.BuctJsonTimetableParser
import com.example.timetable.importer.NenuPdfTimetableParser
import com.example.timetable.importer.ParsedTimetable
import com.example.timetable.importer.TimetableFileParser
import com.example.timetable.importer.TimetableImportSchool
import com.example.timetable.importer.ZjuXlsxTimetableParser
import com.example.timetable.jw.JwApiClient
import com.example.timetable.jw.JwSessionStore
import com.example.timetable.model.Course
import com.example.timetable.model.ScheduleSettings
import com.example.timetable.model.ClassPeriod
import com.example.timetable.model.TimePreset
import com.example.timetable.model.createDefaultScheduleSettings
import com.example.timetable.model.createPresetPeriods
import com.example.timetable.widget.ScheduleWidgetController
import com.example.timetable.update.AppUpdateInfo
import com.example.timetable.update.GitHubUpdateProvider
import com.example.timetable.update.UpdateCheckResult
import com.example.timetable.update.shouldShowUpdatePrompt as shouldDisplayUpdatePrompt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TimetableImportState {
    data object Idle : TimetableImportState
    data class Loading(val message: String) : TimetableImportState
    data object Saving : TimetableImportState
    data class Success(
        val timetable: ParsedTimetable,
        val timePreset: TimePreset? = null
    ) : TimetableImportState
    data class Completed(val importedCount: Int) : TimetableImportState
    data class Error(val message: String) : TimetableImportState
}

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpToDate(val checkedAt: Long) : AppUpdateUiState
    data class Available(val info: AppUpdateInfo) : AppUpdateUiState
    data class Downloading(val info: AppUpdateInfo, val progress: Int) : AppUpdateUiState
    data class Ready(val info: AppUpdateInfo, val apk: File, val message: String? = null) : AppUpdateUiState
    data class Error(val message: String) : AppUpdateUiState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = CourseRepository(
        database.courseDao()
    )
    private val timetableRepository = TimetableRepository(database.timetableDao())
    private val settingsRepository = ScheduleSettingsRepository(application)
    private val jwSessionStore = JwSessionStore(application)
    private val jwCredentialStore = com.example.timetable.jw.JwCredentialStore(application)
    private val updateProvider = GitHubUpdateProvider(application)
    private val updatePreferences = application.getSharedPreferences(
        "app_update_settings",
        android.content.Context.MODE_PRIVATE
    )
    private val uiPreferences = application.getSharedPreferences(
        "timetable_ui_settings",
        android.content.Context.MODE_PRIVATE
    )
    private val timetableParsers: Map<TimetableImportSchool, TimetableFileParser> = mapOf(
        TimetableImportSchool.BEIJING_UNIVERSITY_OF_CHEMICAL_TECHNOLOGY to
            BuctPdfTimetableParser(application),
        TimetableImportSchool.NORTHEAST_NORMAL_UNIVERSITY to
            NenuPdfTimetableParser(application),
        TimetableImportSchool.ZHEJIANG_UNIVERSITY to
            ZjuXlsxTimetableParser(application)
    )
    private val _timetableImportState = MutableStateFlow<TimetableImportState>(
        TimetableImportState.Idle
    )
    val timetableImportState: StateFlow<TimetableImportState> =
        _timetableImportState.asStateFlow()
    private val _updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()
    private val _automaticUpdateChecks = MutableStateFlow(
        updatePreferences.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true)
    )
    val automaticUpdateChecks: StateFlow<Boolean> = _automaticUpdateChecks.asStateFlow()
    private val _updatePopupReminders = MutableStateFlow(
        updatePreferences.getBoolean(KEY_UPDATE_POPUP_REMINDERS, true)
    )
    val updatePopupReminders: StateFlow<Boolean> = _updatePopupReminders.asStateFlow()
    private val _updatePrompt = MutableStateFlow<AppUpdateInfo?>(null)
    val updatePrompt: StateFlow<AppUpdateInfo?> = _updatePrompt.asStateFlow()
    private val _lastUpdateCheck = MutableStateFlow(
        updatePreferences.getLong(KEY_LAST_UPDATE_CHECK, 0L)
    )
    val lastUpdateCheck: StateFlow<Long> = _lastUpdateCheck.asStateFlow()
    private val _compactTimetableView = MutableStateFlow(
        uiPreferences.getBoolean(KEY_COMPACT_TIMETABLE_VIEW, false)
    )
    val compactTimetableView: StateFlow<Boolean> = _compactTimetableView.asStateFlow()

    val timetables = timetableRepository.timetables.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val selectedTimetableId = settingsRepository.selectedTimetableId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 1L
    )

    val currentTimetable: StateFlow<TimetableEntity?> = combine(
        timetables,
        selectedTimetableId
    ) { available, selectedId ->
        available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val courses = selectedTimetableId.flatMapLatest(repository::courses).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val settings = selectedTimetableId.flatMapLatest(settingsRepository::settings).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = createDefaultScheduleSettings()
    )

    init {
        viewModelScope.launch {
            timetableRepository.ensureDefaultTimetable()
        }
        if (_automaticUpdateChecks.value &&
            System.currentTimeMillis() - updatePreferences.getLong(KEY_LAST_UPDATE_CHECK, 0L) >=
            UPDATE_CHECK_INTERVAL_MILLIS
        ) {
            checkForAppUpdate(manual = false)
        }
    }

    fun addCourse(course: Course) {
        viewModelScope.launch {
            repository.add(selectedTimetableId.value, course)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun updateCourse(course: Course) {
        viewModelScope.launch {
            repository.update(selectedTimetableId.value, course)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            repository.delete(selectedTimetableId.value, course)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun saveSettings(settings: ScheduleSettings) {
        viewModelScope.launch {
            settingsRepository.save(selectedTimetableId.value, settings)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun selectTimetable(timetableId: Long) {
        viewModelScope.launch {
            settingsRepository.selectTimetable(timetableId)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun createTimetable(name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isEmpty()) return
        viewModelScope.launch {
            val id = timetableRepository.create(cleanedName)
            settingsRepository.selectTimetable(id)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun renameTimetable(timetableId: Long, name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isEmpty()) return
        viewModelScope.launch {
            timetableRepository.rename(timetableId, cleanedName)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun deleteTimetable(timetableId: Long) {
        val remainingTimetable = timetables.value.firstOrNull { it.id != timetableId } ?: return
        viewModelScope.launch {
            if (selectedTimetableId.value == timetableId) {
                settingsRepository.selectTimetable(remainingTimetable.id)
            }
            timetableRepository.delete(timetableId)
            settingsRepository.deleteSettings(timetableId)
            ScheduleWidgetController.updateAll(getApplication())
        }
    }

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        _automaticUpdateChecks.value = enabled
        updatePreferences.edit().putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, enabled).apply()
    }

    fun setCompactTimetableView(enabled: Boolean) {
        _compactTimetableView.value = enabled
        uiPreferences.edit().putBoolean(KEY_COMPACT_TIMETABLE_VIEW, enabled).apply()
    }

    fun setUpdatePopupReminders(enabled: Boolean) {
        _updatePopupReminders.value = enabled
        updatePreferences.edit().putBoolean(KEY_UPDATE_POPUP_REMINDERS, enabled).apply()
        if (!enabled) _updatePrompt.value = null
    }

    fun checkForAppUpdate() {
        checkForAppUpdate(manual = true)
    }

    private fun checkForAppUpdate(manual: Boolean) {
        if (_updateState.value is AppUpdateUiState.Checking ||
            _updateState.value is AppUpdateUiState.Downloading
        ) return
        viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Checking
            _updateState.value = try {
                when (val result = withContext(Dispatchers.IO) { updateProvider.checkForUpdate() }) {
                    is UpdateCheckResult.Available -> {
                        if (manual || shouldShowUpdatePrompt(result.info.versionCode)) {
                            _updatePrompt.value = result.info
                        }
                        AppUpdateUiState.Available(result.info)
                    }
                    is UpdateCheckResult.UpToDate -> AppUpdateUiState.UpToDate(result.checkedAt)
                }.also {
                    val checkedAt = System.currentTimeMillis()
                    _lastUpdateCheck.value = checkedAt
                    updatePreferences.edit()
                        .putLong(KEY_LAST_UPDATE_CHECK, checkedAt)
                        .apply()
                }
            } catch (error: Exception) {
                AppUpdateUiState.Error(error.message ?: "检查更新失败")
            }
        }
    }

    fun downloadAppUpdate(info: AppUpdateInfo) {
        _updatePrompt.value = null
        viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Downloading(info, 0)
            _updateState.value = try {
                val apk = withContext(Dispatchers.IO) {
                    updateProvider.downloadUpdate(info) { progress ->
                        _updateState.value = AppUpdateUiState.Downloading(info, progress)
                    }
                }
                AppUpdateUiState.Ready(info, apk)
            } catch (error: Exception) {
                AppUpdateUiState.Error(error.message ?: "下载更新失败")
            }
        }
    }

    fun dismissUpdatePrompt() {
        val info = _updatePrompt.value ?: return
        updatePreferences.edit()
            .putLong(KEY_DISMISSED_UPDATE_VERSION, info.versionCode)
            .putLong(KEY_DISMISSED_UPDATE_AT, System.currentTimeMillis())
            .apply()
        _updatePrompt.value = null
    }

    private fun shouldShowUpdatePrompt(versionCode: Long): Boolean {
        val dismissedVersion = updatePreferences.getLong(KEY_DISMISSED_UPDATE_VERSION, -1L)
        val dismissedAt = updatePreferences.getLong(KEY_DISMISSED_UPDATE_AT, 0L)
        return shouldDisplayUpdatePrompt(
            remindersEnabled = _updatePopupReminders.value,
            availableVersionCode = versionCode,
            dismissedVersionCode = dismissedVersion,
            dismissedAt = dismissedAt,
            now = System.currentTimeMillis(),
            snoozeMillis = UPDATE_PROMPT_SNOOZE_MILLIS
        )
    }

    fun installDownloadedUpdate() {
        val ready = _updateState.value as? AppUpdateUiState.Ready ?: return
        if (!updateProvider.requestInstall(ready.apk)) {
            _updateState.value = ready.copy(message = "请允许安装未知应用，返回后再次点击安装")
        }
    }

    fun parseTimetableFile(uri: Uri, school: TimetableImportSchool) {
        viewModelScope.launch {
            _timetableImportState.value = TimetableImportState.Loading("正在本地读取课表文件……")
            _timetableImportState.value = try {
                val parsed = withContext(Dispatchers.IO) {
                    requireNotNull(timetableParsers[school]) {
                        "暂不支持${school.displayName}的课表格式"
                    }.parse(uri, MAX_IMPORT_WEEKS)
                }
                TimetableImportState.Success(
                    timetable = parsed,
                    timePreset = when (school) {
                        TimetableImportSchool.BEIJING_UNIVERSITY_OF_CHEMICAL_TECHNOLOGY ->
                            TimePreset.BUCT
                        else -> null
                    }
                )
            } catch (error: Exception) {
                TimetableImportState.Error(error.message ?: "课表解析失败")
            }
        }
    }

    fun dismissImportResult() {
        _timetableImportState.value = TimetableImportState.Idle
    }

    fun hasSavedJwSession(): Boolean = jwSessionStore.loadCookies() != null

    fun savedJwSession(): String? = jwSessionStore.loadCookies()

    fun savedJwStudentId(): String? = jwCredentialStore.loadStudentId()

    fun savedJwPassword(): String? = jwCredentialStore.loadPassword()

    fun clearSavedJwCredentials() = jwCredentialStore.clear()

    fun saveJwCredentials(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) return
        jwCredentialStore.save(studentId.trim(), password)
    }

    fun importOnlineTimetable(
        cookies: String,
        studentId: String,
        year: Int,
        semester: Int
    ) {
        if (cookies.isBlank()) {
            _timetableImportState.value =
                TimetableImportState.Error("未获取到登录会话，请重新登录")
            return
        }
        // 登录成功即保存会话与学号（即使课表拉取失败也保留），
        // 便于学业页立即复用同一会话查询成绩与考试。
        // 注意：不在此处读写已保存的账号密码，凭据仅在设置中手动管理。
        jwSessionStore.saveCookies(cookies)
        jwSessionStore.saveStudentId(studentId)
        viewModelScope.launch {
            _timetableImportState.value = TimetableImportState.Loading("正在在线拉取课表……")
            _timetableImportState.value = try {
                val parsed = withContext(Dispatchers.IO) {
                    val xqm = requireNotNull(JwApiClient.xqmForSemester(semester)) {
                        "无效的学期代码：$semester"
                    }
                    val json = JwApiClient.fetchSchedule(cookies, studentId, year, xqm)
                    val sessionStudentId = BuctJsonTimetableParser.studentIdOf(json)
                    if (sessionStudentId != null && sessionStudentId != studentId) {
                        throw IllegalStateException(
                            "登录账号（$sessionStudentId）与所填学号（$studentId）不一致，请重新登录"
                        )
                    }
                    BuctJsonTimetableParser.parseBuctJson(json, MAX_IMPORT_WEEKS)
                }
                TimetableImportState.Success(parsed)
            } catch (error: Exception) {
                TimetableImportState.Error(error.message ?: "课表查询失败")
            }
        }
    }

    fun importCourses(importedCourses: List<Course>) {
        if (importedCourses.isEmpty()) return
        val importTimePreset =
            (_timetableImportState.value as? TimetableImportState.Success)?.timePreset
        viewModelScope.launch {
            _timetableImportState.value = TimetableImportState.Saving
            _timetableImportState.value = try {
                val current = settings.value
                val presetPeriods = importTimePreset?.let(::createPresetPeriods)
                val basePeriods = presetPeriods ?: current.classPeriods
                val requiredSectionCount = maxOf(
                    if (presetPeriods == null) current.sectionCount else presetPeriods.size,
                    importedCourses.maxOf(Course::endSection)
                )
                val requiredTotalWeeks = maxOf(
                    current.totalWeeks,
                    importedCourses.maxOf(Course::endWeek)
                )
                val expandedPeriods = basePeriods.toMutableList()
                while (expandedPeriods.size < requiredSectionCount) {
                    val previousEnd = expandedPeriods.lastOrNull()?.endMinutes ?: (8 * 60 - 10)
                    val number = expandedPeriods.size + 1
                    expandedPeriods += ClassPeriod(
                        number = number,
                        startMinutes = previousEnd + 10,
                        endMinutes = previousEnd + 55
                    )
                }
                if (
                    requiredSectionCount != current.sectionCount ||
                    expandedPeriods != current.classPeriods ||
                    requiredTotalWeeks != current.totalWeeks
                ) {
                    settingsRepository.save(
                        selectedTimetableId.value,
                        current.copy(
                            sectionCount = requiredSectionCount,
                            totalWeeks = requiredTotalWeeks,
                            classPeriods = expandedPeriods
                        )
                    )
                }
                repository.addAll(selectedTimetableId.value, importedCourses)
                ScheduleWidgetController.updateAll(getApplication())
                TimetableImportState.Completed(importedCourses.size)
            } catch (error: Exception) {
                TimetableImportState.Error(error.message ?: "课程导入失败")
            }
        }
    }

    companion object {
        private const val MAX_IMPORT_WEEKS = 30
        private const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_UPDATE_POPUP_REMINDERS = "update_popup_reminders"
        private const val KEY_DISMISSED_UPDATE_VERSION = "dismissed_update_version"
        private const val KEY_DISMISSED_UPDATE_AT = "dismissed_update_at"
        private const val KEY_COMPACT_TIMETABLE_VIEW = "compact_timetable_view"
        private const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
        private const val UPDATE_PROMPT_SNOOZE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
