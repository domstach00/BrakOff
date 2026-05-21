package com.wodrol.brakoff.data.repository

import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.remote.dto.DeviceStateRequest
import com.wodrol.brakoff.util.PreferencesManager
import androidx.work.*
import com.wodrol.brakoff.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BrakOffRepository(
    private val api: BrakOffApi,
    private val deliveryDao: DeliveryDao,
    private val productStateDao: ProductStateDao,
    private val preferencesManager: PreferencesManager,
    private val context: android.content.Context
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    sealed class FetchResult {
        object Success : FetchResult()
        data class NewDeliveryAvailable(val newDeliveryId: String, val oldDeliveryId: String) : FetchResult()
        data class DeliveryArchived(val deliveryId: String) : FetchResult()
        data class Error(val message: String) : FetchResult()
        object InvalidToken : FetchResult()
    }

    val allDeliveryItems: Flow<List<DeliveryItem>> = deliveryDao.getAllItems()
    val allProductStates: Flow<List<LocalProductState>> = productStateDao.getAllStates()

    suspend fun getProductState(barcode: String): LocalProductState? {
        return productStateDao.getStateByBarcode(barcode)
    }

    suspend fun updateProductQuantity(barcode: String, quantity: Int) {
        val existing = productStateDao.getStateByBarcode(barcode)
        val deliveryItem = deliveryDao.getItemByBarcode(barcode)

        val newState = if (existing != null) {
            existing.copy(
                quantity = quantity,
                updatedAt = System.currentTimeMillis(),
                revision = existing.revision + 1,
                syncStatus = SyncStatus.PENDING,
                globalScannedQty = deliveryItem?.scannedQty ?: existing.globalScannedQty
            )
        } else {
            LocalProductState(
                barcode = barcode,
                name = deliveryItem?.name,
                quantity = quantity,
                fromDelivery = deliveryItem != null,
                expectedQty = deliveryItem?.expectedQty,
                globalScannedQty = deliveryItem?.scannedQty ?: 0,
                revision = 1,
                syncStatus = SyncStatus.PENDING
            )
        }
        productStateDao.insertState(newState)
        triggerSync()

        // Automatyczna próba wysyłki dla wartości != 0
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

    suspend fun fetchCurrentDelivery(force: Boolean = false): FetchResult {
        try {
            val response = api.getCurrentDelivery()
            if (response.isSuccessful) {
                val body = response.body() ?: return FetchResult.Error("Pusta odpowiedź serwera")
                
                if (body.deliveryId.isEmpty()) {
                    val currentItems = deliveryDao.getAllItems().first()
                    val currentDeliveryId = currentItems.firstOrNull()?.deliveryId
                    return if (currentDeliveryId.isNullOrEmpty()) {
                        FetchResult.Success
                    } else {
                        FetchResult.DeliveryArchived(currentDeliveryId)
                    }
                }

                val currentItems = deliveryDao.getAllItems().first()
                if (currentItems.isNotEmpty()) {
                    val currentDeliveryId = currentItems.first().deliveryId
                    if (currentDeliveryId != body.deliveryId) {
                        if (!force) {
                            return FetchResult.NewDeliveryAvailable(body.deliveryId, currentDeliveryId)
                        } else {
                            // Wymuszamy nową dostawę - czyścimy WSZYSTKIE stare dane lokalne
                            clearLocalData()
                        }
                    }
                }

                val items = body.items.map { dto ->
                    DeliveryItem(
                        barcode = dto.barcode,
                        name = dto.name,
                        expectedQty = dto.expectedQty,
                        deliveryId = body.deliveryId,
                        scannedQty = dto.scannedQty
                    )
                }
                
                deliveryDao.updateDeliveryItems(items)

                val itemsMap = items.associateBy { it.barcode }
                val localStates = productStateDao.getAllStates().first()
                
                localStates.forEach { state ->
                    val serverItem = itemsMap[state.barcode]
                    if (serverItem != null) {
                        val updatedState = state.copy(
                            name = serverItem.name,
                            expectedQty = serverItem.expectedQty,
                            globalScannedQty = serverItem.scannedQty,
                            fromDelivery = true
                        )
                        if (updatedState != state) {
                            productStateDao.insertState(updatedState)
                        }
                    } else if (state.fromDelivery) {
                        productStateDao.insertState(state.copy(fromDelivery = false))
                    }
                }

                // Pobierz stan tego urządzenia z serwera, aby przywrócić ewentualne utracone dane lokalne
                pullDeviceState()

                return FetchResult.Success
            } else {
                if (response.code() == 401) {
                    return FetchResult.InvalidToken
                }
                if (response.code() == 404 || response.code() == 400) {
                    val currentItems = deliveryDao.getAllItems().first()
                    val currentDeliveryId = currentItems.firstOrNull()?.deliveryId
                    return if (currentDeliveryId.isNullOrEmpty()) {
                        FetchResult.Success
                    } else {
                        FetchResult.DeliveryArchived(currentDeliveryId)
                    }
                }
                return FetchResult.Error("Błąd serwera: ${response.code()}")
            }
        } catch (e: Exception) {
            return FetchResult.Error(e.message ?: "Nieznany błąd połączenia")
        }
    }

    suspend fun pullDeviceState() {
        try {
            val deviceId = preferencesManager.deviceId.first()
            val serverStates = getDeviceStateFromServer() ?: return
            
            serverStates.forEach { remote ->
                val local = productStateDao.getStateByBarcode(remote.barcode)
                // Przywracamy tylko jeśli lokalnie mamy 0 lub w ogóle nie mamy wpisu, 
                // a serwer twierdzi że coś już zeskanowaliśmy na TYM urządzeniu.
                if (local == null || (local.quantity == 0 && remote.quantity > 0)) {
                    val deliveryItem = deliveryDao.getItemByBarcode(remote.barcode)
                    val newState = LocalProductState(
                        barcode = remote.barcode,
                        name = remote.name ?: deliveryItem?.name,
                        quantity = remote.quantity,
                        fromDelivery = remote.fromDelivery,
                        expectedQty = deliveryItem?.expectedQty,
                        globalScannedQty = deliveryItem?.scannedQty ?: 0,
                        revision = remote.revision,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = try { isoFormat.parse(remote.updatedAt)?.time ?: System.currentTimeMillis() } catch(e: Exception) { System.currentTimeMillis() }
                    )
                    productStateDao.insertState(newState)
                }
            }
        } catch (e: Exception) {
            // Cichy błąd przy pullu
        }
    }

    suspend fun syncPendingStates() {
        val pending = productStateDao.getPendingSyncStates()
        val deviceId = preferencesManager.deviceId.first()
        val deviceName = preferencesManager.deviceName.first()
        
        pending.forEach { state ->
            val now = System.currentTimeMillis()
            productStateDao.insertState(state.copy(lastSyncAttemptAt = now))
            
            try {
                val deliveryItem = deliveryDao.getItemByBarcode(state.barcode)
                val deliveryId = deliveryItem?.deliveryId ?: "default"

                val request = DeviceStateRequest(
                    deliveryId = deliveryId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    barcode = state.barcode,
                    name = state.name,
                    quantity = state.quantity,
                    fromDelivery = state.fromDelivery,
                    updatedAt = isoFormat.format(Date(state.updatedAt)),
                    revision = state.revision
                )

                val response = api.updateDeviceState(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.accepted == true) {
                        val syncedState = state.copy(
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncSuccessAt = now,
                            remoteQuantityLastSeen = body.serverQuantity
                        )
                        productStateDao.insertState(syncedState)
                    } else {
                        productStateDao.updateSyncStatus(state.barcode, SyncStatus.FAILED)
                    }
                } else if (response.code() == 401) {
                    productStateDao.updateSyncStatus(state.barcode, SyncStatus.FAILED)
                } else if (response.code() == 409) {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (errorBody.contains("DELIVERY_MISMATCH")) {
                        fetchCurrentDelivery(force = false)
                    } else {
                        // STALE_REVISION lub inny konflikt wersji
                        pullDeviceState()
                    }
                    productStateDao.updateSyncStatus(state.barcode, SyncStatus.CONFLICT)
                } else {
                    productStateDao.updateSyncStatus(state.barcode, SyncStatus.WAITING_FOR_NETWORK)
                }
            } catch (e: Exception) {
                productStateDao.updateSyncStatus(state.barcode, SyncStatus.WAITING_FOR_NETWORK)
            }
        }
    }

    suspend fun clearLocalData() {
        deliveryDao.clearAll()
        productStateDao.clearAll()
    }

    suspend fun getDeviceStateFromServer(): List<DeviceStateRequest>? {
        return try {
            val deviceId = preferencesManager.deviceId.first()
            val response = api.getDeviceState(deviceId)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun checkHealth(): Pair<Boolean, String?> {
        return try {
            val response = api.checkHealth()
            if (response.isSuccessful) {
                true to null
            } else {
                false to "Błąd serwera: ${response.code()}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Błąd połączenia")
        }
    }

    suspend fun checkToken(): Pair<Boolean, String?> {
        return try {
            val response = api.getCurrentDelivery()
            when (response.code()) {
                200, 404, 400 -> true to null
                401 -> false to "Nieprawidłowy token API"
                else -> false to "Błąd serwera: ${response.code()}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Błąd połączenia")
        }
    }
}
