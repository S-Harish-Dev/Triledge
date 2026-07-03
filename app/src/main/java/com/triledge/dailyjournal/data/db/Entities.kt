package com.triledge.dailyjournal.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Entity(tableName = "spending_categories")
data class SpendingCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconName: String
)

@Entity(tableName = "account_tags")
data class AccountTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "payment_app_tags")
data class PaymentAppTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "spending_entries",
    foreignKeys = [
        ForeignKey(
            entity = SpendingCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountTag::class,
            parentColumns = ["id"],
            childColumns = ["accountTagId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PaymentAppTag::class,
            parentColumns = ["id"],
            childColumns = ["paymentAppTagId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),
        Index("accountTagId"),
        Index("paymentAppTagId")
    ]
)
data class SpendingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val categoryId: Long?,
    val note: String?,
    val timestamp: Long,
    val accountTagId: Long?,
    val paymentAppTagId: Long?
)

data class SpendingEntryWithRelations(
    @Embedded val entry: SpendingEntry,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: SpendingCategory?,

    @Relation(
        parentColumn = "accountTagId",
        entityColumn = "id"
    )
    val accountTag: AccountTag?,

    @Relation(
        parentColumn = "paymentAppTagId",
        entityColumn = "id"
    )
    val paymentApp: PaymentAppTag?
)

@Entity(tableName = "todo_categories")
data class TodoCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconName: String
)

@Entity(
    tableName = "todo_items",
    foreignKeys = [
        ForeignKey(
            entity = TodoCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId")
    ]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String?,
    val dueDate: Long?,
    val isCompleted: Boolean,
    val categoryId: Long?,
    val createdAt: Long,
    val isPriority: Boolean = false,
    val recurringInterval: String? = null,
    val requiresReview: Boolean = false,
    val linkUrl: String? = null,
    val linkThumbnailPath: String? = null,
    val stickerPath: String? = null
)

data class TodoItemWithCategory(
    @Embedded val item: TodoItem,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: TodoCategory?
)

@Entity(tableName = "broker_presets")
data class BrokerPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brokerageFlat: Double,
    val brokeragePercent: Double,
    val sttPercent: Double,
    val exchangeTxPercent: Double,
    val sebiTurnoverPercent: Double,
    val stampDutyPercent: Double,
    val gstPercent: Double,
    val dpCharge: Double
)

@Entity(tableName = "product_types")
data class ProductType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "trade_items",
    foreignKeys = [
        ForeignKey(
            entity = BrokerPreset::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("presetId")
    ]
)
data class TradeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val quantity: Int,
    val productType: String,
    val segment: String,
    val entryTime: Long,
    val exitTime: Long,
    val presetId: Long?
)

data class TradeItemWithPreset(
    @Embedded val trade: TradeItem,
    @Relation(parentColumn = "presetId", entityColumn = "id")
    val preset: BrokerPreset?
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): kotlinx.coroutines.flow.Flow<List<ChatMessage>>

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Entity(tableName = "token_usage_logs")
data class TokenUsageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int
)

@Dao
interface TokenUsageDao {
    @Query("SELECT * FROM token_usage_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): kotlinx.coroutines.flow.Flow<List<TokenUsageLog>>

    @Insert
    suspend fun insertLog(log: TokenUsageLog): Long

    @Query("DELETE FROM token_usage_logs")
    suspend fun clearLogs()
}

// ── Usage Date tracking (Consistency/Streak tracker) ──
@Entity(tableName = "usage_dates")
data class UsageDate(
    @PrimaryKey val dateString: String  // "yyyy-MM-dd"
)

@Dao
interface UsageDateDao {
    @Query("SELECT * FROM usage_dates ORDER BY dateString DESC")
    fun getAllDatesFlow(): kotlinx.coroutines.flow.Flow<List<UsageDate>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertDate(usageDate: UsageDate)

    @Query("SELECT COUNT(*) FROM usage_dates")
    suspend fun totalDaysUsed(): Int
}
