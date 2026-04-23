package com.wodrol.brakoff.data.remote.dto

data class DeliveryResponse(
    val deliveryId: String,
    val version: String?,
    val items: List<DeliveryItemDto>
)

data class DeliveryItemDto(
    val barcode: String,
    val name: String,
    val expectedQty: Int,
    val scannedQty: Int = 0
)
