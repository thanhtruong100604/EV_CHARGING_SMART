package com.example.carparkingsmart.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.carparkingsmart.data.dao.ChargingDao
import com.example.carparkingsmart.data.entity.ChargingSessionEntity
import com.example.carparkingsmart.data.entity.ChargingStationEntity

@Database(
    entities = [
        ChargingStationEntity::class,
        ChargingSessionEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargingDao(): ChargingDao
}