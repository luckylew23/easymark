package me.tshine.easymarksync.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY modifyTime DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deleted = 0 AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%') ORDER BY modifyTime DESC")
    fun search(q: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM notes")
    suspend fun clear()
}
