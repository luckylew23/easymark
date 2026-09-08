package me.tshine.easymarksync.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 笔记实体（Room）。对应原易码的 NoteMeta + NoteContent，合并为单表。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val modifyTime: Long,
    val createdAt: Long,
    val deleted: Boolean = false
)
