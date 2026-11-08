package com.example.carparkingsmart

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.*

/**
 * NavigationManager: Quản lý toàn bộ logic chỉ đường real-time.
 *
 * Cách dùng trong MainActivity:
 *   val nav = NavigationManager(context, steps, ::onStepChanged, ::onArrived)
 *   nav.start()
 *   // Mỗi khi GPS cập nhật vị trí:
 *   nav.updateLocation(lat, lon)
 *   // Khi người dùng dừng:
 *   nav.stop()
 */
class NavigationManager(
    private val context: Context,
    private val steps: List<DirectionStep>,
    private val onStepChanged: (stepIndex: Int, step: DirectionStep, distanceToTurn: Double) -> Unit,
    private val onArrived: () -> Unit,
    private val onDistanceUpdate: (distanceToTurn: Double, timeRemaining: String) -> Unit
) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentStepIndex = 0
    private var isNavigating = false
    private var lastAnnouncedStep = -1
    private var lastAnnouncedDistance = -1.0

    // Ngưỡng khoảng cách để thông báo (mét)
    companion object {
        const val ANNOUNCE_DISTANCE_FAR = 200.0    // Thông báo trước 200m
        const val ANNOUNCE_DISTANCE_NEAR = 50.0    // Thông báo lại khi còn 50m
        const val ARRIVE_THRESHOLD = 30.0          // Coi như đã đến nơi khi còn 30m
        const val OFF_ROUTE_THRESHOLD = 80.0       // Lệch đường khi cách route > 80m
        const val STEP_ADVANCE_THRESHOLD = 25.0    // Chuyển bước tiếp theo khi còn 25m
    }

    fun start() {
        isNavigating = true
        currentStepIndex = 0
        lastAnnouncedStep = -1
        lastAnnouncedDistance = Double.MAX_VALUE

        // Khởi tạo Text-to-Speech tiếng Việt
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("vi", "VN"))
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                if (!isTtsReady) {
                    // Fallback sang tiếng Anh nếu không có tiếng Việt
                    tts?.setLanguage(Locale.ENGLISH)
                    isTtsReady = true
                }

                // Thông báo bắt đầu chuyến đi
                if (steps.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        speak("Bắt đầu dẫn đường. ${steps[0].instruction}")
                    }, 1000)
                }
            }
        }
    }

    fun stop() {
        isNavigating = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun isMuted(): Boolean = !isTtsReady

    fun toggleMute(): Boolean {
        isTtsReady = !isTtsReady
        return isTtsReady
    }

    /**
     * Gọi hàm này mỗi khi GPS cập nhật vị trí mới.
     */
    fun updateLocation(userLat: Double, userLon: Double) {
        if (!isNavigating || steps.isEmpty()) return

        val currentStep = steps.getOrNull(currentStepIndex) ?: return

        // Tính khoảng cách đến điểm rẽ của bước hiện tại
        val distanceToTurn = calculateDistance(
            userLat, userLon,
            currentStep.startLat, currentStep.startLon
        )

        // Cập nhật UI với khoảng cách và thời gian còn lại
        val timeRemaining = estimateTimeRemaining(userLat, userLon)
        onDistanceUpdate(distanceToTurn, timeRemaining)

        // Kiểm tra đã đến đích chưa (bước cuối cùng)
        if (currentStepIndex == steps.size - 1) {
            val destination = steps.last()
            val distToDest = calculateDistance(
                userLat, userLon,
                destination.endLat, destination.endLon
            )
            if (distToDest <= ARRIVE_THRESHOLD) {
                speak("Bạn đã đến nơi!")
                Handler(Looper.getMainLooper()).postDelayed({
                    onArrived()
                    isNavigating = false
                }, 2000)
                return
            }
        }

        // Logic thông báo chuyển hướng
        handleTurnAnnouncement(distanceToTurn, currentStep, currentStepIndex)

        // Tự động chuyển sang bước tiếp theo nếu đã đi qua điểm rẽ
        if (distanceToTurn <= STEP_ADVANCE_THRESHOLD && currentStepIndex < steps.size - 1) {
            currentStepIndex++
            lastAnnouncedStep = -1
            lastAnnouncedDistance = Double.MAX_VALUE  // ✅ Đổi từ -1.0 thành MAX_VALUE
            onStepChanged(currentStepIndex, steps[currentStepIndex], distanceToTurn)
        }
    }

    private fun handleTurnAnnouncement(distance: Double, step: DirectionStep, stepIdx: Int) {

        // Thông báo khi còn ~200m
        if (distance <= ANNOUNCE_DISTANCE_FAR &&
            distance > ANNOUNCE_DISTANCE_NEAR &&
            lastAnnouncedStep != stepIdx) {  // ✅ Bỏ điều kiện lastAnnouncedDistance

            val distText = if (distance >= 100) "${(distance / 10).toInt() * 10} mét"
            else "${distance.toInt()} mét"
            speak("Sau $distText, ${step.instruction}")
            lastAnnouncedStep = stepIdx
            lastAnnouncedDistance = distance
        }

        // Thông báo lại khi còn ~50m
        if (distance <= ANNOUNCE_DISTANCE_NEAR &&
            lastAnnouncedDistance > ANNOUNCE_DISTANCE_NEAR) {  // ✅ Giữ điều kiện này

            speak(step.instruction)
            lastAnnouncedDistance = distance
        }
    }

    private fun speak(text: String) {
        if (!isTtsReady || tts == null) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_${System.currentTimeMillis()}")
    }

    fun speakNow(text: String) = speak(text)

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // bán kính Trái Đất (mét)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun estimateTimeRemaining(userLat: Double, userLon: Double): String {
        // Tính tổng khoảng cách còn lại từ bước hiện tại
        var totalRemaining = 0.0
        for (i in currentStepIndex until steps.size) {
            val step = steps[i]
            if (i == currentStepIndex) {
                totalRemaining += calculateDistance(userLat, userLon, step.startLat, step.startLon)
            } else {
                totalRemaining += calculateDistance(
                    steps[i-1].endLat, steps[i-1].endLon,
                    step.startLat, step.startLon
                )
            }
        }
        // Ước tính tốc độ 30km/h = 8.33m/s
        val seconds = totalRemaining / 8.33
        val minutes = (seconds / 60).toInt()
        return if (minutes < 1) "< 1 phút" else "$minutes phút"
    }

    fun getCurrentStep(): DirectionStep? = steps.getOrNull(currentStepIndex)
    fun getCurrentStepIndex(): Int = currentStepIndex
    fun getTotalSteps(): Int = steps.size

    /**
     * Tái cấu trúc instruction thành câu tiếng Việt tự nhiên hơn
     */
    fun buildVoiceInstruction(step: DirectionStep): String {
        return when {
            step.maneuver.contains("turn-left") || step.maneuver.contains("left") ->
                "Rẽ trái vào ${step.roadName.ifEmpty { "đường phía trước" }}"
            step.maneuver.contains("turn-right") || step.maneuver.contains("right") ->
                "Rẽ phải vào ${step.roadName.ifEmpty { "đường phía trước" }}"
            step.maneuver.contains("u-turn") ->
                "Quay đầu xe"
            step.maneuver.contains("roundabout") ->
                "Đi vào vòng xuyến, sau đó rẽ ra"
            step.maneuver.contains("merge") ->
                "Nhập làn đường ${step.roadName}"
            step.maneuver.contains("depart") ->
                "Xuất phát, đi thẳng trên ${step.roadName.ifEmpty { "đường" }}"
            step.maneuver.contains("arrive") ->
                "Đã đến nơi!"
            else ->
                "Đi thẳng trên ${step.roadName.ifEmpty { "đường phía trước" }}"
        }
    }
}