package me.tshine.easymarksync.data.repo

import kotlinx.coroutines.flow.Flow
import me.tshine.easymarksync.data.db.AppDatabase
import me.tshine.easymarksync.data.db.NoteEntity
import java.util.UUID

class NoteRepository(private val db: AppDatabase) {

    fun observeActive(): Flow<List<NoteEntity>> = db.noteDao().observeActive()

    fun search(q: String): Flow<List<NoteEntity>> =
        if (q.isBlank()) observeActive() else db.noteDao().search(q.trim())

    suspend fun getById(id: String): NoteEntity? = db.noteDao().getById(id)

    suspend fun upsert(note: NoteEntity) = db.noteDao().upsert(note)

    suspend fun softDelete(id: String) = db.noteDao().softDelete(id)

    suspend fun createNote(): NoteEntity {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            content = "",
            modifyTime = now,
            createdAt = now
        )
        db.noteDao().upsert(note)
        return note
    }
}
