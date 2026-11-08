package com.example.carparkingsmart.navigation

import org.osmdroid.util.GeoPoint

data class RouteStep(
    val instruction: String,
    val distance: Double,
    val duration: Double,
    val maneuver: Maneuver,
    val location: GeoPoint,
    val streetName: String = ""
)

data class Maneuver(
    val type: String,
    val modifier: String = ""
)

data class NavigationRoute(
    val steps: List<RouteStep>,
    val totalDistance: Double,
    val totalDuration: Double,
    val polyline: String
)