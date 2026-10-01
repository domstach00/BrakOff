package com.wodrol.brakoff.data.remote.dto

data class ItemCommentDto(
    val commentId: String,
    val deliveryId: String,
    val barcode: String,
    val originalBarcode: String? = null,
    val originalName: String? = null,
    val deviceId: String,
    val deviceName: String? = null,
    val text: String,
    val suggestedBarcode: String? = null,
    val suggestedName: String? = null,
    val createdAt: String
)

data class ItemCommentRequest(
    val commentId: String,
    val deviceId: String,
    val deviceName: String? = null,
    val text: String,
    val suggestedBarcode: String? = null,
    val suggestedName: String? = null
)

data class CommentErrorResponse(
    val reason: String? = null,
    val message: String? = null
)
