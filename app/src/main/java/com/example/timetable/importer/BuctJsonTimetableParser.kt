package com.example.timetable.importer

import com.example.timetable.model.Course
import com.example.timetable.model.parseActiveWeeks
import org.json.JSONObject

/**
 * 北京化工大学教务系统「在线导出」课表 JSON 解析器。
 *
 * 解析教务系统 JSON API（xskbcx_cxXsKb）返回的结构化课表：
 * `{ "student": {…}, "courses": [ { "name", "day", "periods", "weeks", … } ] }`。
 */
object BuctJsonTimetableParser {
    private val WEEK_DAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val DAY_TO_WEEK_DAY = mapOf(
        "星期一" to "周一",
        "星期二" to "周二",
        "星期三" to "周三",
        "星期四" to "周四",
        "星期五" to "周五",
        "星期六" to "周六",
        "星期日" to "周日",
        "星期天" to "周日"
    )

    fun parseBuctJson(text: String, totalWeeks: Int): ParsedTimetable {
        val json = runCatching { JSONObject(text) }
            .getOrNull()
            ?: throw IllegalArgumentException("无法解析教务系统返回的数据")
        val coursesArray = json.optJSONArray("courses")
            ?: throw IllegalArgumentException("未识别到课表数据（缺少 courses 字段）")
        val student = json.optJSONObject("student")

        val warnings = mutableListOf<String>()
        val courses = (0 until coursesArray.length())
            .mapNotNull { index ->
                mapCourse(coursesArray.optJSONObject(index), totalWeeks, warnings)
            }
            .distinctBy {
                listOf(
                    it.name, it.teacher, it.classroom, it.weekDay,
                    it.startSection, it.endSection, it.activeWeeks
                )
            }
            .sortedWith(
                compareBy<Course> { WEEK_DAYS.indexOf(it.weekDay) }
                    .thenBy(Course::startSection)
                    .thenBy(Course::name)
            )

        if (courses.isEmpty()) {
            throw IllegalArgumentException("没有识别到课程；请确认数据来自北化教务系统")
        }

        val academicYear = student?.optString("academicYear").orEmpty()
        val semester = student?.optString("semester").orEmpty()
        return ParsedTimetable(
            title = student?.optString("name")?.trim()?.takeIf(String::isNotEmpty),
            semester = if (academicYear.isNotEmpty()) buildString {
                append(academicYear)
                if (semester.isNotEmpty()) append("学年第").append(semester).append("学期")
            } else null,
            courses = courses,
            warnings = warnings.distinct()
        )
    }

    private fun mapCourse(
        obj: JSONObject?,
        totalWeeks: Int,
        warnings: MutableList<String>
    ): Course? {
        if (obj == null) return null
        val name = obj.optString("name").trim()
        if (name.isEmpty()) return null
        val day = obj.optString("day").trim()
        val weekDay = dayToWeekDay(day)
        if (weekDay == null) {
            warnings += "$name：无法识别星期“$day”"
            return null
        }
        val periodsText = obj.optString("periods").trim()
        val sections = parsePeriods(periodsText)
        if (sections == null) {
            warnings += "$name：无法识别节次“$periodsText”"
            return null
        }
        val weeksText = obj.optString("weeks").trim()
        val activeWeeks = parseWeekText(weeksText, totalWeeks)
        if (activeWeeks == null) {
            warnings += "$name：无法识别周次“$weeksText”"
            return null
        }
        return Course(
            name = name,
            teacher = obj.optString("teacher").trim().ifEmpty { "无" },
            classroom = obj.optString("location").trim().ifEmpty { "无" },
            weekDay = weekDay,
            startSection = sections.first,
            endSection = sections.second,
            activeWeeks = activeWeeks
        )
    }

    /**
     * 解析周次文本，额外支持单双周标记 `(单)/(双)`。
     * 无标记的段直接委托给 [parseActiveWeeks]。
     */
    internal fun parseWeekText(text: String, totalWeeks: Int): Set<Int>? {
        val normalized = text
            .trim()
            .replace(" ", "")
            .replace('（', '(')
            .replace('）', ')')
            .replace('，', ',')
            .replace('、', ',')
        if (normalized.isEmpty()) return null

        val result = sortedSetOf<Int>()
        for (part in normalized.split(',')) {
            if (part.isEmpty()) return null
            val weekFilter: ((Int) -> Boolean)? = when {
                part.endsWith("(单)") -> { week -> week % 2 == 1 }
                part.endsWith("(双)") -> { week -> week % 2 == 0 }
                else -> null
            }
            val base = part.removeSuffix("(单)").removeSuffix("(双)")
            val weeks = parseActiveWeeks(base, totalWeeks) ?: return null
            result.addAll(if (weekFilter == null) weeks else weeks.filter(weekFilter))
        }
        return result
    }

    internal fun dayToWeekDay(day: String): String? = DAY_TO_WEEK_DAY[day]

    internal fun parsePeriods(text: String): Pair<Int, Int>? {
        val normalized = text
            .trim()
            .replace(" ", "")
            .replace('—', '-')
            .replace('–', '-')
            .replace('~', '-')
        if (normalized.isEmpty()) return null
        val bounds = normalized.split('-')
        val start = bounds.firstOrNull()?.toIntOrNull() ?: return null
        val end = when (bounds.size) {
            1 -> start
            2 -> bounds[1].toIntOrNull() ?: return null
            else -> return null
        }
        if (start !in 1..12 || end !in start..12) return null
        return start to end
    }
}