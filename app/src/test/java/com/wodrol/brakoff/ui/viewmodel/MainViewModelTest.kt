package com.wodrol.brakoff.ui.viewmodel

import com.wodrol.brakoff.data.local.entity.CommentEntity
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import com.wodrol.brakoff.data.repository.BrakOffRepository
import com.wodrol.brakoff.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var repository: BrakOffRepository

    @Mock
    private lateinit var preferencesManager: PreferencesManager

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        runTest {
            `when`(repository.allDeliveryItems).thenReturn(flowOf(emptyList()))
            `when`(repository.allProductStates).thenReturn(flowOf(emptyList()))
            `when`(repository.checkHealth()).thenReturn(true to null)
            `when`(repository.syncPendingStates()).thenReturn(null)
            `when`(preferencesManager.serverUrl).thenReturn(flowOf(""))
            `when`(preferencesManager.deviceName).thenReturn(flowOf("Test Device"))
            `when`(preferencesManager.deviceId).thenReturn(flowOf("test-id"))
            `when`(preferencesManager.apiToken).thenReturn(flowOf("token"))
            `when`(preferencesManager.scanButtonLeft).thenReturn(flowOf(false))
            `when`(preferencesManager.selectedDeliveryId).thenReturn(flowOf("del1"))
            `when`(preferencesManager.dismissedArchiveId).thenReturn(flowOf(""))
            `when`(preferencesManager.autoScanEnabled).thenReturn(flowOf(false))
            `when`(preferencesManager.currentDeliverySummary).thenReturn(
                flowOf(PreferencesManager.CurrentDeliverySummary(deliveryId = "del1", deliveryDisplayName = "Test"))
            )
        }

        viewModel = MainViewModel(repository, preferencesManager, startBackgroundJobs = false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `homeDisplayList sorting incomplete items with zero quantity at top`() = runTest {
        val deliveryItems = listOf(
            DeliveryItem("111", "Product A (Done)", 10, "del1", 10),
            DeliveryItem("222", "Product B (Zero)", 10, "del1", 0),
            DeliveryItem("333", "Product C (In Progress)", 10, "del1", 5)
        )
        val productStates = listOf(
            LocalProductState("111", "Product A (Done)", 10, true, 10, 10, syncStatus = SyncStatus.SYNCED),
            LocalProductState("222", "Product B (Zero)", 0, true, 10, 0, syncStatus = SyncStatus.SYNCED),
            LocalProductState("333", "Product C (In Progress)", 5, true, 10, 5, syncStatus = SyncStatus.SYNCED)
        )

        `when`(repository.allDeliveryItems).thenReturn(flowOf(deliveryItems))
        `when`(repository.allProductStates).thenReturn(flowOf(productStates))

        viewModel = MainViewModel(repository, preferencesManager, startBackgroundJobs = false)
        val job = backgroundScope.launch { viewModel.homeDisplayList.collect { } }
        advanceUntilIdle()

        val result = viewModel.homeDisplayList.value

        assertEquals("222", result[0].barcode)
        assertEquals("333", result[1].barcode)
        assertEquals("111", result[2].barcode)
        job.cancel()
    }

    @Test
    fun `homeDisplayList sorting failed sync at top`() = runTest {
        val deliveryItems = listOf(
            DeliveryItem("111", "Normal", 10, "del1", 0)
        )
        val productStates = listOf(
            LocalProductState("111", "Normal", 0, true, 10, 0, syncStatus = SyncStatus.SYNCED),
            LocalProductState("999", "Failed", 5, false, null, 0, syncStatus = SyncStatus.FAILED)
        )

        `when`(repository.allDeliveryItems).thenReturn(flowOf(deliveryItems))
        `when`(repository.allProductStates).thenReturn(flowOf(productStates))

        viewModel = MainViewModel(repository, preferencesManager, startBackgroundJobs = false)
        val job = backgroundScope.launch { viewModel.homeDisplayList.collect { } }
        advanceUntilIdle()

        val result = viewModel.homeDisplayList.value

        assertEquals("999", result[0].barcode)
        assertEquals("111", result[1].barcode)
        job.cancel()
    }

    @Test
    fun `addComment delegates to repository and sets action result`() = runTest {
        val selJob = backgroundScope.launch { viewModel.selectedDeliveryId.collect { } }
        advanceUntilIdle()

        val mockComment = CommentEntity(
            commentId = "c1",
            deliveryId = "del1",
            barcode = "00123",
            deviceId = "test-id",
            text = "Komentarz testowy",
            createdAt = "2026-10-01T09:15:00Z"
        )
        `when`(
            repository.addComment(
                deliveryId = eq("del1"),
                barcode = eq("00123"),
                text = eq("Komentarz testowy"),
                suggestedBarcode = eq("00999"),
                suggestedName = eq("Śruba"),
                originalName = eq("Śruba z PDF")
            )
        ).thenReturn(BrakOffRepository.CommentResult.Success(mockComment))

        viewModel.addComment(
            barcode = "00123",
            text = "Komentarz testowy",
            suggestedBarcode = "00999",
            suggestedName = "Śruba",
            originalName = "Śruba z PDF"
        )

        advanceUntilIdle()

        val actionResult = viewModel.commentActionResult.value
        assertEquals(BrakOffRepository.CommentResult.Success(mockComment), actionResult)
        verify(repository).fetchItemComments("del1", "00123")
        selJob.cancel()
    }
}
