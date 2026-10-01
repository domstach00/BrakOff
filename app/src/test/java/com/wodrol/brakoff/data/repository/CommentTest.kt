package com.wodrol.brakoff.data.repository

import android.content.Context
import com.wodrol.brakoff.data.local.dao.CommentDao
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.CommentEntity
import com.wodrol.brakoff.data.local.entity.CommentSyncStatus
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.remote.dto.ItemCommentDto
import com.wodrol.brakoff.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CommentTest {

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
        `when`(preferencesManager.selectedDeliveryId).thenReturn(flowOf("del1"))
        `when`(preferencesManager.deviceId).thenReturn(flowOf("phone-1"))
        `when`(preferencesManager.deviceName).thenReturn(flowOf("Magazyn 1"))
    }

    @Test
    fun `addComment preserves Polish characters leading zeros and newlines`() = runTest {
        val multilineText = "Błędny odczyt z PDF.\nZmień kod i nazwę:\n- 🎯 Zażółć gęślą jaźń"
        val result = repository.addComment(
            deliveryId = "del1",
            barcode = "00123",
            text = multilineText,
            suggestedBarcode = "00999",
            suggestedName = "Śruba właściwa M8"
        )

        assertTrue(result is BrakOffRepository.CommentResult.Success)
        val successResult = result as BrakOffRepository.CommentResult.Success
        assertEquals("00123", successResult.comment.barcode)
        assertEquals(multilineText, successResult.comment.text)
        assertEquals("00999", successResult.comment.suggestedBarcode)
        assertEquals("Śruba właściwa M8", successResult.comment.suggestedName)
    }

    @Test
    fun `addComment fails on text longer than 2000 chars`() = runTest {
        val longText = "a".repeat(2001)
        val result = repository.addComment(
            deliveryId = "del1",
            barcode = "00123",
            text = longText
        )

        assertTrue(result is BrakOffRepository.CommentResult.ValidationError)
    }

    @Test
    fun `retry uses same commentId and does not duplicate UUID`() = runTest {
        val originalUuid = "6f465a48-7896-43b1-ae27-4074b627ba07"
        val pendingComment = CommentEntity(
            commentId = originalUuid,
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-1",
            deviceName = "Magazyn 1",
            text = "Zgłoszenie błędu",
            createdAt = "2026-10-01T09:15:00Z",
            syncStatus = CommentSyncStatus.PENDING
        )

        `when`(commentDao.getPendingComments()).thenReturn(listOf(pendingComment))
        `when`(deliveryDao.getItemByBarcode("00123")).thenReturn(
            DeliveryItem("00123", "Śruba M8", 5, "del1")
        )

        val serverResponse = ItemCommentDto(
            commentId = originalUuid,
            deliveryId = "del1",
            barcode = "00123",
            originalBarcode = "00123",
            originalName = "Śruba M8",
            deviceId = "phone-1",
            deviceName = "Magazyn 1",
            text = "Zgłoszenie błędu",
            createdAt = "2026-10-01T09:15:00Z"
        )

        `when`(api.postItemComment(eq("del1"), eq("00123"), argThat { commentId == originalUuid }))
            .thenReturn(Response.success(serverResponse))

        repository.syncPendingComments()

        verify(api).postItemComment(
            eq("del1"),
            eq("00123"),
            argThat { commentId == originalUuid && deviceId == "phone-1" && text == "Zgłoszenie błędu" }
        )
        verify(commentDao).insertComment(
            argThat { commentId == originalUuid && syncStatus == CommentSyncStatus.SYNCED }
        )
    }

    @Test
    fun `comments from different deliveries are isolated by deliveryId`() = runTest {
        repository.getCommentsForProduct("del1", "00123")
        verify(commentDao).getCommentsForProduct("del1", "00123")

        repository.getCommentsForProduct("del2", "00123")
        verify(commentDao).getCommentsForProduct("del2", "00123")
    }

    @Test
    fun `fetchItemComments saves items from other devices`() = runTest {
        val otherDeviceComment = ItemCommentDto(
            commentId = "c-other-1",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-2",
            deviceName = "Magazyn 2",
            text = "Komentarz z innego telefonu",
            createdAt = "2026-10-01T10:00:00Z"
        )

        `when`(api.getItemComments("del1", "00123")).thenReturn(Response.success(listOf(otherDeviceComment)))

        val fetchRes = repository.fetchItemComments("del1", "00123")

        assertTrue(fetchRes is BrakOffRepository.FetchResult.Success)
        verify(commentDao).insertComments(
            argThat {
                size == 1 &&
                        first().commentId == "c-other-1" &&
                        first().deviceId == "phone-2" &&
                        first().deviceName == "Magazyn 2"
            }
        )
    }

    @Test
    fun `syncPendingComments handles 401 InvalidToken`() = runTest {
        val pendingComment = CommentEntity(
            commentId = "c-401",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "phone-1",
            text = "Uwaga",
            createdAt = "2026-10-01T09:15:00Z",
            syncStatus = CommentSyncStatus.PENDING
        )

        `when`(commentDao.getPendingComments()).thenReturn(listOf(pendingComment))
        `when`(deliveryDao.getItemByBarcode("00123")).thenReturn(
            DeliveryItem("00123", "Śruba M8", 5, "del1")
        )
        `when`(api.postItemComment(any(), any(), any())).thenReturn(
            Response.error(401, "".toResponseBody("application/json".toMediaType()))
        )

        val res = repository.syncPendingComments()

        assertEquals(BrakOffRepository.FetchResult.InvalidToken, res)
        verify(commentDao).insertComment(
            argThat { commentId == "c-401" && syncStatus == CommentSyncStatus.FAILED }
        )
    }
}
