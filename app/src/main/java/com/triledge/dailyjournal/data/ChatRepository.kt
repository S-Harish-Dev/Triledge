package com.triledge.dailyjournal.data

import com.triledge.dailyjournal.data.db.ChatMessage
import com.triledge.dailyjournal.data.db.TokenUsageLog
import com.triledge.dailyjournal.data.db.TriledgeDatabase
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val database: TriledgeDatabase) {

    private val chatDao = database.chatDao()

    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessagesFlow()

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        chatDao.clearHistory()
    }

    suspend fun insertTokenUsage(provider: String, model: String, input: Int, output: Int) {
        database.tokenUsageDao().insertLog(
            TokenUsageLog(
                timestamp = System.currentTimeMillis(),
                provider = provider,
                model = model,
                inputTokens = input,
                outputTokens = output
            )
        )
    }

    val allTokenLogs: Flow<List<TokenUsageLog>> = database.tokenUsageDao().getAllLogsFlow()

    suspend fun clearTokenLogs() {
        database.tokenUsageDao().clearLogs()
    }
}
