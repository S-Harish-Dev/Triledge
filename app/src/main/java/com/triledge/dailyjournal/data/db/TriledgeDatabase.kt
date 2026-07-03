package com.triledge.dailyjournal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpendingCategory::class,
        AccountTag::class,
        PaymentAppTag::class,
        SpendingEntry::class,
        TodoCategory::class,
        TodoItem::class,
        BrokerPreset::class,
        ProductType::class,
        TradeItem::class,
        ChatMessage::class,
        TokenUsageLog::class,
        UsageDate::class
    ],
    version = 7,
    exportSchema = false
)
abstract class TriledgeDatabase : RoomDatabase() {
    abstract fun spendingDao(): SpendingDao
    abstract fun todoDao(): TodoDao
    abstract fun tradeDao(): TradeDao
    abstract fun chatDao(): ChatMessageDao
    abstract fun tokenUsageDao(): TokenUsageDao
    abstract fun usageDateDao(): UsageDateDao

    companion object {
        @Volatile
        private var INSTANCE: TriledgeDatabase? = null

        fun getDatabase(context: Context): TriledgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TriledgeDatabase::class.java,
                    "triledge_database"
                )
                .fallbackToDestructiveMigration() // safe for local dev
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
