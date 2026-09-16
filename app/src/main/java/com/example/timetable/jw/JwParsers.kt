package com.example.timetable.jw

import org.json.JSONObject

/**
 * 教务系统成绩 / 考试接口响应的解析器（对照《项目：教务接口抓包记录》字段表）。
 */
object JwParsers {
    fun parseGrades(json: String): List<BuctGrade> {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw IllegalArgumentException("无法解析教务系统返回的数据")
        val items = root.optJSONArray("items")
            ?: throw IllegalArgumentException("未识别到成绩数据（缺少 items 字段）")
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val courseName = item.optString("kcmc").trim()
            if (courseName.isEmpty()) return@mapNotNull null
            val gradePoint = item.optString("jd").trim().toDoubleOrNull()
            BuctGrade(
                courseName = courseName,
                score = item.optString("cj").trim().ifEmpty {
                    item.optString("bfzcj").trim()
                },
                percentScore = item.optString("bfzcj").trim().ifEmpty { null },
                gradePoint = gradePoint,
                credits = item.optString("xf").trim().toDoubleOrNull(),
                semesterName = item.optString("xnmmc").trim(),
                courseNature = item.optString("kcxzmc").trim(),
                courseCategory = item.optString("kclbmc").trim(),
                examMethod = item.optString("khfsmc").trim(),
                courseCode = item.optString("kch").trim(),
                teachingClass = item.optString("jxbmc").trim(),
                countedInGpa = item.optString("sfjf", "1").trim() == "1" &&
                    (gradePoint ?: 0.0) > 0
            )
        }
    }

    /** 从成绩响应中提取学生个人信息；该学期无成绩条目时返回 null。 */
    fun parseStudentInfo(json: String): BuctStudentInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val first = root.optJSONArray("items")?.optJSONObject(0) ?: return null
        val name = first.optString("xm").trim()
        if (name.isEmpty()) return null
        return BuctStudentInfo(
            name = name,
            studentId = first.optString("xh").trim(),
            major = first.optString("zymc").trim(),
            className = first.optString("bj").trim(),
            college = first.optString("jgmc").trim()
        )
    }

    fun parseExams(json: String): List<BuctExam> {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw IllegalArgumentException("无法解析教务系统返回的数据")
        val items = root.optJSONArray("items")
            ?: throw IllegalArgumentException("未识别到考试数据（缺少 items 字段）")
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val courseName = item.optString("kcmc").trim()
            if (courseName.isEmpty()) return@mapNotNull null
            BuctExam(
                courseName = courseName,
                examName = item.optString("ksmc").trim(),
                timeText = item.optString("kssj").trim(),
                location = item.optString("cdmc").trim(),
                seat = item.optString("zwh").trim(),
                invigilator = item.optString("jsxx").trim(),
                method = item.optString("ksfs").trim()
            )
        }.sortedBy { it.timeText }
    }

    /** 从学业情况页面 HTML 中提取官方平均学分绩点（GPA）。 */
    fun extractOfficialGpa(html: String): Double? {
        val anchor = html.indexOf("平均学分绩点")
        if (anchor < 0) return null
        val match = Regex("\\d+\\.\\d+").find(html.substring(anchor)) ?: return null
        return match.value.toDoubleOrNull()
    }
}