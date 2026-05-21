package com.wodrol.brakoff.data.remote

import com.wodrol.brakoff.data.remote.dto.DeliveryResponse
import com.wodrol.brakoff.data.remote.dto.DeviceStateRequest
import com.wodrol.brakoff.data.remote.dto.DeviceStateResponse
import com.wodrol.brakoff.data.remote.dto.HealthResponse
import retrofit2.Response
import retrofit2.http.*

interface BrakOffApi {
    @GET("api/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @GET("api/delivery/current")
    suspend fun getCurrentDelivery(): Response<DeliveryResponse>

    @GET("api/active-delivery")
    suspend fun getActiveDelivery(): Response<DeliveryResponse>

    @POST("api/device-state")
    suspend fun updateDeviceState(@Body request: DeviceStateRequest): Response<DeviceStateResponse>

    @GET("api/device-state/{deviceId}")
    suspend fun getDeviceState(@Path("deviceId") deviceId: String): Response<List<DeviceStateRequest>>

    @POST("api/active-delivery/scans")
    suspend fun sendScan(@Body request: DeviceStateRequest): Response<DeviceStateResponse>
}
