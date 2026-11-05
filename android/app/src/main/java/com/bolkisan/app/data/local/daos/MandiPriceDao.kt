package com.bolkisan.app.data.local.daos

import androidx.room.*
import com.bolkisan.app.data.local.entities.MandiPriceEntity
import com.bolkisan.app.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MandiPriceDao {
    @Query("SELECT * FROM local_mandi_prices ORDER BY observationDate DESC")
    fun getAllPricesFlow(): Flow<List<MandiPriceEntity>>

    @Query("SELECT * FROM local_mandi_prices WHERE commodity LIKE '%' || :commodity || '%'")
    suspend fun getPricesByCommodity(commodity: String): List<MandiPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<MandiPriceEntity>)

    @Query("SELECT COUNT(*) FROM local_mandi_prices")
    suspend fun getCount(): Int
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAllPendingQueue(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(item: SyncQueueEntity)

    @Delete
    suspend fun deleteFromQueue(items: List<SyncQueueEntity>)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)
}
