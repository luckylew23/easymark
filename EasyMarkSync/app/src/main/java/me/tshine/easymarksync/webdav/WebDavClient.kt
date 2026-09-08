package me.tshine.easymarksync.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * 基于 OkHttp 的 WebDAV 客户端（自封装，不依赖老旧 Sardine）。
 *
 * 支持操作：PROPFIND（列目录/探测）、GET（读文件）、PUT（写文件）、
 * MKCOL（建目录）、DELETE（删文件）、MOVE（移动/重命名）、OPTIONS（测试连接）。
 *
 * 认证：Basic Auth（坚果云、Nextcloud 等通用）；Digest 认证暂不支持（可通过升级 OkHttp 拦截器扩展）。
 */
class WebDavClient(private val config: WebDavConfig) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "EasyMarkSync/0.2.0 (WebDAV)")
                        .build()
                )
            }
            .build()
    }

    private val authHeader: String by lazy {
        Credentials.basic(config.username, config.password)
    }

    /** 远端文件信息（来自 PROPFIND）。 */
    data class RemoteFile(
        val path: String,
        val lastModified: Long = 0L,
        val size: Long = 0L,
        val isDirectory: Boolean = false
    )

    /** 拼接完整远端 URL。 */
    fun join(remoteDir: String, name: String): String =
        normalizeBase(config.serverUrl) + normalizeDir(remoteDir).trimStart('/') + name

    /** 测试连接：PROPFIND 根目录，检查是否 207/200。 */
    fun ping(): Boolean {
        val url = normalizeBase(config.serverUrl)
        val request = Request.Builder().url(url).header("Authorization", authHeader)
            .method("PROPFIND", emptyBody()).header("Depth", "0").build()
        client.newCall(request).execute().use { resp ->
            return resp.isSuccessful || resp.code == 207
        }
    }

    /** 列出目录下的文件（Depth:1，过滤子目录）。 */
    fun listFiles(remoteDir: String): List<RemoteFile> {
        val url = normalizeBase(config.serverUrl) + normalizeDir(remoteDir).trimStart('/')
        val request = Request.Builder().url(url).header("Authorization", authHeader)
            .method("PROPFIND", emptyBody()).header("Depth", "1").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 207) {
                throw IOException("PROPFIND 失败: HTTP ${resp.code} $url")
            }
            val body = resp.body?.string() ?: ""
            return parseMultiStatus(body, url)
        }
    }

    /** 递归确保目录存在（逐级 MKCOL）。 */
    fun ensureDirectory(remoteDir: String) {
        val base = normalizeBase(config.serverUrl)
        val parts = normalizeDir(remoteDir).trim('/').split("/").filter { it.isNotBlank() }
        var path = base
        for (part in parts) {
            path += part + "/"
            if (!exists(path)) {
                val req = Request.Builder().url(path).header("Authorization", authHeader)
                    .method("MKCOL", emptyBody()).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 405) {
                        throw IOException("MKCOL 失败: HTTP ${resp.code} $path")
                    }
                }
            }
        }
    }

    /** 路径是否存在（PROPFIND Depth:0）。 */
    fun exists(path: String): Boolean {
        val request = Request.Builder().url(path).header("Authorization", authHeader)
            .method("PROPFIND", emptyBody()).header("Depth", "0").build()
        client.newCall(request).execute().use { resp ->
            return resp.isSuccessful || resp.code == 207
        }
    }

    /** 读取远端文件为 UTF-8 字符串。 */
    fun readFile(path: String): String {
        val request = Request.Builder().url(path).header("Authorization", authHeader).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GET 失败: HTTP ${resp.code} $path")
            return resp.body?.string() ?: ""
        }
    }

    /** 写入远端文件（自动创建父目录）。 */
    fun writeFile(path: String, content: String) {
        val idx = path.lastIndexOf('/')
        if (idx > 0) ensureDirectory(path.substring(0, idx + 1).removePrefix(normalizeBase(config.serverUrl)))
        val mediaType = "text/markdown; charset=utf-8".toMediaType()
        val body = content.toRequestBody(mediaType)
        val request = Request.Builder().url(path).header("Authorization", authHeader).put(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("PUT 失败: HTTP ${resp.code} $path")
        }
    }

    /** 删除远端文件（404 视为成功，幂等）。 */
    fun deleteFile(path: String) {
        val request = Request.Builder().url(path).header("Authorization", authHeader).delete().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IOException("DELETE 失败: HTTP ${resp.code} $path")
            }
        }
    }

    /** 移动 / 重命名远端文件（冲突保留副本时使用）。 */
    fun moveFile(fromPath: String, toPath: String) {
        val request = Request.Builder().url(fromPath).header("Authorization", authHeader)
            .header("Destination", toPath).method("MOVE", emptyBody()).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                throw IOException("MOVE 失败: HTTP ${resp.code}")
            }
        }
    }

    private fun emptyBody() = ByteArray(0).toRequestBody(null)

    /**
     * 解析 PROPFIND 的 multistatus XML 响应。
     * 使用 Android 内置 XmlPullParser，无额外依赖。
     */
    private fun parseMultiStatus(xml: String, baseUrl: String): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        if (xml.isBlank()) return files
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var href = ""
            var lastModified = 0L
            var size = 0L
            var isDir = false
            var inResponse = false

            fun flush() {
                if (inResponse && href.isNotBlank() && href != baseUrl) {
                    files.add(RemoteFile(href, lastModified, size, isDir))
                }
                href = ""; lastModified = 0L; size = 0L; isDir = false
            }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "response" -> { inResponse = true; flush() }
                            "href" -> href = parser.nextText()
                            "getlastmodified" -> {
                                val text = parser.nextText()
                                runCatching { lastModified = parseDavDate(text) }
                            }
                            "getcontentlength" -> {
                                runCatching { size = parser.nextText().trim().toLong() }
                            }
                            "collection" -> isDir = true
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "response") { flush() }
                }
                event = parser.next()
            }
            flush()
        } catch (e: Exception) {
            // XML 解析失败时返回已解析部分
        }
        return files.filterNot { it.isDirectory }
    }

    /** 解析 WebDAV 时间格式（RFC 1123 / ISO8601）。 */
    private fun parseDavDate(text: String): Long {
        // RFC 1123: Sun, 06 Nov 1994 08:49:37 GMT
        val rfc1123 = runCatching {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                .parse(text.trim())?.time ?: 0L
        }.getOrDefault(0L)
        if (rfc1123 > 0) return rfc1123
        // ISO8601: 1997-07-16T19:20:30Z
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .parse(text.trim())?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun normalizeBase(url: String): String {
        val u = url.trim()
        return if (u.endsWith("/")) u else "$u/"
    }

    private fun normalizeDir(dir: String): String {
        val d = dir.trim()
        val withSlash = if (d.startsWith("/")) d else "/$d"
        return if (withSlash.endsWith("/")) withSlash else "$withSlash/"
    }
}
