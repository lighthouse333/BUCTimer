package com.example.timetable.jw

/** 教务系统内学生个人信息（来自成绩接口条目中随附的字段）。 */
data class BuctStudentInfo(
    val name: String,
    val studentId: String,
    val major: String,
    val className: String,
    val college: String
)

/** 一门课程的成绩记录（教务接口原始字段语义）。 */
data class BuctGrade(
    val courseName: String,
    /** cj：可能是百分制数字（"84"）或等级制（"B+"、"合格"）。 */
    val score: String,
    /** bfzcj：百分制折算值（等级制成绩也有此字段）。 */
    val percentScore: String?,
    /** jd：该课绩点，不计绩点的课程为 0.0；缺失时为 null。 */
    val gradePoint: Double?,
    /** xf：学分。 */
    val credits: Double?,
    val semesterName: String,
    val courseNature: String,
    val courseCategory: String,
    val examMethod: String,
    val courseCode: String,
    val teachingClass: String,
    /** 是否计入绩点统计（sfjf == "1"）。 */
    val countedInGpa: Boolean
)

/** 一场考试安排。 */
data class BuctExam(
    val courseName: String,
    val examName: String,
    /** 考试时间完整文本，如 "2026-07-04(08:00-10:00)"。 */
    val timeText: String,
    val location: String,
    val seat: String,
    val invigilator: String,
    val method: String
)

/**
 * 绩点汇总：official 为教务官方值（学业页面提取，可能拿不到）；
 * computed 为本地按 jd×学分 加权计算值（含参考口径差异，仅作后备）。
 */
data class GpaSnapshot(
    val official: Double?,
    val computed: Double?,
    val computedCredits: Double
)

/** 本地加权平均绩点 = Σ(jd×学分) / Σ学分，只统计计入绩点且 jd>0 的课程。 */
fun computeWeightedGpa(grades: List<BuctGrade>): Pair<Double, Double>? {
    val counted = grades.filter {
        it.countedInGpa && (it.gradePoint ?: 0.0) > 0 && (it.credits ?: 0.0) > 0
    }
    if (counted.isEmpty()) return null
    val totalCredits = counted.sumOf { it.credits!! }
    val weighted = counted.sumOf { it.credits!! * it.gradePoint!! }
    return weighted / totalCredits to totalCredits
}