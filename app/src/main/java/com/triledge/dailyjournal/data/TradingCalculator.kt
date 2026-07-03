package com.triledge.dailyjournal.data

import com.triledge.dailyjournal.data.db.BrokerPreset
import com.triledge.dailyjournal.data.db.TradeItem

data class TradeCalculationResult(
    val grossPnL: Double,
    val brokerage: Double,
    val stt: Double,
    val exchangeTx: Double,
    val sebiTurnover: Double,
    val stampDuty: Double,
    val gst: Double,
    val dpCharges: Double,
    val totalCharges: Double,
    val netPnL: Double
)

object TradingCalculator {
    fun calculate(trade: TradeItem, preset: BrokerPreset?): TradeCalculationResult {
        val buyValue = trade.buyPrice * trade.quantity
        val sellValue = trade.sellPrice * trade.quantity
        val turnover = buyValue + sellValue
        val grossPnL = sellValue - buyValue

        if (preset == null) {
            return TradeCalculationResult(
                grossPnL = grossPnL,
                brokerage = 0.0,
                stt = 0.0,
                exchangeTx = 0.0,
                sebiTurnover = 0.0,
                stampDuty = 0.0,
                gst = 0.0,
                dpCharges = 0.0,
                totalCharges = 0.0,
                netPnL = grossPnL
            )
        }

        // 1. Brokerage (capped at flat per side)
        val buyBrokerage = if (preset.brokeragePercent > 0) {
            val calc = buyValue * preset.brokeragePercent / 100.0
            if (preset.brokerageFlat > 0) kotlin.math.min(calc, preset.brokerageFlat) else calc
        } else {
            preset.brokerageFlat
        }

        val sellBrokerage = if (preset.brokeragePercent > 0) {
            val calc = sellValue * preset.brokeragePercent / 100.0
            if (preset.brokerageFlat > 0) kotlin.math.min(calc, preset.brokerageFlat) else calc
        } else {
            preset.brokerageFlat
        }
        
        val brokerage = buyBrokerage + sellBrokerage

        // 2. STT (Securities Transaction Tax)
        // Equity Delivery STT is on both buy and sell. Intraday / F&O STT is on sell side only.
        val isDelivery = trade.segment.equals("equity delivery", ignoreCase = true)
        val stt = if (isDelivery) {
            turnover * preset.sttPercent / 100.0
        } else {
            sellValue * preset.sttPercent / 100.0
        }

        // 3. Exchange Transaction Charges (both sides)
        val exchangeTx = turnover * preset.exchangeTxPercent / 100.0

        // 4. SEBI Turnover Charges (both sides)
        val sebiTurnover = turnover * preset.sebiTurnoverPercent / 100.0

        // 5. Stamp Duty (Buy side only)
        val stampDuty = buyValue * preset.stampDutyPercent / 100.0

        // 6. GST (18% on Brokerage + Exchange Tx + SEBI Turnover)
        val gst = (brokerage + exchangeTx + sebiTurnover) * preset.gstPercent / 100.0

        // 7. DP Charges (debit side only, so on Sell, and only for Equity Delivery sells)
        val dpCharges = if (isDelivery && preset.dpCharge > 0) {
            preset.dpCharge
        } else {
            0.0
        }

        val totalCharges = brokerage + stt + exchangeTx + sebiTurnover + stampDuty + gst + dpCharges
        val netPnL = grossPnL - totalCharges

        return TradeCalculationResult(
            grossPnL = grossPnL,
            brokerage = brokerage,
            stt = stt,
            exchangeTx = exchangeTx,
            sebiTurnover = sebiTurnover,
            stampDuty = stampDuty,
            gst = gst,
            dpCharges = dpCharges,
            totalCharges = totalCharges,
            netPnL = netPnL
        )
    }
}
