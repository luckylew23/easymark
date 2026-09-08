package me.tshine.easymarksync.webdav

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * WebDAV 云备份配置（加密 SharedPreferences 存储）。参考 dav_diary 的 WebDavConfig。
 * 敏感字段（密码）经 EncryptedSharedPreferences 加密存储。
 */
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteDir: String = DEFAULT_REMOTE_DIR,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS,
    val autoSync: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastSyncMessage: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    companion object {
        const val DEFAULT_REMOTE_DIR = "/EasyMarkSync/"

        private const val PREFS = "webdav_sync_config"
        private const val K_URL = "server_url"
        private const val K_USER = "username"
        private const val K_PWD = "password"
        private const val K_DIR = "remote_dir"
        private const val K_STRATEGY = "conflict_strategy"
        private const val K_AUTO = "auto_sync"
        private const val K_LAST_TIME = "last_sync_time"
        private const val K_LAST_MSG = "last_sync_message"

        private fun prefs(context: Context): SharedPreferences {
            // 加密 SharedPreferences：密码等敏感字段落盘时加密
            return runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse {
                // 加密初始化失败（如系统密钥环不可用）时回退明文，不阻塞功能
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
        }

        fun load(context: Context): WebDavConfig {
            val sp = prefs(context)
            return WebDavConfig(
                serverUrl = sp.getString(K_URL, "") ?: "",
                username = sp.getString(K_USER, "") ?: "",
                password = sp.getString(K_PWD, "") ?: "",
                remoteDir = sp.getString(K_DIR, DEFAULT_REMOTE_DIR) ?: DEFAULT_REMOTE_DIR,
                conflictStrategy = runCatching {
                    ConflictStrategy.valueOf(sp.getString(K_STRATEGY, null) ?: "")
                }.getOrDefault(ConflictStrategy.LAST_WRITE_WINS),
                autoSync = sp.getBoolean(K_AUTO, false),
                lastSyncTime = sp.getLong(K_LAST_TIME, 0L),
                lastSyncMessage = sp.getString(K_LAST_MSG, "") ?: ""
            )
        }

        fun save(context: Context, config: WebDavConfig) {
            prefs(context).edit()
                .putString(K_URL, config.serverUrl)
                .putString(K_USER, config.username)
                .putString(K_PWD, config.password)
                .putString(K_DIR, config.remoteDir.ifBlank { DEFAULT_REMOTE_DIR })
                .putString(K_STRATEGY, config.conflictStrategy.name)
                .putBoolean(K_AUTO, config.autoSync)
                .putLong(K_LAST_TIME, config.lastSyncTime)
                .putString(K_LAST_MSG, config.lastSyncMessage)
                .apply()
        }
    }
}
