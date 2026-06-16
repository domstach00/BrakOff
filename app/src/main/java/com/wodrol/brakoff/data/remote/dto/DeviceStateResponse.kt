package com.wodrol.brakoff.data.remote.dto

data class DeviceStateResponse(
    val accepted: Boolean,
    val unchanged: Boolean = false,
    val reason: String?,
    val serverQuantity: Int?,
    val suggestedDeliveries: List<ActiveDeliveryDto> = emptyList()
)
