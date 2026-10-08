package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.ui.viewmodel.MainViewModel
import com.wodrol.brakoff.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveDeliveriesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val selectedDeliveryId by viewModel.selectedDeliveryId.collectAsState()
    val activeDeliveries by viewModel.activeDeliveries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktywne dostawy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Button(
                onClick = { viewModel.fetchActiveDeliveries() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Odśwież listę dostaw")
            }

            Text("Wybierz aktywną dostawę", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            if (activeDeliveries.isNotEmpty()) {
                activeDeliveries.forEach { delivery ->
                    ElevatedCard(
                        onClick = { viewModel.switchDelivery(delivery.deliveryId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (delivery.deliveryId == selectedDeliveryId)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = delivery.deliveryId == selectedDeliveryId,
                                onClick = { viewModel.switchDelivery(delivery.deliveryId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(delivery.uiTitle(), style = MaterialTheme.typography.titleMedium)
                                delivery.uiSubtitle()?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                delivery.activatedAt?.let { activated ->
                                    val formattedDate = DateUtils.formatIsoToShortDisplay(activated)
                                    if (formattedDate.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Aktywna od: " + formattedDate,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val shortId = if (delivery.deliveryId.length > 6) "...${delivery.deliveryId.takeLast(6)}" else delivery.deliveryId
                            Text(
                                text = shortId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Bottom)
                            )
                        }
                    }
                }
            } else {
                Text("Brak aktywnych dostaw", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
