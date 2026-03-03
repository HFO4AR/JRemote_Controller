package com.example.jremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jremote_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SEND_INTERVAL_MS = longPreferencesKey("send_interval_ms")
        val SHOW_DEBUG_PANEL = booleanPreferencesKey("show_debug_panel")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val TOGGLE_BUTTON_LAYOUT = stringPreferencesKey("toggle_button_layout")
        val LAST_CONNECTED_DEVICE_ADDRESS = stringPreferencesKey("last_connected_device_address")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_CONNECTION_MODE = stringPreferencesKey("last_connection_mode")
        val LAST_CONNECTED_DEVICE_IP = stringPreferencesKey("last_connected_device_ip")
    }
    
    private object ButtonKeys {
        fun isEnabled(id: Int) = booleanPreferencesKey("button_${id}_enabled")
        fun isToggle(id: Int) = booleanPreferencesKey("button_${id}_toggle")
    }
    
    val appSettings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val lastConnectionModeStr = context.dataStore.data.first()[Keys.LAST_CONNECTION_MODE] ?: "BLE"
        val lastConnectionMode = try {
            ConnectionMode.valueOf(lastConnectionModeStr)
        } catch (e: Exception) {
            ConnectionMode.BLE
        }
        val lastConnectedDeviceIp = context.dataStore.data.first()[Keys.LAST_CONNECTED_DEVICE_IP]

        AppSettings(
            sendIntervalMs = prefs[Keys.SEND_INTERVAL_MS] ?: 20L,
            showDebugPanel = prefs[Keys.SHOW_DEBUG_PANEL] ?: true,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: false,
            toggleButtonLayout = try {
                ToggleButtonLayout.valueOf(prefs[Keys.TOGGLE_BUTTON_LAYOUT] ?: "HORIZONTAL")
            } catch (e: IllegalArgumentException) {
                ToggleButtonLayout.HORIZONTAL
            },
            lastConnectedDeviceAddress = prefs[Keys.LAST_CONNECTED_DEVICE_ADDRESS],
            themeMode = try {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "SYSTEM")
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            },
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            lastConnectionMode = lastConnectionMode,
            lastConnectedDeviceIp = lastConnectedDeviceIp
        )
    }
    
    suspend fun updateAppSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEND_INTERVAL_MS] = settings.sendIntervalMs
            prefs[Keys.SHOW_DEBUG_PANEL] = settings.showDebugPanel
            prefs[Keys.HAPTIC_FEEDBACK] = settings.hapticFeedback
            prefs[Keys.AUTO_RECONNECT] = settings.autoReconnect
            prefs[Keys.TOGGLE_BUTTON_LAYOUT] = settings.toggleButtonLayout.name
            settings.lastConnectedDeviceAddress?.let {
                prefs[Keys.LAST_CONNECTED_DEVICE_ADDRESS] = it
            }
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            prefs[Keys.LAST_CONNECTION_MODE] = settings.lastConnectionMode.name
            settings.lastConnectedDeviceIp?.let {
                prefs[Keys.LAST_CONNECTED_DEVICE_IP] = it
            }
        }
    }
    
    fun getButtonEnabled(id: Int): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ButtonKeys.isEnabled(id)] ?: true
    }
    
    fun getButtonToggle(id: Int): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ButtonKeys.isToggle(id)] ?: false
    }
    
    suspend fun updateButtonConfig(id: Int, isEnabled: Boolean, isToggle: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ButtonKeys.isEnabled(id)] = isEnabled
            prefs[ButtonKeys.isToggle(id)] = isToggle
        }
    }
}
