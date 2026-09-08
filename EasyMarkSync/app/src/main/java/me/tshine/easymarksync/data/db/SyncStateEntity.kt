package me.tshine.easymarksync.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步状态表：记录每条笔记"上次双方达成一致"时的远端路径与内容指纹，
 * 用于三方比对（本地 / 远端 / 上次状态）实现增量同步。对应 dav_diary 的 _SyncState。
 */
@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey val noteId: String,
    val remotePath: String = "",
    val fingerprint: String = "",
    val lastSyncTime: Long = 0L
) {
    val isInitialized: Boolean get() = remotePath.isNotEmpty() || fingerprint.isNotEmpty()
}
