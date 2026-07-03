package com.triledge.dailyjournal.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Calendar

enum class QuoteSlot { MORNING, EVENING, NIGHT }

data class QuoteSettings(
    val morningStart: Int = 5,   // hour of day (0-23)
    val morningEnd: Int = 12,
    val eveningStart: Int = 12,
    val eveningEnd: Int = 18,
    val nightStart: Int = 18,
    val nightEnd: Int = 5
)

class QuotesRepository(private val context: Context) {

    private val quotesDir = File(context.filesDir, "quotes").also { it.mkdirs() }

    private val prefs = context.getSharedPreferences("quote_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<QuoteSettings> = _settings.asStateFlow()

    private fun loadSettings(): QuoteSettings {
        return QuoteSettings(
            morningStart = prefs.getInt("morning_start", 5),
            morningEnd = prefs.getInt("morning_end", 12),
            eveningStart = prefs.getInt("evening_start", 12),
            eveningEnd = prefs.getInt("evening_end", 18),
            nightStart = prefs.getInt("night_start", 18),
            nightEnd = prefs.getInt("night_end", 5)
        )
    }

    fun updateSettings(settings: QuoteSettings) {
        prefs.edit()
            .putInt("morning_start", settings.morningStart)
            .putInt("morning_end", settings.morningEnd)
            .putInt("evening_start", settings.eveningStart)
            .putInt("evening_end", settings.eveningEnd)
            .putInt("night_start", settings.nightStart)
            .putInt("night_end", settings.nightEnd)
            .apply()
        _settings.value = settings
    }

    fun importQuotes(slot: QuoteSlot, content: String) {
        val file = File(quotesDir, "${slot.name.lowercase()}.txt")
        file.writeText(content)
    }

    fun getQuotes(slot: QuoteSlot): List<String> {
        val file = File(quotesDir, "${slot.name.lowercase()}.txt")
        if (!file.exists()) return emptyList()
        val text = file.readText()
        // Split by blank line (two consecutive newlines) for multi-line quotes
        // If no blank lines found, fall back to one-per-line
        val byBlankLine = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        return if (byBlankLine.size > 1 || text.contains("\n\n")) {
            byBlankLine
        } else {
            text.lines().map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun exportQuotes(slot: QuoteSlot): String {
        val quotes = getQuotes(slot)
        return quotes.joinToString("\n")
    }

    fun getQuoteCount(slot: QuoteSlot): Int = getQuotes(slot).size

    fun currentSlot(): QuoteSlot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = _settings.value
        return when {
            isInRange(hour, s.morningStart, s.morningEnd) -> QuoteSlot.MORNING
            isInRange(hour, s.eveningStart, s.eveningEnd) -> QuoteSlot.EVENING
            else -> QuoteSlot.NIGHT
        }
    }

    fun getRandomQuote(): String? {
        val slot = currentSlot()
        val quotes = getQuotes(slot)
        return quotes.randomOrNull()
    }

    private fun isInRange(hour: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }
}
