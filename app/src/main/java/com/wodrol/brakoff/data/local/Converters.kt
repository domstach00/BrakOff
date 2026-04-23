package com.wodrol.brakoff.data.local

import androidx.room.TypeConverter
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
}
