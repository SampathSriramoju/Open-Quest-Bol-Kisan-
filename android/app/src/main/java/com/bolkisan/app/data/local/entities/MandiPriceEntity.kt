package com.bolkisan.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_mandi_prices")
data class MandiPriceEntity(
    @PrimaryKey
    val id: String,
    val state: String,
    val district: String,
    val marketName: String,
    val commodity: String,
    val variety: String,
    val minPrice: Double,
    val maxPrice: Double,
    val modalPrice: Double,
    val observationDate: String = "2026-11-04",
    val isMockFeed: Boolean = true,
    val lastSyncedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,
    val queueType: String, // "INTERACTION", "ESCALATION", "CONSENT"
    val payloadJson: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
