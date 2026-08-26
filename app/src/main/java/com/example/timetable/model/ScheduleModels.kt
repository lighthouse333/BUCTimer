package com.example.timetable.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class WeekType(val displayName: String) {
    EVERY_WEEK("每周"),
    ODD_WEEK("单周"),
    EVEN_WEEK("双周")
}

data class Course(
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val classroom: String,
    val weekDay: String,
    val startSection: Int,
    val endSection: Int,
    val activeWeeks: Set<Int>,
    val customStartMinutes: Int? = null,
    val customEndMinutes: Int? = null,
    val note: String = ""
) {
    val startWeek: Int
        get() = activeWeeks.min()

    val endWeek: Int
        get() = activeWeeks.max()

    init {
        require(startSection in 1..12) { "开始节次必须在 1 到 12 之间" }
        require(endSection in startSection..12) { "结束节次不能早于开始节次" }
        require(activeWeeks.isNotEmpty()) { "课程至少需要一个有效周次" }
        require(activeWeeks.all { it >= 1 }) { "有效周次必须大于 0" }
        require((customStartMinutes == null) == (customEndMinutes == null)) {
            "自定义开始和结束时间必须同时设置"
        }
        if (customStartMinutes != null && customEndMinutes != null) {
            require(customStartMinutes in 0..1439) { "自定义开始时间无效" }
            require(customEndMinutes in 1..1440) { "自定义结束时间无效" }
            require(customEndMinutes > customStartMinutes) { "结束时间必须晚于开始时间" }
        }
    }
}

fun Course.isActiveInWeek(week: Int): Boolean = week in activeWeeks

fun Course.effectiveStartMinutes(periods: List<ClassPeriod>): Int =
    customStartMinutes ?: periods[startSection - 1].startMinutes

fun Course.effectiveEndMinutes(periods: List<ClassPeriod>): Int =
    customEndMinutes ?: periods[endSection - 1].endMinutes

fun weekSchedulesOverlap(
    firstActiveWeeks: Set<Int>,
    secondCourse: Course
): Boolean = firstActiveWeeks.any(secondCourse.activeWeeks::contains)

fun createActiveWeeks(
    startWeek: Int,
    endWeek: Int,
    weekType: WeekType
): Set<Int> = (startWeek..endWeek).filterTo(sortedSetOf()) { week ->
    when (weekType) {
        WeekType.EVERY_WEEK -> true
        WeekType.ODD_WEEK -> week % 2 == 1
        WeekType.EVEN_WEEK -> week % 2 == 0
    }
}

fun parseActiveWeeks(text: String, totalWeeks: Int): Set<Int>? {
    val normalized = text
        .trim()
        .replace("第", "")
        .replace("周", "")
        .replace(" ", "")
        .replace('（', '(')
        .replace('）', ')')
        .replace('，', ',')
        .replace('、', ',')
        .replace('—', '-')
        .replace('–', '-')
        .replace('~', '-')
        .replace("至", "-")

    if (normalized.isEmpty()) return null

    val result = sortedSetOf<Int>()
    for (part in normalized.split(',')) {
        if (part.isEmpty()) return null
        val oddEven: ((Int) -> Boolean)? = when {
            part.endsWith("(单)") -> { w -> w % 2 == 1 }
            part.endsWith("(双)") -> { w -> w % 2 == 0 }
            else -> null
        }
        val base = part.removeSuffix("(单)").removeSuffix("(双)")
        val bounds = base.split('-')
        val start = bounds.firstOrNull()?.toIntOrNull() ?: return null
        val end = when (bounds.size) {
            1 -> start
            2 -> bounds[1].toIntOrNull() ?: return null
            else -> return null
        }
        if (start !in 1..totalWeeks || end !in start..totalWeeks) return null
        val weeks = (start..end).filter { oddEven?.invoke(it) ?: true }
        result.addAll(weeks)
    }
    return result
}

fun formatActiveWeeks(activeWeeks: Set<Int>): String {
    if (activeWeeks.isEmpty()) return ""
    val weeks = activeWeeks.sorted()
    val ranges = mutableListOf<String>()
    var rangeStart = weeks.first()
    var previous = rangeStart

    for (week in weeks.drop(1)) {
        if (week == previous + 1) {
            previous = week
            continue
        }
        ranges += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
        rangeStart = week
        previous = week
    }
    ranges += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
    return ranges.joinToString(",")
}

data class ClassPeriod(
    val number: Int,
    val startMinutes: Int,
    val endMinutes: Int
)

fun createDefaultPeriods(count: Int): List<ClassPeriod> = (1..count).map { number ->
    val startMinutes = 8 * 60 + (number - 1) * 55
    ClassPeriod(
        number = number,
        startMinutes = startMinutes,
        endMinutes = startMinutes + 45
    )
}

data class ScheduleSettings(
    val semesterStart: LocalDate,
    val totalWeeks: Int,
    val sectionCount: Int,
    val classPeriods: List<ClassPeriod>
)

fun findWeekContainingDate(
    date: LocalDate,
    semesterStart: LocalDate,
    totalWeeks: Int
): Int {
    require(totalWeeks > 0) { "totalWeeks must be positive" }

    if (date.isBefore(semesterStart)) return 1

    for (week in 1..totalWeeks) {
        val weekStart = semesterStart.plusWeeks((week - 1).toLong())
        if (date.isBefore(weekStart.plusWeeks(1))) return week
    }

    return totalWeeks
}

fun createDefaultScheduleSettings(): ScheduleSettings {
    val sectionCount = 6
    return ScheduleSettings(
        semesterStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        totalWeeks = 20,
        sectionCount = sectionCount,
        classPeriods = createDefaultPeriods(sectionCount)
    )
}
