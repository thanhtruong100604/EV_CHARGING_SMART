package com.example.carparkingsmart.api
import com.google.gson.annotations.SerializedName

data class RegisterResponse(val id: Int, val username: String, val email: String)
data class LoginResponse(val token: String)

data class StationResponse(
    val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("ward") val ward: String?,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double, // Dùng Double theo ý bạn
    @SerializedName("longitude") val longitude: Double, // Dùng Double theo ý bạn
    @SerializedName("total_slots") val total_slots: Int,
    @SerializedName("available_slots") val available_slots: Int
)
data class BookingResponse(val id: Int, val status: String, val qr_code_data: String, val expiry_time: String)