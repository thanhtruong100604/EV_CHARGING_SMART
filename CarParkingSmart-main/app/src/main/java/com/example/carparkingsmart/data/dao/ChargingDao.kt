package com.example.carparkingsmart.data.dao

import androidx.room.*
import com.example.carparkingsmart.data.entity.ChargingSessionEntity
import com.example.carparkingsmart.data.entity.ChargingStationEntity

@Dao
interface ChargingDao {

    // ===== TRẠM SẠC =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: ChargingStationEntity)

    @Query("SELECT * FROM charging_station")
    suspend fun getAllStations(): List<ChargingStationEntity>

    // ===== PHIÊN SẠC =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_session WHERE userId = :userId")
    suspend fun getSessionsByUser(userId: String): List<ChargingSessionEntity>

    @Query("UPDATE charging_station SET availableSlots = :slots WHERE id = :id")
    suspend fun updateAvailableSlots(id: Int, slots: Int)
}