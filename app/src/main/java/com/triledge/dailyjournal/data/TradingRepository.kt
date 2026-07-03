package com.triledge.dailyjournal.data

import com.triledge.dailyjournal.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class TradingRepository(private val database: TriledgeDatabase) {
    private val dao = database.tradeDao()

    val allPresets: Flow<List<BrokerPreset>> = dao.getAllPresets()
    val allProductTypes: Flow<List<ProductType>> = dao.getAllProductTypes()
    val allTrades: Flow<List<TradeItemWithPreset>> = dao.getAllTradesWithPreset()

    suspend fun insertPreset(preset: BrokerPreset) = dao.insertPreset(preset)
    suspend fun updatePreset(preset: BrokerPreset) = dao.updatePreset(preset)
    suspend fun deletePreset(preset: BrokerPreset) = dao.deletePreset(preset)

    suspend fun insertProductType(productType: ProductType) = dao.insertProductType(productType)
    suspend fun updateProductType(productType: ProductType) = dao.updateProductType(productType)
    suspend fun deleteProductType(productType: ProductType) = dao.deleteProductType(productType)

    suspend fun insertTrade(trade: TradeItem) = dao.insertTrade(trade)
    suspend fun deleteTrade(trade: TradeItem) = dao.deleteTrade(trade)

    suspend fun ensureSeededData() = withContext(Dispatchers.IO) {
        val presets = allPresets.first()
        if (presets.isEmpty()) {
            val defaultPresets = listOf(
                BrokerPreset(
                    name = "Zerodha Delivery",
                    brokerageFlat = 0.0,
                    brokeragePercent = 0.0,
                    sttPercent = 0.1,         // 0.1% on Buy & Sell
                    exchangeTxPercent = 0.00343, // NSE txn charge
                    sebiTurnoverPercent = 0.0001,
                    stampDutyPercent = 0.015,  // 0.015% on Buy
                    gstPercent = 18.0,
                    dpCharge = 15.93          // 13.5 + 18% GST approx
                ),
                BrokerPreset(
                    name = "Zerodha Intraday",
                    brokerageFlat = 20.0,
                    brokeragePercent = 0.03,   // 0.03% capped at flat Rs. 20
                    sttPercent = 0.025,        // 0.025% on Sell side only
                    exchangeTxPercent = 0.00343,
                    sebiTurnoverPercent = 0.0001,
                    stampDutyPercent = 0.003,  // 0.003% on Buy side
                    gstPercent = 18.0,
                    dpCharge = 0.0
                ),
                BrokerPreset(
                    name = "Upstox Intraday",
                    brokerageFlat = 20.0,
                    brokeragePercent = 0.05,   // 0.05% capped at flat Rs. 20
                    sttPercent = 0.025,
                    exchangeTxPercent = 0.00343,
                    sebiTurnoverPercent = 0.0001,
                    stampDutyPercent = 0.003,
                    gstPercent = 18.0,
                    dpCharge = 0.0
                )
            )
            for (preset in defaultPresets) {
                dao.insertPreset(preset)
            }
        }

        val products = allProductTypes.first()
        if (products.isEmpty()) {
            val defaultProducts = listOf(
                ProductType(name = "MIS"),
                ProductType(name = "CNC"),
                ProductType(name = "NRML"),
                ProductType(name = "BO"),
                ProductType(name = "CO")
            )
            for (prod in defaultProducts) {
                dao.insertProductType(prod)
            }
        }
    }
}
