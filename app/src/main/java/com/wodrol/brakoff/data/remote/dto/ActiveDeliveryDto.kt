package com.wodrol.brakoff.data.remote.dto

data class ActiveDeliveryDto(
    val deliveryId: String,
    val deliveryDisplayName: String?,
    val sourceFileName: String?,
    val activatedAt: String? = null,
    val itemCount: Int? = null,
    val supplierName: String? = null,
    val commercialDocumentNumber: String? = null,
    val warehouseDocumentNumber: String? = null,
    val barcode: String? = null,
    val name: String? = null,
    val expectedQty: Int? = null,
    val unit: String? = null
) {
    fun uiTitle(): String = deliveryDisplayName?.takeIf { it.isNotBlank() }
        ?: supplierName?.takeIf { it.isNotBlank() }
        ?: sourceFileName?.takeIf { it.isNotBlank() }
        ?: deliveryId

    fun uiSubtitle(): String? {
        val parts = listOfNotNull(
            commercialDocumentNumber?.takeIf { it.isNotBlank() },
            warehouseDocumentNumber?.takeIf { it.isNotBlank() }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }
}
