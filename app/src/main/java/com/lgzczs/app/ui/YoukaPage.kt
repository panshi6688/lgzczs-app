package com.lgzczs.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.gecko.removeFromParent
import com.lgzczs.app.util.TokenManager
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoukaPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    youkaToken: String?
) {
    val context = LocalContext.current
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { arrayOf(it) } ?: photoUri?.let { arrayOf(it) }
        } else null
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
        photoUri = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                sessionManager.youkaWebView?.let { existing ->
                    existing.removeFromParent()
                    existing
                } ?: run {
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)
                        settings.allowFileAccess = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        settings.textZoom = 100
                        settings.defaultFontSize = 16

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setNetworkAvailable(true)

                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onToken(token: String) {
                                sessionManager.onYoukaToken(token)
                            }
                        }, "YoukaBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                sessionManager.updateYoukaUrl(url)
                                sessionManager.onStatusChange("youka", true)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                sessionManager.updateYoukaUrl(url)
                                sessionManager.onStatusChange("youka", false)
                                view?.evaluateJavascript(sessionManager.getYoukaTokenJs()) { value ->
                                    val token = value?.trim('"')?.trim()
                                    if (!token.isNullOrEmpty() && token != "null") {
                                        sessionManager.onYoukaToken(token)
                                    }
                                }
                                view?.loadUrl("javascript:(function(){if(window.__yb)return;window.__yb=true;var max=30;(function c(){var m=document.cookie.match(/admin_token=([^;]+)/);if(m){YoukaBridge.onToken(m[1]);return}if(--max>0)setTimeout(c,800)})()})()")
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.proceed()
                            }
                        }

                        sessionManager.attachYoukaWebView(this)
                        sessionManager.loadYoukaPage(youkaToken)
                    }
                }
            },
            update = { webView ->
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, true, false)
                    }
                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                        val chrome = this
                        val transport = view!!.WebViewTransport()
                        transport.webView = WebView(view!!.context).apply {
                            webChromeClient = chrome
                            webViewClient = view!!.webViewClient
                            settings.javaScriptEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        resultMsg?.obj = transport
                        resultMsg?.sendToTarget()
                        return true
                    }
                    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?): Boolean {
                        fileCallback = filePathCallback
                        val ctx = webView?.context ?: return false
                        val photoFile = File(ctx.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                        photoFile.parentFile?.mkdirs()
                        photoFile.createNewFile()
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", photoFile)
                        photoUri = uri

                        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
                        }
                        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, uri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(galleryIntent, "选择文件").apply {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                        }
                        filePickerLauncher.launch(chooser)
                        return true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
