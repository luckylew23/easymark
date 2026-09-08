package me.tshine.easymarksync

import android.app.Application
import me.tshine.easymarksync.data.db.AppDatabase
import me.tshine.easymarksync.data.repo.SyncRepository

class EasyMarkApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // 启动时依据配置启用/停用周期同步任务（幂等）
        SyncRepository(this).applyAutoSyncSchedule(
            SyncRepository(this).loadConfig()
        )
    }
}
