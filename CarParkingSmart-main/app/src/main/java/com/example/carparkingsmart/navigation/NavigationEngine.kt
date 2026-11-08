package com.example.carparkingsmart.navigation

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.math.*

class NavigationEngine(
    private val context: Context,
    private val onNavigationUpdate: (NavigationState) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var currentRoute: NavigationRoute? = null
    private var currentStepIndex = 0
    private var lastAnnouncedDistance = Double.MAX_VALUE
    private var isNavigating = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    data class NavigationState(
        val currentStep: RouteStep?,
        val nextStep: RouteStep?,
        val distanceToNextTurn: Double,
        val remainingDistance: Double,
        val remainingDuration: Double,
        val isOffRoute: Boolean = false
    )

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    suspend fun startNavigation(from: GeoPoint, to: GeoPoint) {
        isNavigating = true
        currentStepIndex = 0
        lastAnnouncedDistance = Double.MAX_VALUE
        currentRoute = fetchRoute(from, to)
        currentRoute?.let { route ->
            speak("Bắt đầu chỉ đường. Tổng quãng đường ${formatDistance(route.totalDistance)}")
            if (route.steps.isNotEmpty()) {
                speak(route.steps[0].instruction)
            }
        }
    }

    fun updateLocation(location: Location) {
        if (!isNavigating || currentRoute == null) return
        val route = currentRoute ?: return
        if (currentStepIndex >= route.steps.size) { finishNavigation(); return }

        val step = route.steps[currentStepIndex]
        val distanceToTurn = haversine(
            location.latitude, location.longitude,
            step.location.latitude, step.location.longitude
        )

        var remainingDistance = distanceToTurn
        for (i in (currentStepIndex + 1) until route.steps.size) {
            remainingDistance += route.steps[i].distance
        }

        if (distanceToTurn < 20) {
            currentStepIndex++
            lastAnnouncedDistance = Double.MAX_VALUE
            if (currentStepIndex < route.steps.size) {
                speak(route.steps[currentStepIndex].instruction)
            } else {
                finishNavigation()
            }
        } else {
            announceIfNeeded(distanceToTurn, step)
        }

        val nextStep = route.steps.getOrNull(currentStepIndex + 1)
        onNavigationUpdate(NavigationState(
            currentStep = step,
            nextStep = nextStep,
            distanceToNextTurn = distanceToTurn,
            remainingDistance = remainingDistance,
            remainingDuration = remainingDistance / 8.33,
            isOffRoute = distanceToTurn > 150 && currentStepIndex > 0
        ))
    }

    private fun announceIfNeeded(distance: Double, step: RouteStep) {
        val thresholds = listOf(500.0, 300.0, 100.0, 50.0)
        for (threshold in thresholds) {
            if (distance < threshold && lastAnnouncedDistance >= threshold) {
                speak("Sau ${threshold.toInt()} mét, ${getShortInstruction(step)}")
                lastAnnouncedDistance = distance
                return
            }
        }
        if (lastAnnouncedDistance == Double.MAX_VALUE) {
            lastAnnouncedDistance = distance
        }
    }

    private fun getShortInstruction(step: RouteStep): String {
        return when (step.maneuver.type) {
            "turn" -> when (step.maneuver.modifier) {
                "left" -> "rẽ trái"
                "right" -> "rẽ phải"
                "slight left" -> "rẽ nhẹ trái"
                "slight right" -> "rẽ nhẹ phải"
                "sharp left" -> "rẽ gấp trái"
                "sharp right" -> "rẽ gấp phải"
                else -> "rẽ"
            }
            "arrive" -> "đến đích"
            "roundabout", "rotary" -> "vào vòng xuyến"
            "uturn" -> "quay đầu"
            else -> "đi thẳng"
        }
    }

    private fun finishNavigation() {
        isNavigating = false
        speak("Bạn đã đến đích")
    }

    fun stopNavigation() {
        isNavigating = false
        currentRoute = null
        currentStepIndex = 0
        tts?.stop()
    }

    private suspend fun fetchRoute(from: GeoPoint, to: GeoPoint): NavigationRoute? =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                        "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
                        "?overview=full&geometries=polyline&steps=true"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                if (conn.responseCode == 200) {
                    parseRoute(conn.inputStream.bufferedReader().readText())
                } else null
            } catch (e: Exception) {
                Log.e("NavEngine", "fetchRoute error: ${e.message}")
                null
            }
        }

    private fun parseRoute(json: String): NavigationRoute? {
        return try {
            val root = JSONObject(json)
            if (root.getString("code") != "Ok") return null
            val route = root.getJSONArray("routes").getJSONObject(0)
            val leg = route.getJSONArray("legs").getJSONObject(0)
            val stepsJson = leg.getJSONArray("steps")
            val steps = mutableListOf<RouteStep>()

            for (i in 0 until stepsJson.length()) {
                val s = stepsJson.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val streetName = s.optString("name", "đường phía trước")
                val type = m.getString("type")
                val modifier = m.optString("modifier", "")
                steps.add(RouteStep(
                    instruction = buildInstruction(type, modifier, streetName),
                    distance = s.getDouble("distance"),
                    duration = s.getDouble("duration"),
                    maneuver = Maneuver(type, modifier),
                    location = GeoPoint(loc.getDouble(1), loc.getDouble(0)),
                    streetName = streetName
                ))
            }

            NavigationRoute(
                steps = steps,
                totalDistance = route.getDouble("distance"),
                totalDuration = route.getDouble("duration"),
                polyline = route.getString("geometry")
            )
        } catch (e: Exception) {
            Log.e("NavEngine", "parseRoute error: ${e.message}"); null
        }
    }

    private fun buildInstruction(type: String, modifier: String, street: String): String {
        val road = if (street.isBlank()) "đường phía trước" else street
        return when (type) {
            "depart"    -> "Xuất phát trên $road"
            "arrive"    -> "Đã đến đích"
            "turn" -> when (modifier) {
                "left"        -> "Rẽ trái vào $road"
                "right"       -> "Rẽ phải vào $road"
                "slight left" -> "Đi chếch trái vào $road"
                "slight right"-> "Đi chếch phải vào $road"
                "sharp left"  -> "Rẽ gấp trái vào $road"
                "sharp right" -> "Rẽ gấp phải vào $road"
                "uturn"       -> "Quay đầu xe"
                else          -> "Rẽ vào $road"
            }
            "continue"      -> "Tiếp tục đi thẳng trên $road"
            "roundabout",
            "rotary"        -> "Vào vòng xuyến, rẽ ra tại $road"
            "merge"         -> "Nhập làn vào $road"
            "uturn"         -> "Quay đầu xe"
            else            -> "Đi thẳng trên $road"
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1-a))
    }

    private fun formatDistance(m: Double) =
        if (m < 1000) "${m.toInt()} mét" else "%.1f ki-lô-mét".format(m/1000)

    fun cleanup() { scope.cancel(); tts?.stop(); tts?.shutdown() }
}