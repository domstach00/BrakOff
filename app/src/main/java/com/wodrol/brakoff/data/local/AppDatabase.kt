package com.wodrol.brakoff.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wodrol.brakoff.data.local.dao.CommentDao
import com.wodrol.brakoff.data.local.dao.DeliveryDao
import com.wodrol.brakoff.data.local.dao.ProductStateDao
import com.wodrol.brakoff.data.local.entity.CommentEntity
import com.wodrol.brakoff.data.local.entity.DeliveryItem
import com.wodrol.brakoff.data.local.entity.LocalProductState

@Database(
    entities = [DeliveryItem::class, LocalProductState::class, CommentEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryDao(): DeliveryDao
    abstract fun productStateDao(): ProductStateDao
    abstract fun commentDao(): CommentDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `comments` (
                        `commentId` TEXT NOT NULL,
                        `deliveryId` TEXT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `originalBarcode` TEXT,
                        `originalName` TEXT,
                        `deviceId` TEXT NOT NULL,
                        `deviceName` TEXT,
                        `text` TEXT NOT NULL,
                        `suggestedBarcode` TEXT,
                        `suggestedName` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `errorReason` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`commentId`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
