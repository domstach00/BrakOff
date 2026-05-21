package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.ui.viewmodel.MainViewModel
import com.wodrol.brakoff.ui.viewmodel.VerificationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val results by viewModel.verificationResults.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.verifyWithServer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weryfikacja z serwerem") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val matchCount = results.count { it.status == VerificationStatus.MATCH }
            val diffCount = results.count { it.status != VerificationStatus.MATCH }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Zgodne", style = MaterialTheme.typography.labelMedium)
                    Text("$matchCount", style = MaterialTheme.typography.headlineMedium, color = Color.Green)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rozbieżne", style = MaterialTheme.typography.labelMedium)
                    Text("$diffCount", style = MaterialTheme.typography.headlineMedium, color = Color.Red)
                }
            }

            Divider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { result ->
                    ListItem(
                        headlineContent = { Text("Kod: ${result.barcode}") },
                        supportingContent = {
                            Text("Lokalnie: ${result.localQty ?: 0} | Na Serwerze: ${result.serverQty ?: 0}")
                        },
                        trailingContent = {
                            val color = when (result.status) {
                                VerificationStatus.MATCH -> Color.Green
                                VerificationStatus.MISSING_ON_SERVER -> Color.Yellow
                                VerificationStatus.ONLY_ON_SERVER -> Color.Cyan
                                VerificationStatus.DIFFERENT_VALUE -> Color.Red
                            }
                            Text(result.status.name, color = color)
                        }
                    )
                }
            }
            
            Button(
                onClick = { viewModel.verifyWithServer() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Odśwież")
            }
        }
    }
}
