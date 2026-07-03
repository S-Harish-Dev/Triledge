package com.triledge.dailyjournal.data

import android.content.Context
import com.triledge.dailyjournal.data.db.TradeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object KiteConnectClient {

    data class KiteTrade(
        val tradeId: String,
        val orderId: String,
        val tradingSymbol: String,
        val transactionType: String, // BUY or SELL
        val product: String, // MIS, CNC, etc.
        val price: Double,
        val quantity: Int,
        val timeMs: Long
    )

    suspend fun fetchTrades(
        apiKey: String,
        accessToken: String
    ): List<KiteTrade> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.kite.trade/trades")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Kite-Version", "3")
            connection.setRequestProperty("Authorization", "token $apiKey:$accessToken")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                if (root.getString("status") == "success") {
                    val dataArray = root.getJSONArray("data")
                    val tradesList = mutableListOf<KiteTrade>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        val timeStr = obj.getString("fill_timestamp")
                        val timeMs = sdf.parse(timeStr)?.time ?: System.currentTimeMillis()
                        tradesList.add(
                            KiteTrade(
                                tradeId = obj.getString("trade_id"),
                                orderId = obj.getString("order_id"),
                                tradingSymbol = obj.getString("tradingsymbol"),
                                transactionType = obj.getString("transaction_type"),
                                product = obj.getString("product"),
                                price = obj.getDouble("average_price"),
                                quantity = obj.getInt("quantity"),
                                timeMs = timeMs
                            )
                        )
                    }
                    return@withContext tradesList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    /**
     * Simple FIFO matching algorithm to pair BUY and SELL fills of the same symbol into complete realized trades.
     */
    fun matchTrades(kiteTrades: List<KiteTrade>): List<TradeItem> {
        val result = mutableListOf<TradeItem>()
        // Group trades by Symbol
        val groups = kiteTrades.sortedBy { it.timeMs }.groupBy { it.tradingSymbol }

        for ((symbol, list) in groups) {
            val buys = list.filter { it.transactionType == "BUY" }.toMutableList()
            val sells = list.filter { it.transactionType == "SELL" }.toMutableList()

            while (buys.isNotEmpty() && sells.isNotEmpty()) {
                val buy = buys.first()
                val sell = sells.first()

                val matchedQty = minOf(buy.quantity, sell.quantity)
                
                result.add(
                    TradeItem(
                        symbol = symbol,
                        buyPrice = buy.price,
                        sellPrice = sell.price,
                        quantity = matchedQty,
                        productType = buy.product,
                        segment = if (buy.product == "CNC") "Equity Delivery" else "Equity Intraday",
                        entryTime = minOf(buy.timeMs, sell.timeMs),
                        exitTime = maxOf(buy.timeMs, sell.timeMs),
                        presetId = null // Use default charges
                    )
                )

                // Deduct matched quantity
                if (buy.quantity == matchedQty) {
                    buys.removeAt(0)
                } else {
                    buys[0] = buy.copy(quantity = buy.quantity - matchedQty)
                }

                if (sell.quantity == matchedQty) {
                    sells.removeAt(0)
                } else {
                    sells[0] = sell.copy(quantity = sell.quantity - matchedQty)
                }
            }
        }
        return result
    }
}
