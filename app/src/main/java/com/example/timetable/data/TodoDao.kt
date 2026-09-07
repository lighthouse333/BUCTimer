package com.example.timetable.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY createdAt, id")
    fun observeAll(): Flow<List<TodoEntity>>

    @Insert
    suspend fun insert(item: TodoEntity): Long

    @Query("UPDATE todo_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean)

    @Query("UPDATE todo_items SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
