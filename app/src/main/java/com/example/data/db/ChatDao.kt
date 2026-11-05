package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE senderAddress = :deviceAddress OR receiverAddress = :deviceAddress ORDER BY timestamp ASC")
    fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE senderAddress = :deviceAddress OR receiverAddress = :deviceAddress")
    suspend fun clearMessagesForDevice(deviceAddress: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
