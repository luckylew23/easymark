package me.tshine.easymarksync.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Markdown 预览渲染器：flexmark 解析 Markdown → HTML，
 * WebView 加载 assets 中的 highlight.js / markdown.css / MathJax 资源
 * （渲染资源复用自原版易码 APK）。
 */
object MarkdownRenderer {

    private val options = MutableDataSet()
        .set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
        ))
        .set(HtmlRenderer.SOFT_BREAK, "\n")

    private val parser: Parser = Parser.builder(options).build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder(options).build()

    /** 生成完整的渲染 HTML（flexmark 解析 + 模板）。 */
    fun buildHtml(markdown: String): String {
        val document = parser.parse(markdown)
        val body = htmlRenderer.render(document)
        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="markdown.css">
        <script src="highlight.js"></script>
        <script src="highlight-init.js"></script>
        <script src="mathjax-config.js"></script>
        <script type="text/javascript" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
        </head>
        <body>
        $body
        <script>hljs.highlightAll();</script>
        </body>
        </html>
        """.trimIndent()
    }

    /** 初始化 WebView（JS + 链接拦截）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun setup(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // 拦截外部链接：不跳走，改用系统浏览器打开
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url ?: return false
                return openExternally(view.context, url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return openExternally(view.context, Uri.parse(url))
            }
        }
    }

    /** 加载渲染 HTML（file:///android_asset/ 为基准，可访问本地渲染资源）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun loadHtml(webView: WebView, html: String) {
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun openExternally(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme == "file" || scheme == "about" || scheme == "data") return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            false
        }
    }
}
