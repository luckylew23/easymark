package me.tshine.easymarksync.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.tshine.easymarksync.BuildConfig
import me.tshine.easymarksync.webdav.ConflictStrategy
import me.tshine.easymarksync.webdav.WebDavConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("WebDAV 云备份", style = MaterialTheme.typography.titleMedium)
            Text(
                "数据直连你的私有云（坚果云 / Nextcloud / 群晖等），参考 dav_diary 的增量同步策略：指纹比对、三方状态、冲突决策。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = config.serverUrl,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                singleLine = true
            )
            OutlinedTextField(
                value = config.username,
                onValueChange = viewModel::onUserChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账号（坚果云为邮箱）") },
                singleLine = true
            )
            OutlinedTextField(
                value = config.password,
                onValueChange = viewModel::onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("应用专用密码（非登录密码）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = config.remoteDir,
                onValueChange = viewModel::onRemoteDirChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("远端备份目录") },
                singleLine = true
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("冲突策略", style = MaterialTheme.typography.titleSmall)
                    StrategyRow(
                        label = "最后写入者胜（默认）",
                        selected = config.conflictStrategy == ConflictStrategy.LAST_WRITE_WINS,
                        onClick = { viewModel.onStrategyChange(ConflictStrategy.LAST_WRITE_WINS) }
                    )
                    StrategyRow(
                        label = "保留副本（冲突时保留 .conflict 文件）",
                        selected = config.conflictStrategy == ConflictStrategy.KEEP_BOTH,
                        onClick = { viewModel.onStrategyChange(ConflictStrategy.KEEP_BOTH) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = config.autoSync, onCheckedChange = { viewModel.onAutoSyncChange(it) })
                Text("自动同步（每 6 小时后台任务）")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.save()
                        Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                Button(
                    onClick = { viewModel.testConnection { ok ->
                        Toast.makeText(
                            context,
                            if (ok) "连接成功" else "连接失败：请检查地址、账号与应用专用密码",
                            Toast.LENGTH_LONG
                        ).show()
                    } },
                    modifier = Modifier.weight(1f),
                    enabled = !testing
                ) {
                    if (testing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                    else Text("测试连接")
                }
            }

            Button(
                onClick = {
                    viewModel.save()
                    viewModel.syncNow()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !syncing
            ) {
                if (syncing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                else Text("立即同步")
            }

            // 同步状态
            val lastTime = config.lastSyncTime
            val statusText = buildString {
                if (lastTime > 0) {
                    append("上次同步：")
                    append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastTime)))
                    append("\n")
                }
                append(config.lastSyncMessage.ifBlank { "尚未同步" })
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "易码同步 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StrategyRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
