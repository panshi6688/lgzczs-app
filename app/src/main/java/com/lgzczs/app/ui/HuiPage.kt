package com.lgzczs.app.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import kotlinx.coroutines.delay

class HuiWebInterface(
    private val onToken: (String) -> Unit
) {
    @JavascriptInterface
    fun onTokenReceived(token: String) {
        onToken(token)
    }
}

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

    val handler = remember { Handler(Looper.getMainLooper()) }

    val webInterface = remember {
        HuiWebInterface { token ->
            handler.post {
                tokenManager.huiToken = token
                hasToken = true
            }
        }
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
                builtInZoomControls(true)
                displayZoomControls(false)
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            addJavascriptInterface(webInterface, "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var t = localStorage.getItem('access_token');
                            if (t) Android.onTokenReceived(t);
                        })()
                        """.trimIndent(), null
                    )
                }
            }

            loadUrl("https://sup.78k.cn/")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            webView.evaluateJavascript(
                """
                (function() {
                    var t = localStorage.getItem('access_token');
                    if (t) Android.onTokenReceived(t);
                })()
                """.trimIndent(), null
            )
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}