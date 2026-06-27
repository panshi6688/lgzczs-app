package com.lgzczs.app.ui

import android.util.Log
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager

@Composable
fun HuiPage(
    tokenManager: TokenManager,
    onStatusChange: (PlatformStatus) -> Unit
) {
    val context = LocalContext.current
    var hasToken by remember { mutableStateOf(tokenManager.huiToken != null) }

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
                    view?.loadUrl(request?.url.toString())
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("HuiWebView", "Loading: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        "(function(){ return localStorage.getItem('access_token') })()"
                    ) { value ->
                        if (value != null && value != "null" && value.isNotEmpty()) {
                            val token = value.trim('"')
                            if (token.isNotEmpty()) {
                                tokenManager.huiToken = token
                                hasToken = true
                            }
                        }
                    }
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
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    Log.d("HuiWebView", "${message?.message()} (${message?.sourceId()}:${message?.lineNumber()})")
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
                        val transport = resultMsg.obj as? android.webkit.WebView.WebViewTransport
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
                    result: JsPromptResult?
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

            loadUrl("https://sup.78k.cn/")
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

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}
