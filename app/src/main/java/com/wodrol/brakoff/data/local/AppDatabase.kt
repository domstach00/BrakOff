package com.wodrol.brakoff.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState

@Database(
    entities = [DeliveryItem::class, LocalProductState::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryDao(): DeliveryDao
    abstract fun productStateDao(): ProductStateDao
}
