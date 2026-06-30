package com.lgzczs.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import org.json.JSONObject
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HuiPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    huiToken: String?,
    onLogout: () -> Unit = {}
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
                sessionManager.huiWebView?.let { existing ->
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
                                sessionManager.onHuiToken(token)
                            }
                        }, "HuiBridge")

                        webViewClient = object : WebViewClient() {
                            private val pollHandler = Handler(Looper.getMainLooper())
                            private var tokenFound = false
                            private var currentView: WebView? = null

                            private val tokenPoll = Runnable {
                                val wv = currentView ?: return@Runnable
                                if (tokenFound) return@Runnable
                                wv.evaluateJavascript(sessionManager.getHuiTokenJs()) { value ->
                                    val token = value?.trim('"')?.trim()
                                    if (!token.isNullOrEmpty() && token != "null") {
                                        tokenFound = true
                                        sessionManager.onHuiToken(token)
                                        if (tokenManager.huiUsername == null) {
                                            wv.evaluateJavascript("""
                                                (function(){
                                                    var acc=document.getElementById('account');
                                                    var pwd=document.getElementById('password');
                                                    if(acc&&pwd)return JSON.stringify({user:acc.value,pass:pwd.value});
                                                    return '{}';
                                                })()
                                            """.trimIndent()) { json ->
                                                try {
                                                    val obj = JSONObject(json?.trim('"') ?: "{}")
                                                    val user = obj.optString("user", "")
                                                    val pass = obj.optString("pass", "")
                                                    if (user.isNotEmpty()) {
                                                        tokenManager.huiUsername = user
                                                        tokenManager.huiPassword = pass
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    } else {
                                        pollHandler.postDelayed(tokenPoll, 2000L)
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                tokenFound = false
                                currentView = view
                                sessionManager.updateHuiUrl(url)
                                sessionManager.onStatusChange("hui", true)
                                if (url?.contains("login") == true && tokenManager.huiToken != null) {
                                    onLogout()
                                }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                sessionManager.updateHuiUrl(url)
                                sessionManager.onStatusChange("hui", false)

                                val savedUser = tokenManager.huiUsername
                                val savedPass = tokenManager.huiPassword
                                if (savedUser != null && savedPass != null && url?.contains("login") == true) {
                                    val safeUser = savedUser.replace("\\", "\\\\").replace("'", "\\'")
                                    val safePass = savedPass.replace("\\", "\\\\").replace("'", "\\'")
                                    view?.evaluateJavascript("""
                                        (function(){
                                            var u='$safeUser',p='$safePass';
                                            if(!u||!p)return;
                                            function fill(){
                                                var acc=document.getElementById('account');
                                                var pwd=document.getElementById('password');
                                                if(acc&&pwd&&acc.offsetParent!==null){
                                                    acc.value=u;pwd.value=p;
                                                    acc.dispatchEvent(new Event('input',{bubbles:true}));
                                                    pwd.dispatchEvent(new Event('input',{bubbles:true}));
                                                    acc.dispatchEvent(new Event('change',{bubbles:true}));
                                                    pwd.dispatchEvent(new Event('change',{bubbles:true}));
                                                    return true;
                                                }
                                                return false;
                                            }
                                            if(!fill()){var ob=new MutationObserver(function(){if(fill())ob.disconnect()});ob.observe(document.body,{childList:true,subtree:true})}
                                        })();
                                    """.trimIndent(), null)
                                }

                                pollHandler.removeCallbacks(tokenPoll)
                                tokenPoll.run()
                            }
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                request?.requestHeaders?.forEach { (key, value) ->
                                    if (key.equals("Authorization", ignoreCase = true) && value.startsWith("Bearer ") && !tokenFound) {
                                        val token = value.removePrefix("Bearer ").trim()
                                        if (token.isNotEmpty()) {
                                            tokenFound = true
                                            sessionManager.onHuiToken(token)
                                        }
                                    }
                                    if (key.equals("Cookie", ignoreCase = true) && !tokenFound) {
                                        Regex("access_token=([^;]+)").find(value)?.groupValues?.get(1)?.let { token ->
                                            if (token.isNotEmpty()) {
                                                tokenFound = true
                                                sessionManager.onHuiToken(token)
                                            }
                                        }
                                    }
                                }
                                return null
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.proceed()
                            }
                        }

                        sessionManager.attachHuiWebView(this)
                        sessionManager.loadHuiPage()
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
