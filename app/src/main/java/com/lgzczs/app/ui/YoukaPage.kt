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
fun YoukaPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    youkaToken: String?,
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
                            @android.webkit.JavascriptInterface
                            fun onCredentials(json: String) {
                                if (tokenManager.youkaUsername != null) return
                                try {
                                    val obj = JSONObject(json)
                                    val user = obj.optString("user", "")
                                    val pass = obj.optString("pass", "")
                                    if (user.isNotEmpty() && pass.isNotEmpty()) {
                                        tokenManager.youkaUsername = user
                                        tokenManager.youkaPassword = pass
                                    }
                                } catch (_: Exception) {}
                            }
                            @android.webkit.JavascriptInterface
                            fun onLogoutDetected() {
                                Handler(Looper.getMainLooper()).post { onLogout() }
                            }
                        }, "YoukaBridge")

                        webViewClient = object : WebViewClient() {
                            private val pollHandler = Handler(Looper.getMainLooper())
                            private var tokenFound = false
                            private var currentView: WebView? = null

                            private val tokenPoll = object : Runnable {
                                override fun run() {
                                    val wv = currentView ?: return
                                    if (tokenFound) return
                                    wv.evaluateJavascript(sessionManager.getYoukaTokenJs()) { value ->
                                        var token = value?.trim('"')?.trim()
                                        if (token.isNullOrEmpty() || token == "null") {
                                            val cookies = CookieManager.getInstance().getCookie("http://supplier.hgmqy.cn/")
                                            if (!cookies.isNullOrEmpty()) {
                                                val match = Regex("admin_token=([^;]+)").find(cookies)
                                                if (match != null) token = match.groupValues[1]
                                            }
                                        }
                                        if (!token.isNullOrEmpty() && token != "null") {
                                            tokenFound = true
                                            sessionManager.onYoukaToken(token)
                                            if (tokenManager.youkaUsername == null) {
                                                wv.evaluateJavascript("""
                                                    (function(){
                                                        var el=document.querySelector('input[placeholder*="用户名"]')||document.querySelector('input.ivu-input:not([type="password"])');
                                                        var el2=document.querySelector('input[placeholder*="密码"]')||document.querySelector('input.ivu-input[type="password"]');
                                                        if(el&&el2)return JSON.stringify({user:el.value,pass:el2.value});
                                                        return '{}';
                                                    })()
                                                """.trimIndent()) { json ->
                                                    try {
                                                        val obj = JSONObject(json?.trim('"') ?: "{}")
                                                        val user = obj.optString("user", "")
                                                        val pass = obj.optString("pass", "")
                                                        if (user.isNotEmpty()) {
                                                            tokenManager.youkaUsername = user
                                                            tokenManager.youkaPassword = pass
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        } else {
                                            pollHandler.postDelayed(this, 2000L)
                                        }
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                tokenFound = false
                                currentView = view
                                sessionManager.updateYoukaUrl(url)
                                sessionManager.onStatusChange("youka", true)
                                if (url?.contains("login") == true && tokenManager.youkaToken != null) {
                                    onLogout()
                                }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                sessionManager.updateYoukaUrl(url)
                                sessionManager.onStatusChange("youka", false)

                                val savedUser = tokenManager.youkaUsername
                                val savedPass = tokenManager.youkaPassword
                                if (savedUser != null && savedPass != null) {
                                    val safeUser = savedUser.replace("\\", "\\\\").replace("'", "\\'")
                                    val safePass = savedPass.replace("\\", "\\\\").replace("'", "\\'")
                                    view?.evaluateJavascript("""
                                        (function(){
                                            var u='$safeUser',p='$safePass';
                                            if(!u||!p)return;
                                            function fill(){
                                                var el=document.querySelector('input[placeholder*="用户名"]')||document.querySelector('input.ivu-input:not([type="password"])');
                                                var el2=document.querySelector('input[placeholder*="密码"]')||document.querySelector('input.ivu-input[type="password"]');
                                                if(el&&el2&&el.offsetParent!==null){
                                                    el.value=u;el2.value=p;
                                                    el.setAttribute('autocomplete','username');
                                                    el2.setAttribute('autocomplete','current-password');
                                                    el.dispatchEvent(new Event('input',{bubbles:true}));
                                                    el2.dispatchEvent(new Event('input',{bubbles:true}));
                                                    return true;
                                                }
                                                return false;
                                            }
                                            if(!fill()){var ob=new MutationObserver(function(){if(fill())ob.disconnect()});ob.observe(document.body,{childList:true,subtree:true})}
                                        })();
                                    """.trimIndent(), null)
                                } else if (url?.contains("login") == true) {
                                    view?.evaluateJavascript("""
                                        (function(){
                                            function ac(){
                                                var el=document.querySelector('input.ivu-input:not([type="password"])');
                                                var el2=document.querySelector('input.ivu-input[type="password"]');
                                                if(el)el.setAttribute('autocomplete','username');
                                                if(el2)el2.setAttribute('autocomplete','current-password');
                                            }
                                            ac();var ob=new MutationObserver(function(){ac()});
                                            ob.observe(document.body,{childList:true,subtree:true});
                                        })();
                                    """.trimIndent(), null)
                                }

                                if (tokenManager.youkaUsername == null) {
                                    view?.evaluateJavascript("""
                                        (function(){if(window.__yci)return;window.__yci=true;
                                        var _o=XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open=function(m,u){this._u=u;return _o.apply(this,arguments);};
                                        var _s=XMLHttpRequest.prototype.send;
                                        XMLHttpRequest.prototype.send=function(b){
                                            if(b&&typeof b=='string'&&this._u&&this._u.indexOf('auth/login')>=0){
                                                try{var d=JSON.parse(b);if(d.account&&d.password)YoukaBridge.onCredentials(JSON.stringify({user:d.account,pass:d.password}));}catch(e){}
                                            }
                                            return _s.apply(this,arguments);
                                        };
                                        var _f=window.fetch;
                                        if(_f)window.fetch=function(u,i){
                                            var url=typeof u=='string'?u:(u.url||'');
                                            if(i&&i.body&&typeof i.body=='string'&&url.indexOf('auth/login')>=0){
                                                try{var d=JSON.parse(i.body);if(d.account&&d.password)YoukaBridge.onCredentials(JSON.stringify({user:d.account,pass:d.password}));}catch(e){}
                                            }
                                            return _f.apply(this,arguments);
                                        };})();
                                    """.trimIndent(), null)
                                }

                                pollHandler.removeCallbacks(tokenPoll)
                                tokenPoll.run()

                                view?.evaluateJavascript("""
                                    (function(){
                                        if(window.__ykLM)return;
                                        var hadToken=false;
                                        window.__ykLM=setInterval(function(){
                                            var tk=localStorage.getItem('admin_token');
                                            if(tk){hadToken=true;return;}
                                            if(hadToken){YoukaBridge.onLogoutDetected();clearInterval(window.__ykLM);}
                                        },3000);
                                    })();
                                """.trimIndent(), null)
                            }
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val reqUrl = request?.url?.toString()
                                if (reqUrl != null && reqUrl.contains("spa/auth/login") && tokenManager.youkaUsername == null) {
                                    pollHandler.post {
                                        view?.evaluateJavascript("""
                                            (function(){
                                                var el=document.querySelector('input[placeholder*="用户名"]')||document.querySelector('input[type="text"]');
                                                var el2=document.querySelector('input[placeholder*="密码"]')||document.querySelector('input[type="password"]');
                                                if(el&&el2)return JSON.stringify({user:el.value,pass:el2.value});
                                                return '{}';
                                            })()
                                        """.trimIndent()) { json ->
                                            try {
                                                val obj = JSONObject(json?.trim('"') ?: "{}")
                                                val user = obj.optString("user", "")
                                                val pass = obj.optString("pass", "")
                                                if (user.isNotEmpty()) {
                                                    tokenManager.youkaUsername = user
                                                    tokenManager.youkaPassword = pass
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                request?.requestHeaders?.forEach { (key, value) ->
                                    if (key.equals("Authorization", ignoreCase = true) && value.startsWith("Bearer ") && !tokenFound) {
                                        val token = value.removePrefix("Bearer ").trim()
                                        if (token.isNotEmpty()) {
                                            tokenFound = true
                                            sessionManager.onYoukaToken(token)
                                        }
                                    }
                                    if (key.equals("Cookie", ignoreCase = true) && !tokenFound) {
                                        Regex("admin_token=([^;]+)").find(value)?.groupValues?.get(1)?.let { token ->
                                            if (token.isNotEmpty()) {
                                                tokenFound = true
                                                sessionManager.onYoukaToken(token)
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
