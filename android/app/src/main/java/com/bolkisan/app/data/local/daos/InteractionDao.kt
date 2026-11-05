package com.bolkisan.app.data.local.daos

import androidx.room.*
import com.bolkisan.app.data.local.entities.InteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions ORDER BY timestamp DESC")
    fun getAllInteractionsFlow(): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions WHERE isSynced = 0")
    suspend fun getPendingUnsyncedInteractions(): List<InteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: InteractionEntity)

    @Query("UPDATE interactions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("DELETE FROM interactions")
    suspend fun clearHistory()
}
