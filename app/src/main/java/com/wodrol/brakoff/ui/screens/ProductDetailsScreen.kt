package com.wodrol.brakoff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wodrol.brakoff.data.local.entity.CommentEntity
import com.wodrol.brakoff.data.local.entity.CommentSyncStatus
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.repository.BrakOffRepository
import com.wodrol.brakoff.ui.theme.ErrorRed
import com.wodrol.brakoff.ui.theme.SuccessGreen
import com.wodrol.brakoff.ui.theme.WarningOrange
import com.wodrol.brakoff.ui.viewmodel.MainViewModel
import com.wodrol.brakoff.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(barcode: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val productStates by viewModel.productStates.collectAsState()
    val deliveryItems by viewModel.deliveryItems.collectAsState()
    val commentActionResult by viewModel.commentActionResult.collectAsState()

    val product = productStates.find { it.barcode == barcode }
    val deliveryItem = deliveryItems.find { it.barcode == barcode }

    val displayName = product?.name ?: deliveryItem?.name ?: "Produkt spoza dostawy"
    val isFromDelivery = product?.fromDelivery == true || deliveryItem != null
    val expectedQuantity = product?.expectedQty ?: deliveryItem?.expectedQty
    val unit = product?.unit ?: deliveryItem?.unit ?: "szt"

    var quantityText by remember { mutableStateOf(product?.quantity?.toString() ?: "1") }

    // Target override if the worker scans an unrecognized code and wants to report against a PDF item
    var targetPdfItem by remember { mutableStateOf<DeliveryItem?>(null) }
    var showPdfSelectDialog by remember { mutableStateOf(false) }

    val activeTargetBarcode = targetPdfItem?.barcode ?: barcode
    val activeTargetName = targetPdfItem?.name ?: displayName

    // Observe comments for the active target barcode
    val commentsFlow = remember(activeTargetBarcode) {
        viewModel.getCommentsForProduct(activeTargetBarcode)
    }
    val comments by commentsFlow.collectAsState(initial = emptyList())

    // Form states
    var commentText by remember { mutableStateOf("") }
    var showProposals by remember { mutableStateOf(false) }
    var suggestedBarcodeText by remember { mutableStateOf("") }
    var suggestedNameText by remember { mutableStateOf("") }
    var formValidationError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Start comment polling for active target barcode
    DisposableEffect(activeTargetBarcode) {
        viewModel.startCommentsPolling(activeTargetBarcode)
        onDispose {
            viewModel.stopCommentsPolling()
        }
    }

    LaunchedEffect(commentActionResult) {
        when (val res = commentActionResult) {
            is BrakOffRepository.CommentResult.Success -> {
                snackbarHostState.showSnackbar("Komentarz dodany do kolejki")
                commentText = ""
                suggestedBarcodeText = ""
                suggestedNameText = ""
                showProposals = false
                formValidationError = null
                viewModel.clearCommentActionResult()
            }
            is BrakOffRepository.CommentResult.ValidationError -> {
                formValidationError = res.message
                viewModel.clearCommentActionResult()
            }
            is BrakOffRepository.CommentResult.Error -> {
                snackbarHostState.showSnackbar("Błąd: ${res.message}")
                viewModel.clearCommentActionResult()
            }
            null -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unrecognized Barcode / PDF Target Picker Banner
            if (!isFromDelivery && targetPdfItem == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Produkt spoza listy dostawy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Jeżeli ten kod jest błędny na opakowaniu, możesz wskazać poprawną pozycję z PDF do zgłoszenia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showPdfSelectDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Wskarz pozycję z PDF")
                        }
                    }
                }
            } else if (targetPdfItem != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cel zgłoszenia (pozycja PDF):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = targetPdfItem!!.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Kod PDF: ${targetPdfItem!!.barcode} | Zeskanowano: $barcode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        TextButton(
                            onClick = {
                                targetPdfItem = null
                                suggestedBarcodeText = ""
                            }
                        ) {
                            Text("ZMIEŃ")
                        }
                    }
                }
            }

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
            if (product?.syncStatus == SyncStatus.CONFLICT) {
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
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val q = quantityText.toIntOrNull() ?: 0
                            viewModel.updateQuantity(barcode, q)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = quantityText.isNotEmpty() && quantityText.toIntOrNull() != null,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("ZATWIERDŹ ILOŚĆ", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Comments Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Komentarze (${comments.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (comments.any { it.syncStatus != CommentSyncStatus.SYNCED }) {
                    IconButton(onClick = { viewModel.retryPendingComments() }) {
                        Icon(Icons.Default.AddComment, contentDescription = "Ponów wysyłanie")
                    }
                }
            }

            // Comments List
            if (comments.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Brak komentarzy",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comments.forEach { comment ->
                        CommentItemCard(comment)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add Comment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Dodaj komentarz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (targetPdfItem != null) {
                        Text(
                            text = "Dotyczy pozycji z PDF: ${targetPdfItem!!.name} (${targetPdfItem!!.barcode})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Multiline Text Field with Counter
                    Column {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = {
                                if (it.length <= 2000) {
                                    commentText = it
                                    formValidationError = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Treść komentarza") },
                            placeholder = { Text("Wpisz uwagę lub wyjaśnienie...") },
                            minLines = 3,
                            maxLines = 6,
                            shape = MaterialTheme.shapes.medium
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${commentText.length} / 2000",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (commentText.length > 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Shortcut Button: "Błędny barcode / nazwa z PDF"
                    OutlinedButton(
                        onClick = {
                            val presetText = "Błędny odczyt produktu z PDF. Proszę poprawić barcode lub nazwę."
                            if (!commentText.contains(presetText)) {
                                commentText = if (commentText.isBlank()) presetText else "$presetText\n$commentText"
                            }
                            showProposals = true
                            if (suggestedBarcodeText.isBlank() && barcode != activeTargetBarcode) {
                                suggestedBarcodeText = barcode
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Błędny barcode / nazwa z PDF")
                    }

                    // Toggle Proposals visibility
                    TextButton(
                        onClick = { showProposals = !showProposals },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(if (showProposals) "Ukryj propozycje zmian" else "+ Dodaj proponowaną zmianę barcode / nazwy")
                    }

                    if (showProposals) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    MaterialTheme.shapes.medium
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Propozycje poprawek do produktu:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = suggestedBarcodeText,
                                onValueChange = {
                                    suggestedBarcodeText = it
                                    formValidationError = null
                                },
                                label = { Text("Proponowany barcode (tylko cyfry ASCII)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = suggestedNameText,
                                onValueChange = {
                                    suggestedNameText = it
                                    formValidationError = null
                                },
                                label = { Text("Proponowana nazwa") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (formValidationError != null) {
                        Text(
                            text = formValidationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val isSendEnabled = commentText.trim().isNotEmpty() &&
                            commentText.length <= 2000 &&
                            (suggestedBarcodeText.isBlank() || suggestedBarcodeText.trim().matches(Regex("^[0-9]+$"))) &&
                            (suggestedBarcodeText.isBlank() || suggestedBarcodeText.trim().length <= 100) &&
                            (suggestedNameText.isBlank() || suggestedNameText.trim().length <= 500)

                    Button(
                        onClick = {
                            viewModel.addComment(
                                barcode = activeTargetBarcode,
                                text = commentText,
                                suggestedBarcode = suggestedBarcodeText.takeIf { showProposals },
                                suggestedName = suggestedNameText.takeIf { showProposals },
                                originalName = activeTargetName
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = isSendEnabled,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WYŚLIJ KOMENTARZ")
                    }

                    Text(
                        text = "(Nie zatwierdza ilości produktów)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    // PDF Item Selection Dialog
    if (showPdfSelectDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredDeliveryItems = remember(deliveryItems, searchQuery) {
            if (searchQuery.isBlank()) deliveryItems
            else deliveryItems.filter {
                it.barcode.contains(searchQuery, ignoreCase = true) ||
                        it.name.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showPdfSelectDialog = false },
            title = { Text("Wybierz pozycję z PDF") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Szukaj w dostawie...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredDeliveryItems) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        targetPdfItem = item
                                        suggestedBarcodeText = barcode
                                        showProposals = true
                                        showPdfSelectDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Kod: ${item.barcode} | Oczekiwano: ${item.expectedQty} ${item.unit}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPdfSelectDialog = false }) { Text("ANULUJ") }
            }
        )
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

@Composable
fun CommentItemCard(comment: CommentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (comment.syncStatus) {
                CommentSyncStatus.SYNCED -> MaterialTheme.colorScheme.surface
                CommentSyncStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val author = comment.deviceName?.takeIf { it.isNotBlank() } ?: comment.deviceId
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val displayDate = DateUtils.formatIsoToLocalDisplay(comment.createdAt).ifBlank {
                    DateUtils.formatMillisToLocalDisplay(comment.createdAtMillis)
                }
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment text (preserves newlines)
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Proposed Barcode / Name
            if (!comment.suggestedBarcode.isNullOrBlank() || !comment.suggestedName.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                if (!comment.suggestedBarcode.isNullOrBlank()) {
                    Text(
                        text = "Proponowany barcode: ${comment.suggestedBarcode}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (!comment.suggestedName.isNullOrBlank()) {
                    Text(
                        text = "Proponowana nazwa: ${comment.suggestedName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Sync Status badge if not synced
            if (comment.syncStatus != CommentSyncStatus.SYNCED) {
                Spacer(modifier = Modifier.height(4.dp))
                CommentStatusBadge(comment.syncStatus, comment.errorMessage)
            }
        }
    }
}

@Composable
fun CommentStatusBadge(status: CommentSyncStatus, errorMessage: String?) {
    val (text, color) = when (status) {
        CommentSyncStatus.SYNCED -> "WYŚLANY" to SuccessGreen
        CommentSyncStatus.PENDING -> "OCZEKUJE NA WYSŁANIE" to WarningOrange
        CommentSyncStatus.FAILED -> ("BŁĄD: " + (errorMessage ?: "Niepowodzenie")) to ErrorRed
        CommentSyncStatus.DELIVERY_NOT_ACTIVE -> "DOSTAWA NIEAKTYWNA LUB ZAMKNIĘTA" to ErrorRed
        CommentSyncStatus.ITEM_NOT_FOUND -> "POZYCJA NIE ISTNIEJE NA PC" to ErrorRed
        CommentSyncStatus.COMMENT_ID_CONFLICT -> "KONFLIKT ID KOMENTARZA" to ErrorRed
        CommentSyncStatus.PC_UPDATE_REQUIRED -> "WYMAGANA AKTUALIZACJA BRAKOFFPC" to ErrorRed
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
