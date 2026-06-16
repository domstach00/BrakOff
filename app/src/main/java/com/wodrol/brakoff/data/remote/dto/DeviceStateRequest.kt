package com.wodrol.brakoff.data.remote.dto

data class DeviceStateRequest(
    val deviceId: String,
    val deviceName: String?,
    val barcode: String,
    val name: String?,
    val quantity: Int,
    val fromDelivery: Boolean,
    val updatedAt: String,
    val revision: Long,
    val unit: String? = "szt"
)
