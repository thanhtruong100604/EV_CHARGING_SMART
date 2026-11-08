package com.example.carparkingsmart.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Đăng ký tài khoản
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    // Đăng nhập lấy Token
    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    // Lấy danh sách trạm sạc
    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    // Lấy danh sách ô sạc theo ID trạm sạc
    @GET("api/stations/{id}/slots/")
    suspend fun getSlots(
        @Path("id") stationId: Int
    ): Response<List<com.example.carparkingsmart.ChargingSlot>>

    // Tạo đơn đặt chỗ mới
    @FormUrlEncoded
    @POST("api/bookings/")
    suspend fun createBooking(
        @Field("user_id") userId: String,
        @Field("station") stationId: Int,
        @Field("slot") slotId: Int,
        @Field("status") status: String,
        @Field("scheduled_hour") scheduledHour: Int = -1,
        @Field("hold_minutes") holdMinutes: Int = 10   // thời gian server giữ chỗ (phút)
    ): Response<BookingResponse>

    // Cập nhật trạng thái đặt chỗ
    @FormUrlEncoded
    @POST("api/bookings/{id}/update_status/")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: Int,
        @Field("status") status: String
    ): Response<ResponseBody>


    @GET("api/bookings/{id}/")
    suspend fun getBookingDetail(
        @Path("id") bookingId: Int
    ): Response<BookingResponse>

    // Sửa lại tên cho nhất quán — dùng 1 cái duy nhất
    @POST("api/bookings/{id}/confirm_payment/")
    suspend fun confirmBookingAndSubtractSlot(
        @Path("id") bookingId: Int
    ): Response<ResponseBody>

    // Lấy danh sách giờ đã bị đặt của một ô trong ngày
    @GET("api/bookings/booked_hours/")
    suspend fun getBookedHours(
        @Query("station_id") stationId: Int,
        @Query("slot_id") slotId: Int
    ): Response<List<Int>>

    // Cấu trúc dữ liệu phản hồi cho Đặt chỗ
    data class BookingResponse(
        val id: Int,
        val user_id: String,
        val station: Int,
        val slot: Int?,
        val status: String,
        val booking_time: String,
        val expiry_time: String? = null
    )

    // THÊM method mới này
    @POST("api/bookings/create_booking_with_slot/")
    suspend fun createBookingWithSlot(
        @Body request: CreateBookingRequest
    ): Response<BookingResponse>

    // Thông tin chuyển khoản (số tiền, tài khoản, nội dung) lấy từ server — dùng chung với web
    @GET("api/bookings/{id}/payment_info/")
    suspend fun getPaymentInfo(
        @Path("id") bookingId: Int
    ): Response<PaymentInfo>

    data class PaymentInfo(
        val amount: Int,
        val content: String,
        val bank_bin: String,
        val account_no: String,
        val account_name: String? = null,
        val qr_url: String? = null
    )

    @GET("api/bookings/stats/")
    suspend fun getUserBookingStats(
        @Query("user_id") userId: String
    ): Response<BookingStats>

    @GET("api/bookings/")
    suspend fun getUserBookings(
        @Query("user_id") userId: String
    ): Response<List<BookingHistoryItem>>
    data class BookingHistoryItem(
        val id: Int,
        val status: String,
        val station_name: String?,   // ← cần Django trả về
        val slot_code: String?,      // ← cần Django trả về
        val scheduled_hour: Int?,
        val booking_time: String?
    )

    data class BookingStats(
        val total: Int,
        val completed: Int,
        val cancelled: Int
    )

    data class CreateBookingRequest(
        val user_id: String,
        val station: Int,
        val slot: Int,
        val scheduled_hour: Int,
        val status: String = "Quick_Booking"
    )
    data class StationResponse(
        val id: Int,
        val name: String,
        val ward: String?,
        val latitude: Double,
        val longitude: Double,
        val total_slots: Int?,
        val available_slots: Int?,
        val address: String?,
        val real_available_time_slots: Int? = 0  // ← THÊM DÒNG NÀY
    )
}