package com.triledge.dailyjournal.data

import com.triledge.dailyjournal.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SpendingRepository(private val database: TriledgeDatabase) {
    private val dao = database.spendingDao()

    val allCategories: Flow<List<SpendingCategory>> = dao.getAllCategories()
    val allAccountTags: Flow<List<AccountTag>> = dao.getAllAccountTags()
    val allPaymentAppTags: Flow<List<PaymentAppTag>> = dao.getAllPaymentAppTags()
    val allEntries: Flow<List<SpendingEntryWithRelations>> = dao.getAllEntriesWithRelations()

    suspend fun insertCategory(category: SpendingCategory) = dao.insertCategory(category)
    suspend fun updateCategory(category: SpendingCategory) = dao.updateCategory(category)
    suspend fun deleteCategory(category: SpendingCategory) = dao.deleteCategory(category)

    suspend fun insertAccountTag(tag: AccountTag) = dao.insertAccountTag(tag)
    suspend fun updateAccountTag(tag: AccountTag) = dao.updateAccountTag(tag)
    suspend fun deleteAccountTag(tag: AccountTag) = dao.deleteAccountTag(tag)

    suspend fun insertPaymentAppTag(tag: PaymentAppTag) = dao.insertPaymentAppTag(tag)
    suspend fun updatePaymentAppTag(tag: PaymentAppTag) = dao.updatePaymentAppTag(tag)
    suspend fun deletePaymentAppTag(tag: PaymentAppTag) = dao.deletePaymentAppTag(tag)

    suspend fun insertEntry(entry: SpendingEntry) = dao.insertEntry(entry)
    suspend fun deleteEntry(entry: SpendingEntry) = dao.deleteEntry(entry)

    suspend fun ensureSeededData() = withContext(Dispatchers.IO) {
        val categories = allCategories.first()
        if (categories.isEmpty()) {
            val defaultCategories = listOf(
                SpendingCategory(name = "Food", colorHex = "#FF5722", iconName = "restaurant"),
                SpendingCategory(name = "Shopping", colorHex = "#2196F3", iconName = "shopping_cart"),
                SpendingCategory(name = "Utilities", colorHex = "#4CAF50", iconName = "home"),
                SpendingCategory(name = "Travel", colorHex = "#00BCD4", iconName = "directions_car"),
                SpendingCategory(name = "Entertainment", colorHex = "#9C27B0", iconName = "movie"),
                SpendingCategory(name = "Other", colorHex = "#9E9E9E", iconName = "star")
            )
            for (cat in defaultCategories) {
                dao.insertCategory(cat)
            }
        }

        val accounts = allAccountTags.first()
        if (accounts.isEmpty()) {
            val defaultAccounts = listOf(
                AccountTag(name = "Cash"),
                AccountTag(name = "Bank Account")
            )
            for (acc in defaultAccounts) {
                dao.insertAccountTag(acc)
            }
        }

        val paymentApps = allPaymentAppTags.first()
        if (paymentApps.isEmpty()) {
            val defaultPaymentApps = listOf(
                PaymentAppTag(name = "Cash"),
                PaymentAppTag(name = "GPay"),
                PaymentAppTag(name = "PhonePe"),
                PaymentAppTag(name = "Paytm"),
                PaymentAppTag(name = "Card")
            )
            for (app in defaultPaymentApps) {
                dao.insertPaymentAppTag(app)
            }
        }
    }
}
