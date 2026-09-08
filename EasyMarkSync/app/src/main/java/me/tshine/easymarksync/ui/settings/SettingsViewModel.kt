package me.tshine.easymarksync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tshine.easymarksync.data.repo.SyncRepository
import me.tshine.easymarksync.webdav.ConflictStrategy
import me.tshine.easymarksync.webdav.SyncResult
import me.tshine.easymarksync.webdav.WebDavConfig

class SettingsViewModel(private val sync: SyncRepository) : ViewModel() {

    private val _config = MutableStateFlow(sync.loadConfig())
    val config: StateFlow<WebDavConfig> = _config

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _lastResult = MutableStateFlow<SyncResult?>(null)
    val lastResult: StateFlow<SyncResult?> = _lastResult

    fun onUrlChange(v: String) { _config.value = _config.value.copy(serverUrl = v) }
    fun onUserChange(v: String) { _config.value = _config.value.copy(username = v) }
    fun onPasswordChange(v: String) { _config.value = _config.value.copy(password = v) }
    fun onRemoteDirChange(v: String) { _config.value = _config.value.copy(remoteDir = v) }
    fun onStrategyChange(s: ConflictStrategy) { _config.value = _config.value.copy(conflictStrategy = s) }
    fun onAutoSyncChange(b: Boolean) { _config.value = _config.value.copy(autoSync = b) }

    fun save() {
        val c = _config.value
        sync.saveConfig(c)
        // 依据 autoSync 开关重新调度/取消 WorkManager 周期任务
        sync.applyAutoSyncSchedule(c)
    }

    fun testConnection(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _testing.value = true
            val ok = sync.testConnection(_config.value)
            _testing.value = false
            onDone(ok)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _syncing.value = true
            // 先保存当前表单，确保使用最新配置同步
            save()
            val result = sync.syncNow()
            _syncing.value = false
            _lastResult.value = result
            _config.value = sync.loadConfig()
        }
    }
}
