package me.tshine.easymarksync.webdav

/**
 * 冲突决策策略，对齐 dav_diary（Kidiary）的 ConflictStrategy。
 */
enum class ConflictStrategy {
    /** 最后写入者胜：比较修改时间，较新的一方保留。 */
    LAST_WRITE_WINS,

    /** 保留副本：冲突时远端旧文件保留为 .conflict-<时间>.md，本地版本覆盖上传。 */
    KEEP_BOTH
}
