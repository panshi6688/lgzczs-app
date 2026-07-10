package com.lgzczs.app.gecko

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView

class SessionManager(
    val onYoukaToken: (String) -> Unit,
    val onHuiToken: (String) -> Unit,
    val onLog: (type: String, source: String, message: String) -> Unit,
    val onStatusChange: (platform: String, isLoading: Boolean) -> Unit
) {
    var youkaCurrentUrl: String? = null
        private set
    var huiCurrentUrl: String? = null
        private set

    var youkaWebView: WebView? = null
        private set
    var huiWebView: WebView? = null
        private set

    private val huiDefaultUrl = "https://sup.78k.cn/"
    private val youkaDefaultUrl = "http://supplier.hgmqy.cn/"

    private val huiTokenJs = """
        (() => {
            try {
                var t = localStorage.getItem('access_token');
                if (t) return t;
                t = sessionStorage.getItem('access_token');
                if (t) return t;
                var m = document.cookie.match(/access_token=([^;]+)/);
                if (m) return m[1];
            } catch(e) {}
            return '';
        })()
    """.trimIndent()

    private val youkaTokenJs = """
        (() => {
            try {
                var t = localStorage.getItem('admin_token');
                if (t) return t;
                var m = document.cookie.match(/admin_token=([^;]+)/);
                if (m) return m[1];
            } catch(e) {}
            return '';
        })()
    """.trimIndent()

    fun attachYoukaWebView(wv: WebView) {
        youkaWebView = wv
    }

    fun attachHuiWebView(wv: WebView) {
        huiWebView = wv
    }

    fun updateYoukaUrl(url: String?) {
        if (url != null && !url.startsWith("javascript:")) {
            youkaCurrentUrl = url
        }
    }

    fun updateHuiUrl(url: String?) {
        if (url != null && !url.startsWith("javascript:")) {
            huiCurrentUrl = url
        }
    }

    fun loadHuiPage() {
        huiWebView?.loadUrl(huiDefaultUrl)
    }

    fun loadYoukaPage(storedToken: String? = null) {
        if (storedToken != null) {
            CookieManager.getInstance().setCookie(youkaDefaultUrl, "admin_token=$storedToken; path=/")
            CookieManager.getInstance().flush()
        }
        youkaWebView?.loadUrl(youkaDefaultUrl)
    }

    fun reloadHuiPage() {
        huiWebView?.loadUrl(huiCurrentUrl ?: huiDefaultUrl)
    }

    fun reloadYoukaPage() {
        youkaWebView?.loadUrl(youkaCurrentUrl ?: youkaDefaultUrl)
    }

    fun refreshTokens() {
        huiWebView?.evaluateJavascript(huiTokenJs) { value ->
            val token = value?.trim('"')?.trim()
            if (!token.isNullOrEmpty() && token != "null") {
                onHuiToken(token)
            }
        }
        youkaWebView?.evaluateJavascript(youkaTokenJs) { value ->
            val token = value?.trim('"')?.trim()
            if (!token.isNullOrEmpty() && token != "null") {
                onYoukaToken(token)
            }
        }
    }

    fun getHuiTokenJs() = huiTokenJs
    fun getYoukaTokenJs() = youkaTokenJs
}

fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
