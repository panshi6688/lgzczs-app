package com.lgzczs.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Log
import java.net.URLEncoder

object UrlOpener {

    private const val TAOBAO_PACKAGE = "com.taobao.taobao"
    private const val DIANTAO_PACKAGE = "com.taobao.live"

    fun open(context: Context, url: String, keyword: String? = null) {
        val finalUrl = if (!keyword.isNullOrBlank() && url.contains("s.m.taobao.com")) {
            replaceQParam(url, keyword)
        } else {
            url
        }
        openInternal(context, finalUrl)
    }

    private fun openInternal(context: Context, url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "链接地址无效", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(context, "链接格式无效", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            when {
                url.contains("shop294995043.m.taobao.com") ||
                url == "https://main.m.taobao.com/cart/index.html?spm=a2141.7631565.tbshopmod-photo_retouch.21&spm=a2141.7631565.tbshopmod-photo_retouch.18" ||
                url == "https://main.m.taobao.com/olist/index.html?tabCode=waitPay" -> {
                    openWithPackage(context, url, DIANTAO_PACKAGE)
                }
                url.contains("s.m.taobao.com") || url.contains("web.m.taobao.com") -> {
                    openWithTbOpenScheme(context, url)
                }
                url.contains(".taobao.com") || url.contains(".tmall.com") -> {
                    openWithPackage(context, url, TAOBAO_PACKAGE)
                }
                else -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("UrlOpener", "Failed to open URL: $url", e)
            Toast.makeText(context, "打开链接失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openWithPackage(context: Context, url: String, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    private fun openWithTbOpenScheme(context: Context, url: String) {
        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val scheme = "tbopen://m.taobao.com/tbopen/index.html?" +
                    "h5Url=$encodedUrl" +
                    "&action=ali.open.nav" +
                    "&module=h5" +
                    "&bootImage=0" +
                    "&slk_t=${System.currentTimeMillis()}" +
                    "&slk_gid=gid_er_sidebar_0" +
                    "&afcPromotionOpen=false" +
                    "&bc_fl_src=h5_huanduan" +
                    "&source=slk_dp"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
                setPackage(TAOBAO_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UrlOpener", "tbopen scheme failed, falling back to WebView", e)
            openWithWebView(context, url)
        }
    }

    private fun openWithWebView(context: Context, url: String) {
        try {
            val webView = WebView(context.applicationContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, interceptedUrl: String?): Boolean {
                    if (interceptedUrl == null) return false
                    if (interceptedUrl.startsWith("taobao://") || interceptedUrl.startsWith("tbopen://")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(interceptedUrl)).apply {
                                setPackage(TAOBAO_PACKAGE)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            view?.postDelayed({ webView.destroy() }, 1000)
                            return true
                        } catch (e: Exception) {
                            Log.e("UrlOpener", "WebView scheme failed", e)
                        }
                    }
                    return false
                }
            }

            webView.loadUrl(url)
        } catch (e: Exception) {
            Log.e("UrlOpener", "WebView fallback failed", e)
            Toast.makeText(context, "打开链接失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replaceQParam(url: String, keyword: String): String {
        val uri = Uri.parse(url)
        val qValue = uri.getQueryParameter("q") ?: return url
        return uri.buildUpon().clearQuery()
            .apply {
                uri.queryParameterNames.forEach { name ->
                    val value = if (name == "q") keyword else uri.getQueryParameter(name)
                    appendQueryParameter(name, value!!)
                }
            }.build().toString()
    }
}
