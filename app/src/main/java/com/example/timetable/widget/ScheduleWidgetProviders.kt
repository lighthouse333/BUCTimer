package com.example.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.CourseRepository
import com.example.timetable.data.ScheduleSettingsRepository
import com.example.timetable.model.Course
import com.example.timetable.model.ScheduleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CompactScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgetsAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in REFRESH_ACTIONS) updateWidgetsAsync(context)
    }

    private companion object {
        val REFRESH_ACTIONS = setOf(
            ScheduleWidgetController.ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}

class ExpandedScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgetsAsync(context)
    }
}

private fun AppWidgetProvider.updateWidgetsAsync(context: Context) {
    val pendingResult = goAsync()
    ScheduleWidgetController.updateAll(context, pendingResult::finish)
}

object ScheduleWidgetController {
    const val ACTION_REFRESH = "com.example.timetable.widget.REFRESH"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()
    private val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun updateAll(context: Context, onFinished: () -> Unit = {}) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                updateMutex.withLock {
                    val data = loadWidgetData(appContext)
                    val manager = AppWidgetManager.getInstance(appContext)
                    val compactIds = manager.getAppWidgetIds(
                        ComponentName(appContext, CompactScheduleWidgetProvider::class.java)
                    )
                    val expandedIds = manager.getAppWidgetIds(
                        ComponentName(appContext, ExpandedScheduleWidgetProvider::class.java)
                    )
                    val clickIntent = launchAppIntent(appContext)

                    compactIds.forEach { id ->
                        manager.updateAppWidget(
                            id,
                            RemoteViews(appContext.packageName, R.layout.widget_schedule_compact).apply {
                                setOnClickPendingIntent(R.id.widget_root, clickIntent)
                                bindCourse(
                                    CURRENT_IDS,
                                    data.current,
                                    if (data.next == null) "今天没有课啦" else "现在没有课程"
                                )
                            }
                        )
                    }
                    expandedIds.forEach { id ->
                        manager.updateAppWidget(
                            id,
                            RemoteViews(appContext.packageName, R.layout.widget_schedule_expanded).apply {
                                setOnClickPendingIntent(R.id.widget_root, clickIntent)
                                bindCourse(
                                    CURRENT_IDS,
                                    data.current,
                                    if (data.next == null) "今天没有课啦" else "现在没有课程"
                                )
                                setTextViewText(R.id.next_label, appContext.getString(R.string.widget_next_course))
                                bindCourse(
                                    NEXT_IDS,
                                    data.next,
                                    if (data.current == null) "今天没有课啦" else "今天没有下一节课了"
                                )
                            }
                        )
                    }
                    scheduleBoundaryRefresh(
                        appContext,
                        data.nextRefreshAt,
                        compactIds.isNotEmpty() || expandedIds.isNotEmpty()
                    )
                }
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun loadWidgetData(context: Context): WidgetData {
        val settingsRepository = ScheduleSettingsRepository(context)
        val timetableId = settingsRepository.selectedTimetableId.first()
        val settings = settingsRepository.settings(timetableId).first()
        val courses = CourseRepository(AppDatabase.getInstance(context).courseDao())
            .courses(timetableId)
            .first()
        // Database and DataStore reads can suspend across a course boundary. Capture the
        // time only after the data is ready so an old refresh cannot publish stale state.
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val minute = now.hour * 60 + now.minute

        val current = coursesForDate(today, courses, settings)
            .mapNotNull { it.toDisplay(today, settings) }
            .firstOrNull { minute >= it.startMinutes && minute < it.endMinutes }
        val next = findNextCourseToday(now, courses, settings)
        val nextRefresh = listOfNotNull(
            current?.let { today.atStartOfDay().plusMinutes(it.endMinutes.toLong()) },
            next?.date?.atStartOfDay()?.plusMinutes(next.startMinutes.toLong()),
            today.plusDays(1).atStartOfDay()
        ).filter { it.isAfter(now) }.minOrNull()

        return WidgetData(current, next, nextRefresh)
    }

    private fun findNextCourseToday(
        now: LocalDateTime,
        courses: List<Course>,
        settings: ScheduleSettings
    ): DisplayCourse? = coursesForDate(now.toLocalDate(), courses, settings)
        .mapNotNull { it.toDisplay(now.toLocalDate(), settings) }
        .filter { it.startMinutes > now.hour * 60 + now.minute }
        .minByOrNull(DisplayCourse::startMinutes)

    private fun coursesForDate(
        date: LocalDate,
        courses: List<Course>,
        settings: ScheduleSettings
    ): List<Course> {
        if (date.isBefore(settings.semesterStart)) return emptyList()
        val days = Duration.between(
            settings.semesterStart.atStartOfDay(),
            date.atStartOfDay()
        ).toDays()
        val week = (days / 7).toInt() + 1
        if (week !in 1..settings.totalWeeks) return emptyList()
        val weekDay = weekDays[date.dayOfWeek.value - 1]
        return courses.filter { it.weekDay == weekDay && week in it.activeWeeks }
    }

    private fun Course.toDisplay(date: LocalDate, settings: ScheduleSettings): DisplayCourse? {
        val start = customStartMinutes
            ?: settings.classPeriods.getOrNull(startSection - 1)?.startMinutes
            ?: return null
        val end = customEndMinutes
            ?: settings.classPeriods.getOrNull(endSection - 1)?.endMinutes
            ?: return null
        return DisplayCourse(date, name, teacher, classroom, start, end)
    }

    private fun RemoteViews.bindCourse(ids: CourseViewIds, course: DisplayCourse?, emptyText: String) {
        setTextViewText(ids.name, course?.name ?: emptyText)
        setTextViewText(ids.time, course?.let { "${formatTime(it.startMinutes)}–${formatTime(it.endMinutes)}" }.orEmpty())
        setTextViewText(ids.place, course?.classroom?.takeIf(String::isNotBlank)?.let { "教室  $it" }.orEmpty())
        setTextViewText(ids.teacher, course?.teacher?.takeIf(String::isNotBlank)?.let { "教师  $it" }.orEmpty())
        setViewVisibility(ids.time, if (course == null) View.GONE else View.VISIBLE)
        setViewVisibility(ids.place, if (course?.classroom.isNullOrBlank()) View.GONE else View.VISIBLE)
        setViewVisibility(ids.teacher, if (course?.teacher.isNullOrBlank()) View.GONE else View.VISIBLE)
    }

    private fun scheduleBoundaryRefresh(context: Context, time: LocalDateTime?, enabled: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = refreshPendingIntent(context)
        alarmManager.cancel(pending)
        if (!enabled || time == null) return
        val triggerAt = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
                return
            } catch (_: SecurityException) {
                // Permission can be revoked between the capability check and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CompactScheduleWidgetProvider::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun launchAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private val CURRENT_IDS = CourseViewIds(
        R.id.current_name, R.id.current_time, R.id.current_place, R.id.current_teacher
    )
    private val NEXT_IDS = CourseViewIds(
        R.id.next_name, R.id.next_time, R.id.next_place, R.id.next_teacher
    )
}

private data class CourseViewIds(val name: Int, val time: Int, val place: Int, val teacher: Int)

private data class DisplayCourse(
    val date: LocalDate,
    val name: String,
    val teacher: String,
    val classroom: String,
    val startMinutes: Int,
    val endMinutes: Int
)

private data class WidgetData(
    val current: DisplayCourse?,
    val next: DisplayCourse?,
    val nextRefreshAt: LocalDateTime?
)
