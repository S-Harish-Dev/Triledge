package com.triledge.dailyjournal.data

import android.content.Context
import androidx.work.*
import com.triledge.dailyjournal.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

class TodoRepository(private val database: TriledgeDatabase, private val context: Context) {
    private val dao = database.todoDao()
    private val workManager = WorkManager.getInstance(context)

    val allCategories: Flow<List<TodoCategory>> = dao.getAllCategories()
    val allItems: Flow<List<TodoItemWithCategory>> = dao.getAllItemsWithCategory()

    suspend fun insertCategory(category: TodoCategory) = dao.insertCategory(category)
    suspend fun updateCategory(category: TodoCategory) = dao.updateCategory(category)
    suspend fun deleteCategory(category: TodoCategory) = dao.deleteCategory(category)

    suspend fun insertItem(item: TodoItem): Long {
        val id = dao.insertItem(item)
        val inserted = item.copy(id = id)
        scheduleWorkForTask(inserted)
        return id
    }

    suspend fun updateItem(item: TodoItem) {
        dao.updateItem(item)
        scheduleWorkForTask(item)
    }

    suspend fun deleteItem(item: TodoItem) {
        dao.deleteItem(item)
        cancelWorkForTask(item.id)
    }

    private fun scheduleWorkForTask(item: TodoItem) {
        // Cancel existing work first
        cancelWorkForTask(item.id)

        // Only schedule if not completed and reminder/interval or due date is set
        if (item.isCompleted) return

        val inputData = workDataOf(
            "taskId" to item.id,
            "title" to item.title,
            "note" to (item.note ?: "Triledge task reminder")
        )

        if (item.recurringInterval != null) {
            val intervalMin = parseIntervalToMinutes(item.recurringInterval)
            val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
                intervalMin,
                TimeUnit.MINUTES
            )
            .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "task_reminder_${item.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else if (item.dueDate != null) {
            val delayMs = item.dueDate - System.currentTimeMillis()
            if (delayMs > 0) {
                val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .build()

                workManager.enqueueUniqueWork(
                    "task_reminder_${item.id}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }
    }

    private fun cancelWorkForTask(taskId: Long) {
        workManager.cancelUniqueWork("task_reminder_$taskId")
    }

    private fun parseIntervalToMinutes(interval: String): Long {
        val lower = interval.lowercase().trim()
        return when {
            lower == "daily" -> 24 * 60
            lower == "weekly" -> 7 * 24 * 60
            lower.startsWith("every ") && lower.endsWith(" hours") -> {
                val hoursStr = lower.substringAfter("every ").substringBefore(" hours").trim()
                val hours = hoursStr.toLongOrNull() ?: 24
                hours * 60
            }
            lower.startsWith("every ") && lower.endsWith(" mins") -> {
                val minsStr = lower.substringAfter("every ").substringBefore(" mins").trim()
                val mins = minsStr.toLongOrNull() ?: 15
                mins.coerceAtLeast(15) // WorkManager periodic minimum is 15 mins
            }
            else -> 24 * 60
        }
    }

    suspend fun ensureSeededData() = withContext(Dispatchers.IO) {
        val categories = allCategories.first()
        if (categories.isEmpty()) {
            val defaultCategories = listOf(
                TodoCategory(name = "Work", colorHex = "#3F51B5", iconName = "list"),
                TodoCategory(name = "Personal", colorHex = "#E91E63", iconName = "person"),
                TodoCategory(name = "Health", colorHex = "#4CAF50", iconName = "favorite"),
                TodoCategory(name = "Shopping", colorHex = "#FF9800", iconName = "shopping_cart"),
                TodoCategory(name = "Other", colorHex = "#9E9E9E", iconName = "star")
            )
            for (cat in defaultCategories) {
                dao.insertCategory(cat)
            }
        }
    }
}
