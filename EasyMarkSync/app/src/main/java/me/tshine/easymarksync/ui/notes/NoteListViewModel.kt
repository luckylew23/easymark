package me.tshine.easymarksync.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tshine.easymarksync.data.db.NoteEntity
import me.tshine.easymarksync.data.repo.NoteRepository
import me.tshine.easymarksync.data.repo.SyncRepository
import me.tshine.easymarksync.webdav.SyncResult

class NoteListViewModel(
    private val notes: NoteRepository,
    private val sync: SyncRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val notesFlow: StateFlow<List<NoteEntity>> =
        combine(query, notes.observeActive()) { q, list ->
            if (q.isBlank()) list else list.filter {
                it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _lastSyncMessage = MutableStateFlow("")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage

    fun onQueryChange(q: String) {
        query.value = q
    }

    suspend fun createNote(): NoteEntity = notes.createNote()

    suspend fun deleteNote(id: String) = notes.softDelete(id)

    fun syncNow(onResult: (SyncResult) -> Unit) {
        viewModelScope.launch {
            _syncing.value = true
            val result = sync.syncNow()
            _syncing.value = false
            _lastSyncMessage.value = result.message
            onResult(result)
        }
    }

    fun loadSyncStatus() {
        _lastSyncMessage.value = sync.loadConfig().lastSyncMessage
    }
}
