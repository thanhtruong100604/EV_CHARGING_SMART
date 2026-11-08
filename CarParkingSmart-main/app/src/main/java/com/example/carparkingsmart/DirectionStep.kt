package com.example.carparkingsmart
data class DirectionStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val roadName: String = "",
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    val bearingDegrees: Double = -1.0
) {
    // Chuyển vào trong class, không để top-level nữa
    fun maneuverIcon(): String {
        return when {
            maneuver.contains("depart")       -> "🏁"
            maneuver.contains("arrive")       -> "📍"
            maneuver.contains("turn-left")    -> "⬅️"
            maneuver.contains("sharp-left")   -> "↰"
            maneuver.contains("slight-left")  -> "↖️"
            maneuver.contains("turn-right")   -> "➡️"
            maneuver.contains("sharp-right")  -> "↱"
            maneuver.contains("slight-right") -> "↗️"
            maneuver.contains("u-turn")       -> "🔄"
            maneuver.contains("roundabout")   -> "🔵"
            maneuver.contains("merge")        -> "🔀"
            maneuver.contains("fork")         -> "⑂"
            maneuver.contains("left")         -> "⬅️"
            maneuver.contains("right")        -> "➡️"
            else                              -> "⬆️"
        }
    }

    fun hudColor(): String {
        return when {
            maneuver.contains("arrive")                        -> "#2E7D32"
            maneuver.contains("u-turn")                        -> "#B71C1C"
            maneuver.contains("left") || maneuver.contains("right") -> "#1565C0"
            maneuver.contains("roundabout")                    -> "#6A1B9A"
            else                                               -> "#37474F"
        }
    }
    fun compassDirection(): String {
        val bearing = if (bearingDegrees >= 0) bearingDegrees else {
            // Tự tính bearing từ start → end nếu không có sẵn
            val dLon = Math.toRadians(endLon - startLon)
            val lat1 = Math.toRadians(startLat)
            val lat2 = Math.toRadians(endLat)
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
            var b = Math.toDegrees(Math.atan2(y, x))
            if (b < 0) b += 360.0
            b
        }
        return when {
            bearing >= 337.5 || bearing < 22.5  -> "hướng Bắc"
            bearing < 67.5                       -> "hướng Đông Bắc"
            bearing < 112.5                      -> "hướng Đông"
            bearing < 157.5                      -> "hướng Đông Nam"
            bearing < 202.5                      -> "hướng Nam"
            bearing < 247.5                      -> "hướng Tây Nam"
            bearing < 292.5                      -> "hướng Tây"
            else                                 -> "hướng Tây Bắc"
        }
    }
}