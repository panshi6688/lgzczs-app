package com.lgzczs.app.ui

import android.webkit.CookieManager
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
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            setAllowFileAccess(false)
            setAllowContentAccess(false)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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
