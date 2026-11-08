package com.example.carparkingsmart

import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.google.android.gms.location.*

import com.example.carparkingsmart.navigation.NavigationEngine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri


import androidx.room.Room
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.carparkingsmart.data.AppDatabase
import com.example.carparkingsmart.api.RetrofitClient

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup


import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.carparkingsmart.auth.LoginActivity
import org.osmdroid.views.overlay.TilesOverlay
import androidx.appcompat.app.AppCompatDelegate

import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.carparkingsmart.api.ApiService
import com.google.android.material.bottomsheet.BottomSheetDialog



class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase

    private var currentSelectedSlotCode: String = ""

    private lateinit var map: MapView
    private lateinit var btnDetailedDirections: Button
    private lateinit var searchBox: AutoCompleteTextView
    private lateinit var btnVoice: ImageButton
    private lateinit var btnMyLocation: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnDirections: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var tvPlaceName: TextView
    private lateinit var tvPlaceAddress: TextView
    private lateinit var tvPlaceDistance: TextView
    private lateinit var tvPlaceRating: TextView
    private lateinit var tvPlaceCategory: TextView
    private lateinit var btnDirectionsBottom: Button
    private lateinit var btnSavePlace: LinearLayout
    private lateinit var btnSharePlace: LinearLayout
    private lateinit var btnNearby: LinearLayout

    private var directionSteps = mutableListOf<DirectionStep>()

    private lateinit var directionsBottomSheetDialog: BottomSheetDialog

    private lateinit var placeAdapter: PlaceAdapter

    private lateinit var chipRestaurant: MaterialCardView
    private lateinit var chipCafe: MaterialCardView
    private lateinit var chipHotel: MaterialCardView
    private lateinit var chipHospital: MaterialCardView

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var searchMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var currentPlace: PlaceInfo? = null

    private var parkingLots: MutableList<ParkingLot> = mutableListOf()
    private val parkingMarkers = mutableListOf<Marker>()
    private val updateHandler = Handler(Looper.getMainLooper())
    private val notificationHandler = Handler(Looper.getMainLooper())

    private val suggestionsData = mutableListOf<PlaceInfo>()

    private lateinit var btnCloseSheet: ImageButton

    private lateinit var btnBookParking: Button

    private lateinit var btnSaveMySpot: Button
    private lateinit var btnFindMySpot: Button
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private var savedParkingSpot: GeoPoint? = null
    private var savedParkingTime: Long = 0L
    private var savedParkingName: String? = null

    private var currentBookingId: Int = -1

    private var stationLoadRetries = 0

    private var previousNearestParking: ParkingLot? = null

    private lateinit var btnThemeToggle: ImageButton

    private var lastNotificationTime: Long = 0L

    private lateinit var btnShowParkingList: Button

    private lateinit var btnBookParkingLater: Button

    private lateinit var tvLiveOccupancy: TextView

    private var checkPaymentHandler = Handler(Looper.getMainLooper())

    private var currentSelectedSlotId: Int = 0
    private var navigationPanel: View? = null
    private lateinit var tvNavigationInstruction: TextView
    private lateinit var tvDistanceRemaining: TextView
    private lateinit var tvTimeRemaining: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var rvRemainingSteps: RecyclerView
    private var totalDistanceToDest = 0.0
    private var startTime = 0L
    private var isVoiceMuted = false

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val startPoint = GeoPoint(lat1, lon1)
        val endPoint = GeoPoint(lat2, lon2)
        return startPoint.distanceToAsDouble(endPoint)
    }

    data class PlaceInfo(
        val id: Int,
        val lat: Double,
        val lon: Double,
        val name: String,
        val address: String,
        val category: String = "",
        val rating: String = ""
    )

    data class QuickBooking(
        val stationId: Int,
        val stationName: String,
        val bookingTime: Long,
        val expiryTime: Long
    )

    data class ParkingLot(
        val id: Int,
        val name: String,
        val ward: String,
        val lat: Double,
        val lon: Double,
        val totalSpots: Int,
        var availableSpots: Int,
        val address: String,
        var isNearest: Boolean = false,
        val hasChargingStation: Boolean = false,
        val totalChargingSpots: Int = 0,
        var availableChargingSpots: Int = 0,
        var realAvailableTimeSlots: Int = 0 
    )
    
    data class ChargingRequest(
        val vehicleId: String,
        val userLat: Double,
        val userLon: Double,
        val timestamp: Long,
        val distanceToStation: Double
    )

    private var currentNearestParking: ParkingLot? = null
    private val chargingRequestQueue = mutableListOf<ChargingRequest>()
    private var currentNearestChargingStation: ParkingLot? = null

    private var navigationManager: NavigationManager? = null
    private var isNavigating = false
    private lateinit var navHud: LinearLayout
    private lateinit var tvNavInstruction: TextView
    private lateinit var tvNavRoadName: TextView
    private lateinit var tvNavDistanceToTurn: TextView
    private lateinit var tvNavDistanceUnit: TextView
    private lateinit var tvNavTimeRemaining: TextView
    private lateinit var tvNavTotalDistance: TextView
    private lateinit var tvNavStepCounter: TextView
    private lateinit var tvNavManeuverIcon: TextView
    private lateinit var btnNavStop: Button
    private lateinit var btnNavMute: ImageButton
    private lateinit var navArrivalBanner: LinearLayout

    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            if (isNavigating) {
                navigationManager?.updateLocation(location.latitude, location.longitude)
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    // Thêm vào đầu class MainActivity, cạnh các biến lateinit
    private var pendingInvoiceBitmap: android.graphics.Bitmap? = null

    private val saveInvoiceLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let { dest ->
            pendingInvoiceBitmap?.let { bmp ->
                try {
                    contentResolver.openOutputStream(dest)?.use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Toast.makeText(this, "Đã lưu hóa đơn thành công!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Lỗi lưu file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private val VOICE_SEARCH_REQUEST_CODE = 101

    private var navigationEngine: NavigationEngine? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isNavigationActive = false

    // UI cho navigation
    private lateinit var navPanel: LinearLayout
    private lateinit var tvCurrentInstruction: TextView
    private lateinit var tvDistanceToTurn: TextView
    private lateinit var tvRemainingDistance: TextView
    private lateinit var btnStopNavigation: Button
    private lateinit var ivManeuverIcon: ImageView


    private fun initNavigationPanel() {
        navPanel = findViewById(R.id.navigation_panel)
        tvCurrentInstruction = findViewById(R.id.tv_current_instruction)
        tvDistanceToTurn = findViewById(R.id.tv_distance_to_turn)
        tvRemainingDistance = findViewById(R.id.tv_remaining_distance)
        btnStopNavigation = findViewById(R.id.btn_stop_navigation)
        ivManeuverIcon = findViewById(R.id.iv_maneuver_icon)

        btnStopNavigation.setOnClickListener {
            stopNavigation()
        }

        // Ẩn panel ban đầu
        navPanel.visibility = View.GONE
    }

    /**
     * Bắt đầu navigation (Gọi khi user nhấn "Chỉ đường")
     */
    private fun startNavigationToDestination() {
        val destination = currentPlace ?: return

        myLocationOverlay?.myLocation?.let { myLoc ->
            val from = GeoPoint(myLoc.latitude, myLoc.longitude)
            val to = GeoPoint(destination.lat, destination.lon)

            lifecycleScope.launch {
                try {
                    // Hiện panel navigation
                    navPanel.visibility = View.VISIBLE
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                    // Bắt đầu navigation
                    navigationEngine?.startNavigation(from, to)
                    isNavigationActive = true

                    // Bắt đầu theo dõi vị trí
                    startLocationUpdates()

                    // Vẽ route
                    calculateRoute(from.latitude, from.longitude, to.latitude, to.longitude)

                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Lỗi khởi động navigation: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Không xác định được vị trí hiện tại", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Bắt đầu cập nhật vị trí liên tục
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000 // 2 giây
            fastestInterval = 1000 // 1 giây
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    navigationEngine?.updateLocation(location)
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    /**
     * Cập nhật UI navigation
     */
    private fun updateNavigationUI(state: NavigationEngine.NavigationState) {
        runOnUiThread {
            state.currentStep?.let { step ->
                tvCurrentInstruction.text = step.instruction
                tvDistanceToTurn.text = formatDistance(state.distanceToNextTurn)
                tvRemainingDistance.text = "Còn lại: ${formatDistance(state.remainingDistance)}"

                // Cập nhật icon rẽ
                updateManeuverIcon(step.maneuver)

                // Highlight nếu off-route
                if (state.isOffRoute) {
                    tvCurrentInstruction.setTextColor(android.graphics.Color.RED)
                    tvCurrentInstruction.text = "Bạn đã đi lệch hướng! Đang tính toán lại..."
                    // TODO: Recalculate route
                } else {
                    tvCurrentInstruction.setTextColor(android.graphics.Color.parseColor("#1B5E20"))
                }
            }
        }
    }

    /**
     * Cập nhật icon chỉ dẫn rẽ
     */
    private fun updateManeuverIcon(maneuver: com.example.carparkingsmart.navigation.Maneuver) {
        val iconRes = when (maneuver.type) {
            "turn" -> when (maneuver.modifier) {
                "left" -> R.drawable.ic_turn_left
                "right" -> R.drawable.ic_turn_right
                "slight left" -> R.drawable.ic_turn_slight_left
                "slight right" -> R.drawable.ic_turn_slight_right
                else -> R.drawable.ic_arrow_up
            }
            "arrive" -> R.drawable.ic_flag
            else -> R.drawable.ic_arrow_up
        }
        ivManeuverIcon.setImageResource(iconRes)
    }

    private fun showDirectionsBottomSheet(
        totalDistance: Double,
        totalDuration: Double,
        steps: List<DirectionStep>
    ) {
        // Đóng dialog cũ nếu đang mở
        if (::directionsBottomSheetDialog.isInitialized && directionsBottomSheetDialog.isShowing) {
            directionsBottomSheetDialog.dismiss()
        }

        directionsBottomSheetDialog = BottomSheetDialog(this)  // ← gán vào biến class
        val view = layoutInflater.inflate(R.layout.activity_directions, null)
        directionsBottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.tv_total_distance).text =
            "📏 ${formatDistance(totalDistance)}"
        view.findViewById<TextView>(R.id.tv_total_duration).text =
            "⏱ ${formatDuration(totalDuration)}"

        val rv = view.findViewById<RecyclerView>(R.id.rv_directions)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rv.adapter = DirectionStepAdapter(steps)

        view.findViewById<Button>(R.id.btn_start_navigation).setOnClickListener {
            directionsBottomSheetDialog.dismiss()
            startRealTimeNavigation(steps)
        }

        directionsBottomSheetDialog.show()
    }

    private fun showDirections() {
        currentPlace?.let { place ->
            val myLoc = myLocationOverlay?.myLocation
            if (myLoc != null) {
                calculateRoute(myLoc.latitude, myLoc.longitude, place.lat, place.lon)
            } else {
                Toast.makeText(this, "Đang xác định vị trí, vui lòng chờ...", Toast.LENGTH_SHORT).show()
                myLocationOverlay?.runOnFirstFix {
                    runOnUiThread {
                        myLocationOverlay?.myLocation?.let { loc ->
                            calculateRoute(loc.latitude, loc.longitude, place.lat, place.lon)
                        }
                    }
                }
            }
        } ?: Toast.makeText(this, "Chưa chọn địa điểm đích", Toast.LENGTH_SHORT).show()
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cấu hình OSMDroid
        Configuration.getInstance().userAgentValue = "CarParkingSmart/1.0"
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Gửi token đăng nhập kèm mọi request; token hết hạn → tự về màn hình đăng nhập
        val authPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        RetrofitClient.tokenProvider = { authPrefs.getString("token", null) }
        RetrofitClient.onUnauthorized = { runOnUiThread { if (!isFinishing) performLogout() } }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.my_toolbar)
        setSupportActionBar(toolbar)

        // Khởi tạo Database
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "charging_db").build()

        // Khởi tạo giao diện
        initViews()
        setupMap()
        setupMyLocation()
        setupSearchWithAutocomplete()
        setupButtons()
        setupBottomSheet()

        // setupCategoryChips() <-- Tạm thời comment dòng này lại nếu hàm này bên dưới vẫn đang gọi đến các chipRestaurant cũ

        initNavHud()
        loadChargingStationsFromDB()
        navigationEngine = NavigationEngine(this) { state ->
            updateNavigationUI(state)
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initNavigationPanel()
        
    }

    private fun initViews() {
        map = findViewById(R.id.map)
        searchBox = findViewById(R.id.search_box)
        btnVoice = findViewById(R.id.btn_voice)
        btnMyLocation = findViewById(R.id.btn_my_location)
        btnLayers = findViewById(R.id.btn_layers)
        //btnDirections = findViewById(R.id.btn_directions)
        bottomSheet = findViewById(R.id.bottom_sheet)
        tvPlaceName = findViewById(R.id.tv_place_name)
        tvPlaceAddress = findViewById(R.id.tv_place_address)
        tvPlaceDistance = findViewById(R.id.tv_place_distance)
        tvPlaceRating = findViewById(R.id.tv_place_rating)
        tvPlaceCategory = findViewById(R.id.tv_place_category)
        btnDirectionsBottom = findViewById(R.id.btn_directions_bottom)
        btnSavePlace = findViewById(R.id.btn_save_place)
        btnSharePlace = findViewById(R.id.btn_share_place)
        btnNearby = findViewById(R.id.btn_nearby)
        btnCloseSheet = findViewById(R.id.btn_close_sheet)
        btnDirections = findViewById(R.id.btn_directions)
        btnBookParking = findViewById(R.id.btn_book_parking)
        btnBookParkingLater = findViewById(R.id.btn_book_parking_later)
        tvLiveOccupancy = findViewById(R.id.tv_live_occupancy)
        btnThemeToggle = findViewById(R.id.btn_theme_toggle)
        btnDetailedDirections = findViewById(R.id.btn_detailed_directions)

        val btnLogout = findViewById<ImageButton>(R.id.btn_logout_map)
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        val userAvatar = findViewById<ImageView>(R.id.user_avatar)
        userAvatar.setOnClickListener {
            showUserProfileDialog()
        }
    }

    private fun showSlotSelectionDialog(slots: List<ChargingSlot>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_select_slot, null)
        dialog.setContentView(view)

        val rvSlots = view.findViewById<RecyclerView>(R.id.rv_slots)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm_slot)

        var selectedSlot: ChargingSlot? = null

        rvSlots.layoutManager = GridLayoutManager(this, 5)
        rvSlots.adapter = SlotAdapter(slots) { slot ->
            selectedSlot = slot
            currentSelectedSlotId = slot.id
            currentSelectedSlotCode = slot.slot_code
            btnConfirm.visibility = View.VISIBLE
            btnConfirm.text = "Xác nhận đặt ô ${slot.slot_code}"
        }

        // ← CHỈ 1 LỚP LISTENER, KHÔNG LỒNG NHAU
        btnConfirm.setOnClickListener {
            selectedSlot?.let { slot ->
                currentSelectedSlotId = slot.id
                currentSelectedSlotCode = slot.slot_code
                dialog.dismiss()
                
                selectedHour = -1  // ← THÊM DÒNG NÀY: reset giờ khi chọn ô mới
                selectedBookingTimeMillis = 0L  // ← reset luôn
                
                Handler(Looper.getMainLooper()).postDelayed({
                    showTimeSlotDialog(slot)
                }, 200)
            }
    }

        dialog.show()
    }

    private fun initNavHud() {
        navHud = findViewById<LinearLayout>(R.id.navigation_hud)
        tvNavManeuverIcon   = navHud.findViewById(R.id.tv_nav_maneuver_icon)
        tvNavInstruction    = navHud.findViewById(R.id.tv_nav_instruction)
        tvNavRoadName       = navHud.findViewById(R.id.tv_nav_road_name)
        tvNavDistanceToTurn = navHud.findViewById(R.id.tv_nav_distance_to_turn)
        tvNavDistanceUnit   = navHud.findViewById(R.id.tv_nav_distance_unit)
        tvNavTimeRemaining  = navHud.findViewById(R.id.tv_nav_time_remaining)
        tvNavTotalDistance  = navHud.findViewById(R.id.tv_nav_total_distance)
        tvNavStepCounter    = navHud.findViewById(R.id.tv_nav_step_counter)
        btnNavStop          = navHud.findViewById(R.id.btn_nav_stop)
        btnNavMute          = navHud.findViewById(R.id.btn_nav_mute)
        navArrivalBanner    = navHud.findViewById(R.id.nav_arrival_banner)

        btnNavStop.setOnClickListener { stopNavigation() }
        btnNavMute.setOnClickListener {
            val nowOn = navigationManager?.toggleMute() ?: false
            btnNavMute.setImageResource(
                if (nowOn) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode
            )
        }
    }
    private fun guilenServerDatCho(slot: ChargingSlot) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUserEmail = sharedPref.getString("user_email", "Guest") ?: "Guest"

        // Kiểm tra selectedHour trước khi gọi API
        if (selectedHour == -1) {
            Toast.makeText(this, "Lỗi: Chưa chọn khung giờ!", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("BOOKING", "Đặt ô: ${slot.slot_code}, giờ: $selectedHour, trạm: ${currentPlace?.id}")
        android.util.Log.d("BOOKING_DEBUG", """
        selectedHour = $selectedHour
        currentPlace = $currentPlace
        stationId = ${currentPlace?.id}
        slotId = ${slot.id}
        slotCode = ${slot.slot_code}
        userEmail = $currentUserEmail
    """.trimIndent())

    if (selectedHour == -1) {
        Toast.makeText(this, "Lỗi: Chưa chọn khung giờ!", Toast.LENGTH_LONG).show()
        return
    }

    if (currentPlace?.id == null) {
        Toast.makeText(this, "Lỗi: Không xác định được trạm sạc!", Toast.LENGTH_LONG).show()
        return
    }
        lifecycleScope.launch {
            try {
                val targetStationId = currentPlace?.id ?: 1

                val response = RetrofitClient.instance.createBookingWithSlot(
                    ApiService.CreateBookingRequest(
                        user_id = currentUserEmail,
                        station = targetStationId,
                        slot = slot.id,
                        scheduled_hour = selectedHour,
                        status = "Quick_Booking"
                    )
                )

                if (response.isSuccessful) {
                    val bookingResponse = response.body()
                    currentBookingId = bookingResponse?.id ?: -1
                    //loadChargingStationsFromDB()

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Đã giữ ô ${slot.slot_code} khung ${selectedHour}:00-${selectedHour+2}:00!",
                            Toast.LENGTH_LONG
                        ).show()
                        bottomSheetBehavior.isHideable = false
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        currentPlace?.let { place ->
                            parkingLots.find { it.id == place.id }?.let { station ->
                                station.realAvailableTimeSlots -= 1
                            }
                        }

                        showBookingPayment(currentPlace?.name ?: "Trạm sạc")
                        loadChargingStationsFromDB()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("API_ERROR", "HTTP ${response.code()}: $errorBody")
                    runOnUiThread {
                        // Hiện lỗi cụ thể thay vì chỉ "Không thể đặt chỗ"
                        val serverMsg = try {
                            JSONObject(errorBody ?: "").optString("error")
                        } catch (e: Exception) { "" }
                        val msg = if (serverMsg.isNotBlank()) serverMsg else when (response.code()) {
                            400 -> "Không thể đặt chỗ, vui lòng kiểm tra lại!"
                            409 -> "Ô sạc này đã bị đặt trong khung giờ đó!"
                            else -> "Lỗi server (${response.code()})"
                        }
                        // Có người vừa đặt trước → tải lại để cập nhật giờ trống
                        if (response.code() == 409) loadChargingStationsFromDB()
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("API_ERROR", "Lỗi kết nối: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Lỗi kết nối Server: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /*private fun guilenServerDatCho(slot: ChargingSlot) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUserEmail = sharedPref.getString("user_email", "Guest") ?: "Guest"

        android.util.Log.d("BOOKING", "currentPlace = $currentPlace")
        android.util.Log.d("BOOKING", "slot = ${slot.slot_code}, selectedHour = $selectedHour")

        lifecycleScope.launch {
            try {
                val targetStationId = currentPlace?.id ?: 1

                val response = RetrofitClient.instance.createBooking(
                    userId = currentUserEmail,
                    stationId = targetStationId,
                    slotId = slot.id,
                    status = "Quick_Booking",
                    scheduledHour = selectedHour
                )

                if (response.isSuccessful) {
                    val bookingResponse = response.body()
                    currentBookingId = bookingResponse?.id ?: -1
                    loadChargingStationsFromDB()

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Đã giữ ô ${slot.slot_code}! Vui lòng thanh toán trong 10 phút.",
                            Toast.LENGTH_LONG
                        ).show()

                        bottomSheetBehavior.isHideable = false
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                        // ĐẶT TRƯỚC → hiện QR thanh toán ngay
                        showBookingPayment(currentPlace?.name ?: "Trạm sạc")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("API_ERROR", "Lỗi: $errorBody")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Không thể đặt chỗ!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("API_ERROR", "Lỗi kết nối: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Lỗi kết nối Server!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }*/




    private fun showDetailedDirections() {
        currentPlace?.let { place ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Bắt đầu chỉ đường?")
                .setMessage("Chỉ đường đến ${place.name} với hướng dẫn giọng nói?")
                .setPositiveButton("Bắt đầu") { _, _ ->
                    val myLoc = myLocationOverlay?.myLocation
                    if (myLoc != null) {
                        calculateDetailedRoute(myLoc.latitude, myLoc.longitude, place.lat, place.lon)
                    } else {
                        Toast.makeText(this, "Đang xác định vị trí, vui lòng chờ...", Toast.LENGTH_SHORT).show()
                        myLocationOverlay?.runOnFirstFix {
                            runOnUiThread {
                                myLocationOverlay?.myLocation?.let { loc ->
                                    calculateDetailedRoute(loc.latitude, loc.longitude, place.lat, place.lon)
                                }
                            }
                        }
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        } ?: Toast.makeText(this, "Chưa chọn địa điểm đích", Toast.LENGTH_SHORT).show()
    }

    private fun updateMapCameraForNavigation(lat: Double, lon: Double, bearing: Float = 0f) {
        // Zoom theo vị trí người dùng khi đang navigation
        map.controller.animateTo(GeoPoint(lat, lon))
        map.controller.setZoom(18.0)
        // Xoay bản đồ theo hướng đi
        map.mapOrientation = -bearing
    }

    // Tính route chi tiết với các bước chỉ đường
    private fun calculateDetailedRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        showLoadingDialog("Đang tính toán lộ trình...")

        Thread {
            try {
                // Sử dụng API OSRM với steps=true để lấy chi tiết từng bước
                val urlString = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                        "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline&steps=true"

                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val result = JSONObject(json)

                    if (result.getString("code") == "Ok") {
                        val routes = result.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getString("geometry")
                            val totalDistance = route.getDouble("distance")
                            val totalDuration = route.getDouble("duration")


                            // Parse các bước chỉ đường
                            val legs = route.getJSONArray("legs")
                            val stepsList = mutableListOf<DirectionStep>()

                            for (i in 0 until legs.length()) {
                                val leg = legs.getJSONObject(i)
                                val steps = leg.getJSONArray("steps")

                                for (j in 0 until steps.length()) {
                                    val step = steps.getJSONObject(j)
                                    val maneuverObj = step.getJSONObject("maneuver")

                                    val instruction = step.getString("name").takeIf { it.isNotEmpty() }
                                        ?: maneuverObj.getString("type")

                                    val stepDistance = step.getDouble("distance")
                                    val stepDuration = step.getDouble("duration")
                                    val maneuverType = maneuverObj.getString("type")

                                    val startPoint = maneuverObj.getJSONArray("location")
                                    val stepStartLat = startPoint.getDouble(1)
                                    val stepStartLon = startPoint.getDouble(0)

                                    val stepGeom = step.optString("geometry", "")
                                    val stepPoints = if (stepGeom.isNotEmpty()) decodePolyline(stepGeom) else emptyList()
                                    val stepEndLat = stepPoints.lastOrNull()?.latitude ?: stepStartLat
                                    val stepEndLon = stepPoints.lastOrNull()?.longitude ?: stepStartLon

                                    val bearingAfter = maneuverObj.optDouble("bearing_after", -1.0)

                                    val tempStep = DirectionStep(
                                        instruction = "",
                                        distance    = formatDistance(stepDistance),
                                        duration    = formatDuration(stepDuration),
                                        maneuver    = maneuverType,
                                        roadName    = instruction,
                                        startLat    = stepStartLat,
                                        startLon    = stepStartLon,
                                        endLat      = stepEndLat,
                                        endLon      = stepEndLon,
                                        bearingDegrees = bearingAfter
                                    )

                                    stepsList.add(tempStep.copy(
                                        instruction = buildStepInstruction(maneuverType, instruction, tempStep)
                                    ))
                                }
                            }

                            runOnUiThread {
                                dismissLoadingDialog()
                                directionSteps.clear()
                                directionSteps.addAll(stepsList)

                                // Vẽ route lên bản đồ
                                drawRoute(geometry)


                                startRealTimeNavigation(stepsList)
                            }
                        }
                    } else {
                        runOnUiThread {
                            dismissLoadingDialog()
                            Toast.makeText(this, "Không thể tìm đường đi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        dismissLoadingDialog()
                        Toast.makeText(this, "Lỗi server: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    dismissLoadingDialog()
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }



    // Thêm marker điểm đầu và điểm cuối
    private fun addStartFinishMarkers(points: List<GeoPoint>) {
        // Xóa marker cũ
        searchMarker?.let { map.overlays.remove(it) }

        if (points.isNotEmpty()) {
            // Marker điểm bắt đầu
            val startMarker = Marker(map).apply {
                position = points.first()
                title = "Điểm xuất phát"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_start)
            }
            map.overlays.add(startMarker)

            // Marker điểm kết thúc
            val endMarker = Marker(map).apply {
                position = points.last()
                title = currentPlace?.name ?: "Điểm đến"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_finish)
            }
            map.overlays.add(endMarker)

            parkingMarkers.add(startMarker)
            parkingMarkers.add(endMarker)
        }
    }



    private fun showNextNavigationStep(steps: List<DirectionStep>, index: Int) {
        AlertDialog.Builder(this)
            .setTitle("🚗 Bước ${index + 1}/${steps.size}")
            .setMessage("${steps[index].instruction}\n\n📏 ${steps[index].distance} • ⏱ ${steps[index].duration}")
            .setPositiveButton("Tiếp theo") { _, _ ->
                if (index + 1 < steps.size) {
                    showNextNavigationStep(steps, index + 1)
                } else {
                    Toast.makeText(this, "Đã đến nơi!", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Đóng") { _, _ -> }
            .setNeutralButton("Xem bản đồ") { _, _ ->
                val currentStep = steps[index]
                map.controller.animateTo(GeoPoint(currentStep.startLat, currentStep.startLon))
                map.controller.setZoom(18.0)
            }
            .show()
    }


    // Thêm loading dialog
    private var loadingDialog: AlertDialog? = null

    private fun showLoadingDialog(message: String) {
        loadingDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun startBookingTimerWithPaymentButton(duration: Long, stationName: String, slot: ChargingSlot) {
        bookingCountDownTimer?.cancel()

        // ← XÓA nút cũ nếu còn tồn tại (tránh addView trùng)
        val bottomSheetLayout = bottomSheet as LinearLayout
        val oldBtn = bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")
        if (oldBtn != null) bottomSheetLayout.removeView(oldBtn)

        // Tạo nút thanh toán
        val btnPayNow = Button(this).apply {
            tag = "btn_pay_now"   // ← PHẢI có tag để tìm và xóa sau này
            text = "Tôi đã đến trạm - Thanh toán"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6A1B9A"))
            setPadding(48, 24, 48, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 16, 32, 8) }
        }

        bottomSheetLayout.addView(btnPayNow)

        btnPayNow.setOnClickListener {
            showBookingPayment(stationName)
        }

        // ← Thu nhỏ bottom sheet về COLLAPSED thay vì để EXPANDED
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bookingCountDownTimer = object : android.os.CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvPlaceRating.text = "⏳ Giữ chỗ tại $stationName: ${String.format("%02d:%02d", minutes, seconds)}"
                tvPlaceRating.setTextColor(Color.RED)
            }

            override fun onFinish() {
                tvPlaceRating.text = "ĐÃ HẾT HẠN GIỮ CHỖ"
                tvPlaceRating.setTextColor(Color.GRAY)

                // ← Xóa nút bằng tag thay vì giữ reference (an toàn hơn)
                val btn = bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")
                if (btn != null) bottomSheetLayout.removeView(btn)

                if (currentBookingId != -1) {
                    lifecycleScope.launch {
                        try {
                            RetrofitClient.instance.updateBookingStatus(currentBookingId, "Cancelled")
                            runOnUiThread {
                                loadChargingStationsFromDB()
                                displayParkingLots()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Đã tự động hủy giữ chỗ!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentBookingId = -1
                                bottomSheetBehavior.isHideable = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TIMER_ERROR", "Lỗi: ${e.message}")
                        }
                    }
                }
            }
        }.start()
    }

    private fun setupMap() {
        // 1. Thiết lập nguồn bản đồ và các điều khiển cơ bản
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false) // Tắt nút +/- mặc định để dùng cử chỉ tay

        // 2. Cấu hình giới hạn Zoom để tránh người dùng zoom quá xa hoặc quá gần
        map.minZoomLevel = 4.0
        map.maxZoomLevel = 20.0
        map.controller.setZoom(15.0)

        // 3. Xử lý Giao diện tối (Dark Mode) cho các tấm bản đồ (Tiles)
        if (isDarkMode()) {
            // Sử dụng bộ lọc đảo ngược màu để biến bản đồ trắng thành đen
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }

        // 4. Thiết lập vị trí trung tâm mặc định (ĐH Mỏ Địa chất/Cổ Nhuế)
        val startPoint = GeoPoint(21.0717, 105.7672)
        map.controller.setCenter(startPoint)


        // 5. Tùy chọn: Chặn bản đồ bị xoay (giúp người dùng đỡ rối khi tìm đường)
        map.setMapOrientation(0f, false)
    }

    // Hàm bổ trợ kiểm tra chế độ tối của hệ thống
    private fun isDarkMode(): Boolean {
        val darkModeFlag = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return darkModeFlag == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun displayParkingLots(list: List<ParkingLot>? = null) {
        runOnUiThread {
            val dataToShow = list ?: parkingLots
            if (map == null) return@runOnUiThread

            try {
                parkingMarkers.forEach { map.overlays.remove(it) }
                parkingMarkers.clear()

                dataToShow.forEach { parking ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(parking.lat, parking.lon)
                        title = parking.name

                        val chargingInfo = if (parking.hasChargingStation) {
                            if (parking.realAvailableTimeSlots <= 0) "⚡ Hết khung giờ trống"
                            else "⚡ Còn ${parking.realAvailableTimeSlots} khung giờ trống"
                        } else {
                            "🅿 Bãi đỗ xe"
                        }

                        snippet = "$chargingInfo\n${parking.address}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // --- PHẦN THAY ĐỔI TẠI ĐÂY ---
                        icon = when {
                            // 1. Nếu là trạm sạc xe điện, dùng icon packing.png bạn đã tải
                            parking.hasChargingStation -> {
                                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_electric_car)
                            }
                            // 2. Nếu là bãi đỗ xe đặc biệt (Ví dụ ĐH Mỏ Địa chất)
                            parking.name.contains("ĐH Mỏ Địa chất", ignoreCase = true) || parking.isNearest -> {
                                ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_star_big_on)
                            }
                            // 3. Nếu bãi đỗ xe thường hết chỗ
                            parking.availableSpots <= 0 -> {
                                ContextCompat.getDrawable(this@MainActivity, R.drawable.warn)
                            }
                            // 4. Bãi đỗ xe thường còn chỗ
                            else -> {
                                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_parking) // Hoặc giữ ic_menu_mylocation
                            }
                        }

                        setOnMarkerClickListener { _, _ ->
                            showParkingDetails(parking)
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                            showInfoWindow()
                            true
                        }
                    }
                    parkingMarkers.add(marker)
                    map.overlays.add(marker)
                }
                map.invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showParkingDetails(parking: ParkingLot) {
        // Lưu thông tin địa điểm hiện tại
        currentPlace = PlaceInfo(
            parking.id,
            parking.lat,
            parking.lon,
            parking.name,
            parking.address,
            if (parking.hasChargingStation) "Trạm sạc xe điện" else "Bãi đỗ xe"
        )

        // 1. Cập nhật UI cơ bản
        tvPlaceName.text = parking.name + (if (parking.isNearest) " ⚡ GẦN NHẤT" else "")
        tvPlaceAddress.text = parking.address

        // 2. Cập nhật trạng thái chỗ sạc/đỗ và Live Occupancy
        if (parking.hasChargingStation) {
            val totalTimeSlots = parking.totalChargingSpots * 12
            val bookedTimeSlots = totalTimeSlots - parking.realAvailableTimeSlots
            tvPlaceRating.text = "⚡ Còn ${parking.realAvailableTimeSlots}/$totalTimeSlots khung giờ trống"
            tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            tvPlaceCategory.text = "Trạm sạc xe điện"
            (tvLiveOccupancy.parent as? View)?.visibility = View.VISIBLE
            tvLiveOccupancy.text = "Đã đặt $bookedTimeSlots/$totalTimeSlots khung giờ"
        }

        // 3. XỬ LÝ KHOẢNG CÁCH (PHẦN QUAN TRỌNG NHẤT)
        myLocationOverlay?.myLocation?.let { myLoc ->
            // Bước A: Tính tạm đường chim bay (12.8km) để hiện ngay lập tức tránh để trống UI
            val chimBay = calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
            tvPlaceDistance.text = "Đang tính lộ trình... (~${formatDistance(chimBay)})"

            // Bước B: Gọi API OSRM để lấy khoảng cách lái xe thực tế (~23km)
            // Hàm này sẽ tự động ghi đè lên tvPlaceDistance khi có kết quả
            updateActualDrivingDistance(parking)

        } ?: run {
            tvPlaceDistance.text = "Bật GPS để xem khoảng cách"
        }

        // 4. XỬ LÝ CÁC NÚT ĐẶT CHỖ
        val btnBookNow = findViewById<Button>(R.id.btn_book_parking)
        btnBookNow.setOnClickListener {
            if (parking.realAvailableTimeSlots <= 0) {
                Toast.makeText(this, "Rất tiếc, trạm này hiện đã hết chỗ sạc!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Mở dialog chọn ô, sau khi chọn ô sẽ đặt ngay (không chọn giờ)
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.instance.getSlots(parking.id)
                    if (response.isSuccessful) {
                        val realSlots = response.body() ?: emptyList()
                        if (realSlots.isNotEmpty()) {
                            showSlotSelectionForQuickBooking(realSlots, parking)
                        } else {
                            Toast.makeText(this@MainActivity, "Trạm này chưa có dữ liệu ô sạc!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Không thể tải sơ đồ ô sạc!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnBookLater = findViewById<Button>(R.id.btn_book_parking_later)
        btnBookLater.setOnClickListener {
            if (parking.hasChargingStation) {
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.getSlots(parking.id)
                        if (response.isSuccessful) {
                            val realSlots = response.body() ?: emptyList()
                            if (realSlots.isNotEmpty()) {
                                showSlotSelectionDialog(realSlots)
                            } else {
                                Toast.makeText(this@MainActivity, "Trạm này chưa có dữ liệu ô sạc!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Không thể tải sơ đồ ô sạc!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Bãi đỗ này không hỗ trợ đặt chỗ trước trực tuyến!", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset nút về trạng thái ban đầu mỗi khi chọn trạm mới
        btnDirectionsBottom.visibility = View.VISIBLE
        btnDetailedDirections.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private var selectedHour: Int = -1
    private var selectedBookingTimeMillis: Long = 0L

    private fun showTimeSlotDialog(slot: ChargingSlot) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val tvInfo     = dialogView.findViewById<TextView>(R.id.tv_selected_slot_info)
        val rvTime     = dialogView.findViewById<RecyclerView>(R.id.rv_time_slots)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm_time)

        tvInfo.text = "Ô đã chọn: ${slot.slot_code} — Chọn giờ bắt đầu sạc (mỗi lần sạc 2 tiếng)"
        selectedHour = -1
        btnConfirm.visibility = View.GONE

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        lifecycleScope.launch {
            val bookedHours = fetchBookedHours(currentPlace?.id ?: 1, slot.id)
            
            // Tạo 12 khung giờ chẵn
            val timeSlots = (0..22 step 2).map { h ->
                // CHỈ CẦN KIỂM TRA h (start_hour)
                val isBooked = h in bookedHours  // ← SỬA: bỏ kiểm tra h+1
                TimeSlot(
                    hour = h,
                    label = String.format("%02d:00 – %02d:00", h, h + 2),
                    isBooked = isBooked
                )
            }

            runOnUiThread {
                rvTime.layoutManager = GridLayoutManager(this@MainActivity, 3)
                rvTime.adapter = TimeSlotAdapter(timeSlots) { chosen ->
                    selectedHour = chosen.hour
                    btnConfirm.visibility = View.VISIBLE
                    btnConfirm.text = "ĐẶT Ô ${slot.slot_code}: ${chosen.label}"
                }
            }
        }

        btnConfirm.setOnClickListener {
            if (selectedHour == -1) {
                Toast.makeText(this, "Vui lòng chọn khung giờ!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            selectedBookingTimeMillis = cal.timeInMillis
            dialog.dismiss()
            guilenServerDatCho(slot)
        }

        dialog.show()
    }

    private fun confirmQuickBookingWithHour(slot: ChargingSlot, parking: ParkingLot) {
        // Tính thời gian di chuyển ước tính
        val myLoc = myLocationOverlay?.myLocation
        val estimatedMinutes = if (myLoc != null) {
            val distanceMeters = calculateDistance(
                myLoc.latitude, myLoc.longitude, parking.lat, parking.lon
            )
            // ~30km/h trong thành phố + 5 phút buffer, tối thiểu 5 phút
            ((distanceMeters / 30000.0 * 60).toInt() + 5).coerceAtLeast(5)
        } else {
            15 // mặc định 15 phút nếu không có GPS
        }

        val endHour = selectedHour + 2
        val timeLabel = String.format("%02d:00 – %02d:00", selectedHour, endHour)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận đặt ngay")
            .setMessage(
                "Trạm: ${parking.name}\n" +
                        "Ô sạc: ${slot.slot_code}\n" +
                        "Khung giờ: $timeLabel\n\n" +
                        "Bạn có $estimatedMinutes phút để đến trạm.\n" +
                        "Thanh toán trực tiếp khi đến nơi.\n\n" +
                        "Nếu không đến kịp, chỗ sẽ tự động hủy."
            )
            .setPositiveButton("Xuất phát!") { _, _ ->
                val userEmail = getLoggedInUserEmail()

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.createBooking(
                            userId        = userEmail,
                            stationId     = parking.id,
                            slotId        = slot.id,
                            status        = "Quick_Booking",
                            scheduledHour = selectedHour,
                            //scheduledHour = -1
                        )

                        if (response.isSuccessful) {
                            val body = response.body()
                            currentBookingId = body?.id ?: -1

                            loadChargingStationsFromDB()

                            runOnUiThread {
                                btnBookParking.visibility = View.GONE
                                btnBookParkingLater.visibility = View.GONE
                                bottomSheetBehavior.isHideable = false
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                                // Bắt đầu đếm ngược đến trạm, khi đến bấm nút mới hiện QR
                                startArrivalCountdown(
                                    durationMs = estimatedMinutes * 60 * 1000L,
                                    parking    = parking,
                                    slotCode   = slot.slot_code
                                )

                                // Vẽ đường đi trên bản đồ
                                showDirections()

                                Toast.makeText(
                                    this@MainActivity,
                                    "⚡ Đã giữ ô ${slot.slot_code}! Hãy đến trạm trong $estimatedMinutes phút.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Thất bại! Trạm có thể đã hết chỗ.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("QUICK_BOOK", "Lỗi: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    private fun showTimeSlotDialogForQuickBooking(slot: ChargingSlot, parking: ParkingLot) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val tvInfo     = dialogView.findViewById<TextView>(R.id.tv_selected_slot_info)
        val rvTime     = dialogView.findViewById<RecyclerView>(R.id.rv_time_slots)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm_time)

        tvInfo.text = "Ô: ${slot.slot_code} — Chọn khung giờ bắt đầu sạc"
        selectedHour = -1
        btnConfirm.visibility = View.GONE

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        lifecycleScope.launch {
            val bookedHours = fetchBookedHours(parking.id, slot.id)
            val timeSlots = (0..22 step 2).map { h ->
                TimeSlot(
                    hour     = h,
                    label    = String.format("%02d:00 – %02d:00", h, h + 2),
                    isBooked = h in bookedHours  // ← ĐÃ SỬA: bỏ kiểm tra h+1
                )
            }
            runOnUiThread {
                rvTime.layoutManager = GridLayoutManager(this@MainActivity, 3)
                rvTime.adapter = TimeSlotAdapter(timeSlots) { chosen ->
                    selectedHour = chosen.hour
                    btnConfirm.visibility = View.VISIBLE
                    btnConfirm.text = "ĐẶT NGAY ô ${slot.slot_code}: ${chosen.label}"
                }
            }
        }

        btnConfirm.setOnClickListener {
            if (selectedHour == -1) {
                Toast.makeText(this, "Vui lòng chọn khung giờ!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            Handler(Looper.getMainLooper()).postDelayed({
                confirmQuickBookingWithHour(slot, parking)
            }, 200)
        }

        dialog.show()
    }

    private fun showSlotSelectionForQuickBooking(slots: List<ChargingSlot>, parking: ParkingLot) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_select_slot, null)
        dialog.setContentView(view)

        val rvSlots = view.findViewById<RecyclerView>(R.id.rv_slots)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm_slot)

        // Đổi tiêu đề cho rõ là đặt ngay
        //view.findViewById<TextView?>(R.id.tv_slot_dialog_title)?.text = "⚡ Chọn ô sạc — Đặt ngay"

        var selectedSlot: ChargingSlot? = null

        rvSlots.layoutManager = GridLayoutManager(this, 5)
        rvSlots.adapter = SlotAdapter(slots) { slot ->
            selectedSlot = slot
            currentSelectedSlotId = slot.id
            currentSelectedSlotCode = slot.slot_code
            btnConfirm.visibility = View.VISIBLE
            btnConfirm.text = "Chọn giờ cho ô ${slot.slot_code} →"
        }

        btnConfirm.setOnClickListener {
            selectedSlot?.let { slot ->
                currentSelectedSlotId = slot.id
                currentSelectedSlotCode = slot.slot_code
                dialog.dismiss()
                // Sau khi chọn ô → mở chọn giờ (luồng đặt ngay)
                Handler(Looper.getMainLooper()).postDelayed({
                    showTimeSlotDialogForQuickBooking(slot, parking)
                }, 200)
            }
        }

        dialog.show()
    }

    private suspend fun fetchBookedHours(stationId: Int, slotId: Int): Set<Int> {
        return try {
            val response = RetrofitClient.instance.getBookedHours(stationId, slotId)
            if (response.isSuccessful) {
                val data = response.body() ?: emptyList()
                android.util.Log.d("BOOKED_HOURS", "Station=$stationId, Slot=$slotId → Booked: $data")
                data.toSet()
            } else {
                android.util.Log.e("BOOKED_HOURS", "HTTP ${response.code()}")
                emptySet()
            }
        } catch (e: Exception) {
            android.util.Log.e("BOOKED_HOURS", "Exception: ${e.message}")
            emptySet()
        }
    }

    private fun getLoggedInUserEmail(): String {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("user_email", "guest@example.com") ?: "guest@example.com"
    }

    private fun startArrivalCountdown(durationMs: Long, parking: ParkingLot, slotCode: String) {
        bookingCountDownTimer?.cancel()

        // Xóa nút cũ nếu có
        val bottomSheetLayout = bottomSheet as LinearLayout
        bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")?.let {
            bottomSheetLayout.removeView(it)
        }

        val btnArrived = Button(this).apply {
            tag = "btn_pay_now"
            text = "Thanh toán ngay"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            // Gradient tím giống nút đặt ngay/đặt trước
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.gradient_green_bg)
            // Bo góc 28dp + elevation giống 2 nút kia
            elevation = 4f * resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(
                    (16 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
            }
        }
        bottomSheetLayout.addView(btnArrived)

        btnArrived.setOnClickListener {
            // Người dùng đã đến → hủy đếm ngược và mở QR thanh toán
            bookingCountDownTimer?.cancel()
            showBookingPayment(parking.name)
        }

        // Đếm ngược thời gian di chuyển
        bookingCountDownTimer = object : android.os.CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvPlaceRating.text = "🚗 Đến trạm trong: ${String.format("%02d:%02d", minutes, seconds)}"
                tvPlaceRating.setTextColor(Color.parseColor("#1565C0"))
            }

            override fun onFinish() {
                btnBookParking.visibility = View.VISIBLE
                btnBookParkingLater.visibility = View.VISIBLE
                tvPlaceRating.text = "Hết thời gian di chuyển"
                tvPlaceRating.setTextColor(Color.RED)

                bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")?.let {
                    bottomSheetLayout.removeView(it)
                }

                if (currentBookingId != -1) {
                    lifecycleScope.launch {
                        try {
                            RetrofitClient.instance.updateBookingStatus(currentBookingId, "Cancelled")
                            runOnUiThread {
                                loadChargingStationsFromDB()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Đã hủy giữ chỗ vì không đến kịp!",
                                    Toast.LENGTH_LONG
                                ).show()
                                currentBookingId = -1
                                bottomSheetBehavior.isHideable = true
                                tvPlaceRating.text = "⚡ Trạm sạc"
                                tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TIMER_ERROR", "Lỗi hủy: ${e.message}")
                        }
                    }
                }
            }
        }.start()
    }

    // Hàm phụ: Xác nhận đặt ngay và cảnh báo 10 phút
    private fun confirmQuickBooking(parking: ParkingLot) {
        // Tính thời gian di chuyển dự kiến (lấy từ OSRM)
        val myLoc = myLocationOverlay?.myLocation
        if (myLoc == null) {
            Toast.makeText(this, "Bật GPS để sử dụng tính năng này!", Toast.LENGTH_SHORT).show()
            return
        }

        // Tính khoảng cách chim bay để ước tính thời gian
        val distanceMeters = calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
        // Ước tính: tốc độ trung bình 30km/h trong thành phố → 1m ≈ 0.12 giây
        val estimatedSeconds = (distanceMeters * 0.12).toLong().coerceAtLeast(120) // tối thiểu 2 phút
        val estimatedMinutes = (estimatedSeconds / 60).toInt() + 5 // + 5 phút buffer
        val totalSeconds = estimatedMinutes * 60L

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận Đặt chỗ sạc ngay")
            .setMessage(
                "Hệ thống sẽ giữ chỗ tại '${parking.name}'.\n\n" +
                        "⏱ Bạn có khoảng $estimatedMinutes phút để đến trạm.\n" +
                        "Sau khi đến, bạn sẽ thanh toán trực tiếp tại trạm.\n\n" +
                        "Nếu không đến kịp, chỗ sẽ bị hủy tự động."
            )
            .setPositiveButton("Đồng ý, xuất phát!") { _, _ ->
                val userEmail = getLoggedInUserEmail()

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.createBooking(
                            userId = userEmail,
                            stationId = parking.id,
                            slotId = currentSelectedSlotId,
                            status = "Quick_Booking",
                            scheduledHour = -1,  // đặt ngay, không có giờ cụ thể
                            holdMinutes = estimatedMinutes  // server giữ chỗ đủ thời gian di chuyển
                        )

                        if (response.isSuccessful) {
                            val body = response.body()
                            currentBookingId = body?.id ?: -1

                            runOnUiThread {
                                bottomSheetBehavior.isHideable = false
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                                // ĐẶT NGAY → hiện đếm ngược di chuyển, chưa hiện QR
                                startArrivalCountdown(
                                    durationMs  = totalSeconds * 1000L,
                                    parking     = parking,
                                    slotCode    = currentSelectedSlotCode
                                )

                                // Chỉ đường
                                showDirections()
                            }

                            loadChargingStationsFromDB()

                        } else {
                            runOnUiThread {
                                val serverMsg = try {
                                    JSONObject(response.errorBody()?.string() ?: "").optString("error")
                                } catch (e: Exception) { "" }
                                Toast.makeText(
                                    this@MainActivity,
                                    if (serverMsg.isNotBlank()) serverMsg else "Thất bại: Trạm có thể đã hết chỗ!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("API_ERROR", "Error: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // 4. BỔ SUNG HÀM ĐẾM NGƯỢC
    private var bookingCountDownTimer: android.os.CountDownTimer? = null

    private fun startBookingTimer(duration: Long, stationName: String) {
    bookingCountDownTimer?.cancel()

    bookingCountDownTimer = object : android.os.CountDownTimer(duration, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val minutes = (millisUntilFinished / 1000) / 60
            val seconds = (millisUntilFinished / 1000) % 60
            tvPlaceRating.text = "⏳ Giữ chỗ tại $stationName: ${String.format("%02d:%02d", minutes, seconds)}"
            tvPlaceRating.setTextColor(android.graphics.Color.RED)
        }

        override fun onFinish() {
            tvPlaceRating.text = "ĐÃ HẾT HẠN GIỮ CHỖ"
            tvPlaceRating.setTextColor(android.graphics.Color.GRAY)

            if (currentBookingId != -1) {
                lifecycleScope.launch {
                    try {
                        RetrofitClient.instance.updateBookingStatus(currentBookingId, "Cancelled")
                        loadChargingStationsFromDB()
                        displayParkingLots()
                        Toast.makeText(this@MainActivity, "Đã tự động hủy giữ chỗ!", Toast.LENGTH_SHORT).show()
                        currentBookingId = -1
                    } catch (e: Exception) {
                        android.util.Log.e("TIMER_ERROR", "Lỗi: ${e.message}")
                    }
                }
            }

            val btnBookNow = findViewById<Button>(R.id.btn_book_parking)
            btnBookNow.text = "Đặt lại ngay"
            btnBookNow.isEnabled = true
        }
    }.start()
}

    private fun findNearestChargingStation() {
        myLocationOverlay?.myLocation?.let { myLoc ->
            val chargingStations = parkingLots.filter { 
                it.hasChargingStation && it.availableChargingSpots > 0 
            }
            
            if (chargingStations.isEmpty()) {
                Toast.makeText(this, "Không có trạm sạc nào còn chỗ trống", Toast.LENGTH_LONG).show()
                
                // Hiển thị tất cả trạm sạc (kể cả đầy)
                showAllChargingStations()
                return
            }
            
            val nearestStation = chargingStations.minByOrNull { station ->
                calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
            }
            
            nearestStation?.let { station ->
                val distance = calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
                
                currentNearestChargingStation = station
                
                Toast.makeText(this,
                    "⚡ TRẠM SẠC GẦN NHẤT\n" +
                    "${station.name}\n" +
                    "Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ sạc\n" +
                    "Cách ${formatDistance(distance)}",
                    Toast.LENGTH_LONG).show()
                
                // Zoom đến trạm sạc
                map.controller.animateTo(GeoPoint(station.lat, station.lon))
                map.controller.setZoom(16.0)
                
                showParkingDetails(station)
            }
        } ?: Toast.makeText(this, "Bật GPS để tìm trạm sạc", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAllChargingStations() {
        myLocationOverlay?.myLocation?.let { myLoc ->
            val allStations = parkingLots
                .filter { it.hasChargingStation }
                .map { station ->
                    val distance = calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
                    Triple(station, distance, station.availableChargingSpots > 0)
                }
                .sortedBy { it.second }
            
            val stationList = allStations.mapIndexed { index, (station, distance, hasSpots) ->
                val status = if (hasSpots) {
                    "Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ"
                } else {
                    "Đã đầy (0/${station.totalChargingSpots})"
                }
                "${index + 1}. ${station.name}\n" +
                "   $status • Cách ${formatDistance(distance)}"
            }.joinToString("\n\n")
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("TẤT CẢ TRẠM SẠC XE ĐIỆN")
                .setMessage(stationList)
                .setPositiveButton("Đóng", null)
                .show()
                
        } ?: Toast.makeText(this, "Bật GPS để xem danh sách", Toast.LENGTH_SHORT).show()
    }


    private fun setupMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val provider = GpsMyLocationProvider(this)
        myLocationOverlay = MyLocationNewOverlay(provider, map)
        myLocationOverlay?.enableMyLocation()

        val userIcon = ContextCompat.getDrawable(this, R.drawable.location_person)
        if (userIcon is android.graphics.drawable.BitmapDrawable) {
            myLocationOverlay?.setPersonIcon(userIcon.bitmap)
            myLocationOverlay?.setDirectionIcon(userIcon.bitmap)
        }

        // Khi bắt được vị trí lần đầu tiên
        myLocationOverlay?.runOnFirstFix {
            runOnUiThread {
                zoomToMyLocation(false)
                // Cập nhật lại danh sách phường dựa trên vị trí mới bắt được
                updateWardChips()
            }
        }
        map.overlays.add(myLocationOverlay)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_SEARCH_REQUEST_CODE && resultCode == android.app.Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS
            )

            val spokenText = results?.firstOrNull()?.trim()

            if (!spokenText.isNullOrEmpty()) {
                searchBox.setText(spokenText.toString())
                searchLocation(spokenText.toString())
                hideKeyboard()
                Toast.makeText(this, "Đang tìm: \"$spokenText\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không nhận ra giọng nói, hãy thử lại!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun startVoiceSearch() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Nói tên trạm sạc cần tìm...")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            startActivityForResult(intent, VOICE_SEARCH_REQUEST_CODE)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận dạng giọng nói!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        btnMyLocation.setOnClickListener { zoomToMyLocation(true) }

        btnLayers.setOnClickListener {
            showLayerOptions()
        }

        btnVoice.setOnClickListener {
            startVoiceSearch()

        }

        btnDirections.setOnClickListener {
            showDetailedDirections()  // Gọi hàm chỉ đường chi tiết
        }

        val btnThemeToggle = findViewById<ImageButton>(R.id.btn_theme_toggle)

// Cập nhật icon theo chế độ hiện tại ngay khi khởi động
        btnThemeToggle.setImageResource(
            if (isDarkMode()) R.drawable.ic_sun else R.drawable.ic_moon
        )

        btnThemeToggle.setOnClickListener {
            if (isDarkMode()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun showUserProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_profile, null)
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("user_email", "Chưa đăng nhập") ?: "Chưa đăng nhập"

        // Hiển thị email
        dialogView.findViewById<TextView>(R.id.tv_profile_email).text = userEmail

        // Tải thống kê từ API
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserBookingStats(userEmail)
                if (response.isSuccessful) {
                    val stats = response.body()
                    runOnUiThread {
                        dialogView.findViewById<TextView>(R.id.tv_stat_total).text =
                            "${stats?.total ?: 0}"
                        dialogView.findViewById<TextView>(R.id.tv_stat_completed).text =
                            "${stats?.completed ?: 0}"
                        dialogView.findViewById<TextView>(R.id.tv_stat_cancelled).text =
                            "${stats?.cancelled ?: 0}"
                    }
                }
            } catch (e: Exception) {
                // Nếu chưa có API stats, tính local từ bookingHistory
                runOnUiThread {
                    dialogView.findViewById<TextView>(R.id.tv_stat_total).text = "—"
                    dialogView.findViewById<TextView>(R.id.tv_stat_completed).text = "—"
                    dialogView.findViewById<TextView>(R.id.tv_stat_cancelled).text = "—"
                }
            }
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        // Lịch sử đặt chỗ
        dialogView.findViewById<LinearLayout>(R.id.btn_booking_history).setOnClickListener {
            dialog.dismiss()
            showBookingHistory(userEmail)
        }

        // Trạm yêu thích (tính năng tương lai)
        dialogView.findViewById<LinearLayout>(R.id.btn_favorite_stations).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Tính năng đang phát triển!", Toast.LENGTH_SHORT).show()
        }

        // Đăng xuất
        dialogView.findViewById<LinearLayout>(R.id.btn_profile_logout).setOnClickListener {
            dialog.dismiss()
            showLogoutConfirmation()
        }

        dialog.show()
    }

    private fun showBookingHistory(userEmail: String) {

        lifecycleScope.launch {
            try {
                showLoadingDialog("Đang tải lịch sử...")
                val response = RetrofitClient.instance.getUserBookings(userEmail)
                dismissLoadingDialog()

                if (response.isSuccessful) {
                    val bookings = response.body() ?: emptyList()
                    runOnUiThread {
                        if (bookings.isEmpty()) {
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Lịch sử đặt chỗ")
                                .setMessage("Bạn chưa có lần đặt chỗ nào.")
                                .setPositiveButton("Đóng", null)
                                .show()
                            return@runOnUiThread
                        }

                        val historyText = bookings.take(10).joinToString("\n\n") { booking ->
                            val statusIcon = when (booking.status) {
                                "Confirmed" -> "✅"
                                "Cancelled" -> "❌"
                                "Completed" -> "🏁"
                                else -> "⏳"
                            }
                            "$statusIcon ${booking.station_name ?: "Trạm sạc"}\n" +
                                    "   Ô: ${booking.slot_code ?: "—"} | Giờ: ${
                                        if ((booking.scheduled_hour ?: -1) >= 0)
                                            "${booking.scheduled_hour}:00–${(booking.scheduled_hour ?: 0) + 2}:00"
                                        else "—"
                                    }\n" +
                                    "   ${booking.booking_time?.take(16)?.replace("T", " ") ?: ""}"
                        }

                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("📋 Lịch sử đặt chỗ (10 gần nhất)")
                            .setMessage(historyText)
                            .setPositiveButton("Đóng", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                dismissLoadingDialog()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Lỗi tải lịch sử: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun zoomToMyLocation(animate: Boolean) {
        myLocationOverlay?.myLocation?.let {
            if (animate) {
                map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            } else {
                map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            }
            map.controller.setZoom(17.0)
        } ?: run {
            Toast.makeText(this, "Đang tìm vị trí...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchWithAutocomplete() {
        placeAdapter = PlaceAdapter(this, suggestionsData)
        searchBox.setAdapter(placeAdapter)
        searchBox.threshold = 1
        searchBox.dropDownHeight = 1000

        // ✅ Dùng post{} để đo sau khi layout render xong
        searchBox.post {
            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels

            // Card margin 12dp mỗi bên = 24dp tổng
            val cardMargin = (24 * density).toInt()
            val cardWidth = screenWidth - cardMargin

            // Dropdown rộng bằng Card
            searchBox.dropDownWidth = cardWidth

            // Bù lại icon logo (44dp) + padding LinearLayout (2dp) + card margin trái (12dp)
            val offsetLeft = ((44 + 2 + 12) * density).toInt()
            searchBox.dropDownHorizontalOffset = -offsetLeft
            searchBox.dropDownVerticalOffset = 4
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                if (query.length >= 1) {
                    searchRunnable = Runnable { this@MainActivity.loadSuggestions(query) }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = searchBox.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                    searchBox.dismissDropDown()
                    hideKeyboard()
                    true
                } else false
            } else false
        }

        searchBox.setOnItemClickListener { _, _, position, _ ->
            val selected = placeAdapter.getItem(position) ?: return@setOnItemClickListener
            searchBox.setText(selected.name)
            showMarkerAtLocation(selected.lat, selected.lon, selected.name, selected.address)
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
    }

    private fun loadSuggestions(query: String) {
        val localMatches = parkingLots.filter { parking ->
            parking.name.contains(query, ignoreCase = true) ||
                    parking.address.contains(query, ignoreCase = true) ||
                    parking.ward.contains(query, ignoreCase = true)
        }.map { parking ->
            PlaceInfo(
                parking.id, parking.lat, parking.lon,
                parking.name + if (parking.hasChargingStation) " ⚡" else "",
                parking.address,
                if (parking.hasChargingStation) "Trạm sạc" else "Bãi đỗ xe"
            )
        }

        if (localMatches.isNotEmpty()) {
            runOnUiThread {
                suggestionsData.clear()
                suggestionsData.addAll(localMatches)
                placeAdapter.replaceAll(localMatches)   // ✅ Dùng placeAdapter
                if (searchBox.hasFocus()) searchBox.showDropDown()
            }
            return
        }

        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                var urlString = "https://nominatim.openstreetmap.org/search" +
                        "?format=json&q=$encoded&countrycodes=vn&limit=10&addressdetails=1&accept-language=vi"
                myLocationOverlay?.myLocation?.let { urlString += "&lat=${it.latitude}&lon=${it.longitude}" }

                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "CarParkingSmart/1.0")
                    connectTimeout = 8000; readTimeout = 8000
                }

                if (conn.responseCode == 200) {
                    val array    = JSONArray(conn.inputStream.bufferedReader().readText())
                    val tempList = mutableListOf<PlaceInfo>()
                    val seen     = mutableSetOf<String>()

                    for (i in 0 until array.length()) {
                        val obj         = array.getJSONObject(i)
                        val lat         = obj.getString("lat").toDouble()
                        val lon         = obj.getString("lon").toDouble()
                        val displayName = obj.getString("display_name")
                        val shortName   = if (obj.has("name") && obj.getString("name").isNotBlank())
                            obj.getString("name") else displayName.split(",")[0].trim()

                        if (shortName !in seen) {
                            seen.add(shortName)
                            tempList.add(PlaceInfo(0, lat, lon, shortName, displayName))
                        }
                    }

                    runOnUiThread {
                        suggestionsData.clear()
                        suggestionsData.addAll(tempList)
                        placeAdapter.replaceAll(tempList)   // ✅ Dùng placeAdapter
                        if (tempList.isNotEmpty() && searchBox.hasFocus()) searchBox.showDropDown()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun searchLocation(query: String) {
        // Kiểm tra xem có phải tìm trạm sạc không
        val isChargingStationSearch = query.contains("sạc", ignoreCase = true) || 
                                      query.contains("điện", ignoreCase = true) ||
                                      query.contains("charging", ignoreCase = true)
        
        // Tìm trong danh sách bãi đỗ xe trước
        val matchingParkingLots = parkingLots.filter { parking ->
            parking.name.contains(query, ignoreCase = true) ||
            parking.address.contains(query, ignoreCase = true)
        }

        // Tiếp tục hàm searchLocation...
        if (matchingParkingLots.isNotEmpty()) {
            val firstMatch = matchingParkingLots[0]
            val point = GeoPoint(firstMatch.lat, firstMatch.lon)
            map.controller.animateTo(point)
            map.controller.setZoom(18.0)
            showParkingDetails(firstMatch)
            Toast.makeText(this, "Đã tìm thấy: ${firstMatch.name}", Toast.LENGTH_SHORT).show()
        } else {
            // Nếu không có trong bãi đỗ thì tìm trên bản đồ chung (Nominatim)
            loadSuggestions(query)
            Toast.makeText(this, "Tìm kiếm trên bản đồ...", Toast.LENGTH_SHORT).show()
        }

        // Nếu tìm "trạm sạc" hoặc "sạc xe điện"
        if (isChargingStationSearch) {
            runOnUiThread {
                findNearestChargingStation()
            }
            return
        }
        
        // Không tìm thấy trong danh sách → tìm trên mạng
        Toast.makeText(this, "Đang tìm kiếm...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                var urlString = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&countrycodes=vn&limit=1&addressdetails=1&accept-language=vi"

                myLocationOverlay?.myLocation?.let { myLoc ->
                    urlString += "&lat=${myLoc.latitude}&lon=${myLoc.longitude}"
                }

                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "CarParkingSmart/1.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(json)

                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val lat = obj.getDouble("lat")
                        val lon = obj.getDouble("lon")
                        val displayName = obj.getString("display_name")
                        val name = if (obj.has("name") && obj.getString("name").isNotEmpty()) {
                            obj.getString("name")
                        } else {
                            query
                        }

                        runOnUiThread {
                            showMarkerAtLocation(lat, lon, name, displayName)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Không tìm thấy: $query", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Lỗi kết nối: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showMarkerAtLocation(lat: Double, lon: Double, title: String, address: String, category: String = "") {
        val point = GeoPoint(lat, lon)

        searchMarker?.let {
            map.overlays.remove(it)
            searchMarker = null
        }

        searchMarker = Marker(map).apply {
            position = point
            this.title = title.ifEmpty { "Vị trí tìm kiếm" }
            this.snippet = address
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            setOnMarkerClickListener { m, _ ->
                showPlaceDetails(
                    m.position.latitude,
                    m.position.longitude,
                    m.title,
                    m.snippet,
                    category
                )
                true
            }
        }

        map.overlays.add(searchMarker)
        map.controller.animateTo(point)
        map.controller.setZoom(17.0)
        map.invalidate()

        showPlaceDetails(lat, lon, title, address, category)
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // 1. Cấu hình mặc định
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 300
        bottomSheetBehavior.isHideable = true

        // 2. Callback xử lý logic khóa Card
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Nếu đang có giao dịch mà bị kéo ẩn (bằng tay), thì ép hiện lại
                if (currentBookingId != -1 && newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.isHideable = false
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // 3. Logic Nút X (Sửa lại logic 2 bước ẩn)
        btnCloseSheet.setOnClickListener {
            if (currentBookingId != -1) {
                Toast.makeText(this, "Đang có giao dịch đặt chỗ, chỉ có thể thu nhỏ!", Toast.LENGTH_SHORT).show()
                bottomSheetBehavior.isHideable = false
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return@setOnClickListener
            }

            // Trường hợp không có giao dịch
            bottomSheetBehavior.isHideable = true

            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    // Nếu đang mở to -> Thu nhỏ lại (Bước 1)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    // Nếu đang thu nhỏ -> Ấn lần nữa mới ẩn hẳn (Bước 2)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    clearMapOverlays() // Hàm dọn dẹp map (viết bên dưới)
                }
                else -> {
                    // Nếu lỡ đang ở trạng thái khác thì cứ ẩn đi
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        }

        btnDirectionsBottom.setOnClickListener {
            currentPlace?.let {
                // Vẽ route đơn giản lên bản đồ
                showDirections()
                // Ẩn nút này, hiện nút chi tiết
                btnDirectionsBottom.visibility = View.GONE
                btnDetailedDirections.visibility = View.VISIBLE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } ?: Toast.makeText(this, "Chưa chọn địa điểm đích", Toast.LENGTH_SHORT).show()
        }

        btnDetailedDirections.setOnClickListener {
            // Mở chỉ đường chi tiết với giọng nói
            showDetailedDirections()
        }

        btnSharePlace.setOnClickListener {
            currentPlace?.let { place ->
                val shareText = "${place.name}\n${place.address}\n\nhttps://www.google.com/maps?q=${place.lat},${place.lon}"
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ địa điểm"))
            }
        }

        btnNearby.setOnClickListener {
            currentPlace?.let { place ->
                map.controller.animateTo(GeoPoint(place.lat, place.lon))
                map.controller.setZoom(18.0)
                // Đảm bảo hiện lên nếu đang ẩn
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    // Hàm phụ để dọn dẹp bản đồ khi đóng hẳn
    private fun clearMapOverlays() {
        routeLine?.let { map.overlays.remove(it); routeLine = null }
        searchMarker?.let { map.overlays.remove(it); searchMarker = null }
        map.invalidate()
        currentPlace = null

        // Reset 2 nút về trạng thái ban đầu
        btnDirectionsBottom.visibility = View.VISIBLE
        btnDetailedDirections.visibility = View.GONE
    }

    private fun showPlaceDetails(lat: Double, lon: Double, name: String, address: String, category: String = "") {
        currentPlace = PlaceInfo(0, lat, lon, name, address, category)

        tvPlaceName.text = name
        tvPlaceAddress.text = address

        // 1. Hiển thị tạm thời khoảng cách đường thẳng (trong khi chờ API phản hồi)
        myLocationOverlay?.myLocation?.let { myLoc ->
            val chimBay = calculateDistance(myLoc.latitude, myLoc.longitude, lat, lon)
            tvPlaceDistance.text = "Đang tính lộ trình... (${formatDistance(chimBay)})"

            // 2. GỌI NGAY hàm tính đường đi thực tế (OSRM)
            val target = ParkingLot(0, name, "", lat, lon, 0, 0, address)
            updateActualDrivingDistance(target)
        } ?: run {
            tvPlaceDistance.text = "Vui lòng bật GPS"
        }

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    // ============================================================
// THAY THẾ hàm showBookingPayment() CŨ bằng hàm này
// ============================================================
    private fun showBookingPayment(placeName: String) {
        // Xóa nút "btn_pay_now" khỏi bottom sheet nếu còn
        val bottomSheetLayout = bottomSheet as LinearLayout
        bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")?.let {
            bottomSheetLayout.removeView(it)
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_methods, null)
        val tvTimer    = dialogView.findViewById<TextView>(R.id.tv_payment_timer)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btn_cancel_payment)
        val btnQR      = dialogView.findViewById<LinearLayout>(R.id.btn_pay_qr)
        val btnMomo    = dialogView.findViewById<LinearLayout>(R.id.btn_pay_momo)
        val btnZalo    = dialogView.findViewById<LinearLayout>(R.id.btn_pay_zalopay)
        val btnVnpay   = dialogView.findViewById<LinearLayout>(R.id.btn_pay_vnpay)

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)

        // ── Đếm ngược 10 phút ──────────────────────────────────
        bookingCountDownTimer?.cancel()
        bookingCountDownTimer = object : android.os.CountDownTimer(600_000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val s = millisUntilFinished / 1000
                tvTimer.text = "%02d:%02d".format(s / 60, s % 60)
                tvPlaceRating.text = "⏳ Chờ thanh toán: %02d:%02d".format(s / 60, s % 60)
                tvPlaceRating.setTextColor(android.graphics.Color.RED)
            }
            override fun onFinish() {
                if (dialog.isShowing) dialog.dismiss()
                stopAutoCheckPayment()
                handleBookingExpired()
            }
        }.start()

        // ── Bắt đầu polling ────────────────────────────────────
        stopAutoCheckPayment()
        startPollingPayment(currentBookingId, dialog)

        // ── Helpers mở app / deep-link ─────────────────────────
        // Giá trị mặc định; được thay bằng thông tin từ server ngay khi tải xong (cùng nguồn với web)
        var amount    = 100_000
        val content   = "DatCho_${currentBookingId}"
        var accountNo = "108876696755"
        var bankBin   = "970415"          // VietinBank
        val payBookingId = currentBookingId
        lifecycleScope.launch {
            try {
                val info = RetrofitClient.instance.getPaymentInfo(payBookingId)
                if (info.isSuccessful) {
                    info.body()?.let {
                        amount = it.amount
                        accountNo = it.account_no
                        bankBin = it.bank_bin
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PAYMENT_INFO", "Dùng thông tin thanh toán mặc định: ${e.message}")
            }
        }

        fun openUri(uri: String, fallbackMsg: String = "Bạn chưa cài app này!") {
            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(uri)
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(this, fallbackMsg, Toast.LENGTH_SHORT).show()
            }
        }

        btnQR.setOnClickListener {
            // Dùng VietQR universal link → mở được MoMo, ZaloPay, app ngân hàng bất kỳ
            // và tự điền sẵn: số tài khoản + ngân hàng + số tiền + nội dung
            val vietQRDeepLink = "https://dl.vietqr.io/pay" +
                    "?app=momo" +              // ← mở thẳng MoMo
                    "&ba=$accountNo" +         // số tài khoản nhận
                    "&bin=$bankBin" +          // BIN ngân hàng (970415 = VietinBank)
                    "&am=$amount" +            // số tiền
                    "&tn=$content"             // nội dung

            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(vietQRDeepLink)
                    )
                )
            } catch (e: Exception) {
                openUri(
                    "https://play.google.com/store/apps/details?id=com.mservice.momotransfer",
                    "Mở Play Store để tải MoMo"
                )
            }
        }

        btnMomo.setOnClickListener {
            // Format đúng của MoMo deep link để chuyển đến số điện thoại/tài khoản cụ thể
            val momoUri = "momo://app?action=payWithApp" +
                    "&isScanQR=false" +
                    "&phoneNumber=0123456789" +   // ← SĐT tài khoản MoMo nhận tiền (thay bằng số thật)
                    "&amount=$amount" +
                    "&description=$content" +
                    "&merchantname=TramSacThongMinh" +
                    "&merchantcode=TRAMSA001"

            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(momoUri)
                    )
                )
            } catch (e: Exception) {
                // MoMo chưa cài → mở Play Store
                openUri(
                    "https://play.google.com/store/apps/details?id=com.mservice.momotransfer",
                    "Mở Play Store để tải MoMo"
                )
            }
        }

        // ZaloPay deep-link
        btnZalo.setOnClickListener {
            val vietQRDeepLink = "https://dl.vietqr.io/pay" +
                    "?app=zalopay" +           // ← mở thẳng ZaloPay
                    "&ba=$accountNo" +
                    "&bin=$bankBin" +
                    "&am=$amount" +
                    "&tn=$content"

            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(vietQRDeepLink)
                    )
                )
            } catch (e: Exception) {
                openUri(
                    "https://play.google.com/store/apps/details?id=vn.com.vng.zalopay",
                    "Mở Play Store để tải ZaloPay"
                )
            }
        }

        // VNPay – VietQR universal link
        btnVnpay.setOnClickListener {
            val vietQR = "https://dl.vietqr.io/pay?app=vietqr" +
                    "&ba=$accountNo&bin=$bankBin&am=$amount&tn=$content"
            openUri(vietQR)
        }

        // Hủy
        btnCancel.setOnClickListener {
            bookingCountDownTimer?.cancel()
            stopAutoCheckPayment()
            dialog.dismiss()
            handleBookingExpired()
        }

        dialog.show()
    }

    // ============================================================
// THAY THẾ hàm startPollingPayment() – nhận BottomSheetDialog
// (Nếu đang dùng AlertDialog, đổi kiểu tham số cho khớp)
// ============================================================
    private fun startPollingPayment(
        bookingId: Int,
        dialog: com.google.android.material.bottomsheet.BottomSheetDialog
    ) {
        lateinit var checkRunnable: Runnable
        checkRunnable = Runnable {
            if (bookingId == -1) return@Runnable

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.instance.getBookingDetail(bookingId)
                    if (response.isSuccessful) {
                        val booking = response.body()
                        when (booking?.status) {
                            "Confirmed" -> {
                                subtractSlotFromServerBSD(bookingId, dialog)
                            }
                            "Cancelled" -> {
                                stopAutoCheckPayment()
                                runOnUiThread {
                                    if (dialog.isShowing) dialog.dismiss()
                                    Toast.makeText(this@MainActivity, "Đặt chỗ đã bị hủy.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> checkPaymentHandler.postDelayed(checkRunnable, 5000)
                        }
                    } else {
                        checkPaymentHandler.postDelayed(checkRunnable, 5000)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PAYMENT_CHECK", "Lỗi: ${e.message}")
                    checkPaymentHandler.postDelayed(checkRunnable, 5000)
                }
            }
        }
        checkPaymentHandler.post(checkRunnable)
    }

    // ============================================================
// Hàm phụ: trừ slot sau khi polling phát hiện đã thanh toán
// ============================================================
    private fun subtractSlotFromServerBSD(
        bookingId: Int,
        dialog: com.google.android.material.bottomsheet.BottomSheetDialog
    ) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.confirmBookingAndSubtractSlot(bookingId)
                runOnUiThread {
                    stopAutoCheckPayment()
                    bookingCountDownTimer?.cancel()
                    if (dialog.isShowing) dialog.dismiss()

                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Thanh toán thành công! Chỗ sạc đã được xác nhận.",
                            Toast.LENGTH_LONG
                        ).show()

                        currentPlace?.let { place ->
                            parkingLots.find { it.id == place.id }?.let { updated ->
                                updated.realAvailableTimeSlots -= 1
                                showParkingDetails(updated)
                            }
                        }

                        showAndSaveInvoice(
                            bookingId     = bookingId,
                            slotCode      = currentSelectedSlotCode,
                            stationName   = currentPlace?.name ?: "Trạm sạc",
                            scheduledHour = selectedHour
                        )
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Lỗi xác nhận thanh toán (${response.code()})",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    currentBookingId = -1
                    tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    bottomSheetBehavior.isHideable = true
                    loadChargingStationsFromDB()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    stopAutoCheckPayment()
                    if (dialog.isShowing) dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Lỗi kết nối khi xác nhận!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showBankChooserAndPay() {
        val amount = 100000
        val content = "DatCho_${currentBookingId}"
        val accountNo = "108876696755"
        val bankBin = "970415" // VietinBank

        // Danh sách app ngân hàng phổ biến + deep link
        val banks = arrayOf(
            "MoMo",
            "ZaloPay",
            "VietinBank iPay",
            "VCB Digibank",
            "Mở VietQR trên trình duyệt"
        )

        AlertDialog.Builder(this)
            .setTitle("Chọn app thanh toán")
            .setItems(banks) { _, which ->
                val uri = when (which) {
                    0 -> "momo://transfer?amount=$amount&memo=$content"
                    1 -> "zalopay://transfer?amount=$amount&description=$content"
                    2 -> "ipay://payment?account=$accountNo&amount=$amount&content=$content"
                    3 -> "vcbdigibank://transfer?account=$accountNo&amount=$amount&note=$content"
                    else -> "https://img.vietqr.io/image/ICB-$accountNo-compact.png?amount=$amount&addInfo=$content"
                }

                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                } catch (e: Exception) {
                    // App chưa cài → mở Store hoặc fallback web
                    Toast.makeText(this, "Bạn chưa cài app này!", Toast.LENGTH_SHORT).show()
                    openVietQRInBrowser(amount, content, accountNo, bankBin)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openVietQRInBrowser(amount: Int, content: String, accountNo: String, bankBin: String) {
        val url = "https://img.vietqr.io/image/ICB-$accountNo-compact.png" +
                "?amount=$amount&addInfo=$content"
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    private fun subtractSlotFromServer(
    bookingId: Int,
    dialog: androidx.appcompat.app.AlertDialog
    ) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.confirmBookingAndSubtractSlot(bookingId)
                runOnUiThread {
                    stopAutoCheckPayment()
                    bookingCountDownTimer?.cancel()
                    if (dialog.isShowing) dialog.dismiss()

                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@MainActivity,
                            "Thanh toán thành công! Đã trừ 1 chỗ sạc.",
                            Toast.LENGTH_LONG
                        ).show()

                        // THÊM: Cập nhật realAvailableTimeSlots
                        currentPlace?.let { place ->
                            val updatedStation = parkingLots.find { it.id == place.id }
                            updatedStation?.let { updated ->
                                updated.realAvailableTimeSlots -= 1
                                showParkingDetails(updated)
                            }
                        }

                        showAndSaveInvoice(
                            bookingId     = bookingId,
                            slotCode      = currentSelectedSlotCode,
                            stationName   = currentPlace?.name ?: "Trạm sạc",
                            scheduledHour = selectedHour
                        )
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Thanh toán ghi nhận nhưng không trừ được slot (${response.code()})",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    currentBookingId = -1
                    tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                    bottomSheetBehavior.isHideable = true
                    loadChargingStationsFromDB()
                }
            } catch (e: Exception) {
                android.util.Log.e("SLOT_ERROR", "Không trừ được slot: ${e.message}")
                runOnUiThread {
                    stopAutoCheckPayment()
                    if (dialog.isShowing) dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Lỗi kết nối khi trừ slot!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            loadChargingStationsFromDB()
        }, 500)
    }

    private fun stopAutoCheckPayment() {
        checkPaymentHandler.removeCallbacksAndMessages(null)
    }



    private fun handleBookingExpired() {
        if (currentBookingId == -1) return  // ← Thêm dòng này để tránh gọi 2 lần
        bookingCountDownTimer?.cancel()
        stopAutoCheckPayment()

        val expiredId = currentBookingId
        currentBookingId = -1  // ← Reset NGAY lập tức trước khi gọi API

        lifecycleScope.launch {
            try {
                RetrofitClient.instance.updateBookingStatus(expiredId, "Cancelled")
            } catch (e: Exception) {
                android.util.Log.e("API_ERROR", "Lỗi khi tự động hủy: ${e.message}")
            }
            runOnUiThread {
                val bottomSheetLayout = bottomSheet as LinearLayout
                val btnPayNow = bottomSheetLayout.findViewWithTag<Button>("btn_pay_now")
                if (btnPayNow != null) bottomSheetLayout.removeView(btnPayNow)
                loadChargingStationsFromDB()
                Toast.makeText(this@MainActivity,
                    "Hết thời gian thanh toán! Đặt chỗ đã tự động hủy.",
                    Toast.LENGTH_LONG).show()
                tvPlaceRating.text = "⭐ 4.8"
                tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                bottomSheetBehavior.isHideable = true
            }
        }
    }



    private fun startRealTimeNavigation(steps: List<DirectionStep>) {
        if (steps.isEmpty()) return
        isNavigating = true
        navHud.visibility = View.VISIBLE
        navArrivalBanner.visibility = View.GONE

        updateHudStep(0, steps[0], 0.0)
        tvNavTotalDistance.text = calculateTotalDistance(steps)
        tvNavStepCounter.text = "Bước 1/${steps.size}"

        navigationManager = NavigationManager(
            context = this,
            steps = steps,
            onStepChanged = { stepIndex, step, distToTurn ->
                runOnUiThread {
                    updateHudStep(stepIndex, step, distToTurn)
                    tvNavStepCounter.text = "Bước ${stepIndex + 1}/${steps.size}"
                }
            },
            onArrived = {
                runOnUiThread { showArrivalAnimation() }
            },
            onDistanceUpdate = { distToTurn, timeRemaining ->
                runOnUiThread {
                    if (distToTurn >= 1000) {
                        tvNavDistanceToTurn.text = "%.1f".format(distToTurn / 1000)
                        tvNavDistanceUnit.text = "km"
                    } else {
                        tvNavDistanceToTurn.text = distToTurn.toInt().toString()
                        tvNavDistanceUnit.text = "mét"
                    }
                    tvNavTimeRemaining.text = timeRemaining
                }
            }
        )
        navigationManager?.start()

        // ✅ Dùng fusedLocationClient thay vì GPS system
        startFusedLocationForNavigation()
    }

    @SuppressLint("MissingPermission")
    private fun startFusedLocationForNavigation() {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (isNavigating) {
                        navigationManager?.updateLocation(location.latitude, location.longitude)
                        // ✅ Thêm dòng này để camera theo vị trí + hướng đi
                        updateMapCameraForNavigation(
                            location.latitude,
                            location.longitude,
                            location.bearing  // bearing là góc hướng đi từ GPS
                        )
                    }
                    if (isNavigationActive) {
                        navigationEngine?.updateLocation(location)
                    }
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }
    private fun updateHudStep(stepIndex: Int, step: DirectionStep, distanceToTurn: Double) {
        tvNavManeuverIcon.text = step.maneuverIcon()
        tvNavInstruction.text  = step.instruction
        tvNavRoadName.text     = step.roadName
        if (distanceToTurn > 0) {
            if (distanceToTurn >= 1000) {
                tvNavDistanceToTurn.text = "%.1f".format(distanceToTurn / 1000)
                tvNavDistanceUnit.text = "km"
            } else {
                tvNavDistanceToTurn.text = distanceToTurn.toInt().toString()
                tvNavDistanceUnit.text = "mét"
            }
        }
        try {
            val cardView = navHud.getChildAt(0) as? com.google.android.material.card.MaterialCardView
            cardView?.setCardBackgroundColor(android.graphics.Color.parseColor(step.hudColor()))
        } catch (e: Exception) {}
    }

    private fun showArrivalAnimation() {
        navArrivalBanner.visibility = View.VISIBLE
        navHud.getChildAt(0)?.visibility = View.GONE
        navHud.getChildAt(1)?.visibility = View.GONE
        Handler(Looper.getMainLooper()).postDelayed({ stopNavigation() }, 4000)
        Toast.makeText(this, "Bạn đã đến trạm sạc!", Toast.LENGTH_LONG).show()
    }

    /*private fun stopNavigation() {
    // Dừng NavigationManager cũ (navHud)
        isNavigating = false
        navigationManager?.stop()
        navigationManager = null
        navHud.visibility = View.GONE
        navArrivalBanner.visibility = View.GONE
        navHud.getChildAt(0)?.visibility = View.VISIBLE
        navHud.getChildAt(1)?.visibility = View.VISIBLE
        stopHighFrequencyGPS()

        // Dừng NavigationEngine mới (navPanel)
        isNavigationActive = false
        navigationEngine?.stopNavigation()
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        if (::navPanel.isInitialized) navPanel.visibility = View.GONE

        Toast.makeText(this, "Đã dừng chỉ đường", Toast.LENGTH_SHORT).show()
    }*/

    private fun stopNavigation() {
        isNavigating = false
        navigationManager?.stop()
        navigationManager = null
        navHud.visibility = View.GONE
        navArrivalBanner.visibility = View.GONE

        // ✅ Reset bản đồ về hướng Bắc, zoom ra xa hơn
        map.mapOrientation = 0f
        map.controller.setZoom(15.0)

        isNavigationActive = false
        navigationEngine?.stopNavigation()
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        locationCallback = null
        if (::navPanel.isInitialized) navPanel.visibility = View.GONE

        Toast.makeText(this, "Đã dừng chỉ đường", Toast.LENGTH_SHORT).show()
    }


    private fun stopHighFrequencyGPS() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            lm.removeUpdates(locationListener)
        } catch (e: Exception) {}
    }

    private fun calculateTotalDistance(steps: List<DirectionStep>): String {
        var totalMeters = 0.0
        steps.forEach { step ->
            val dist = step.distance
            when {
                dist.contains("km") -> totalMeters += dist.replace("km","").trim().toDoubleOrNull()?.times(1000) ?: 0.0
                dist.contains("m")  -> totalMeters += dist.replace("m","").trim().toDoubleOrNull() ?: 0.0
            }
        }
        return if (totalMeters >= 1000) "%.1f km".format(totalMeters/1000) else "${totalMeters.toInt()} m"
    }


    private fun buildStepInstruction(
        maneuverType: String,
        roadName: String,
        step: DirectionStep          // ← THÊM tham số
    ): String {
        // Nếu có tên đường → dùng tên đường như cũ
        // Nếu không → dùng hướng la bàn
        val destination = if (roadName.isNotEmpty()) roadName
        else step.compassDirection()   // ← thay "đường phía trước"

        return when {
            maneuverType == "depart"                                          -> "Xuất phát theo $destination"
            maneuverType == "arrive"                                          -> "Đã đến đích"
            maneuverType.contains("left") && maneuverType.contains("sharp")  -> "Rẽ gấp trái theo $destination"
            maneuverType.contains("left") && maneuverType.contains("slight") -> "Đi chếch trái theo $destination"
            maneuverType.contains("left")                                     -> "Rẽ trái theo $destination"
            maneuverType.contains("right") && maneuverType.contains("sharp") -> "Rẽ gấp phải theo $destination"
            maneuverType.contains("right") && maneuverType.contains("slight")-> "Đi chếch phải theo $destination"
            maneuverType.contains("right")                                    -> "Rẽ phải theo $destination"
            maneuverType.contains("u-turn")                                   -> "Quay đầu xe"
            maneuverType.contains("roundabout")                               -> "Vào vòng xuyến, ra theo $destination"
            maneuverType.contains("merge")                                    -> "Nhập làn theo $destination"
            else                                                              -> "Đi thẳng theo $destination"
        }
    }

    private fun calculateRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        Toast.makeText(this, "Đang tính toán đường đi...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val urlString = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                        "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline"

                android.util.Log.d("ROUTE", "Calling: $urlString")  // ← thêm log để debug

                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000  // tăng lên 20s
                conn.readTimeout = 20000
                conn.requestMethod = "GET"

                val responseCode = conn.responseCode
                android.util.Log.d("ROUTE", "Response: $responseCode")

                if (responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val result = JSONObject(json)

                    if (result.getString("code") == "Ok") {
                        val routes = result.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getString("geometry")
                            val distance = route.getDouble("distance")
                            val duration = route.getDouble("duration")

                            runOnUiThread {
                                drawRoute(geometry)
                                Toast.makeText(
                                    this,
                                    "Khoảng cách: ${formatDistance(distance)} • ${formatDuration(duration)}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Không tìm thấy đường đi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Lỗi server: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("ROUTE", "Error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun drawRoute(encodedPolyline: String) {
        routeLine?.let { map.overlays.remove(it) }

        val points = decodePolyline(encodedPolyline)
        routeLine = Polyline().apply {
            outlinePaint.color = Color.parseColor("#1A73E8")
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setPoints(points)
        }

        map.overlays.add(routeLine)
        map.invalidate()

        if (points.isNotEmpty()) {
            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
            map.zoomToBoundingBox(bounds, true, 100)
        }
    }

    private fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = mutableListOf<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(GeoPoint(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    private fun showLayerOptions() {
        val items = arrayOf("Bản đồ tiêu chuẩn", "Bản đồ vệ tinh", "Bản đồ địa hình")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chọn kiểu bản đồ")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> map.setTileSource(TileSourceFactory.MAPNIK)
                    1 -> map.setTileSource(TileSourceFactory.USGS_SAT)
                    2 -> map.setTileSource(TileSourceFactory.OPEN_SEAMAP)
                }
                map.invalidate()
                Toast.makeText(this, "Đã chuyển sang ${items[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    private fun updateActualDrivingDistance(parking: ParkingLot) {
        myLocationOverlay?.myLocation?.let { myLoc ->
            Thread {
                try {

                    val urlString = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                            "${myLoc.longitude},${myLoc.latitude};${parking.lon},${parking.lat}?overview=false"

                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000

                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val root = JSONObject(json)
                        val routes = root.getJSONArray("routes")

                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            // distance trả về đơn vị MÉT
                            val realDistanceInMeters = route.getDouble("distance")
                            val durationInSeconds = route.getDouble("duration")

                            runOnUiThread {
                                // Cập nhật lại TextView với con số thực tế (ví dụ ~23km)
                                tvPlaceDistance.text = "${formatDistance(realDistanceInMeters)} (Theo lộ trình lái xe)"

                                // Hiển thị thêm thời gian dự kiến vào phần category hoặc một TextView khác
                                val timeText = formatDuration(durationInSeconds)
                                tvPlaceCategory.text = "$timeText"
                            }
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }


    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            "%.1f km".format(meters / 1000)
        }
    }

    private fun formatDuration(seconds: Double): String {
        val minutes = (seconds / 60).toInt()
        return if (minutes < 60) {
            "$minutes phút"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            "$hours giờ $mins phút"
        }
    }

    // 1. Hàm tạo danh sách Chip (Gọi cái này trong runOnUiThread của loadChargingStationsFromDB)
    private fun updateWardChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_wards)
        chipGroup.removeAllViews()

        // 1. Lấy vị trí hiện tại của người dùng
        val userLoc = myLocationOverlay?.myLocation

        // 2. Lọc danh sách phường dựa trên khoảng cách
        val nearbyWards = if (userLoc != null) {
            parkingLots.filter { station ->
                // Tính khoảng cách từ người dùng đến trạm
                val distance = calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    station.lat, station.lon
                )
                distance <= 5000 // Chỉ lấy các trạm trong bán kính 5km (5000 mét)
            }.map { it.ward }.distinct().sorted()
        } else {
            // Nếu chưa có GPS, tạm thời hiện tất cả hoặc hiện danh sách trống tùy bạn
            parkingLots.map { it.ward }.distinct().sorted()
        }

        // 3. Luôn thêm nút "Tất cả" đầu tiên
        addWardChip("Tất cả", true)

        // 4. Chỉ thêm các Chip của phường thỏa mãn điều kiện 5km
        nearbyWards.forEach { wardName ->
            if (wardName != "Tất cả") {
                addWardChip(wardName, false)
            }
        }
    }

    // 2. Hàm vẽ từng Chip lên màn hình
    private fun addWardChip(wardName: String, isDefault: Boolean) {
        val chip = Chip(this)
        chip.text = wardName
        chip.isCheckable = true
        chip.isChecked = isDefault

        // Sử dụng màu từ colors.xml (Đảm bảo đã thêm green_main vào colors.xml)
        chip.setChipBackgroundColorResource(if (isDefault) R.color.green_main else R.color.white)
        chip.setTextColor(if (isDefault) android.graphics.Color.WHITE else android.graphics.Color.BLACK)

        chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                filterByWard(wardName)
            }
        }
        findViewById<ChipGroup>(R.id.chip_group_wards).addView(chip)
    }

    private fun showParkingListDialog(wardName: String, list: List<ParkingLot>) {
        // Sắp xếp theo khoảng cách gần nhất (nếu có GPS)
        val sortedList = myLocationOverlay?.myLocation?.let { myLoc ->
            list.sortedBy { station ->
                calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
            }
        } ?: list

        val stationNames = sortedList.map { station ->
            val spotsInfo = if (station.hasChargingStation) {
                "Còn ${station.realAvailableTimeSlots} khung giờ"
            } else {
                "Còn ${station.availableSpots} chỗ"
            }
            "${station.name}  —  $spotsInfo"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Bãi đỗ còn trống tại $wardName")
            .setItems(stationNames) { _, which ->
                val selectedStation = sortedList[which]
                map.controller.animateTo(GeoPoint(selectedStation.lat, selectedStation.lon))
                map.controller.setZoom(18.0)
                showParkingDetails(selectedStation)
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun openBankingAppForPayment() {
        val amount = 100000
        val content = "DatCho_${currentBookingId}"
        val bankBin = "970415"      // ICB = VietinBank, mã BIN 970415
        val accountNo = "108876696755"

        // 1. Thử mở MoMo trước
        val momoDeepLink = "momo://transfer?amount=$amount&memo=$content"

        // 2. VietQR universal deep link (hỗ trợ hầu hết ngân hàng)
        val vietQRLink = "https://dl.vietqr.io/pay?app=vietqr" +
                "&ba=$accountNo" +
                "&bin=$bankBin" +
                "&am=$amount" +
                "&tn=$content"

        // Thử theo thứ tự: app ngân hàng → trình duyệt mở VietQR
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(vietQRLink))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: copy nội dung vào clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nội dung CK", content))
            Toast.makeText(this, "Đã copy nội dung chuyển khoản!", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Hàm lọc dữ liệu chuẩn
    private fun filterByWard(wardName: String) {
        val filteredList = if (wardName == "Tất cả") {
            parkingLots
        } else {
            parkingLots.filter { it.ward == wardName }
        }

        // Cập nhật Marker trên bản đồ (hiện tất cả, kể cả hết chỗ)
        displayParkingLots(filteredList)

        // Khi hiện danh sách đề xuất → chỉ lấy bãi còn chỗ trống
        if (wardName != "Tất cả" && filteredList.isNotEmpty()) {
            val availableList = filteredList.filter { parking ->
                if (parking.hasChargingStation) {
                    parking.realAvailableTimeSlots > 0  // Trạm sạc: còn khung giờ trống
                } else {
                    parking.availableSpots > 0           // Bãi đỗ thường: còn chỗ đậu
                }
            }

            if (availableList.isNotEmpty()) {
                showParkingListDialog(wardName, availableList)
            } else {
                Toast.makeText(this, "Tất cả bãi đỗ tại $wardName đã hết chỗ!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadChargingStationsFromDB() {
        lifecycleScope.launch {
            try {
                val apiStations = RetrofitClient.instance.getStations()
                stationLoadRetries = 0

                // Xóa cũ thêm mới an toàn
                parkingLots.clear()

                apiStations.forEach { station ->
                    parkingLots.add(ParkingLot(
                        id = station.id,
                        name = station.name,
                        ward = station.ward ?: "Khác",
                        lat = station.latitude,
                        lon = station.longitude,
                        totalSpots = station.total_slots ?: 0,
                        availableSpots = station.available_slots ?: 0,
                        address = station.address ?: "",
                        hasChargingStation = true,
                        totalChargingSpots = station.total_slots ?: 0,
                        availableChargingSpots = station.available_slots ?: 0,
                        realAvailableTimeSlots = station.real_available_time_slots ?: 0
                    ))
                }

                // ĐƯA LÊN UI THREAD ĐỂ VẼ - KHÔNG SẼ BỊ OUT
                runOnUiThread {
                    if (parkingLots.isNotEmpty()) {
                        displayParkingLots(parkingLots)
                        updateWardChips()
                        if (currentBookingId != -1) {
                            bottomSheetBehavior.isHideable = false
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("STATIONS", "Không tải được trạm sạc: ${e.message}")
                if (stationLoadRetries < 3) {
                    stationLoadRetries++
                    Toast.makeText(
                        this@MainActivity,
                        "Máy chủ đang khởi động, đang thử lại... ($stationLoadRetries/3)",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateHandler.postDelayed({ loadChargingStationsFromDB() }, 6000)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Không tải được danh sách trạm sạc. Hãy kiểm tra kết nối mạng.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMyLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        notificationHandler.removeCallbacksAndMessages(null)
        checkPaymentHandler.removeCallbacksAndMessages(null)   // dừng polling thanh toán
        bookingCountDownTimer?.cancel()                         // dừng đồng hồ đếm ngược
        RetrofitClient.onUnauthorized = null
        navigationManager?.stop()
        stopHighFrequencyGPS()
        // Thêm cleanup cho navigationEngine
        navigationEngine?.cleanup()
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
    }

    // --- PHẦN XỬ LÝ ĐĂNG XUẤT (MENU) ---
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        // Nạp file xml menu vào ActionBar
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        // Xử lý khi bấm vào mục "Đăng xuất"
        if (item.itemId == R.id.action_logout) {
            showLogoutConfirmation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận đăng xuất")
            .setMessage("Bạn có chắc chắn muốn thoát tài khoản không?")
            .setPositiveButton("Đăng xuất") { _, _ -> performLogout() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performLogout() {
        // 1. Xóa sạch dữ liệu đăng nhập
        val sharedPref = getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // 2. Chuyển về màn hình Đăng nhập
        val intent = android.content.Intent(this, LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showAndSaveInvoice(bookingId: Int, slotCode: String, stationName: String, scheduledHour: Int) {
        val invoiceView = layoutInflater.inflate(R.layout.layout_invoice, null)

        invoiceView.findViewById<TextView>(R.id.tv_invoice_id).text = "Mã HĐ: #${bookingId}"
        invoiceView.findViewById<TextView>(R.id.tv_invoice_station).text = "Trạm: $stationName"
        invoiceView.findViewById<TextView>(R.id.tv_invoice_slot).text = "Ô sạc: $slotCode"

        val endHour = scheduledHour + 2
        invoiceView.findViewById<TextView>(R.id.tv_invoice_time).text =
            "Khung giờ: ${String.format("%02d:00 – %02d:00", scheduledHour, endHour)}"

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        invoiceView.findViewById<TextView>(R.id.tv_invoice_date).text =
            "Thời gian: ${sdf.format(java.util.Date())}"

        invoiceView.findViewById<TextView>(R.id.tv_invoice_amount).text = "Số tiền: 100.000 VNĐ"
        invoiceView.findViewById<TextView>(R.id.tv_invoice_status).text = "✅ ĐÃ THANH TOÁN"

        invoiceView.measure(
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        invoiceView.layout(0, 0, invoiceView.measuredWidth, invoiceView.measuredHeight)

        val bitmap = android.graphics.Bitmap.createBitmap(
            invoiceView.measuredWidth,
            invoiceView.measuredHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        invoiceView.draw(canvas)

        // ← XÓA 2 dòng này:
        // val fileName = "HoaDon_${bookingId}_${System.currentTimeMillis()}.png"
        // val savedUri = saveInvoiceBitmap(bitmap, fileName)

        // Chỉ hiện dialog, KHÔNG tự lưu
        showInvoicePreviewDialog(bitmap)
    }

    private fun showInvoicePreviewDialog(bitmap: android.graphics.Bitmap) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_invoice_preview, null)
        val imgInvoice = dialogView.findViewById<ImageView>(R.id.img_invoice_preview)
        val tvSaveStatus = dialogView.findViewById<TextView>(R.id.tv_save_status)
        val btnShare = dialogView.findViewById<Button>(R.id.btn_share_invoice)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_invoice)
        val btnDownload = dialogView.findViewById<Button>(R.id.btn_download_invoice)

        imgInvoice.setImageBitmap(bitmap)
        tvSaveStatus.text = "Nhấn tải xuống để lưu hóa đơn vào máy"
        tvSaveStatus.setTextColor(Color.parseColor("#1565C0"))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Nút tải xuống → mở hộp thoại chọn nơi lưu
        btnDownload.setOnClickListener {
            pendingInvoiceBitmap = bitmap
            val fileName = "HoaDon_${System.currentTimeMillis()}.png"
            saveInvoiceLauncher.launch(fileName)
        }

        // Nút chia sẻ → dùng URI tạm trong cache
        btnShare.setOnClickListener {
            try {
                val cacheFile = java.io.File(cacheDir, "invoice_share.png")
                java.io.FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                val shareUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    cacheFile
                )
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ hóa đơn"))
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
