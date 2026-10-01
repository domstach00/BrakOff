package com.wodrol.brakoff.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val commentId: String,
    val deliveryId: String,
    val barcode: String,
    val originalBarcode: String? = null,
    val originalName: String? = null,
    val deviceId: String,
    val deviceName: String? = null,
    val text: String,
    val suggestedBarcode: String? = null,
    val suggestedName: String? = null,
    val createdAt: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: CommentSyncStatus = CommentSyncStatus.PENDING,
    val errorReason: String? = null,
    val errorMessage: String? = null
)

enum class CommentSyncStatus {
    PENDING,
    SYNCED,
    FAILED,
    DELIVERY_NOT_ACTIVE,
    ITEM_NOT_FOUND,
    COMMENT_ID_CONFLICT,
    PC_UPDATE_REQUIRED
}
