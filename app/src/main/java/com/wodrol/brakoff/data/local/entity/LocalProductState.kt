package com.wodrol.brakoff.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_product_state")
data class LocalProductState(
    @PrimaryKey val barcode: String,
    val name: String?,
    val quantity: Int,
    val fromDelivery: Boolean,
    val expectedQty: Int?,
    val globalScannedQty: Int = 0,
    val revision: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val remoteQuantityLastSeen: Int? = null,
    val lastSyncAttemptAt: Long? = null,
    val lastSyncSuccessAt: Long? = null
)

enum class SyncStatus {
    PENDING,
    WAITING_FOR_NETWORK,
    SYNCED,
    FAILED,
    CONFLICT
}
