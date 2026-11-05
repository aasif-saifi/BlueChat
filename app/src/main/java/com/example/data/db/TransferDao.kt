package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE isIncoming = 1 AND status = 'COMPLETED' ORDER BY timestamp DESC")
    fun getReceivedFiles(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE fileId = :fileId")
    suspend fun getTransferById(fileId: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE fileId = :fileId")
    suspend fun deleteTransfer(fileId: String)

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()
}
