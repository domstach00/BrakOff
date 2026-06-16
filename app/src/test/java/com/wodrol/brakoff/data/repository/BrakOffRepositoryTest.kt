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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun `syncPendingStates updates status to synced on success`() = runTest {
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
        `when`(preferencesManager.selectedDeliveryId).thenReturn(flowOf("delivery-1"))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("device123"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("My Phone"))
        `when`(
            api.updateDeviceStateForDelivery(
                any(),
                any()
            )
        ).thenReturn(
            Response.success(
                DeviceStateResponse(
                    accepted = true,
                    reason = null,
                    serverQuantity = 5
                )
            )
        )

        repository.syncPendingStates()

        verify(productStateDao).insertState(
            argThat<LocalProductState> { syncStatus == SyncStatus.SYNCED && barcode == "123" }
        )
    }

    @Test
    fun `syncPendingStates returns scan conflict for other delivery`() = runTest {
        val pendingState = LocalProductState(
            barcode = "123",
            name = "Test",
            quantity = 5,
            fromDelivery = false,
            expectedQty = null,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            revision = 2
        )

        `when`(productStateDao.getPendingSyncStates()).thenReturn(listOf(pendingState))
        `when`(preferencesManager.selectedDeliveryId).thenReturn(flowOf("delivery-1"))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("device123"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("My Phone"))

        val errorJson = """
            {
              "accepted": false,
              "unchanged": false,
              "reason": "ITEM_BELONGS_TO_OTHER_DELIVERY",
              "serverQuantity": 0,
              "suggestedDeliveries": [
                {
                  "deliveryId": "delivery-2",
                  "deliveryDisplayName": "Hermes"
                }
              ]
            }
        """.trimIndent()
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        `when`(api.updateDeviceStateForDelivery(any(), any())).thenReturn(Response.error(409, errorBody))

        val result = repository.syncPendingStates()

        verify(productStateDao).updateSyncStatus("123", SyncStatus.CONFLICT)
        assert(result is BrakOffRepository.FetchResult.ScanConflict)
    }
}
