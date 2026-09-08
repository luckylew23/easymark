package me.tshine.easymarksync.ui.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.tshine.easymarksync.util.MarkdownRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    markdown: String,
    onBack: () -> Unit
) {
    val html = MarkdownRenderer.buildHtml(markdown)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    MarkdownRenderer.setup(this)
                    loadHtml(html)
                }
            },
            update = { webView ->
                // 内容变化时重载渲染，避免首次为空时白屏
                webView.loadHtml(html)
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.loadHtml(html: String) {
    MarkdownRenderer.loadHtml(this, html)
}
