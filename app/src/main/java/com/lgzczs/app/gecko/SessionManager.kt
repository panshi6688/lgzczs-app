package com.lgzczs.app.gecko

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
    private val youkaDefaultUrl = "http://supplier.ukayun.cn/"

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

    fun reloadAll() {
        huiWebView?.loadUrl(huiCurrentUrl ?: huiDefaultUrl)
        youkaWebView?.loadUrl(youkaCurrentUrl ?: youkaDefaultUrl)
    }

    fun reloadHuiPage() {
        huiWebView?.loadUrl(huiCurrentUrl ?: huiDefaultUrl)
    }

    fun reloadYoukaPage() {
        youkaWebView?.loadUrl(youkaCurrentUrl ?: youkaDefaultUrl)
    }
}
