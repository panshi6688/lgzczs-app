package com.lgzczs.app.ui

import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.LogType
import com.lgzczs.app.util.TokenManager
import com.lgzczs.app.util.WebViewDiagnostics

@Composable
fun YoukaPage(
    tokenManager: TokenManager,
    debugMode: Boolean = false,
    onStatusChange: (PlatformStatus) -> Unit
) {
    val context = LocalContext.current
    var hasToken by remember { mutableStateOf(tokenManager.youkaToken != null) }
    var showDebugPanel by remember { mutableStateOf(false) }

    LaunchedEffect(hasToken) {
        onStatusChange(if (hasToken) PlatformStatus.LOGGED_IN else PlatformStatus.NOT_LOGGED_IN)
    }

    WebView.setWebContentsDebuggingEnabled(true)

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                setBuiltInZoomControls(true)
                setDisplayZoomControls(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                textZoom = 100
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    WebViewDiagnostics.add(LogType.NETWORK_REQ, url, "NAVIGATE")
                    view?.loadUrl(url)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("YoukaWebView", "Loading: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cookie = CookieManager.getInstance().getCookie("http://supplier.ukayun.cn")
                    if (cookie != null) {
                        val token = cookie.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("admin_token=") }
                            ?.removePrefix("admin_token=")
                        if (token != null && token.isNotEmpty()) {
                            tokenManager.youkaToken = token
                            hasToken = true
                            WebViewDiagnostics.add(LogType.JS_LOG, url ?: "", "Token extracted: ${token.take(8)}...")
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request != null) {
                        WebViewDiagnostics.add(LogType.NETWORK_REQ, request.url.toString(), request.method ?: "GET")
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val url = request?.url.toString()
                    val status = errorResponse?.statusCode ?: 0
                    WebViewDiagnostics.add(LogType.HTTP_ERROR, url, "HTTP $status ${errorResponse?.reasonPhrase}")
                }

                @Deprecated("deprecated")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    WebViewDiagnostics.add(LogType.ERROR, failingUrl ?: "", "Error $errorCode: $description")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    WebViewDiagnostics.add(LogType.ERROR, request?.url.toString(), "Error ${error?.errorCode}: ${error?.description}")
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: android.webkit.SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    val msg = message?.message() ?: ""
                    val src = "${message?.sourceId()}:${message?.lineNumber()}"
                    val level = message?.messageLevel()
                    val type = when (level) {
                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> LogType.JS_ERROR
                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> LogType.JS_WARN
                        android.webkit.ConsoleMessage.MessageLevel.TIP -> LogType.JS_DEBUG
                        else -> LogType.JS_LOG
                    }
                    WebViewDiagnostics.add(type, src, msg)
                    Log.d("YoukaWebView", "[$level] $msg ($src)")
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val newView = view
                    if (newView != null && resultMsg != null) {
                        val transport = resultMsg.obj as? WebView.WebViewTransport
                        transport?.webView = newView
                        resultMsg.sendToTarget()
                    }
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(context)
                        .setTitle(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm() }
                        .setCancelable(false)
                        .show()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm() }
                        .setNegativeButton("取消") { _, _ -> result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: android.webkit.JsPromptResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("提示")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm(defaultValue) }
                        .setNegativeButton("取消") { _, _ -> result?.cancel() }
                        .show()
                    return true
                }
            }

            loadUrl("http://supplier.ukayun.cn/")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (debugMode) {
            FloatingActionButton(
                onClick = { showDebugPanel = !showDebugPanel },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp),
                containerColor = Color(0x99000000),
                contentColor = Color.White
            ) {
                Text("🐛", fontSize = 18.sp)
            }
        }

        if (showDebugPanel) {
            DebugPanel(webView = webView, onClose = { showDebugPanel = false })
        }
    }
}
