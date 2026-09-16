package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.example.timetable.ui.ClassScheduleApp
import com.example.timetable.ui.TimetableViewModel
import com.example.timetable.ui.TodoViewModel
import com.example.timetable.ui.PomodoroViewModel
import com.example.timetable.ui.AcademicsViewModel
import com.example.timetable.ui.theme.ClassScheduleTheme

class MainActivity : ComponentActivity() {
    private var foregroundEntry by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        foregroundEntry++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val timetableViewModel = ViewModelProvider(this)[TimetableViewModel::class.java]
        val todoViewModel = ViewModelProvider(this)[TodoViewModel::class.java]
        val pomodoroViewModel = ViewModelProvider(this)[PomodoroViewModel::class.java]
        val academicsViewModel = ViewModelProvider(this)[AcademicsViewModel::class.java]

        setContent {
            ClassScheduleTheme {
                ClassScheduleApp(
                    viewModel = timetableViewModel,
                    todoViewModel = todoViewModel,
                    pomodoroViewModel = pomodoroViewModel,
                    academicsViewModel = academicsViewModel,
                    foregroundEntry = foregroundEntry
                )
            }
        }
    }
}
