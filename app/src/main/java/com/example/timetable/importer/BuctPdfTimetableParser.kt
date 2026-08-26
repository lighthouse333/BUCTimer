package com.example.timetable.importer

import android.content.Context
import android.net.Uri
import com.example.timetable.model.Course
import com.example.timetable.model.MAX_SECTION
import com.example.timetable.model.parseActiveWeeks
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import kotlin.math.floor

data class ParsedTimetable(
    val title: String?,
    val semester: String?,
    val courses: List<Course>,
    val warnings: List<String>,
    val groups: List<ParsedTimetableGroup> = emptyList()
)

data class ParsedTimetableGroup(
    val name: String,
    val courses: List<Course>,
    val warnings: List<String> = emptyList()
)

class BuctPdfTimetableParser(private val context: Context) : TimetableFileParser {
    override fun parse(uri: Uri, totalWeeks: Int): ParsedTimetable {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "无法打开所选文件"
        }
        return input.use { parseDocument(it, totalWeeks) }
    }

    private fun parseDocument(input: InputStream, totalWeeks: Int): ParsedTimetable {
        PDDocument.load(input).use { document ->
            val collector = WeekdayTextCollector()
            collector.sortByPosition = true
            collector.getText(document)
            val completeText = collector.completeText()
            require("星期一" in completeText && "节次" in completeText) {
                "未识别到北京化工大学课表表头"
            }

            val warnings = mutableListOf<String>()
            val courses = collector.weekdayTexts().flatMapIndexed { dayIndex, text ->
                parseCourseColumn(
                    text = text,
                    weekDay = WEEK_DAYS[dayIndex],
                    totalWeeks = totalWeeks,
                    warnings = warnings
                )
            }.distinctBy {
                listOf(it.name, it.weekDay, it.startSection, it.endSection, it.activeWeeks)
            }

            require(courses.isNotEmpty()) {
                "没有识别到课程；该文件可能是扫描件或并非北化课表"
            }
            return ParsedTimetable(
                title = Regex("([^\\s]{1,20}课表)").find(completeText)?.groupValues?.get(1),
                semester = Regex("(\\d{4}-\\d{4}学年第\\d学期)")
                    .find(completeText)?.groupValues?.get(1),
                courses = courses.sortedWith(
                    compareBy<Course> { WEEK_DAYS.indexOf(it.weekDay) }
                        .thenBy { it.startSection }
                ),
                warnings = warnings.distinct()
            )
        }
    }

    private class WeekdayTextCollector : PDFTextStripper() {
        private val positionedCharacters = MutableList(WEEK_DAYS.size) {
            mutableListOf<PositionedCharacter>()
        }
        private val allCharacters = mutableListOf<PositionedCharacter>()
        private var pageNumber = 0
        private var pageWidth = 1f

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            super.startPage(page)
            pageNumber += 1
            pageWidth = displayedPageWidth(
                width = page.cropBox.width,
                height = page.cropBox.height,
                rotation = page.rotation
            )
        }

        override fun processTextPosition(text: TextPosition) {
            super.processTextPosition(text)
            val item = PositionedCharacter(
                page = pageNumber,
                y = text.yDirAdj,
                x = text.xDirAdj,
                text = text.unicode
            )
            allCharacters += item
            val normalizedX = text.xDirAdj / pageWidth
            val dayIndex = floor(
                (normalizedX - TABLE_START_RATIO) / DAY_WIDTH_RATIO
            ).toInt()
            if (dayIndex in WEEK_DAYS.indices) positionedCharacters[dayIndex] += item
        }

        fun weekdayTexts(): List<String> = positionedCharacters.map(::charactersToText)

        fun completeText(): String = charactersToText(allCharacters)

        private fun charactersToText(characters: List<PositionedCharacter>): String = characters
            .groupBy { it.page to (it.y * 2).toInt() }
            .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .values
            .joinToString("\n") { line ->
                line.sortedBy(PositionedCharacter::x).joinToString("") { it.text }
            }
    }

    private data class PositionedCharacter(
        val page: Int,
        val y: Float,
        val x: Float,
        val text: String
    )

    companion object {
        private val WEEK_DAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        private const val TABLE_START_RATIO = 0.118f
        private const val DAY_WIDTH_RATIO = 0.1235f

        internal fun displayedPageWidth(width: Float, height: Float, rotation: Int): Float =
            if (Math.floorMod(rotation, 180) == 90) height else width

        internal fun parseCourseColumn(
            text: String,
            weekDay: String,
            totalWeeks: Int,
            warnings: MutableList<String> = mutableListOf()
        ): List<Course> {
            val normalized = text
                .replace("\r", "")
                .replace("（", "(")
                .replace("）", ")")
            val markerPattern = Regex(
                "(?m)^([^/\\n]{1,50}?)[★○●◇]\\s*\\((\\d+)-(\\d+)节\\)"
            )
            val markers = markerPattern.findAll(normalized).toList()

            return markers.mapIndexedNotNull { index, match ->
                val blockEnd = markers.getOrNull(index + 1)?.range?.first ?: normalized.length
                val compactBlock = normalized.substring(match.range.first, blockEnd)
                    .replace(Regex("\\s+"), "")
                val weeksText = Regex("\\(\\d+-\\d+节\\)([^/]+)")
                    .find(compactBlock)
                    ?.groupValues
                    ?.get(1)
                if (weeksText == null) {
                    warnings += "$weekDay ${match.groupValues[1].trim()}：缺少周次信息"
                    return@mapIndexedNotNull null
                }
                val name = match.groupValues[1].trim()
                val startSection = match.groupValues[2].toInt()
                val endSection = match.groupValues[3].toInt()
                val activeWeeks = parseActiveWeeks(weeksText, totalWeeks)
                if (activeWeeks == null) {
                    warnings += "$weekDay $name：无法识别周次“$weeksText”"
                    return@mapIndexedNotNull null
                }
                if (endSection > MAX_SECTION) {
                    warnings += "$weekDay $name：节次超出当前支持范围"
                    return@mapIndexedNotNull null
                }
                Course(
                    name = name,
                    teacher = extractField(compactBlock, "教师") ?: "无",
                    classroom = extractField(compactBlock, "场地") ?: "无",
                    weekDay = weekDay,
                    startSection = startSection,
                    endSection = endSection,
                    activeWeeks = activeWeeks
                )
            }.toList()
        }

        private fun extractField(block: String, fieldName: String): String? = Regex(
            "(?:^|/)${Regex.escape(fieldName)}:([^/]*)"
        ).find(block)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
