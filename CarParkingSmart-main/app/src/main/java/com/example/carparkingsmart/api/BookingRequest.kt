package com.example.carparkingsmart.api

import com.google.gson.annotations.SerializedName

data class BookingRequest(
    @SerializedName("user") val userId: Int,
    @SerializedName("station") val stationId: Int,
    @SerializedName("amount") val amount: Double
)