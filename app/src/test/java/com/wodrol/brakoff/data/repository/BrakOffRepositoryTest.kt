package com.wodrol.brakoff.data.repository

import android.content.Context
import com.wodrol.brakoff.data.local.dao.CommentDao
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.CommentEntity
import com.wodrol.brakoff.data.local.entity.CommentSyncStatus
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.remote.dto.DeviceStateResponse
import com.wodrol.brakoff.data.remote.dto.ItemCommentDto
import com.wodrol.brakoff.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
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
    private lateinit var commentDao: CommentDao
    @Mock
    private lateinit var preferencesManager: PreferencesManager
    @Mock
    private lateinit var context: Context

    private lateinit var repository: BrakOffRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = BrakOffRepository(api, deliveryDao, productStateDao, commentDao, preferencesManager, context)
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

    @Test
    fun `addComment validates empty text`() = runTest {
        val result = repository.addComment(
            deliveryId = "del1",
            barcode = "00123",
            text = "   "
        )
        assertTrue(result is BrakOffRepository.CommentResult.ValidationError)
    }

    @Test
    fun `addComment validates non-digit suggestedBarcode`() = runTest {
        val result = repository.addComment(
            deliveryId = "del1",
            barcode = "00123",
            text = "Popraw kod",
            suggestedBarcode = "ABC123"
        )
        assertTrue(result is BrakOffRepository.CommentResult.ValidationError)
    }

    @Test
    fun `addComment inserts pending comment into dao`() = runTest {
        `when`(preferencesManager.selectedDeliveryId).thenReturn(flowOf("del1"))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("phone-1"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("Magazyn 1"))
        `when`(commentDao.getPendingComments()).thenReturn(emptyList())

        val result = repository.addComment(
            deliveryId = "del1",
            barcode = "00123",
            text = "Błędny odczyt z PDF",
            suggestedBarcode = "00999",
            suggestedName = "Śruba właściwa"
        )

        assertTrue(result is BrakOffRepository.CommentResult.Success)
        verify(commentDao).insertComment(
            argThat {
                barcode == "00123" &&
                        text == "Błędny odczyt z PDF" &&
                        suggestedBarcode == "00999" &&
                        suggestedName == "Śruba właściwa" &&
                        syncStatus == CommentSyncStatus.PENDING
            }
        )
    }

    @Test
    fun `syncPendingComments updates status to SYNCED on 200`() = runTest {
        val pendingComment = CommentEntity(
            commentId = "uuid-123",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-1",
            deviceName = "Magazyn 1",
            text = "Uwaga",
            createdAt = "2026-10-01T09:15:00Z",
            syncStatus = CommentSyncStatus.PENDING
        )

        `when`(commentDao.getPendingComments()).thenReturn(listOf(pendingComment))
        `when`(deliveryDao.getItemByBarcode("00123")).thenReturn(
            DeliveryItem("00123", "Test Item", 5, "del1")
        )

        val serverResponseDto = ItemCommentDto(
            commentId = "uuid-123",
            deliveryId = "del1",
            barcode = "00123",
            originalBarcode = "00123",
            originalName = "Test Item",
            deviceId = "phone-1",
            deviceName = "Magazyn 1",
            text = "Uwaga",
            createdAt = "2026-10-01T09:15:00Z"
        )

        `when`(api.postItemComment(any(), any(), any())).thenReturn(Response.success(serverResponseDto))

        repository.syncPendingComments()

        verify(commentDao).insertComment(
            argThat {
                commentId == "uuid-123" && syncStatus == CommentSyncStatus.SYNCED
            }
        )
    }

    @Test
    fun `syncPendingComments handles 404 ITEM_NOT_FOUND`() = runTest {
        val pendingComment = CommentEntity(
            commentId = "uuid-404",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-1",
            text = "Uwaga",
            createdAt = "2026-10-01T09:15:00Z",
            syncStatus = CommentSyncStatus.PENDING
        )

        `when`(commentDao.getPendingComments()).thenReturn(listOf(pendingComment))
        `when`(deliveryDao.getItemByBarcode("00123")).thenReturn(
            DeliveryItem("00123", "Test Item", 5, "del1")
        )

        val errorJson = """{"reason": "ITEM_NOT_FOUND", "message": "Item not found"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        `when`(api.postItemComment(any(), any(), any())).thenReturn(Response.error(404, errorBody))

        repository.syncPendingComments()

        verify(commentDao).insertComment(
            argThat {
                commentId == "uuid-404" && syncStatus == CommentSyncStatus.ITEM_NOT_FOUND
            }
        )
    }

    @Test
    fun `syncPendingComments handles 409 COMMENT_ID_CONFLICT`() = runTest {
        val pendingComment = CommentEntity(
            commentId = "uuid-409",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-1",
            text = "Uwaga",
            createdAt = "2026-10-01T09:15:00Z",
            syncStatus = CommentSyncStatus.PENDING
        )

        `when`(commentDao.getPendingComments()).thenReturn(listOf(pendingComment))
        `when`(deliveryDao.getItemByBarcode("00123")).thenReturn(
            DeliveryItem("00123", "Test Item", 5, "del1")
        )

        val errorJson = """{"reason": "COMMENT_ID_CONFLICT"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        `when`(api.postItemComment(any(), any(), any())).thenReturn(Response.error(409, errorBody))

        repository.syncPendingComments()

        verify(commentDao).insertComment(
            argThat {
                commentId == "uuid-409" && syncStatus == CommentSyncStatus.COMMENT_ID_CONFLICT
            }
        )
    }
}
