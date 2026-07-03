package com.triledge.dailyjournal.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    // Categories
    @Query("SELECT * FROM todo_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<TodoCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: TodoCategory): Long

    @Update
    suspend fun updateCategory(category: TodoCategory)

    @Delete
    suspend fun deleteCategory(category: TodoCategory)

    // Items
    @Transaction
    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    fun getAllItemsWithCategory(): Flow<List<TodoItemWithCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TodoItem): Long

    @Update
    suspend fun updateItem(item: TodoItem)

    @Delete
    suspend fun deleteItem(item: TodoItem)
}
