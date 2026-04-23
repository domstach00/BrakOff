package com.wodrol.brakoff.data.local.dao

import androidx.room.*
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM delivery_items")
    fun getAllItems(): Flow<List<DeliveryItem>>

    @Query("SELECT * FROM delivery_items WHERE barcode = :barcode")
    suspend fun getItemByBarcode(barcode: String): DeliveryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DeliveryItem>)

    @Query("DELETE FROM delivery_items")
    suspend fun clearAll()

    @Transaction
    suspend fun updateDeliveryItems(items: List<DeliveryItem>) {
        clearAll()
        insertItems(items)
    }
}
