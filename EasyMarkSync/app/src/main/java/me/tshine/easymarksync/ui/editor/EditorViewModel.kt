package me.tshine.easymarksync.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tshine.easymarksync.data.db.NoteEntity
import me.tshine.easymarksync.data.repo.NoteRepository
import me.tshine.easymarksync.util.DateUtils

class EditorViewModel(
    private val notes: NoteRepository,
    private val noteId: String
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved

    private var saveJob: Job? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            val n = notes.getById(noteId)
            if (n != null) {
                _note.value = n
                _title.value = n.title
                _content.value = n.content
            } else {
                // 笔记不存在时兜底创建，避免后续保存静默丢弃；默认标题 = 当天日期（可修改）
                _note.value = NoteEntity(
                    id = noteId,
                    title = DateUtils.todayName(), content = "",
                    modifyTime = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            }
            loaded = true
        }
    }

    fun onTitleChange(v: String) {
        _title.value = v
        _saved.value = false
        scheduleSave()
    }

    fun onContentChange(v: String) {
        _content.value = v
        _saved.value = false
        scheduleSave()
    }

    /** 防抖自动保存（600ms）。 */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            persist()
        }
    }

    suspend fun persist() {
        val current = _note.value
        if (current == null) {
            // 尚未加载完成（理论上已被 init 兜底）；构造实体避免丢数据
            val base = NoteEntity(
                id = noteId,
                title = _title.value,
                content = _content.value,
                modifyTime = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            notes.upsert(base)
            _note.value = base
            _saved.value = true
            return
        }
        val updated = current.copy(
            title = _title.value,
            content = _content.value,
            modifyTime = System.currentTimeMillis()
        )
        if (updated != current) {
            notes.upsert(updated)
            _note.value = updated
        }
        _saved.value = true
    }

    suspend fun delete() {
        val current = _note.value ?: return
        notes.softDelete(current.id)
    }
}
