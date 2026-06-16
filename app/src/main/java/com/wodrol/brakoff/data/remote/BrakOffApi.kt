package com.wodrol.brakoff.data.remote

import com.wodrol.brakoff.data.remote.dto.ActiveDeliveryDto
import com.wodrol.brakoff.data.remote.dto.DeliveryResponse
import com.wodrol.brakoff.data.remote.dto.DeviceStateRequest
import com.wodrol.brakoff.data.remote.dto.DeviceStateResponse
import com.wodrol.brakoff.data.remote.dto.HealthResponse
import retrofit2.Response
import retrofit2.http.*

interface BrakOffApi {
    @GET("api/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @GET("api/deliveries/active")
    suspend fun getActiveDeliveries(): Response<List<ActiveDeliveryDto>>

    @GET("api/deliveries/{deliveryId}")
    suspend fun getDelivery(@Path("deliveryId") deliveryId: String): Response<DeliveryResponse>

    @GET("api/deliveries/{deliveryId}/device-state/{deviceId}")
    suspend fun getDeviceStateForDelivery(
        @Path("deliveryId") deliveryId: String,
        @Path("deviceId") deviceId: String
    ): Response<List<DeviceStateRequest>>

    @POST("api/deliveries/{deliveryId}/device-state")
    suspend fun updateDeviceStateForDelivery(
        @Path("deliveryId") deliveryId: String,
        @Body request: DeviceStateRequest
    ): Response<DeviceStateResponse>

    @GET("api/delivery/current")
    suspend fun getCurrentDelivery(): Response<DeliveryResponse>
}
