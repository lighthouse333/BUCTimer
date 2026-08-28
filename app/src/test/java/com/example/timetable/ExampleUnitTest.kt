package com.example.timetable

import com.example.timetable.model.Course
import com.example.timetable.model.ClassPeriod
import com.example.timetable.model.TimePreset
import com.example.timetable.model.WeekType
import com.example.timetable.model.createActiveWeeks
import com.example.timetable.model.createPresetPeriods
import com.example.timetable.model.findWeekContainingDate
import com.example.timetable.model.formatActiveWeeks
import com.example.timetable.model.parseActiveWeeks
import com.example.timetable.model.weekSchedulesOverlap
import com.example.timetable.importer.BuctPdfTimetableParser
import com.example.timetable.importer.BuctJsonTimetableParser
import com.example.timetable.importer.NenuPdfTimetableParser
import com.example.timetable.importer.ZjuXlsxTimetableParser
import com.example.timetable.jw.JwApiClient
import com.example.timetable.data.toEntity
import com.example.timetable.data.toDomain
import com.example.timetable.update.parseReleaseVersionCode
import com.example.timetable.update.shouldShowUpdatePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {
    @Test
    fun createsCurrentBuctTwelvePeriodPreset() {
        assertEquals(
            listOf(
                ClassPeriod(1, 480, 525),
                ClassPeriod(2, 530, 575),
                ClassPeriod(3, 585, 630),
                ClassPeriod(4, 640, 685),
                ClassPeriod(5, 690, 735),
                ClassPeriod(6, 810, 855),
                ClassPeriod(7, 860, 905),
                ClassPeriod(8, 915, 960),
                ClassPeriod(9, 965, 1010),
                ClassPeriod(10, 1080, 1125),
                ClassPeriod(11, 1130, 1175),
                ClassPeriod(12, 1180, 1225)
            ),
            createPresetPeriods(TimePreset.BUCT)
        )
    }

    @Test
    fun parsesReleaseVersionCodeUsingSupportedSeparators() {
        assertEquals(5L, parseReleaseVersionCode("版本：1.2.0（versionCode 5）"))
        assertEquals(6L, parseReleaseVersionCode("versionCode：6"))
        assertEquals(7L, parseReleaseVersionCode("versionCode: 7"))
        assertEquals(8L, parseReleaseVersionCode("versionCode = 8"))
        assertNull(parseReleaseVersionCode("版本：1.2.0"))
    }

    @Test
    fun controlsAutomaticUpdatePromptReminders() {
        val day = 24 * 60 * 60 * 1000L
        val now = 10 * day

        assertFalse(shouldShowUpdatePrompt(false, 7, -1, 0, now, day))
        assertFalse(shouldShowUpdatePrompt(true, 7, 7, now - day / 2, now, day))
        assertTrue(shouldShowUpdatePrompt(true, 7, 7, now - day, now, day))
        assertTrue(shouldShowUpdatePrompt(true, 8, 7, now, now, day))
    }

    @Test
    fun findsWeekUsingDisplayedDateRangesAndClampsOutsideSemester() {
        val semesterStart = LocalDate.of(2026, 9, 7)

        assertEquals(
            1,
            findWeekContainingDate(LocalDate.of(2026, 8, 13), semesterStart, 20)
        )
        assertEquals(
            3,
            findWeekContainingDate(LocalDate.of(2026, 9, 21), semesterStart, 20)
        )
        assertEquals(
            20,
            findWeekContainingDate(LocalDate.of(2027, 2, 1), semesterStart, 20)
        )
    }

    @Test
    fun parsesMultipleWeekRanges() {
        assertEquals(
            ((1..9) + (11..18)).toSet(),
            parseActiveWeeks("1-9周,11-18周", 20)
        )
    }

    @Test
    fun acceptsChineseSeparatorsAndFormatsRanges() {
        val weeks = parseActiveWeeks("1至3、5，7-8", 20)

        assertEquals(setOf(1, 2, 3, 5, 7, 8), weeks)
        assertEquals("1-3,5,7-8", formatActiveWeeks(requireNotNull(weeks)))
    }

    @Test
    fun parsesSingleWeekFormats() {
        assertEquals(setOf(1), parseActiveWeeks("1周", 20))
        assertEquals(setOf(5), parseActiveWeeks("5周", 20))
        assertEquals(setOf(1), parseActiveWeeks("第1周", 20))
        assertEquals(setOf(5), parseActiveWeeks("第5周", 20))
    }

    @Test
    fun parsesWeeksWithOddEvenMarkers() {
        assertEquals(
            setOf(1, 2, 4, 5, 7, 8, 11, 13, 14, 16, 17),
            parseActiveWeeks("1-2周,4-5周,7-8周,11-13周(单),14-16周(双),17周", 20)
        )
    }

    @Test
    fun rejectsInvalidOrOutOfSemesterWeeks() {
        assertNull(parseActiveWeeks("", 20))
        assertNull(parseActiveWeeks("5-3", 20))
        assertNull(parseActiveWeeks("1-21", 20))
    }

    @Test
    fun convertsLegacyOddWeekSchedule() {
        assertEquals(
            setOf(1, 3, 5, 7),
            createActiveWeeks(1, 8, WeekType.ODD_WEEK)
        )
    }

    @Test
    fun detectsOverlapUsingExactWeeks() {
        val existing = Course(
            name = "实验课",
            teacher = "教师",
            classroom = "实验室",
            weekDay = "周一",
            startSection = 1,
            endSection = 2,
            activeWeeks = setOf(1, 2, 4)
        )

        assertTrue(weekSchedulesOverlap(setOf(2, 3), existing))
        assertFalse(weekSchedulesOverlap(setOf(3, 5), existing))
    }

    @Test
    fun associatesStoredCourseWithSelectedTimetable() {
        val course = Course(
            name = "测试课程",
            teacher = "教师",
            classroom = "教室",
            weekDay = "周一",
            startSection = 1,
            endSection = 2,
            activeWeeks = setOf(1, 2)
        )

        assertEquals(42L, course.toEntity(timetableId = 42L).timetableId)
    }

    @Test
    fun preservesCustomCourseTimesInStorage() {
        val course = Course(
            name = "自定义时间课程",
            teacher = "教师",
            classroom = "教室",
            weekDay = "周二",
            startSection = 2,
            endSection = 4,
            activeWeeks = setOf(1, 2),
            customStartMinutes = 9 * 60 + 20,
            customEndMinutes = 10 * 60 + 50
        )

        val entity = course.toEntity(timetableId = 1L)
        assertEquals(560, entity.customStartMinutes)
        assertEquals(650, entity.customEndMinutes)
    }

    @Test
    fun preservesCourseNoteInStorage() {
        val course = Course(
            name = "测试课程",
            teacher = "教师",
            classroom = "教室",
            weekDay = "周三",
            startSection = 1,
            endSection = 2,
            activeWeeks = setOf(1, 2),
            note = "下周记得带实验报告"
        )

        val restored = course.toEntity(timetableId = 1L).toDomain()

        assertEquals("下周记得带实验报告", restored.note)
    }

    @Test
    fun parsesBuctCourseCellText() {
        val courses = BuctPdfTimetableParser.parseCourseColumn(
            text = """
                概率论与数理统计A★
                (1-2节)1-9周,11-16周/校区
                :北区/场地:一教B阶-303/教
                师:李志强/教学班:概率论与数理统计A-0002
            """.trimIndent(),
            weekDay = "周五",
            totalWeeks = 20
        )

        assertEquals(1, courses.size)
        assertEquals("概率论与数理统计A", courses.single().name)
        assertEquals("周五", courses.single().weekDay)
        assertEquals(1, courses.single().startSection)
        assertEquals(2, courses.single().endSection)
        assertEquals("一教B阶-303", courses.single().classroom)
        assertEquals("李志强", courses.single().teacher)
        assertEquals(((1..9) + (11..16)).toSet(), courses.single().activeWeeks)
    }

    @Test
    fun parsesBuctCourseCellWithOddEvenWeeks() {
        val courses = BuctPdfTimetableParser.parseCourseColumn(
            text = """
                大学英语3★
                (3-4节)1-2周,4-5周,7-8周,11-
                13周(单),14-16周(双),17周
                /校区:北区/场地:二教D-
                308/教师:王晓召/教学班:大
                学英语3-0016
            """.trimIndent(),
            weekDay = "周三",
            totalWeeks = 20
        )

        assertEquals(1, courses.size)
        assertEquals("大学英语3", courses.single().name)
        assertEquals(
            setOf(1, 2, 4, 5, 7, 8, 11, 13, 14, 16, 17),
            courses.single().activeWeeks
        )
    }

    @Test
    fun parsesBuctStudentCourseWithoutTeacherField() {
        val courses = BuctPdfTimetableParser.parseCourseColumn(
            text = """
                电子技术实验（Ⅰ）○
                (1-4节)11-18周/校区:北区
                /场地:未排地点/教学班:电子技术实验（Ⅰ）-0008
                /教学班组成:电科2501;电科2502/课程学时组成:实验:32
            """.trimIndent(),
            weekDay = "周四",
            totalWeeks = 20
        )

        assertEquals(1, courses.size)
        assertEquals("电子技术实验(Ⅰ)", courses.single().name)
        assertEquals("未排地点", courses.single().classroom)
        assertEquals("无", courses.single().teacher)
        assertEquals((11..18).toSet(), courses.single().activeWeeks)
    }

    @Test
    fun parsesBuctFieldsIndependentlyOfTheirOrder() {
        val courses = BuctPdfTimetableParser.parseCourseColumn(
            text = """
                字段乱序课程★
                (3-5节)1-4周/教学班:字段乱序课程-0001
                /教师:张老师/校区:北区/场地:一教101
            """.trimIndent(),
            weekDay = "周二",
            totalWeeks = 20
        )

        assertEquals(1, courses.size)
        assertEquals("张老师", courses.single().teacher)
        assertEquals("一教101", courses.single().classroom)
        assertEquals(3, courses.single().startSection)
        assertEquals(5, courses.single().endSection)
    }

    @Test
    fun fillsMissingBuctOptionalFieldsWithNone() {
        val courses = BuctPdfTimetableParser.parseCourseColumn(
            text = "缺少可选字段课程★\n(6-7节)2-3周/教学班:课程-0001",
            weekDay = "周三",
            totalWeeks = 20
        )

        assertEquals(1, courses.size)
        assertEquals("无", courses.single().teacher)
        assertEquals("无", courses.single().classroom)
    }

    @Test
    fun usesDisplayedWidthForRotatedBuctPdfPages() {
        assertEquals(
            842f,
            BuctPdfTimetableParser.displayedPageWidth(595f, 842f, 90),
            0f
        )
        assertEquals(
            842f,
            BuctPdfTimetableParser.displayedPageWidth(842f, 595f, 0),
            0f
        )
    }

    @Test
    fun parsesNenuOddEvenAndExplicitWeekCourses() {
        val courses = NenuPdfTimetableParser.parseCell(
            nameLines = listOf(
                NenuPdfTimetableParser.TextLine(100f, "*微观经济学概论"),
                NenuPdfTimetableParser.TextLine(110f, "**微观经济学概论"),
                NenuPdfTimetableParser.TextLine(120f, "劳动教育（1-2周）")
            ),
            teacherLines = listOf(
                NenuPdfTimetableParser.TextLine(100f, "Mario Gonzales"),
                NenuPdfTimetableParser.TextLine(110f, "朱鸿")
            ),
            roomLines = listOf(NenuPdfTimetableParser.TextLine(105f, "传媒东130")),
            weekDay = "周一",
            startSection = 1,
            endSection = 2
        )

        assertEquals(3, courses.size)
        assertEquals((1..16 step 2).toSet(), courses[0].activeWeeks)
        assertEquals((2..16 step 2).toSet(), courses[1].activeWeeks)
        assertEquals(setOf(1, 2), courses[2].activeWeeks)
        assertEquals("劳动教育", courses[2].name)
        assertEquals("传媒东130", courses[0].classroom)
    }

    @Test
    fun parsesZjuRowsAndSplitsMultipleClassTimes() {
        val timetable = ZjuXlsxTimetableParser.parseRows(
            rows = listOf(
                listOf("2026-2027学年秋冬学期测试课表"),
                listOf("课程代码", "课程名称", "教师姓名", "学期", "上课时间", "上课地点"),
                listOf(
                    "LAW2006M",
                    "刑法分论",
                    "梁健",
                    "秋冬",
                    "周一第7,8节;周一第9,10节",
                    "紫金港西1-304;紫金港西1-305"
                )
            ),
            totalWeeks = 30
        )

        assertEquals(2, timetable.courses.size)
        assertEquals("2026-2027学年秋冬学期测试课表", timetable.title)
        assertEquals("周一", timetable.courses[0].weekDay)
        assertEquals(7, timetable.courses[0].startSection)
        assertEquals(8, timetable.courses[0].endSection)
        assertEquals("紫金港西1-304", timetable.courses[0].classroom)
        assertEquals(9, timetable.courses[1].startSection)
        assertEquals("紫金港西1-305", timetable.courses[1].classroom)
        assertEquals((1..16).toSet(), timetable.courses[0].activeWeeks)
    }

    @Test
    fun deduplicatesRepeatedZjuCourseArrangements() {
        val rows = listOf(
            listOf("课程名称", "教师姓名", "学期", "上课时间", "上课地点"),
            listOf("行政法", "金承东", "秋冬", "周二第1,2节", "紫金港西1-317"),
            listOf(
                "行政法",
                "金承东",
                "秋冬",
                "周二第1,2节;周二第3,4节",
                "紫金港西1-317;紫金港西1-303"
            )
        )

        val courses = ZjuXlsxTimetableParser.parseRows(rows, 30).courses

        assertEquals(2, courses.size)
        assertEquals(listOf(1, 3), courses.map(Course::startSection))
    }

    @Test
    fun mapsZjuQuarterTermsToSemesterWeeks() {
        assertEquals((1..8).toSet(), ZjuXlsxTimetableParser.activeWeeksForTerm("秋", 30))
        assertEquals((9..16).toSet(), ZjuXlsxTimetableParser.activeWeeksForTerm("冬", 30))
        assertEquals((1..16).toSet(), ZjuXlsxTimetableParser.activeWeeksForTerm("春夏", 30))
    }

    @Test
    fun parsesBuctJsonWeeksWithOddEvenMarkers() {
        assertEquals(
            setOf(11, 13, 14, 16, 17),
            BuctJsonTimetableParser.parseWeekText("11-13周(单),14-16周(双),17周", 20)
        )
        assertEquals(
            ((1..9) + (11..17)).toSet(),
            BuctJsonTimetableParser.parseWeekText("1-9周,11-17周", 20)
        )
        assertEquals(
            setOf(1, 2, 4, 5, 7, 8, 11, 13, 14, 16, 17),
            BuctJsonTimetableParser.parseWeekText(
                "1-2周,4-5周,7-8周,11-13周(单),14-16周(双),17周",
                20
            )
        )
    }

    @Test
    fun mapsBuctJsonDayNamesToShortForm() {
        assertEquals("周一", BuctJsonTimetableParser.dayToWeekDay("星期一"))
        assertEquals("周日", BuctJsonTimetableParser.dayToWeekDay("星期天"))
        assertEquals("周日", BuctJsonTimetableParser.dayToWeekDay("星期日"))
        assertNull(BuctJsonTimetableParser.dayToWeekDay("Monday"))
    }

    @Test
    fun parsesBuctJsonScheduleIntoCourses() {
        val json = """
            {
              "student": { "name": "测试学生", "academicYear": "2026-2027", "semester": "1" },
              "courses": [
                {
                  "name": "数字电子技术",
                  "day": "星期二",
                  "periods": "3-5",
                  "weeks": "1-9周,11-17周",
                  "location": "一教B阶-103",
                  "teacher": "杜彬"
                },
                {
                  "name": "大学物理实验(II)",
                  "day": "星期一",
                  "periods": "7-9",
                  "weeks": "3-9周,11-16周",
                  "teacher": "李童"
                }
              ]
            }
        """.trimIndent()

        val timetable = BuctJsonTimetableParser.parseBuctJson(json, 30)

        assertEquals("测试学生", timetable.title)
        assertEquals("2026-2027学年第1学期", timetable.semester)
        assertEquals(2, timetable.courses.size)

        val first = timetable.courses.first { it.name == "数字电子技术" }
        assertEquals("周二", first.weekDay)
        assertEquals(3, first.startSection)
        assertEquals(5, first.endSection)
        assertEquals("一教B阶-103", first.classroom)
        assertEquals("杜彬", first.teacher)
        assertEquals(((1..9) + (11..17)).toSet(), first.activeWeeks)

        val second = timetable.courses.first { it.name == "大学物理实验(II)" }
        assertEquals("周一", second.weekDay)
        assertEquals("无", second.classroom)
        assertEquals(((3..9) + (11..16)).toSet(), second.activeWeeks)
    }

    @Test
    fun mapsBuctSemesterCodesToJwXqm() {
        assertEquals("3", JwApiClient.xqmForSemester(1))
        assertEquals("12", JwApiClient.xqmForSemester(2))
        assertEquals("16", JwApiClient.xqmForSemester(3))
        assertNull(JwApiClient.xqmForSemester(4))
    }

    @Test
    fun buildsBuctScheduleApiUrl() {
        assertEquals(
            "https://jwglxt.buct.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html" +
                "?gnmkdm=N2145&layout=default&su=2025050094&xnm=2026&xqm=3",
            JwApiClient.scheduleUrl("2025050094", 2026, "3")
        )
    }

}
