package com.wodrol.brakoff.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.repository.BrakOffRepository
import com.wodrol.brakoff.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val repository: BrakOffRepository,
    private val preferencesManager: PreferencesManager,
    private val startBackgroundJobs: Boolean = true
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val deliveryItems: StateFlow<List<DeliveryItem>> = repository.allDeliveryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productStates: StateFlow<List<LocalProductState>> = repository.allProductStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeDisplayList: StateFlow<List<LocalProductState>> = combine(
        deliveryItems,
        productStates,
        _searchQuery
    ) { delivery, states, query ->
        val stateMap = states.associateBy { it.barcode }
        val allBarcodes = (delivery.map { it.barcode } + states.map { it.barcode }).distinct()
        
        allBarcodes.map { barcode ->
            val state = stateMap[barcode]
            val deliveryItem = delivery.find { it.barcode == barcode }
            
            val item = state ?: LocalProductState(
                barcode = barcode,
                name = deliveryItem?.name,
                quantity = 0,
                fromDelivery = deliveryItem != null,
                expectedQty = deliveryItem?.expectedQty,
                globalScannedQty = deliveryItem?.scannedQty ?: 0,
                unit = deliveryItem?.unit ?: "szt",
                syncStatus = SyncStatus.SYNCED
            )
            
            // Prefer unit and name from delivery if available
            if (deliveryItem != null) {
                item.copy(
                    unit = deliveryItem.unit,
                    name = deliveryItem.name,
                    expectedQty = deliveryItem.expectedQty
                )
            } else {
                item
            }
        }.filter {
            it.barcode.contains(query, ignoreCase = true) || 
            (it.name?.contains(query, ignoreCase = true) == true)
        }.sortedWith(
            compareByDescending<LocalProductState> { it.syncStatus == SyncStatus.FAILED || it.syncStatus == SyncStatus.CONFLICT }
            .thenBy { 
                // Produkty NIEUKOŃCZONE (globalScannedQty < expectedQty) idą na górę
                val isCompleted = it.expectedQty != null && it.globalScannedQty >= it.expectedQty
                isCompleted 
            }
            .thenBy { it.quantity > 0 } // Te z ilością 0 idą wyżej niż te już zaczęte
            .thenBy { it.name ?: "" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverUrl: StateFlow<String> = preferencesManager.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val deviceName: StateFlow<String?> = preferencesManager.deviceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deviceId: StateFlow<String> = preferencesManager.deviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val apiToken: StateFlow<String> = preferencesManager.apiToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val scanButtonLeft: StateFlow<Boolean> = preferencesManager.scanButtonLeft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedDeliveryId: StateFlow<String> = preferencesManager.selectedDeliveryId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val currentDeliverySummary: StateFlow<PreferencesManager.CurrentDeliverySummary> =
        preferencesManager.currentDeliverySummary
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                PreferencesManager.CurrentDeliverySummary()
            )

    val dismissedArchiveId: StateFlow<String> = preferencesManager.dismissedArchiveId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isServerOnline = MutableStateFlow<Boolean?>(null)
    val isServerOnline: StateFlow<Boolean?> = _isServerOnline.asStateFlow()

    private val _isStatusVisible = MutableStateFlow(true)
    val isStatusVisible: StateFlow<Boolean> = _isStatusVisible.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    private val _isScanningNetwork = MutableStateFlow(false)
    val isScanningNetwork: StateFlow<Boolean> = _isScanningNetwork.asStateFlow()

    private val _activeDeliveries = MutableStateFlow<List<com.wodrol.brakoff.data.remote.dto.ActiveDeliveryDto>>(emptyList())
    val activeDeliveries: StateFlow<List<com.wodrol.brakoff.data.remote.dto.ActiveDeliveryDto>> = _activeDeliveries.asStateFlow()

    private val _autoScanEnabled = preferencesManager.autoScanEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoScanEnabled: StateFlow<Boolean> = _autoScanEnabled

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())
    val verificationResults: StateFlow<List<VerificationResult>> = _verificationResults.asStateFlow()

    private val _manualBarcodeQuery = MutableStateFlow("")
    val manualBarcodeQuery: StateFlow<String> = _manualBarcodeQuery.asStateFlow()

    val scannerDisplayList: StateFlow<List<LocalProductState>> = combine(
        deliveryItems,
        productStates,
        _manualBarcodeQuery
    ) { delivery, states, query ->
        val stateMap = states.associateBy { it.barcode }
        val allBarcodes = (delivery.map { it.barcode } + states.map { it.barcode }).distinct()
        
        allBarcodes.map { barcode ->
            val state = stateMap[barcode]
            val deliveryItem = delivery.find { it.barcode == barcode }
            
            val item = state ?: LocalProductState(
                barcode = barcode,
                name = deliveryItem?.name,
                quantity = 0,
                fromDelivery = deliveryItem != null,
                expectedQty = deliveryItem?.expectedQty,
                globalScannedQty = deliveryItem?.scannedQty ?: 0,
                unit = deliveryItem?.unit ?: "szt",
                syncStatus = SyncStatus.SYNCED
            )
            
            // Prefer unit and name from delivery if available
            if (deliveryItem != null) {
                item.copy(
                    unit = deliveryItem.unit,
                    name = deliveryItem.name,
                    expectedQty = deliveryItem.expectedQty
                )
            } else {
                item
            }
        }.filter {
            query.isEmpty() || 
            it.barcode.contains(query, ignoreCase = true) || 
            (it.name?.contains(query, ignoreCase = true) == true)
        }.sortedWith(
            compareByDescending<LocalProductState> { it.syncStatus == SyncStatus.FAILED || it.syncStatus == SyncStatus.CONFLICT }
            .thenBy { 
                val isCompleted = it.expectedQty != null && it.globalScannedQty >= it.expectedQty
                isCompleted 
            }
            .thenBy { it.quantity > 0 }
            .thenBy { it.name ?: "" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (startBackgroundJobs) {
            startAutoSync()
            startConnectionMonitor()
        }
    }

    private var monitorJob: kotlinx.coroutines.Job? = null

    private fun startConnectionMonitor() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            var isFirstCheck = true
            while (true) {
                _isCheckingConnection.value = true
                val (online, _) = repository.checkHealth()
                val wasOffline = _isServerOnline.value == false
                _isServerOnline.value = online
                _isCheckingConnection.value = false

                if (online) {
                    // Jeśli połączenie wróciło, wymuś synchronizację oczekujących produktów
                    repository.syncPendingStates()

                    if (isFirstCheck || wasOffline) {
                        // Pokaż "Połączono" na 5 sekund przy pierwszym uruchomieniu lub po odzyskaniu połączenia
                        _isStatusVisible.value = true
                        launch {
                            delay(5.seconds)
                            // Ukryj tylko jeśli nadal jesteśmy online
                            if (_isServerOnline.value == true) {
                                _isStatusVisible.value = false
                            }
                        }
                    }
                } else {
                    // Przy braku połączenia zawsze pokazujemy status
                    _isStatusVisible.value = true
                }

                isFirstCheck = false
                val delayTime = if (online) 45.seconds else 15.seconds
                delay(delayTime)
            }
        }
    }

    fun onResume() {
        // Przy wznowieniu wymuszamy sprawdzenie i pokazanie statusu
        viewModelScope.launch {
            _isStatusVisible.value = true
            val (online, _) = repository.checkHealth()
            _isServerOnline.value = online
            if (online) {
                delay(5.seconds)
                if (_isServerOnline.value == true) {
                    _isStatusVisible.value = false
                }
            }
        }
        // Restartujemy monitor, aby odliczał od teraz
        startConnectionMonitor()
    }

    private fun startAutoSync() {
        viewModelScope.launch {
            while (true) {
                try {
                    // 0. Sprawdź czy mamy wybraną dostawę, jeśli nie - pobierz listę
                    if (selectedDeliveryId.value.isEmpty()) {
                        fetchActiveDeliveries()
                        delay(15.seconds)
                        continue
                    }

                    // 1. Wysyłamy lokalne zmiany na serwer
                    val syncResult = repository.syncPendingStates()
                    if (syncResult is BrakOffRepository.FetchResult.ScanConflict ||
                        syncResult is BrakOffRepository.FetchResult.InvalidToken
                    ) {
                        _fetchResult.value = syncResult
                    }
                    
                    // 2. Pobieramy aktualne dane z serwera (np. zmiany w expectedQty)
                    // force = false sprawia, że jeśli dostawa się nie zmieniła, 
                    // dane zostaną zaktualizowane po cichu w tle.
                    val result = repository.fetchCurrentDelivery(force = false)
                    
                    // Jeśli pojawiła się zupełnie nowa dostawa (inny ID) lub stara została zakończona,
                    // powiadamiamy użytkownika przez _fetchResult
                    if (result is BrakOffRepository.FetchResult.NewDeliveryAvailable || 
                        result is BrakOffRepository.FetchResult.DeliveryArchived ||
                        result is BrakOffRepository.FetchResult.ActiveDeliveriesLoaded) {
                        _fetchResult.value = result
                        if (result is BrakOffRepository.FetchResult.ActiveDeliveriesLoaded) {
                            _activeDeliveries.value = result.deliveries
                        }
                    }
                    
                    // 3. Sprawdzamy stan połączenia
                    repository.checkHealth()
                } catch (e: Exception) {
                    // Ignorujemy błędy w auto-sync, by nie irytować użytkownika
                }
                delay(15.seconds)
            }
        }
    }

    val filteredDeliveryItems: StateFlow<List<DeliveryItem>> = _manualBarcodeQuery
        .combine(deliveryItems) { query: String, items: List<DeliveryItem> ->
            if (query.isEmpty()) emptyList<DeliveryItem>()
            else items.filter { it.barcode.contains(query) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setManualBarcodeQuery(query: String) {
        _manualBarcodeQuery.value = query
    }

    fun updateQuantity(barcode: String, quantity: Int) {
        viewModelScope.launch {
            repository.updateProductQuantity(barcode, quantity)

            when (val syncResult = repository.syncPendingStates()) {
                is BrakOffRepository.FetchResult.ScanConflict,
                is BrakOffRepository.FetchResult.InvalidToken,
                is BrakOffRepository.FetchResult.Error -> {
                    _fetchResult.value = syncResult
                }
                else -> {
                    val state = repository.getProductState(barcode)
                    if (state?.syncStatus == SyncStatus.CONFLICT) {
                        checkConflictSuggestions()
                    }
                }
            }
        }
    }

    private fun checkConflictSuggestions() {
        viewModelScope.launch {
            // We need to pass the selectedDeliveryId if we want to ensure we get suggestions for the right context,
            // but repository.fetchCurrentDelivery(force = false) uses preferencesManager.selectedDeliveryId.first()
            // which should be correct.
            val result = repository.fetchCurrentDelivery(force = false)
            if (result is BrakOffRepository.FetchResult.ScanConflict) {
                _fetchResult.value = result
            }
        }
    }

    fun switchDelivery(deliveryId: String, retryBarcode: String? = null, retryQuantity: Int? = null) {
        viewModelScope.launch {
            _isSyncing.value = true
            preferencesManager.saveSelectedDeliveryId(deliveryId)
            repository.clearLocalData()
            val result = repository.fetchCurrentDelivery(force = true, deliveryId = deliveryId)
            _fetchResult.value = result
            
            if (retryBarcode != null && retryQuantity != null) {
                repository.updateProductQuantity(retryBarcode, retryQuantity)
            }
            _isSyncing.value = false
        }
    }

    fun fetchActiveDeliveries() {
        viewModelScope.launch {
            val result = repository.fetchActiveDeliveries()
            if (result is BrakOffRepository.FetchResult.ActiveDeliveriesLoaded) {
                _activeDeliveries.value = result.deliveries
                if (result.deliveries.size == 1 && selectedDeliveryId.value.isBlank()) {
                    switchDelivery(result.deliveries.first().deliveryId)
                    return@launch
                }
            }
            _fetchResult.value = result
        }
    }

    private val _fetchResult = MutableStateFlow<BrakOffRepository.FetchResult?>(null)
    val fetchResult: StateFlow<BrakOffRepository.FetchResult?> = _fetchResult.asStateFlow()

    fun fetchDelivery(force: Boolean = false) {
        viewModelScope.launch {
            if (selectedDeliveryId.value.isBlank()) {
                fetchActiveDeliveries()
            } else {
                _fetchResult.value = repository.fetchCurrentDelivery(force)
            }
            checkServerHealth()
        }
    }

    fun clearFetchResult() {
        _fetchResult.value = null
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.saveServerUrl(url)
        }
    }

    fun saveDeviceName(name: String) {
        viewModelScope.launch {
            preferencesManager.saveDeviceName(name)
        }
    }

    fun saveApiToken(token: String) {
        viewModelScope.launch {
            preferencesManager.saveApiToken(token)
        }
    }

    fun setScanButtonLeft(isLeft: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveScanButtonLeft(isLeft)
        }
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoScanEnabled(enabled)
        }
    }

    fun startNetworkScan() {
        if (_isScanningNetwork.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningNetwork.value = true
            _connectionError.value = "Skanowanie sieci..."
            
            val localIp = com.wodrol.brakoff.util.NetworkUtils.getLocalIpAddress()
            if (localIp == null) {
                _connectionError.value = "Brak połączenia z WiFi"
                _isScanningNetwork.value = false
                return@launch
            }

            val subnet = com.wodrol.brakoff.util.NetworkUtils.getSubnet(localIp)
            if (subnet == null) {
                _connectionError.value = "Nie można określić podsieci"
                _isScanningNetwork.value = false
                return@launch
            }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            // Skanujemy zakres 1-254
            for (i in 1..254) {
                if (!_isScanningNetwork.value) break
                val testIp = "$subnet$i"
                if (testIp == localIp) continue

                val url = "http://$testIp:8080/api/health"
                val request = okhttp3.Request.Builder().url(url).build()
                
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val foundUrl = "http://$testIp:8080"
                            saveServerUrl(foundUrl)
                            _isScanningNetwork.value = false
                            _connectionError.value = null
                            checkServerHealth()
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // Ignorujemy błędy połączenia podczas skanowania
                }
            }
            
            _isScanningNetwork.value = false
            if (serverUrl.value.isEmpty()) {
                _connectionError.value = "Nie znaleziono serwera"
            }
        }
    }

    fun stopNetworkScan() {
        _isScanningNetwork.value = false
    }

    fun dismissArchive(deliveryId: String) {
        viewModelScope.launch {
            preferencesManager.saveDismissedArchiveId(deliveryId)
        }
    }

    fun clearData() {
        viewModelScope.launch {
            repository.clearLocalData()
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            if (serverUrl.value.isEmpty()) {
                if (autoScanEnabled.value) {
                    startNetworkScan()
                } else {
                    _connectionError.value = "Brak skonfigurowanego adresu serwera"
                }
                return@launch
            }

            _isCheckingConnection.value = true
            _connectionError.value = null
            val (online, error) = repository.checkHealth()
            
            if (online) {
                val (tokenOk, tokenError) = repository.checkToken()
                _isServerOnline.value = tokenOk
                _connectionError.value = tokenError
            } else {
                _isServerOnline.value = false
                _connectionError.value = error
            }

            _isCheckingConnection.value = false
        }
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun refreshAllData() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncPendingStates()
                _fetchResult.value = repository.fetchCurrentDelivery(force = true)
            } catch (e: Exception) {
                _fetchResult.value = BrakOffRepository.FetchResult.Error(e.message ?: "Błąd synchronizacji")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            val syncResult = repository.syncPendingStates()
            _isSyncing.value = false
            if (syncResult is BrakOffRepository.FetchResult.ScanConflict ||
                syncResult is BrakOffRepository.FetchResult.InvalidToken
            ) {
                _fetchResult.value = syncResult
            }
            // Po wymuszonej synchronizacji odświeżamy weryfikację
            verifyWithServer()
        }
    }

    fun verifyWithServer() {
        viewModelScope.launch {
            val serverState = repository.getDeviceStateFromServer()
            val localState = productStates.value
            
            if (serverState == null) {
                // Handle error
                return@launch
            }

            val results = mutableListOf<VerificationResult>()
            val serverMap = serverState.associateBy { it.barcode }
            val localMap = localState.associateBy { it.barcode }

            val allBarcodes = serverMap.keys + localMap.keys

            allBarcodes.forEach { barcode ->
                val local = localMap[barcode]
                val server = serverMap[barcode]
                
                val status = when {
                    local?.quantity == server?.quantity -> VerificationStatus.MATCH
                    server == null -> VerificationStatus.MISSING_ON_SERVER
                    local == null -> VerificationStatus.ONLY_ON_SERVER
                    else -> VerificationStatus.DIFFERENT_VALUE
                }
                
                val unit = local?.unit ?: server?.unit ?: "szt"
                
                results.add(VerificationResult(barcode, local?.quantity, server?.quantity, status, unit))
            }
            _verificationResults.value = results
        }
    }
}

data class VerificationResult(
    val barcode: String,
    val localQty: Int?,
    val serverQty: Int?,
    val status: VerificationStatus,
    val unit: String = "szt"
)

enum class VerificationStatus {
    MATCH,
    MISSING_ON_SERVER,
    DIFFERENT_VALUE,
    ONLY_ON_SERVER
}
