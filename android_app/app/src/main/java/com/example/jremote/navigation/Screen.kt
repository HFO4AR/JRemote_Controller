package com.example.jremote.navigation

sealed class Screen(val route: String) {
    data object Control : Screen("control")
    data object Connection : Screen("connection")
    data object Settings : Screen("settings")
    data object BleConfig : Screen("ble_config")
}
