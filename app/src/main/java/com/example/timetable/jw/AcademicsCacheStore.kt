package com.example.timetable.jw

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 学业查询结果的本地缓存：按「学号 + 学年起始年 + 学期」保存最近一次查询
 * 得到的成绩、考试与官方绩点，并持久化最近使用的学年/学期选择。下次打开
 * 学业页（含 App 重启）时直接展示缓存，避免重复请求教务系统；需要最新
 * 数据时由用户点「刷新」强制联网重新拉取。
 */
class AcademicsCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("academics_cache", Context.MODE_PRIVATE)

    /** 一次查询的完整结果快照。 */
    data class Entry(
        val student: BuctStudentInfo?,
        val grades: List<BuctGrade>,
        val exams: List<BuctExam>,
        val officialGpa: Double?,
        val savedAt: Long
    )

    fun saveSelection(studentId: String, year: Int, semester: Int) {
        prefs.edit().putString(selectionKey(studentId), "$year,$semester").apply()
    }

    /** 该学号上次使用的（学年起始年, 学期）；从未查询过时为 null。 */
    fun loadSelection(studentId: String): Pair<Int, Int>? {
        val parts = prefs.getString(selectionKey(studentId), null)
            ?.split(',') ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val semester = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return year to semester
    }

    fun saveEntry(studentId: String, year: Int, semester: Int, entry: Entry) {
        prefs.edit().putString(entryKey(studentId, year, semester), encode(entry)).apply()
    }

    fun loadEntry(studentId: String, year: Int, semester: Int): Entry? =
        prefs.getString(entryKey(studentId, year, semester), null)
            ?.let { raw -> runCatching { decode(raw) }.getOrNull() }

    /** 退出登录时清除该学号持久化的选择与全部学期缓存。 */
    fun clearFor(studentId: String) {
        val editor = prefs.edit().remove(selectionKey(studentId))
        val prefix = entryPrefix(studentId)
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun selectionKey(studentId: String) = "sel_$studentId"
    private fun entryPrefix(studentId: String) = "entry_${studentId}_"
    private fun entryKey(studentId: String, year: Int, semester: Int) =
        entryPrefix(studentId) + year + "_" + semester

    private fun encode(entry: Entry): String = JSONObject().apply {
        entry.student?.let { student ->
            put("student", JSONObject().apply {
                put("name", student.name)
                put("studentId", student.studentId)
                put("major", student.major)
                put("className", student.className)
                put("college", student.college)
            })
        }
        put("grades", JSONArray().apply {
            entry.grades.forEach { grade ->
                put(JSONObject().apply {
                    put("courseName", grade.courseName)
                    put("score", grade.score)
                    grade.percentScore?.let { put("percentScore", it) }
                    grade.gradePoint?.let { put("gradePoint", it) }
                    grade.credits?.let { put("credits", it) }
                    put("semesterName", grade.semesterName)
                    put("courseNature", grade.courseNature)
                    put("courseCategory", grade.courseCategory)
                    put("examMethod", grade.examMethod)
                    put("courseCode", grade.courseCode)
                    put("teachingClass", grade.teachingClass)
                    put("countedInGpa", grade.countedInGpa)
                })
            }
        })
        put("exams", JSONArray().apply {
            entry.exams.forEach { exam ->
                put(JSONObject().apply {
                    put("courseName", exam.courseName)
                    put("examName", exam.examName)
                    put("timeText", exam.timeText)
                    put("location", exam.location)
                    put("seat", exam.seat)
                    put("invigilator", exam.invigilator)
                    put("method", exam.method)
                })
            }
        })
        entry.officialGpa?.let { put("gpa", it) }
        put("savedAt", entry.savedAt)
    }.toString()

    private fun decode(raw: String): Entry {
        val root = JSONObject(raw)
        val student = root.optJSONObject("student")?.let {
            BuctStudentInfo(
                name = it.optString("name"),
                studentId = it.optString("studentId"),
                major = it.optString("major"),
                className = it.optString("className"),
                college = it.optString("college")
            )
        }
        val grades = root.optJSONArray("grades")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                BuctGrade(
                    courseName = item.optString("courseName"),
                    score = item.optString("score"),
                    percentScore = item.optString("percentScore", "").ifEmpty { null },
                    gradePoint = item.optDouble("gradePoint", Double.NaN)
                        .takeUnless { it.isNaN() },
                    credits = item.optDouble("credits", Double.NaN)
                        .takeUnless { it.isNaN() },
                    semesterName = item.optString("semesterName"),
                    courseNature = item.optString("courseNature"),
                    courseCategory = item.optString("courseCategory"),
                    examMethod = item.optString("examMethod"),
                    courseCode = item.optString("courseCode"),
                    teachingClass = item.optString("teachingClass"),
                    countedInGpa = item.optBoolean("countedInGpa")
                )
            }
        } ?: emptyList()
        val exams = root.optJSONArray("exams")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                BuctExam(
                    courseName = item.optString("courseName"),
                    examName = item.optString("examName"),
                    timeText = item.optString("timeText"),
                    location = item.optString("location"),
                    seat = item.optString("seat"),
                    invigilator = item.optString("invigilator"),
                    method = item.optString("method")
                )
            }
        } ?: emptyList()
        val gpa = root.optDouble("gpa", Double.NaN)
        return Entry(
            student = student,
            grades = grades,
            exams = exams,
            officialGpa = gpa.takeUnless { it.isNaN() },
            savedAt = root.optLong("savedAt", System.currentTimeMillis())
        )
    }
}