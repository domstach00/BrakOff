package com.wodrol.brakoff.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val API_TOKEN = stringPreferencesKey("api_token")
        private val DISMISSED_ARCHIVE_ID = stringPreferencesKey("dismissed_archive_id")
        private val SCAN_BUTTON_LEFT = booleanPreferencesKey("scan_button_left")
        private val AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
    }

    val autoScanEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_SCAN_ENABLED] ?: false
        }

    suspend fun setAutoScanEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SCAN_ENABLED] = enabled
        }
    }

    val scanButtonLeft: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SCAN_BUTTON_LEFT] ?: false
        }

    suspend fun saveScanButtonLeft(isLeft: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_BUTTON_LEFT] = isLeft
        }
    }

    val dismissedArchiveId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DISMISSED_ARCHIVE_ID] ?: ""
        }

    suspend fun saveDismissedArchiveId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[DISMISSED_ARCHIVE_ID] = id
        }
    }

    val serverUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SERVER_URL] ?: "https://brakoff.mpdwodrol.com"
        }

    val apiToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[API_TOKEN] ?: ""
        }

    val deviceName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEVICE_NAME] ?: ""
        }

    val deviceId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEVICE_ID] ?: ""
        }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    suspend fun saveDeviceName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_NAME] = name
        }
    }

    suspend fun saveApiToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[API_TOKEN] = token
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        var id = ""
        context.dataStore.edit { preferences ->
            id = preferences[DEVICE_ID] ?: run {
                val newId = UUID.randomUUID().toString()
                preferences[DEVICE_ID] = newId
                newId
            }
        }
        return id
    }
}
