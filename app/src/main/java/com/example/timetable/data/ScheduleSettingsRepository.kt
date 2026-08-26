package com.example.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timetable.model.ClassPeriod
import com.example.timetable.model.MAX_SECTION
import com.example.timetable.model.ScheduleSettings
import com.example.timetable.model.createDefaultScheduleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

private val Context.scheduleSettingsDataStore by preferencesDataStore(
    name = "schedule_settings"
)

class ScheduleSettingsRepository(
    private val context: Context
) {
    private object Keys {
        val semesterStart = stringPreferencesKey("semester_start")
        val totalWeeks = intPreferencesKey("total_weeks")
        val sectionCount = intPreferencesKey("section_count")
        val classPeriods = stringPreferencesKey("class_periods")
        val selectedTimetableId = longPreferencesKey("selected_timetable_id")
    }

    val selectedTimetableId: Flow<Long> = context.scheduleSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { preferences -> preferences[Keys.selectedTimetableId] ?: 1L }

    fun settings(timetableId: Long): Flow<ScheduleSettings> = context.scheduleSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            val defaults = createDefaultScheduleSettings()
            val sectionCount = preferences[intPreferencesKey("section_count_$timetableId")]
                ?: preferences[Keys.sectionCount].takeIf { timetableId == 1L }
            val validatedSectionCount = sectionCount
                ?.coerceIn(1, MAX_SECTION)
                ?: defaults.sectionCount
            val encodedPeriods = preferences[stringPreferencesKey("class_periods_$timetableId")]
                ?: preferences[Keys.classPeriods].takeIf { timetableId == 1L }
            val periods = decodePeriods(encodedPeriods)
                .takeIf { it.size == validatedSectionCount }
                ?: defaults.classPeriods.takeIf { it.size == validatedSectionCount }
                ?: com.example.timetable.model.createDefaultPeriods(validatedSectionCount)

            ScheduleSettings(
                semesterStart = (
                    preferences[stringPreferencesKey("semester_start_$timetableId")]
                        ?: preferences[Keys.semesterStart].takeIf { timetableId == 1L }
                    )
                    ?.let { savedDate ->
                        runCatching { LocalDate.parse(savedDate) }.getOrNull()
                    }
                    ?: defaults.semesterStart,
                totalWeeks = (
                    preferences[intPreferencesKey("total_weeks_$timetableId")]
                        ?: preferences[Keys.totalWeeks].takeIf { timetableId == 1L }
                    )
                    ?.coerceIn(1, 30)
                    ?: defaults.totalWeeks,
                sectionCount = validatedSectionCount,
                classPeriods = periods
            )
        }

    suspend fun selectTimetable(timetableId: Long) {
        context.scheduleSettingsDataStore.edit { preferences ->
            preferences[Keys.selectedTimetableId] = timetableId
        }
    }

    suspend fun save(timetableId: Long, settings: ScheduleSettings) {
        context.scheduleSettingsDataStore.edit { preferences ->
            preferences[stringPreferencesKey("semester_start_$timetableId")] =
                settings.semesterStart.toString()
            preferences[intPreferencesKey("total_weeks_$timetableId")] = settings.totalWeeks
            preferences[intPreferencesKey("section_count_$timetableId")] = settings.sectionCount
            preferences[stringPreferencesKey("class_periods_$timetableId")] =
                encodePeriods(settings.classPeriods)
        }
    }

    suspend fun deleteSettings(timetableId: Long) {
        context.scheduleSettingsDataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("semester_start_$timetableId"))
            preferences.remove(intPreferencesKey("total_weeks_$timetableId"))
            preferences.remove(intPreferencesKey("section_count_$timetableId"))
            preferences.remove(stringPreferencesKey("class_periods_$timetableId"))
        }
    }

    private fun encodePeriods(periods: List<ClassPeriod>): String =
        periods.joinToString(";") { period ->
            "${period.number},${period.startMinutes},${period.endMinutes}"
        }

    private fun decodePeriods(value: String?): List<ClassPeriod> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";").mapNotNull { encodedPeriod ->
            val parts = encodedPeriod.split(",")
            if (parts.size != 3) return@mapNotNull null
            val number = parts[0].toIntOrNull() ?: return@mapNotNull null
            val start = parts[1].toIntOrNull() ?: return@mapNotNull null
            val end = parts[2].toIntOrNull() ?: return@mapNotNull null
            ClassPeriod(number, start, end)
        }.takeIf { periods ->
            periods.indices.all { index ->
                periods[index].number == index + 1 &&
                    periods[index].startMinutes < periods[index].endMinutes &&
                    (index == 0 || periods[index].startMinutes >= periods[index - 1].endMinutes)
            }
        }.orEmpty()
    }
}
