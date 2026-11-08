package com.example.carparkingsmart.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_station")
data class ChargingStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val name: String,
    val lat: Double,
    val lon: Double,
    val address: String,

    val totalSlots: Int,
    val availableSlots: Int,

    val power: Double,
    val pricePerKwh: Double,

    val type: String
)