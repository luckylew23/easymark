package me.tshine.easymarksync.webdav

/** 一次同步的结果统计，对齐 dav_diary 的 SyncResult。 */
data class SyncResult(
    val success: Boolean,
    val message: String,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflicts: Int = 0,
    val removed: Int = 0
) {
    companion object {
        fun failure(message: String) = SyncResult(false, message)
        fun ok(message: String, uploaded: Int, downloaded: Int, conflicts: Int, removed: Int) =
            SyncResult(true, message, uploaded, downloaded, conflicts, removed)
    }
}
