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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
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

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val job = scope.launch {
            while (isActive) {
                delay(2000)
                withContext(Dispatchers.Main) {
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
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                job.cancel()
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