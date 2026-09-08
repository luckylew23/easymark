package me.tshine.easymarksync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.tshine.easymarksync.data.repo.SyncRepository
import java.util.concurrent.TimeUnit

/**
 * 后台自动同步 Worker（WorkManager）。仅当配置了 WebDAV 且开启自动同步时执行。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = SyncRepository(applicationContext)
        val config = repo.loadConfig()
        if (!config.autoSync || !config.isConfigured) {
            return Result.success() // 未启用自动同步，静默跳过
        }
        val result = repo.syncNow()
        return if (result.success) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "easymarksync_periodic_webdav"
        private const val INTERVAL_HOURS = 6L

        /** 注册 / 更新周期同步任务（幂等）。 */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 立即触发一次同步（供设置页手动同步按钮使用，也可复用周期任务通道）。 */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
