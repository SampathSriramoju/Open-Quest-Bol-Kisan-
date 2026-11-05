package com.bolkisan.app.data.local.daos

import androidx.room.*
import com.bolkisan.app.data.local.entities.FarmerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerProfileDao {
    @Query("SELECT * FROM farmer_profile LIMIT 1")
    fun getProfileFlow(): Flow<FarmerProfileEntity?>

    @Query("SELECT * FROM farmer_profile LIMIT 1")
    suspend fun getProfile(): FarmerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: FarmerProfileEntity)

    @Query("UPDATE farmer_profile SET preferredLanguage = :lang WHERE id = :id")
    suspend fun updateLanguage(id: String, lang: String)

    @Query("UPDATE farmer_profile SET hasAudioConsent = :consent WHERE id = :id")
    suspend fun updateConsent(id: String, consent: Boolean)
}
