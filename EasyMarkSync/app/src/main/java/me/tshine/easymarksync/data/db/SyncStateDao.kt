package me.tshine.easymarksync.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_states")
    suspend fun getAll(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_states WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_states WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)
}
