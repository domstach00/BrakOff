package com.wodrol.brakoff.data.repository

import android.content.Context
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.remote.dto.DeviceStateResponse
import com.wodrol.brakoff.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class BrakOffRepositoryTest {

    @Mock
    private lateinit var api: BrakOffApi
    @Mock
    private lateinit var deliveryDao: DeliveryDao
    @Mock
    private lateinit var productStateDao: ProductStateDao
    @Mock
    private lateinit var preferencesManager: PreferencesManager
    @Mock
    private lateinit var context: Context

    private lateinit var repository: BrakOffRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = BrakOffRepository(api, deliveryDao, productStateDao, preferencesManager, context)
    }

    @Test
    fun `syncPendingStates - should update status to SYNCED on success`() = runTest {
        // Given
        val pendingState = LocalProductState(
            barcode = "123",
            name = "Test",
            quantity = 5,
            fromDelivery = true,
            expectedQty = 10,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        
        `when`(productStateDao.getPendingSyncStates()).thenReturn(listOf(pendingState))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("device123"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("My Phone"))
        `when`(deliveryDao.getItemByBarcode("123")).thenReturn(null)
        
        val successResponse = DeviceStateResponse(
            accepted = true,
            reason = null,
            serverQuantity = 5,
            serverRevision = 1L
        )
        `when`(api.updateDeviceState(any())).thenReturn(Response.success(successResponse))

        // When
        repository.syncPendingStates()

        // Then
        verify(productStateDao).insertState(argThat<LocalProductState> { state -> state.syncStatus == SyncStatus.SYNCED && state.barcode == "123" })
    }

    @Test
    fun `syncPendingStates - should set CONFLICT status when server returns STALE_REVISION`() = runTest {
        // Given
        val pendingState = LocalProductState(
            barcode = "123",
            name = "Test",
            quantity = 5,
            fromDelivery = true,
            expectedQty = 10,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            revision = 2
        )
        
        `when`(productStateDao.getPendingSyncStates()).thenReturn(listOf(pendingState))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("device123"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("My Phone"))
        
        val conflictResponse = DeviceStateResponse(
            accepted = false,
            reason = "STALE_REVISION",
            serverQuantity = 10,
            serverRevision = 5L
        )
        `when`(api.updateDeviceState(any())).thenReturn(Response.success(conflictResponse))

        // When
        repository.syncPendingStates()

        // Then
        verify(productStateDao).insertState(argThat<LocalProductState> { state -> state.syncStatus == SyncStatus.CONFLICT && state.remoteQuantityLastSeen == 10 })
    }
}
