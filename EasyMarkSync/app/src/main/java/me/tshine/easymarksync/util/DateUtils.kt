package me.tshine.easymarksync.util

import java.time.LocalDate

/**
 * 日期工具。
 * 新建笔记时默认标题/文件名使用 ISO 日期（如 2026-09-12），创建后可在编辑器内修改。
 */
object DateUtils {
    /** 今天（本地时区）的 ISO 日期，形如 2026-09-12。 */
    fun todayName(): String = LocalDate.now().toString()
}
