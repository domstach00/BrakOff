package com.wodrol.brakoff.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.remote.dto.ActiveDeliveryDto
import com.wodrol.brakoff.data.remote.dto.DeliveryResponse
import com.wodrol.brakoff.data.remote.dto.DeviceStateRequest
import com.wodrol.brakoff.data.remote.dto.DeviceStateResponse
import com.wodrol.brakoff.util.PreferencesManager
import com.wodrol.brakoff.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrakOffRepository(
    private val api: BrakOffApi,
    private val deliveryDao: DeliveryDao,
    private val productStateDao: ProductStateDao,
    private val preferencesManager: PreferencesManager,
    private val context: android.content.Context
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val gson = Gson()

    sealed class FetchResult {
        data object Success : FetchResult()
        data class NewDeliveryAvailable(val newDeliveryId: String, val oldDeliveryId: String) : FetchResult()
        data class ActiveDeliveriesLoaded(val deliveries: List<ActiveDeliveryDto>) : FetchResult()
        data class DeliveryArchived(val deliveryId: String) : FetchResult()
        data class ScanConflict(val barcode: String, val quantity: Int, val suggestedDeliveries: List<ActiveDeliveryDto>) : FetchResult()
        data class Error(val message: String) : FetchResult()
        data object InvalidToken : FetchResult()
    }

    val allDeliveryItems: Flow<List<DeliveryItem>> = deliveryDao.getAllItems()
    val allProductStates: Flow<List<LocalProductState>> = productStateDao.getAllStates()

    suspend fun getProductState(barcode: String): LocalProductState? =
        productStateDao.getStateByBarcode(barcode)

    suspend fun updateProductQuantity(barcode: String, quantity: Int) {
        val existing = productStateDao.getStateByBarcode(barcode)
        val deliveryItem = deliveryDao.getItemByBarcode(barcode)

        val newState = if (existing != null) {
            existing.copy(
                quantity = quantity,
                updatedAt = System.currentTimeMillis(),
                revision = existing.revision + 1,
                syncStatus = SyncStatus.PENDING,
                globalScannedQty = deliveryItem?.scannedQty ?: existing.globalScannedQty,
                unit = deliveryItem?.unit ?: existing.unit,
                name = deliveryItem?.name ?: existing.name,
                expectedQty = deliveryItem?.expectedQty ?: existing.expectedQty,
                fromDelivery = deliveryItem != null
            )
        } else {
            LocalProductState(
                barcode = barcode,
                name = deliveryItem?.name,
                quantity = quantity,
                fromDelivery = deliveryItem != null,
                expectedQty = deliveryItem?.expectedQty,
                globalScannedQty = deliveryItem?.scannedQty ?: 0,
                unit = deliveryItem?.unit ?: "szt",
                revision = 1,
                syncStatus = SyncStatus.PENDING
            )
        }
        productStateDao.insertState(newState)
        triggerSync()

        if (quantity != 0) {
            CoroutineScope(Dispatchers.IO).launch {
                syncPendingStates()
            }
        }
    }

    private fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncWorker",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun fetchActiveDeliveries(): FetchResult {
        return try {
            val response = api.getActiveDeliveries()
            if (response.isSuccessful) {
                FetchResult.ActiveDeliveriesLoaded(response.body().orEmpty())
            } else {
                if (response.code() == 401) FetchResult.InvalidToken
                else FetchResult.Error("Błąd serwera: ${response.code()}")
            }
        } catch (e: Exception) {
            FetchResult.Error(e.message ?: "Błąd połączenia")
        }
    }

    suspend fun fetchCurrentDelivery(force: Boolean = false, deliveryId: String? = null): FetchResult {
        return try {
            val requestedDeliveryId = deliveryId ?: preferencesManager.selectedDeliveryId.first()
            val response = if (requestedDeliveryId.isNotBlank()) {
                api.getDelivery(requestedDeliveryId)
            } else {
                api.getCurrentDelivery()
            }

            if (!response.isSuccessful) {
                return handleDeliveryError(response.code())
            }

            val body = response.body() ?: return FetchResult.Error("Pusta odpowiedź serwera")
            syncFetchedDelivery(body, force)
        } catch (e: Exception) {
            FetchResult.Error(e.message ?: "Nieznany błąd połączenia")
        }
    }

    private suspend fun syncFetchedDelivery(body: DeliveryResponse, force: Boolean): FetchResult {
        if (body.deliveryId.isBlank()) {
            return handleArchivedSelectedDelivery()
        }

        val currentItems = deliveryDao.getAllItems().first()
        val currentDeliveryId = currentItems.firstOrNull()?.deliveryId
        if (!currentDeliveryId.isNullOrBlank() && currentDeliveryId != body.deliveryId && !force) {
            return FetchResult.NewDeliveryAvailable(body.deliveryId, currentDeliveryId)
        }
        if (!currentDeliveryId.isNullOrBlank() && currentDeliveryId != body.deliveryId && force) {
            clearLocalData()
        }

        val items = body.items.map { dto ->
            DeliveryItem(
                barcode = dto.barcode,
                name = dto.name,
                expectedQty = dto.expectedQty,
                deliveryId = body.deliveryId,
                scannedQty = dto.scannedQty,
                unit = dto.unit ?: "szt"
            )
        }

        deliveryDao.updateDeliveryItems(items)
        mergeDeliveryMetadataIntoLocalState(items)
        saveCurrentDeliverySummary(body)
        pullDeviceState(body.deliveryId)
        return FetchResult.Success
    }

    private suspend fun mergeDeliveryMetadataIntoLocalState(items: List<DeliveryItem>) {
        val itemsByBarcode = items.associateBy { it.barcode }
        val localStates = productStateDao.getAllStates().first()

        localStates.forEach { state ->
            val deliveryItem = itemsByBarcode[state.barcode]
            val updatedState = when {
                deliveryItem != null -> state.copy(
                    name = deliveryItem.name,
                    expectedQty = deliveryItem.expectedQty,
                    globalScannedQty = deliveryItem.scannedQty,
                    unit = deliveryItem.unit,
                    fromDelivery = true
                )
                state.fromDelivery -> state.copy(fromDelivery = false)
                else -> state
            }
            if (updatedState != state) {
                productStateDao.insertState(updatedState)
            }
        }
    }

    private suspend fun saveCurrentDeliverySummary(body: DeliveryResponse) {
        preferencesManager.saveCurrentDeliverySummary(
            PreferencesManager.CurrentDeliverySummary(
                deliveryId = body.deliveryId,
                deliveryDisplayName = body.deliveryDisplayName.orEmpty(),
                sourceFileName = body.sourceFileName.orEmpty(),
                supplierName = body.supplierName.orEmpty(),
                commercialDocumentNumber = body.commercialDocumentNumber.orEmpty(),
                warehouseDocumentNumber = body.warehouseDocumentNumber.orEmpty()
            )
        )
    }

    private suspend fun handleDeliveryError(code: Int): FetchResult {
        return when (code) {
            401 -> FetchResult.InvalidToken
            404, 400 -> handleArchivedSelectedDelivery()
            else -> FetchResult.Error("Błąd serwera: $code")
        }
    }

    private suspend fun handleArchivedSelectedDelivery(): FetchResult {
        val currentItems = deliveryDao.getAllItems().first()
        val currentDeliveryId = currentItems.firstOrNull()?.deliveryId
        return if (currentDeliveryId.isNullOrBlank()) {
            preferencesManager.clearCurrentDeliverySummary()
            FetchResult.Success
        } else {
            FetchResult.DeliveryArchived(currentDeliveryId)
        }
    }

    suspend fun pullDeviceState(deliveryId: String? = null) {
        try {
            val targetDeliveryId = deliveryId ?: preferencesManager.selectedDeliveryId.first()
            if (targetDeliveryId.isBlank()) return

            val deviceId = preferencesManager.deviceId.first()
            val response = api.getDeviceStateForDelivery(targetDeliveryId, deviceId)
            val serverStates = if (response.isSuccessful) response.body() else null
            if (serverStates == null) return

            serverStates.forEach { remote ->
                val local = productStateDao.getStateByBarcode(remote.barcode)
                if (local == null || (local.quantity == 0 && remote.quantity > 0)) {
                    val deliveryItem = deliveryDao.getItemByBarcode(remote.barcode)
                    val newState = LocalProductState(
                        barcode = remote.barcode,
                        name = remote.name ?: deliveryItem?.name,
                        quantity = remote.quantity,
                        fromDelivery = remote.fromDelivery,
                        expectedQty = deliveryItem?.expectedQty,
                        globalScannedQty = deliveryItem?.scannedQty ?: 0,
                        unit = remote.unit ?: deliveryItem?.unit ?: "szt",
                        revision = remote.revision,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = try {
                            isoFormat.parse(remote.updatedAt)?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    )
                    productStateDao.insertState(newState)
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun syncPendingStates(): FetchResult? {
        val pending = productStateDao.getPendingSyncStates()
        val selectedDeliveryId = preferencesManager.selectedDeliveryId.first()
        if (selectedDeliveryId.isBlank()) return null

        val deviceId = preferencesManager.deviceId.first()
        val deviceName = preferencesManager.deviceName.first().ifBlank { null }

        pending.forEach { state ->
            val now = System.currentTimeMillis()
            productStateDao.insertState(state.copy(lastSyncAttemptAt = now))

            try {
                val request = DeviceStateRequest(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    barcode = state.barcode,
                    name = state.name,
                    quantity = state.quantity,
                    fromDelivery = state.fromDelivery,
                    updatedAt = isoFormat.format(Date(state.updatedAt)),
                    revision = state.revision,
                    unit = state.unit
                )

                val response = api.updateDeviceStateForDelivery(selectedDeliveryId, request)
                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body?.accepted == true) {
                            productStateDao.insertState(
                                state.copy(
                                    syncStatus = SyncStatus.SYNCED,
                                    lastSyncSuccessAt = now,
                                    remoteQuantityLastSeen = body.serverQuantity
                                )
                            )
                        } else {
                            productStateDao.updateSyncStatus(state.barcode, SyncStatus.FAILED)
                        }
                    }

                    response.code() == 401 -> {
                        productStateDao.updateSyncStatus(state.barcode, SyncStatus.FAILED)
                        return FetchResult.InvalidToken
                    }

                    response.code() == 409 -> {
                        val body = parseConflictBody(response.errorBody()?.string())
                        val reason = body?.reason.orEmpty()
                        productStateDao.updateSyncStatus(state.barcode, SyncStatus.CONFLICT)

                        if (reason == "ITEM_BELONGS_TO_OTHER_DELIVERY" || reason == "ITEM_BELONGS_TO_MULTIPLE_DELIVERIES") {
                            return FetchResult.ScanConflict(
                                barcode = state.barcode,
                                quantity = state.quantity,
                                suggestedDeliveries = body?.suggestedDeliveries.orEmpty()
                            )
                        }

                        if (reason == "STALE_REVISION") {
                            pullDeviceState(selectedDeliveryId)
                        }
                    }

                    else -> {
                        productStateDao.updateSyncStatus(state.barcode, SyncStatus.WAITING_FOR_NETWORK)
                    }
                }
            } catch (_: Exception) {
                productStateDao.updateSyncStatus(state.barcode, SyncStatus.WAITING_FOR_NETWORK)
            }
        }

        return null
    }

    private fun parseConflictBody(errorBody: String?): DeviceStateResponse? {
        return try {
            if (errorBody.isNullOrBlank()) null else gson.fromJson(errorBody, DeviceStateResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearLocalData() {
        deliveryDao.clearAll()
        productStateDao.clearAll()
        preferencesManager.clearCurrentDeliverySummary()
    }

    suspend fun getDeviceStateFromServer(deliveryId: String? = null): List<DeviceStateRequest>? {
        return try {
            val targetDeliveryId = deliveryId ?: preferencesManager.selectedDeliveryId.first()
            if (targetDeliveryId.isBlank()) return null

            val deviceId = preferencesManager.deviceId.first()
            val response = api.getDeviceStateForDelivery(targetDeliveryId, deviceId)
            if (response.isSuccessful) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkHealth(): Pair<Boolean, String?> {
        return try {
            val response = api.checkHealth()
            if (response.isSuccessful) true to null
            else false to "Błąd serwera: ${response.code()}"
        } catch (e: Exception) {
            false to (e.message ?: "Błąd połączenia")
        }
    }

    suspend fun checkToken(): Pair<Boolean, String?> {
        return try {
            val response = api.getActiveDeliveries()
            when (response.code()) {
                200, 404 -> true to null
                401 -> false to "Nieprawidłowy token API"
                else -> false to "Błąd serwera: ${response.code()}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Błąd połączenia")
        }
    }
}
