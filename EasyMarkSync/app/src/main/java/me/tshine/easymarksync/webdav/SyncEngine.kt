package me.tshine.easymarksync.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.tshine.easymarksync.data.db.AppDatabase
import me.tshine.easymarksync.data.db.NoteEntity
import me.tshine.easymarksync.data.db.SyncStateEntity

/**
 * WebDAV 智能增量同步引擎（dav_diary 算法 Kotlin 迁移）。
 *
 * 算法（对齐 Kidiary syncNow）：
 * 1. 三方状态：本地笔记集 / 远端解析集 / 上次同步状态（SyncState）。
 * 2. 变更集计算：localChanged（本地指纹≠上次状态）、remoteChanged（远端指纹≠上次状态）、
 *    conflict（双方同时变更）、removeRemote（本地已删且曾同步）。
 * 3. Phase 1 下载远端变更 → Phase 2 上传本地变更 → Phase 3 冲突解决 →
 *    Phase 4 删除远端孤儿文件。
 */
class SyncEngine(
    private val db: AppDatabase,
    private val config: WebDavConfig
) {

    private data class RemoteNote(val note: NoteEntity, val remotePath: String)

    /** 执行一次完整增量同步（IO 线程）。 */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext SyncResult.failure("WebDAV 尚未配置：请先在设置中填写服务器地址、账号与应用密码")
        }

        val client = WebDavClient(config)
        try {
            // 0. 连接测试 + 确保远端目录
            if (!client.ping()) throw IllegalStateException("连接测试失败，请检查服务器地址与账号密码")
            client.ensureDirectory(config.remoteDir)

            // 1. 本地笔记集（仅有效笔记，一次性查询）
            val allLocal = db.noteDao().observeActive().firstOrNull() ?: emptyList()
            val localById = allLocal.associateBy { it.id }

            // 2. 远端解析集
            val remoteById = mutableMapOf<String, RemoteNote>()
            val remoteFiles = client.listFiles(config.remoteDir)
            for (f in remoteFiles) {
                if (!f.path.endsWith(".md") || f.path.contains(".conflict-")) continue
                val parsed = NoteSerializer.fromMarkdown(client.readFile(f.path)) ?: continue
                val existing = remoteById[parsed.id]
                if (existing == null || parsed.modifyTime > existing.note.modifyTime) {
                    remoteById[parsed.id] = RemoteNote(parsed, f.path)
                }
            }

            // 3. 上次同步状态
            val states = db.syncStateDao().getAll().associateBy { it.noteId }

            // 4. 计算变更集
            val allIds = localById.keys + remoteById.keys
            val localChanged = mutableSetOf<String>()
            val remoteChanged = mutableSetOf<String>()
            val conflicts = mutableSetOf<String>()
            val removeRemote = mutableSetOf<String>()

            for (id in allIds) {
                val local = localById[id]
                val state = states[id]
                val remote = remoteById[id]
                val stateFp = state?.fingerprint ?: ""

                val lc = local != null && NoteSerializer.fingerprint(local) != stateFp
                val rc = remote != null && NoteSerializer.fingerprint(remote.note) != stateFp

                if (lc) localChanged.add(id)
                if (rc) remoteChanged.add(id)
                if (lc && rc) conflicts.add(id)
                if (local == null && state != null && state.isInitialized) removeRemote.add(id)
            }

            var uploaded = 0
            var downloaded = 0
            var conflictCount = 0
            var removed = 0

            // 5. Phase 1：下载远端变更（非冲突）
            for (id in remoteChanged) {
                if (id in conflicts) continue
                if (localById[id] == null && id in removeRemote) continue
                val remote = remoteById[id] ?: continue
                db.noteDao().upsert(remote.note)
                upsertState(remote.note.id, remote.remotePath, NoteSerializer.fingerprint(remote.note))
                downloaded++
            }

            // 6. Phase 2：上传本地变更（非冲突）
            for (id in localChanged) {
                if (id in conflicts) continue
                val local = localById[id] ?: continue
                val remotePath = client.join(config.remoteDir, NoteSerializer.remoteFileName(id))
                client.writeFile(remotePath, NoteSerializer.toMarkdown(local))
                upsertState(id, remotePath, NoteSerializer.fingerprint(local))
                uploaded++
            }

            // 7. Phase 3：冲突解决
            for (id in conflicts) {
                val local = localById[id] ?: continue
                val remote = remoteById[id] ?: continue
                val remotePath = client.join(config.remoteDir, NoteSerializer.remoteFileName(id))
                val localWins = local.modifyTime >= remote.note.modifyTime

                when (config.conflictStrategy) {
                    ConflictStrategy.KEEP_BOTH -> {
                        val conflictPath = remotePath.replace(".md", ".conflict-${remote.note.modifyTime}.md")
                        runCatching { client.moveFile(remote.remotePath, conflictPath) }
                        client.writeFile(remotePath, NoteSerializer.toMarkdown(local))
                        upsertState(id, remotePath, NoteSerializer.fingerprint(local))
                    }
                    ConflictStrategy.LAST_WRITE_WINS -> {
                        if (localWins) {
                            client.writeFile(remotePath, NoteSerializer.toMarkdown(local))
                            upsertState(id, remotePath, NoteSerializer.fingerprint(local))
                        } else {
                            db.noteDao().upsert(remote.note)
                            upsertState(id, remotePath, NoteSerializer.fingerprint(remote.note))
                        }
                    }
                }
                conflictCount++
            }

            // 8. Phase 4：删除远端孤儿文件
            for (id in removeRemote) {
                val state = states[id] ?: continue
                if (state.remotePath.isBlank()) continue
                runCatching { client.deleteFile(state.remotePath) }
                db.syncStateDao().deleteByNoteId(id)
                removed++
            }

            // 9. 更新配置（同步时间 + 结果消息）
            val message = "同步完成：上传 $uploaded，下载 $downloaded，冲突 $conflictCount，删除 $removed"

            SyncResult.ok(message, uploaded, downloaded, conflictCount, removed)
        } catch (e: Exception) {
            SyncResult.failure("同步失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun upsertState(noteId: String, remotePath: String, fingerprint: String) {
        db.syncStateDao().upsert(
            SyncStateEntity(
                noteId = noteId,
                remotePath = remotePath,
                fingerprint = fingerprint,
                lastSyncTime = System.currentTimeMillis()
            )
        )
    }
}
