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
interface SpendingDao {
    // Categories
    @Query("SELECT * FROM spending_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<SpendingCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: SpendingCategory): Long

    @Update
    suspend fun updateCategory(category: SpendingCategory)

    @Delete
    suspend fun deleteCategory(category: SpendingCategory)

    // Account Tags
    @Query("SELECT * FROM account_tags ORDER BY name ASC")
    fun getAllAccountTags(): Flow<List<AccountTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTag(tag: AccountTag): Long

    @Update
    suspend fun updateAccountTag(tag: AccountTag)

    @Delete
    suspend fun deleteAccountTag(tag: AccountTag)

    // Payment App Tags
    @Query("SELECT * FROM payment_app_tags ORDER BY name ASC")
    fun getAllPaymentAppTags(): Flow<List<PaymentAppTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentAppTag(tag: PaymentAppTag): Long

    @Update
    suspend fun updatePaymentAppTag(tag: PaymentAppTag)

    @Delete
    suspend fun deletePaymentAppTag(tag: PaymentAppTag)

    // Spending Entries
    @Transaction
    @Query("SELECT * FROM spending_entries ORDER BY timestamp DESC")
    fun getAllEntriesWithRelations(): Flow<List<SpendingEntryWithRelations>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: SpendingEntry): Long

    @Delete
    suspend fun deleteEntry(entry: SpendingEntry)
}
