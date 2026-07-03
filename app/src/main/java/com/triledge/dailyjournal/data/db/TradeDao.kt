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
interface TradeDao {
    // Broker Presets
    @Query("SELECT * FROM broker_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<BrokerPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: BrokerPreset): Long

    @Update
    suspend fun updatePreset(preset: BrokerPreset)

    @Delete
    suspend fun deletePreset(preset: BrokerPreset)

    // Product Types
    @Query("SELECT * FROM product_types ORDER BY name ASC")
    fun getAllProductTypes(): Flow<List<ProductType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductType(productType: ProductType): Long

    @Update
    suspend fun updateProductType(productType: ProductType)

    @Delete
    suspend fun deleteProductType(productType: ProductType)

    // Trade Items
    @Transaction
    @Query("SELECT * FROM trade_items ORDER BY exitTime DESC")
    fun getAllTradesWithPreset(): Flow<List<TradeItemWithPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeItem): Long

    @Delete
    suspend fun deleteTrade(trade: TradeItem)
}
