package com.bolkisan.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farmer_profile")
data class FarmerProfileEntity(
    @PrimaryKey
    val id: String = "farmer_primary",
    val phoneNumber: String? = null,
    val preferredLanguage: String = "te", // "te" or "hi"
    val primaryCrop: String = "Cotton",
    val state: String = "Telangana",
    val district: String = "Warangal",
    val village: String? = null,
    val hasAudioConsent: Boolean = false,
    val isFirstLaunch: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
