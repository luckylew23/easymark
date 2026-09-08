package me.tshine.easymarksync.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import kotlinx.coroutines.launch
import me.tshine.easymarksync.data.db.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (String) -> Unit,
    onCreateNote: (String) -> Unit
) {
    val notes by viewModel.notesFlow.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val syncMsg by viewModel.lastSyncMessage.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadSyncStatus() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("易码同步") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    val note = viewModel.createNote()
                    onCreateNote(note.id)
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "新建笔记")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.onQueryChange(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索笔记") },
                    singleLine = true
                )
                Box(contentAlignment = Alignment.Center) {
                    if (syncing) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "同步",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp).clickable {
                                viewModel.syncNow { result ->
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
            if (syncMsg.isNotBlank()) {
                Text(
                    text = syncMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无笔记，点击 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onLongClick = { pendingDelete = note }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除笔记") },
            text = { Text("确定删除「${note.title.ifBlank { "无标题" }}」？删除后将在下次同步时清理云端副本。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.deleteNote(note.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = note.title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = note.content.lineSequence().firstOrNull()?.take(80) ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = formatTime(note.modifyTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatTime(t: Long): String = timeFormat.format(Date(t))
