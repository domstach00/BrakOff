package com.wodrol.brakoff.data.local

import androidx.room.TypeConverter
import com.wodrol.brakoff.data.local.entity.CommentSyncStatus
import com.wodrol.brakoff.data.local.entity.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String {
        return value.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }

    @TypeConverter
    fun fromCommentSyncStatus(value: CommentSyncStatus): String {
        return value.name
    }

    @TypeConverter
    fun toCommentSyncStatus(value: String): CommentSyncStatus {
        return try {
            CommentSyncStatus.valueOf(value)
        } catch (_: Exception) {
            CommentSyncStatus.FAILED
        }
    }
}
