package com.wodrol.brakoff.data.local.dao

import androidx.room.*
import com.wodrol.brakoff.data.local.entity.LocalProductState
import com.wodrol.brakoff.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductStateDao {
    @Query("SELECT * FROM local_product_state ORDER BY updatedAt DESC")
    fun getAllStates(): Flow<List<LocalProductState>>

    @Query("SELECT * FROM local_product_state WHERE barcode = :barcode")
    suspend fun getStateByBarcode(barcode: String): LocalProductState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: LocalProductState)

    @Query("UPDATE local_product_state SET syncStatus = :status WHERE barcode = :barcode")
    suspend fun updateSyncStatus(barcode: String, status: SyncStatus)

    @Query("SELECT * FROM local_product_state WHERE syncStatus IN ('PENDING', 'FAILED', 'WAITING_FOR_NETWORK')")
    suspend fun getPendingSyncStates(): List<LocalProductState>

    @Query("DELETE FROM local_product_state")
    suspend fun clearAll()
}
