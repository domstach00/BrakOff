package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(barcode: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val productStates by viewModel.productStates.collectAsState()
    val deliveryItems by viewModel.deliveryItems.collectAsState()
    
    val product = productStates.find { it.barcode == barcode }
    val deliveryItem = deliveryItems.find { it.barcode == barcode }
    
    val displayName = product?.name ?: deliveryItem?.name ?: "Produkt spoza dostawy"
    val isFromDelivery = product?.fromDelivery == true || deliveryItem != null
    val expectedQuantity = product?.expectedQty ?: deliveryItem?.expectedQty
    val unit = product?.unit ?: deliveryItem?.unit ?: "szt"

    var quantityText by remember { mutableStateOf(product?.quantity?.toString() ?: "1") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Szczegóły produktu", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Product Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (!isFromDelivery) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kod: $barcode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (expectedQuantity != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Oczekiwana ilość:", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "$expectedQuantity $unit",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        val globalScanned = product?.globalScannedQty ?: deliveryItem?.scannedQty ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Skanowanie globalne:", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "$globalScanned $unit",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        val globalScanned = product?.globalScannedQty ?: deliveryItem?.scannedQty ?: 0
                        if (globalScanned > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Skanowanie globalne:", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$globalScanned $unit",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
            
            // Conflict Warning
            if (product?.syncStatus == com.wodrol.brakoff.data.local.entity.SyncStatus.CONFLICT) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Konflikt wersji! Dane na serwerze są nowsze. Sprawdź stan na PC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Quantity Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wprowadź ilość ($unit)", style = MaterialTheme.typography.labelLarge)
                    
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentQty = quantityText.toIntOrNull() ?: 0
                        QuickAddButton("-1", -1, currentQty) { quantityText = it.toString() }
                        QuickAddButton("+1", 1, currentQty) { quantityText = it.toString() }
                        QuickAddButton("+2", 2, currentQty) { quantityText = it.toString() }
                        
                        if (isFromDelivery && expectedQuantity != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { quantityText = expectedQuantity.toString() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("$expectedQuantity", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Całość",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    val q = quantityText.toIntOrNull() ?: 0
                    viewModel.updateQuantity(barcode, q)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = quantityText.isNotEmpty() && quantityText.toIntOrNull() != null,
                shape = MaterialTheme.shapes.large
            ) {
                Text("ZATWIERDŹ", style = MaterialTheme.typography.titleLarge)
            }
            
            if (product?.syncStatus != null) {
                Text(
                    text = "Status: ${product.syncStatus.name}",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RowScope.QuickAddButton(label: String, add: Int, current: Int, onUpdate: (Int) -> Unit) {
    OutlinedButton(
        onClick = { onUpdate(current + add) },
        modifier = Modifier.weight(1f)
    ) {
        Text(label)
    }
}
