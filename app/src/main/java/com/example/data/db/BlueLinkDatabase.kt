package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.TransferEntity

@Database(
    entities = [ChatMessageEntity::class, TransferEntity::class, DeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlueLinkDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun transferDao(): TransferDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: BlueLinkDatabase? = null

        fun getDatabase(context: Context): BlueLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlueLinkDatabase::class.java,
                    "bluelink_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
