package com.example.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.TodoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).todoDao()

    val items: StateFlow<List<TodoEntity>> = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun add(title: String, parentId: Long? = null) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        viewModelScope.launch {
            dao.insert(TodoEntity(title = normalizedTitle, parentId = parentId))
        }
    }

    fun setCompleted(item: TodoEntity, completed: Boolean) {
        viewModelScope.launch { dao.setCompleted(item.id, completed) }
    }

    fun updateTitle(item: TodoEntity, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        viewModelScope.launch { dao.updateTitle(item.id, normalizedTitle) }
    }

    fun delete(item: TodoEntity) {
        viewModelScope.launch { dao.deleteById(item.id) }
    }
}
