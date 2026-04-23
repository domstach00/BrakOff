package com.wodrol.brakoff.data.remote.dto

data class DeviceStateResponse(
    val accepted: Boolean,
    val reason: String?,
    val serverQuantity: Int?,
    val serverRevision: Long?
)
