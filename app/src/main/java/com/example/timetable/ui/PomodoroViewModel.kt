package com.example.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PomodoroPhase(val displayName: String) {
    FOCUS("专注"),
    BREAK("休息")
}

data class PomodoroUiState(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val goal: String = ""
) {
    val totalSeconds: Int
        get() = when (phase) {
            PomodoroPhase.FOCUS -> focusMinutes * 60
            PomodoroPhase.BREAK -> breakMinutes * 60
        }
}

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        "pomodoro_state",
        android.content.Context.MODE_PRIVATE
    )
    private var endAtMillis = preferences.getLong(KEY_END_AT, 0L)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<PomodoroUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                updateRunningTimer()
                delay(250)
            }
        }
    }

    fun toggleRunning() {
        val current = _state.value
        if (current.isRunning) {
            updateRunningTimer()
            endAtMillis = 0L
            _state.value = _state.value.copy(isRunning = false)
        } else {
            if (current.phase == PomodoroPhase.FOCUS && current.goal.isBlank()) return
            endAtMillis = System.currentTimeMillis() + current.remainingSeconds * 1_000L
            _state.value = current.copy(isRunning = true)
        }
        persistState()
    }

    fun reset() {
        endAtMillis = 0L
        _state.value = _state.value.copy(
            phase = PomodoroPhase.FOCUS,
            remainingSeconds = _state.value.focusMinutes * 60,
            isRunning = false
        )
        persistState()
    }

    fun updateDurations(focusMinutes: Int, breakMinutes: Int) {
        if (focusMinutes !in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES) return
        if (breakMinutes !in MIN_BREAK_MINUTES..MAX_BREAK_MINUTES) return
        endAtMillis = 0L
        _state.value = _state.value.copy(
            focusMinutes = focusMinutes,
            breakMinutes = breakMinutes,
            phase = PomodoroPhase.FOCUS,
            remainingSeconds = focusMinutes * 60,
            isRunning = false
        )
        persistState()
    }

    fun updateGoal(goal: String) {
        val normalized = goal.trim()
        if (normalized.isEmpty()) return
        _state.value = _state.value.copy(goal = normalized)
        persistState()
    }

    fun deleteGoal() {
        endAtMillis = 0L
        _state.value = _state.value.copy(
            goal = "",
            phase = PomodoroPhase.FOCUS,
            remainingSeconds = _state.value.focusMinutes * 60,
            isRunning = false
        )
        persistState()
    }

    private fun loadState(): PomodoroUiState {
        val focusMinutes = preferences.getInt(KEY_FOCUS_MINUTES, 25)
            .coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES)
        val breakMinutes = preferences.getInt(KEY_BREAK_MINUTES, 5)
            .coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES)
        var phase = runCatching {
            PomodoroPhase.valueOf(
                preferences.getString(KEY_PHASE, PomodoroPhase.FOCUS.name).orEmpty()
            )
        }.getOrDefault(PomodoroPhase.FOCUS)
        var totalSeconds = if (phase == PomodoroPhase.FOCUS) {
            focusMinutes * 60
        } else {
            breakMinutes * 60
        }
        val wasRunning = preferences.getBoolean(KEY_RUNNING, false)
        val savedRemaining = preferences.getInt(KEY_REMAINING_SECONDS, totalSeconds)
            .coerceIn(1, totalSeconds)
        val now = System.currentTimeMillis()
        if (wasRunning && endAtMillis > 0L) {
            while (endAtMillis <= now) {
                phase = if (phase == PomodoroPhase.FOCUS) {
                    PomodoroPhase.BREAK
                } else {
                    PomodoroPhase.FOCUS
                }
                totalSeconds = if (phase == PomodoroPhase.FOCUS) {
                    focusMinutes * 60
                } else {
                    breakMinutes * 60
                }
                endAtMillis += totalSeconds * 1_000L
            }
        }
        val remaining = if (wasRunning && endAtMillis > 0L) {
            secondsUntil(endAtMillis).coerceIn(1, totalSeconds)
        } else {
            savedRemaining
        }
        return PomodoroUiState(
            focusMinutes = focusMinutes,
            breakMinutes = breakMinutes,
            phase = phase,
            remainingSeconds = remaining,
            isRunning = wasRunning && endAtMillis > now,
            goal = preferences.getString(KEY_GOAL, "").orEmpty()
        )
    }

    private fun updateRunningTimer() {
        val current = _state.value
        if (!current.isRunning) return
        val now = System.currentTimeMillis()
        var nextPhase = current.phase
        var phaseChanged = false
        while (endAtMillis <= now) {
            nextPhase = if (nextPhase == PomodoroPhase.FOCUS) {
                PomodoroPhase.BREAK
            } else {
                PomodoroPhase.FOCUS
            }
            val nextDuration = if (nextPhase == PomodoroPhase.FOCUS) {
                current.focusMinutes * 60
            } else {
                current.breakMinutes * 60
            }
            endAtMillis += nextDuration * 1_000L
            phaseChanged = true
        }
        val remaining = secondsUntil(endAtMillis)
        if (remaining > 0) {
            if (remaining != current.remainingSeconds || phaseChanged) {
                _state.value = current.copy(
                    phase = nextPhase,
                    remainingSeconds = remaining
                )
                if (phaseChanged) persistState()
            }
            return
        }
    }

    private fun persistState() {
        val current = _state.value
        preferences.edit()
            .putInt(KEY_FOCUS_MINUTES, current.focusMinutes)
            .putInt(KEY_BREAK_MINUTES, current.breakMinutes)
            .putString(KEY_PHASE, current.phase.name)
            .putInt(KEY_REMAINING_SECONDS, current.remainingSeconds)
            .putBoolean(KEY_RUNNING, current.isRunning)
            .putLong(KEY_END_AT, endAtMillis)
            .putString(KEY_GOAL, current.goal)
            .apply()
    }

    private fun secondsUntil(endAt: Long): Int =
        ((endAt - System.currentTimeMillis() + 999L) / 1_000L).toInt()

    companion object {
        const val MIN_FOCUS_MINUTES = 1
        const val MAX_FOCUS_MINUTES = 180
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 60

        private const val KEY_FOCUS_MINUTES = "focus_minutes"
        private const val KEY_BREAK_MINUTES = "break_minutes"
        private const val KEY_PHASE = "phase"
        private const val KEY_REMAINING_SECONDS = "remaining_seconds"
        private const val KEY_RUNNING = "running"
        private const val KEY_END_AT = "end_at"
        private const val KEY_GOAL = "goal"
    }
}
