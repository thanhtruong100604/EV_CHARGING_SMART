package com.example.carparkingsmart.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_session")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val stationId: Int,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val energyUsed: Double
)