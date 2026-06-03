package com.wodrol.brakoff.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_items")
data class DeliveryItem(
    @PrimaryKey val barcode: String,
    val name: String,
    val expectedQty: Int,
    val deliveryId: String,
    val scannedQty: Int = 0,
    val unit: String = "szt"
)
