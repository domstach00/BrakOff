package com.wodrol.brakoff.data.remote.dto

data class DeliveryResponse(
    val deliveryId: String,
    val deliveryDisplayName: String?,
    val sourceFileName: String?,
    val supplierName: String?,
    val commercialDocumentNumber: String?,
    val warehouseDocumentNumber: String?,
    val items: List<DeliveryItemDto>
)

data class DeliveryItemDto(
    val barcode: String,
    val name: String,
    val expectedQty: Int,
    val scannedQty: Int = 0,
    val unit: String? = "szt"
)
