package com.wodrol.brakoff.data.local.dao

import androidx.room.*
import com.wodrol.brakoff.data.local.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE deliveryId = :deliveryId AND barcode = :barcode ORDER BY createdAtMillis ASC, commentId ASC")
    fun getCommentsForProduct(deliveryId: String, barcode: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE deliveryId = :deliveryId AND barcode = :barcode ORDER BY createdAtMillis ASC, commentId ASC")
    suspend fun getCommentsForProductList(deliveryId: String, barcode: String): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE deliveryId = :deliveryId ORDER BY createdAtMillis ASC, commentId ASC")
    fun getAllCommentsForDelivery(deliveryId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE commentId = :commentId")
    suspend fun getCommentById(commentId: String): CommentEntity?

    @Query("SELECT * FROM comments WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY createdAtMillis ASC")
    suspend fun getPendingComments(): List<CommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE deliveryId = :deliveryId AND syncStatus = 'SYNCED' AND commentId NOT IN (:syncedCommentIds)")
    suspend fun deleteOldSyncedComments(deliveryId: String, syncedCommentIds: List<String>)

    @Query("DELETE FROM comments")
    suspend fun clearAll()
}
