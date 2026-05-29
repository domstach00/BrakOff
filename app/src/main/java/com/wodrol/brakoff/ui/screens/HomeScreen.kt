package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.ui.viewmodel.MainViewModel
import com.wodrol.brakoff.data.repository.BrakOffRepository
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.ui.theme.SuccessGreen
import com.wodrol.brakoff.ui.theme.ErrorRed
import com.wodrol.brakoff.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onScanClick: () -> Unit,
    onVerifyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val displayItems by viewModel.homeDisplayList.collectAsState()
    val serverOnline by viewModel.isServerOnline.collectAsState()
    val isStatusVisible by viewModel.isStatusVisible.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val fetchResult by viewModel.fetchResult.collectAsState()
    val dismissedArchiveId by viewModel.dismissedArchiveId.collectAsState()
    val scanButtonLeft by viewModel.scanButtonLeft.collectAsState()
    
    val isScanningNetwork by viewModel.isScanningNetwork.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(deviceName) {
        if (deviceName == "") {
            showNameDialog = true
        } else if (deviceName != null) {
            showNameDialog = false
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismiss if blank */ },
            title = { Text("Nazwa urządzenia") },
            text = {
                Column {
                    Text("Podaj nazwę dla tego urządzenia.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nazwa urządzenia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.saveDeviceName(nameInput)
                            showNameDialog = false
                        }
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("ZAPISZ")
                }
            }
        )
    }

    LaunchedEffect(fetchResult) {
        when (val result = fetchResult) {
            is BrakOffRepository.FetchResult.Success -> {
                snackbarHostState.showSnackbar("Dostawa pobrana pomyślnie")
                viewModel.clearFetchResult()
            }
            is BrakOffRepository.FetchResult.Error -> {
                snackbarHostState.showSnackbar("Błąd: ${result.message}")
                viewModel.clearFetchResult()
            }
            is BrakOffRepository.FetchResult.InvalidToken -> {
                snackbarHostState.showSnackbar("Nieprawidłowy token API")
                viewModel.clearFetchResult()
            }
            else -> {}
        }
    }

    when (val result = fetchResult) {
        is BrakOffRepository.FetchResult.NewDeliveryAvailable -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearFetchResult() },
                title = { Text("Wykryto zmianę dostawy") },
                text = { Text("Serwer rozpoczął nową dostawę (${result.newDeliveryId}). Twoje obecne dane lokalne z poprzedniej dostawy (${result.oldDeliveryId}) są już nieaktualne. Czy chcesz je usunąć i pobrać nową listę?") },
                confirmButton = {
                    Button(onClick = { viewModel.fetchDelivery(force = true) }) { Text("TAK, NOWA DOSTAWA") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearFetchResult() }) { Text("ANULUJ") }
                }
            )
        }
        is BrakOffRepository.FetchResult.DeliveryArchived -> {
            if (dismissedArchiveId != result.deliveryId) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearFetchResult() },
                    title = { Text("Dostawa zakończona") },
                    text = { Text("Ta dostawa (${result.deliveryId}) została już zarchiwizowana na serwerze. Czy chcesz wyczyścić dane z tego telefonu?") },
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
                        TextButton(onClick = { 
                            viewModel.dismissArchive(result.deliveryId)
                            viewModel.clearFetchResult() 
                        }) { 
                            Text("ZOSTAW DANE") 
                        }
                    }
                )
            }
        }
        else -> {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BrakOff - dostawy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = deviceName ?: "Wczytywanie...", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchDelivery() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("SKANUJ") }
            )
        },
        floatingActionButtonPosition = if (scanButtonLeft) FabPosition.Start else FabPosition.End
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isStatusVisible || isScanningNetwork) {
                ServerStatusBar(
                    isOnline = serverOnline, 
                    isScanning = isScanningNetwork,
                    errorText = connectionError,
                    onStopScan = { viewModel.stopNetworkScan() }
                )
            }

            // Search and Verification Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Szukaj...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                
                val isSyncing by viewModel.isSyncing.collectAsState()
                
                IconButton(
                    onClick = { viewModel.forceSync() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                        .size(56.dp),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Wyślij na serwer", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

                IconButton(
                    onClick = onVerifyClick,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium)
                        .size(56.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Weryfikuj", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayItems) { state ->
                    ProductItem(state, onNavigateToDetails)
                }
            }
        }
    }
}

@Composable
fun ProductItem(state: com.wodrol.brakoff.data.local.entity.LocalProductState, onNavigateToDetails: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetails(state.barcode) },
        colors = CardDefaults.cardColors(
            containerColor = when (state.syncStatus) {
                SyncStatus.CONFLICT -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name ?: "Nieznany produkt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.barcode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val isCompleted = state.expectedQty != null && state.globalScannedQty >= state.expectedQty
                    
                    Text(
                        text = "${state.quantity}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val totalScanned = state.globalScannedQty
                    if (state.expectedQty != null) {
                        Text(
                            text = " ($totalScanned) / ${state.expectedQty}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else if (totalScanned > 0) {
                        Text(
                            text = " ($totalScanned)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                SyncStatusBadge(state.syncStatus)
            }
        }
    }
}

@Composable
fun SyncStatusBadge(status: SyncStatus) {
    val (text, color) = when (status) {
        SyncStatus.SYNCED -> "ZSYNCHRONIZOWANO" to SuccessGreen
        SyncStatus.PENDING -> "OCZEKUJE" to WarningOrange
        SyncStatus.FAILED -> "BŁĄD" to ErrorRed
        SyncStatus.CONFLICT -> "KONFLIKT" to ErrorRed
        SyncStatus.WAITING_FOR_NETWORK -> "OCZEKUJE NA POŁĄCZENIE" to Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ServerStatusBar(
    isOnline: Boolean?,
    isScanning: Boolean = false,
    errorText: String? = null,
    onStopScan: () -> Unit = {}
) {
    val backgroundColor = when {
        isScanning -> MaterialTheme.colorScheme.tertiary
        isOnline == true -> SuccessGreen
        isOnline == false -> ErrorRed
        else -> Color.Gray
    }
    
    val statusText = when {
        isScanning -> errorText ?: "Skanowanie sieci..."
        isOnline == true -> "Połączono"
        isOnline == false -> errorText ?: "Brak połączenia"
        else -> "Sprawdzanie połączenia..."
    }
    
    val icon = when {
        isScanning -> Icons.Default.Search
        isOnline == true -> Icons.Default.CheckCircle
        isOnline == false -> Icons.Default.Warning
        else -> Icons.Default.Refresh
    }

    Surface(
        color = backgroundColor,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isScanning) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PRZERWIJ",
                    modifier = Modifier.clickable { onStopScan() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            }
        }
    }
}
