package com.example.zhidao.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewMarkdown(
    markdown: String,
    baseUrl: String,
    sessionId: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Update content when markdown or baseUrl changes
    LaunchedEffect(markdown, baseUrl, sessionId, webViewRef) {
        webViewRef?.let { webView ->
            val data = JSONObject().apply {
                put("type", "update")
                put("markdown", markdown)
                put("baseUrl", baseUrl)
                put("sessionId", sessionId)
            }
            webView.evaluateJavascript("window.postMessage(${data}, '*')", null)
        }
    }

    AndroidView(
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        
                        // 1. Handle our manual same-origin proxy for remote images
                        // This bypasses Mixed Content blocking by fetching in native code
                        if (url.startsWith("https://appassets.androidplatform.net/proxy")) {
                            val targetUrl = request.url.getQueryParameter("url")
                            if (targetUrl != null) {
                                return try {
                                    android.util.Log.d("WebViewMarkdown", "Proxying request for: $targetUrl")
                                    val connection = java.net.URL(targetUrl).openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 10000
                                    connection.readTimeout = 15000
                                    connection.requestMethod = "GET"
                                    
                                    val responseCode = connection.responseCode
                                    if (responseCode >= 400) {
                                        val errorMsg = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                                        android.util.Log.e("WebViewMarkdown", "Server returned $responseCode for path: $targetUrl - Error: $errorMsg")
                                        return null
                                    }

                                    val contentType = connection.contentType ?: "image/*"
                                    WebResourceResponse(contentType, connection.contentEncoding, connection.inputStream)
                                } catch (e: Exception) {
                                    android.util.Log.e("WebViewMarkdown", "Failed to proxy image: $targetUrl", e)
                                    null
                                }
                            }
                        }
                        
                        // 2. Let assetLoader handle local assets (index.html, css, js)
                        val assetResponse = assetLoader.shouldInterceptRequest(request.url)
                        if (assetResponse != null) return assetResponse

                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        webViewRef = view
                        // Initial content load via postMessage
                        val data = JSONObject().apply {
                            put("type", "update")
                            put("markdown", markdown)
                            put("baseUrl", baseUrl)
                            put("sessionId", sessionId)
                        }
                        view?.evaluateJavascript("window.postMessage(${data}, '*')", null)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            android.util.Log.d("WebViewMarkdown", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                        }
                        return true
                    }
                }

                setBackgroundColor(Color.TRANSPARENT)
                
                // Add JS Interface for communication from Web to Native
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRenderComplete(height: Int) {
                        // Handle height update if needed, though WRAP_CONTENT often works
                    }
                }, "Android")

                loadUrl("https://appassets.androidplatform.net/assets/markdown/index.html")
            }
        },
        update = { },
        modifier = modifier
    )
}
