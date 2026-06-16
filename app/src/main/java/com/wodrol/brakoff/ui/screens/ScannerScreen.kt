package com.wodrol.brakoff.ui.screens

import android.util.Log
import android.os.Vibrator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.wodrol.brakoff.ui.viewmodel.MainViewModel

@OptIn(ExperimentalGetImage::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(viewModel: MainViewModel, onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasScanned by remember { mutableStateOf(false) }
    
    val manualQuery by viewModel.manualBarcodeQuery.collectAsState()
    val displayItems by viewModel.scannerDisplayList.collectAsState()
    val currentDeliverySummary by viewModel.currentDeliverySummary.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentDeliverySummary.deliveryId.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        currentDeliverySummary.title(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    currentDeliverySummary.subtitle()?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Górna część z kamerą (zajmuje 60% wysokości)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val scanner = BarcodeScanning.getClient()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !hasScanned) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { value ->
                                                    hasScanned = true
                                                    // Vibrate on successful scan
                                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        vibrator.vibrate(200)
                                                    }
                                                    onBarcodeScanned(value)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("ScannerScreen", "Use case binding failed", e)
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Czerwona linia lasera (stała, centralna, z odstępami 1/6 po bokach)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(4f / 6f) // 1 - (1/6 + 1/6) = 4/6
                        .height(2.dp)
                        .align(Alignment.Center)
                        .background(Color.Red.copy(alpha = 0.8f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Wymagane uprawnienie do kamery",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (hasScanned) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Dolna część z ręcznym wpisywaniem (zajmuje 40% wysokości)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Ręczne wpisywanie",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = manualQuery,
                    onValueChange = { viewModel.setManualBarcodeQuery(it) },
                    placeholder = { Text("Wpisz kod kreskowy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                if (displayItems.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayItems) { state ->
                            ScannerProductItem(state, onBarcodeScanned)
                        }
                    }
                } else if (manualQuery.isNotEmpty()) {
                    Button(
                        onClick = { 
                            val code = manualQuery
                            viewModel.setManualBarcodeQuery("")
                            onBarcodeScanned(code) 
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Zatwierdź: $manualQuery")
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Skieruj aparat na kod lub wpisz go powyżej",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerProductItem(state: com.wodrol.brakoff.data.local.entity.LocalProductState, onSelect: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(state.barcode) },
        colors = CardDefaults.cardColors(
            containerColor = when (state.syncStatus) {
                com.wodrol.brakoff.data.local.entity.SyncStatus.CONFLICT -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name ?: "Nieznany produkt",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.barcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val isCompleted = state.expectedQty != null && state.globalScannedQty >= state.expectedQty
                    val unit = state.unit
                    
                    Text(
                        text = "${state.quantity} $unit",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCompleted) com.wodrol.brakoff.ui.theme.SuccessGreen else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val totalScanned = state.globalScannedQty
                    if (state.expectedQty != null) {
                        Text(
                            text = " ($totalScanned) / ${state.expectedQty} $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCompleted) com.wodrol.brakoff.ui.theme.SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                        )
                    } else if (totalScanned > 0) {
                        Text(
                            text = " ($totalScanned) $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                        )
                    }
                }
                if (state.syncStatus == com.wodrol.brakoff.data.local.entity.SyncStatus.WAITING_FOR_NETWORK) {
                    Text(
                        text = "CZEKA NA SIEĆ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
