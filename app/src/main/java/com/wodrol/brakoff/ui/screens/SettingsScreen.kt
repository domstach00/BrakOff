package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val isOnline by viewModel.isServerOnline.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val fetchResult by viewModel.fetchResult.collectAsState()
    val scanButtonLeft by viewModel.scanButtonLeft.collectAsState()

    var urlInput by remember { mutableStateOf(serverUrl) }
    var nameInput by remember { mutableStateOf(deviceName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(serverUrl) { urlInput = serverUrl }
    LaunchedEffect(deviceName) { nameInput = deviceName }

    LaunchedEffect(fetchResult) {
        when (val result = fetchResult) {
            is com.wodrol.brakoff.data.repository.BrakOffRepository.FetchResult.Success -> {
                snackbarHostState.showSnackbar("Synchronizacja zakończona pomyślnie")
                viewModel.clearFetchResult()
            }
            is com.wodrol.brakoff.data.repository.BrakOffRepository.FetchResult.Error -> {
                snackbarHostState.showSnackbar("Błąd: ${result.message}")
                viewModel.clearFetchResult()
            }
            else -> {}
        }
    }

    when (val result = fetchResult) {
        is com.wodrol.brakoff.data.repository.BrakOffRepository.FetchResult.NewDeliveryAvailable -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearFetchResult() },
                title = { Text("Dostępna nowa dostawa") },
                text = { Text("Na serwerze jest nowa dostawa (${result.newDeliveryId}). Twoje obecne dane lokalne z dostawy ${result.oldDeliveryId} zostaną usunięte. Czy chcesz kontynuować?") },
                confirmButton = {
                    Button(onClick = { viewModel.fetchDelivery(force = true) }) { Text("TAK, NADPISZ") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearFetchResult() }) { Text("ANULUJ") }
                }
            )
        }
        is com.wodrol.brakoff.data.repository.BrakOffRepository.FetchResult.DeliveryArchived -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearFetchResult() },
                title = { Text("Dostawa zakończona") },
                text = { Text("Ta dostawa została już zarchiwizowana na serwerze PC. Czy chcesz wyczyścić dane z tego telefonu, aby przygotować się do kolejnej pracy?") },
                confirmButton = {
                    Button(
                        onClick = { 
                            viewModel.clearData()
                            viewModel.clearFetchResult()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { 
                        Text("WYCZYŚĆ DANE") 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearFetchResult() }) { 
                        Text("ZOSTAW DANE") 
                    }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Adres serwera PC (np. http://192.168.1.10:8080)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://192.168.1.xxx:8080") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { 
                    viewModel.saveServerUrl(urlInput)
                    scope.launch { snackbarHostState.showSnackbar("Adres zapisany") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zapisz adres")
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Nazwa urządzenia") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { 
                    viewModel.saveDeviceName(nameInput)
                    scope.launch { snackbarHostState.showSnackbar("Nazwa zapisana") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zapisz nazwę")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Preferencje UI", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Przycisk skanowania po lewej")
                Switch(
                    checked = scanButtonLeft,
                    onCheckedChange = { viewModel.setScanButtonLeft(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when(isOnline) {
                        true -> Color(0xFFE8F5E9)
                        false -> Color(0xFFFFEBEE)
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when(isOnline) {
                                true -> Icons.Default.Check
                                false -> Icons.Default.Warning
                                null -> Icons.Default.Refresh
                            },
                            contentDescription = null,
                            tint = when(isOnline) {
                                true -> Color(0xFF2E7D32)
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = when(isOnline) {
                                true -> "Serwer online"
                                false -> "Serwer offline"
                                null -> "Status nieznany"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when(isOnline) {
                                true -> Color(0xFF2E7D32)
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    if (isOnline == false && connectionError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = connectionError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device ID: $deviceId",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val isChecking by viewModel.isCheckingConnection.collectAsState()
                    val isSyncing by viewModel.isSyncing.collectAsState()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.checkServerHealth() },
                            enabled = !isChecking,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Testuj połączenie")
                            }
                        }

                        Button(
                            onClick = { viewModel.refreshAllData() },
                            enabled = isOnline == true && !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Synchronizuj")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("WYCZYŚĆ DANE DOSTAWY Z TELEFONU")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Potwierdź usunięcie") },
            text = { Text("Czy na pewno chcesz usunąć wszystkie dane dostawy z tego telefonu? Ta operacja jest nieodwracalna.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearData()
                        showDeleteConfirm = false
                        scope.launch { snackbarHostState.showSnackbar("Dane zostały usunięte") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("TAK, USUŃ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ANULUJ")
                }
            }
        )
    }
}
