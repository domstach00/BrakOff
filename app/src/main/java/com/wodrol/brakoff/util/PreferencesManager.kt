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

    data class CurrentDeliverySummary(
        val deliveryId: String = "",
        val deliveryDisplayName: String = "",
        val sourceFileName: String = "",
        val supplierName: String = "",
        val commercialDocumentNumber: String = "",
        val warehouseDocumentNumber: String = ""
    ) {
        fun title(): String = deliveryDisplayName.ifBlank {
            supplierName.ifBlank {
                sourceFileName.ifBlank { "Brak wybranej dostawy" }
            }
        }

        fun subtitle(): String? {
            val parts = listOf(
                commercialDocumentNumber,
                warehouseDocumentNumber
            ).filter { it.isNotBlank() }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
        }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val API_TOKEN = stringPreferencesKey("api_token")
        private val DISMISSED_ARCHIVE_ID = stringPreferencesKey("dismissed_archive_id")
        private val SCAN_BUTTON_LEFT = booleanPreferencesKey("scan_button_left")
        private val AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
        private val SELECTED_DELIVERY_ID = stringPreferencesKey("selected_delivery_id")
        private val CURRENT_DELIVERY_NAME = stringPreferencesKey("current_delivery_name")
        private val CURRENT_DELIVERY_SOURCE_FILE = stringPreferencesKey("current_delivery_source_file")
        private val CURRENT_DELIVERY_SUPPLIER = stringPreferencesKey("current_delivery_supplier")
        private val CURRENT_DELIVERY_COMMERCIAL_DOC = stringPreferencesKey("current_delivery_commercial_doc")
        private val CURRENT_DELIVERY_WAREHOUSE_DOC = stringPreferencesKey("current_delivery_warehouse_doc")
    }

    val selectedDeliveryId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_DELIVERY_ID] ?: ""
        }

    suspend fun saveSelectedDeliveryId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_DELIVERY_ID] = id
        }
    }

    val currentDeliverySummary: Flow<CurrentDeliverySummary> = context.dataStore.data
        .map { preferences ->
            CurrentDeliverySummary(
                deliveryId = preferences[SELECTED_DELIVERY_ID] ?: "",
                deliveryDisplayName = preferences[CURRENT_DELIVERY_NAME] ?: "",
                sourceFileName = preferences[CURRENT_DELIVERY_SOURCE_FILE] ?: "",
                supplierName = preferences[CURRENT_DELIVERY_SUPPLIER] ?: "",
                commercialDocumentNumber = preferences[CURRENT_DELIVERY_COMMERCIAL_DOC] ?: "",
                warehouseDocumentNumber = preferences[CURRENT_DELIVERY_WAREHOUSE_DOC] ?: ""
            )
        }

    suspend fun saveCurrentDeliverySummary(summary: CurrentDeliverySummary) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_DELIVERY_ID] = summary.deliveryId
            preferences[CURRENT_DELIVERY_NAME] = summary.deliveryDisplayName
            preferences[CURRENT_DELIVERY_SOURCE_FILE] = summary.sourceFileName
            preferences[CURRENT_DELIVERY_SUPPLIER] = summary.supplierName
            preferences[CURRENT_DELIVERY_COMMERCIAL_DOC] = summary.commercialDocumentNumber
            preferences[CURRENT_DELIVERY_WAREHOUSE_DOC] = summary.warehouseDocumentNumber
        }
    }

    suspend fun clearCurrentDeliverySummary() {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_DELIVERY_ID] = ""
            preferences[CURRENT_DELIVERY_NAME] = ""
            preferences[CURRENT_DELIVERY_SOURCE_FILE] = ""
            preferences[CURRENT_DELIVERY_SUPPLIER] = ""
            preferences[CURRENT_DELIVERY_COMMERCIAL_DOC] = ""
            preferences[CURRENT_DELIVERY_WAREHOUSE_DOC] = ""
        }
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
