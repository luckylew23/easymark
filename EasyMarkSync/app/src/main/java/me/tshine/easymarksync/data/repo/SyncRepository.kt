package me.tshine.easymarksync.data.repo

import android.content.Context
import me.tshine.easymarksync.data.db.AppDatabase
import me.tshine.easymarksync.sync.SyncWorker
import me.tshine.easymarksync.webdav.SyncEngine
import me.tshine.easymarksync.webdav.SyncResult
import me.tshine.easymarksync.webdav.WebDavClient
import me.tshine.easymarksync.webdav.WebDavConfig

/**
 * 同步协调层：加载配置 → 执行引擎 → 回写配置（同步时间 / 结果消息）。
 */
class SyncRepository(private val context: Context) {

    val appContext: Context get() = context

    private val db by lazy { AppDatabase.get(context) }

    fun loadConfig(): WebDavConfig = WebDavConfig.load(context)

    fun saveConfig(config: WebDavConfig) = WebDavConfig.save(context, config)

    /** 依据配置启用/停用 WorkManager 周期任务（幂等）。 */
    fun applyAutoSyncSchedule(config: WebDavConfig) {
        if (config.autoSync && config.isConfigured) {
            SyncWorker.schedule(context)
        } else {
            SyncWorker.cancelPeriodic(context)
        }
    }

    /** 执行一次同步并持久化结果（同步时间 + 消息）。 */
    suspend fun syncNow(): SyncResult {
        val config = WebDavConfig.load(context)
        val result = SyncEngine(db, config).sync()
        val updated = config.copy(
            lastSyncTime = if (result.success) System.currentTimeMillis() else config.lastSyncTime,
            lastSyncMessage = result.message
        )
        WebDavConfig.save(context, updated)
        return result
    }

    /** 测试连接（IO 线程）。 */
    suspend fun testConnection(config: WebDavConfig): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { WebDavClient(config).ping() }.getOrDefault(false)
        }
}
