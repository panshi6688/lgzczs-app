package com.lgzczs.app.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YoukaPage(
    tokenManager: TokenManager,
    onStatusChange: (PlatformStatus) -> Unit
) {
    val context = LocalContext.current
    var hasToken by remember { mutableStateOf(tokenManager.youkaToken != null) }

    LaunchedEffect(hasToken) {
        onStatusChange(if (hasToken) PlatformStatus.LOGGED_IN else PlatformStatus.NOT_LOGGED_IN)
    }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                setBuiltInZoomControls(true)
                setDisplayZoomControls(false)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                textZoom = 100
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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
                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("公告")
                        .setMessage(message)
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
            }

            loadUrl("http://supplier.ukayun.cn/")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val job = scope.launch {
            while (isActive) {
                delay(2000)
                withContext(Dispatchers.Main) {
                    val cookie =
                        CookieManager.getInstance().getCookie("http://supplier.ukayun.cn")
                    if (cookie != null) {
                        val token = cookie.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("admin_token=") }
                            ?.removePrefix("admin_token=")
                        if (token != null && token.isNotEmpty()) {
                            tokenManager.youkaToken = token
                            hasToken = true
                        }
                    }
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_DESTROY -> job.cancel()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            job.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}
