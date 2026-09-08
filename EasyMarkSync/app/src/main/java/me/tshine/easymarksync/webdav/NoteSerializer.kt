package me.tshine.easymarksync.webdav

import me.tshine.easymarksync.data.db.NoteEntity
import java.security.MessageDigest

/**
 * 笔记 ↔ Markdown 文件序列化 + 内容指纹。
 *
 * 参考 dav_diary：每条笔记在远端以独立 .md 文件存放，文件头带 YAML 风格信封
 * （id / updatedAt / title），供同步引擎解析与三方状态比对。
 */
object NoteSerializer {

    private const val ENVELOPE_BEGIN = "---"
    private const val ENVELOPE_END = "---"

    /** 序列化为带信封的 Markdown 文本。 */
    fun toMarkdown(note: NoteEntity): String = buildString {
        append(ENVELOPE_BEGIN).append('\n')
        append("id: ").append(safe(note.id)).append('\n')
        append("createdAt: ").append(note.createdAt).append('\n')
        append("updatedAt: ").append(note.modifyTime).append('\n')
        append("title: ").append(safe(note.title)).append('\n')
        append(ENVELOPE_END).append('\n')
        append(note.content)
    }

    /** 解析远端 Markdown 为笔记；信封缺失或 id 缺失返回 null。 */
    fun fromMarkdown(text: String): NoteEntity? {
        if (text.isBlank()) return null
        var id: String? = null
        var updatedAt = 0L
        var createdAt = 0L
        var title = ""
        var body = text

        if (text.startsWith("$ENVELOPE_BEGIN\n")) {
            val end = text.indexOf("\n$ENVELOPE_END\n")
            if (end > 0) {
                val head = text.substring(ENVELOPE_BEGIN.length + 1, end)
                head.lineSequence().forEach { line ->
                    val idx = line.indexOf(": ")
                    if (idx > 0) {
                        when (line.substring(0, idx)) {
                            "id" -> id = line.substring(idx + 2).trim()
                            "createdAt" -> createdAt = line.substring(idx + 2).trim().toLongOrNull() ?: 0L
                            "updatedAt" -> updatedAt = line.substring(idx + 2).trim().toLongOrNull() ?: 0L
                            "title" -> title = line.substring(idx + 2).trim()
                        }
                    }
                }
                body = text.substring(end + ENVELOPE_END.length + 2)
            }
        }
        if (id.isNullOrBlank()) return null
        // 旧版本信封无 createdAt：回退到 updatedAt，保证向后兼容
        val originCreated = createdAt.takeIf { it > 0 } ?: updatedAt.takeIf { it > 0 }
        val originUpdated = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        return NoteEntity(
            id = id,
            title = title,
            content = body,
            modifyTime = originUpdated,
            createdAt = originCreated?.takeIf { it > 0 } ?: originUpdated
        )
    }

    /** 内容指纹：SHA-256(title + content + modifyTime)。 */
    fun fingerprint(note: NoteEntity): String {
        val raw = "${note.title}\n${note.content}\n${note.modifyTime}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 依据笔记 id 生成远端文件名（规避标题中的非法路径字符）。 */
    fun remoteFileName(noteId: String): String = fingerprint(
        NoteEntity(id = noteId, title = "", content = "", modifyTime = 0L, createdAt = 0L)
    ).take(16) + ".md"

    private fun safe(s: String): String = s.replace("\n", " ").replace("\r", " ")
}
