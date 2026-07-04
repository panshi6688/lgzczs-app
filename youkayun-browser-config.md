# 优卡云页面浏览器配置参考

> 本文档从现有项目 `lgzczs-app`（流光之城）中提取，用于在新建 APK 项目时保持优卡云页面渲染显示一致。
>
> 原始项目：`com.lgzczs.app` | Kotlin + Jetpack Compose + Material 3
> 优卡云 URL：`http://supplier.ukayun.cn/`

---

## 1. WebView 核心设置

**位置：** `YoukaPage.kt:76-92`

```kotlin
settings.javaScriptEnabled = true                          // 启用 JavaScript
settings.javaScriptCanOpenWindowsAutomatically = true      // 允许 JS 自动打开弹窗
settings.domStorageEnabled = true                          // 启用 DOM localStorage/sessionStorage
settings.databaseEnabled = true                            // 启用 Web SQL 数据库
settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // 允许 HTTPS 页面加载 HTTP 资源
settings.loadWithOverviewMode = true                       // 加载时缩放至全览模式
settings.useWideViewPort = true                            // 使用宽视口
settings.builtInZoomControls = true                        // 内置缩放控制
settings.displayZoomControls = false                       // 不显示缩放控件 UI
settings.setSupportZoom(true)                              // 支持手势缩放
settings.allowFileAccess = false                           // 禁止 file:// 协议访问
settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING  // 文本自动调整布局
settings.textZoom = 100                                    // 文本缩放 100%
settings.defaultFontSize = 16                              // 默认字体大小 16sp
```

**第三方 Cookie：**
```kotlin
CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
```

**网络状态：**
```kotlin
setNetworkAvailable(true)
```

---

## 2. WebViewClient

**位置：** `YoukaPage.kt:118-305`

### onPageStarted
- 重置 `tokenFound` 标记
- 记录当前 URL
- 触发加载状态回调
- 若 URL 包含 "login" 且已有 Token，触发登出回调

```kotlin
override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    tokenFound = false
    currentView = view
    sessionManager.updateYoukaUrl(url)
    sessionManager.onStatusChange("youka", true)
    if (url?.contains("login") == true && tokenManager.youkaToken != null) {
        onLogout()
    }
}
```

### onPageFinished
1. **自动填充凭据** — 如有保存的用户名密码，自动填入登录表单并触发 input 事件；否则设置 autocomplete 属性
2. **注入 XHR/Fetch 拦截器** — 拦截 `auth/login` 请求的 POST body，通过 `YoukaBridge.onCredentials()` 回传凭据
3. **轮询 Token** — 通过 JS 读取 `localStorage.admin_token` 或 Cookie 中的 `admin_token`
4. **localStorage 变化监控** — 每 3 秒检查 `admin_token`，从有到无时触发登出回调

**Token 轮询 JS：**
```javascript
(function(){
    var tk = localStorage.getItem('admin_token');
    if (tk) return tk;
    var m = document.cookie.match(/admin_token=([^;]+)/);
    if (m) return m[1];
    return '';
})()
```

**登出检测 JS：**
```javascript
(function(){
    if (window.__ykLM) return;
    var hadToken = false;
    window.__ykLM = setInterval(function(){
        var tk = localStorage.getItem('admin_token');
        if (tk) { hadToken = true; return; }
        if (hadToken) { YoukaBridge.onLogoutDetected(); clearInterval(window.__ykLM); }
    }, 3000);
})();
```

### shouldInterceptRequest
- 捕获请求头中的 `Authorization: Bearer <token>` 和 `Cookie: admin_token=...` 提取 Token
- 在 `auth/login` 请求时获取当前表单输入的用户名密码

```kotlin
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    // 从 Authorization header 提取 token
    if (key.equals("Authorization", ignoreCase = true) && value.startsWith("Bearer ")) { ... }
    // 从 Cookie header 提取 admin_token
    if (key.equals("Cookie", ignoreCase = true)) {
        Regex("admin_token=([^;]+)").find(value)?.groupValues?.get(1)?.let { ... }
    }
}
```

### shouldOverrideUrlLoading
- 返回 `false`，全部由 WebView 自身处理

### onReceivedSslError
- **无条件接受所有 SSL 证书错误**

```kotlin
override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
    handler?.proceed()
}
```

---

## 3. WebChromeClient

**位置：** `YoukaPage.kt:314-368`

| 回调 | 行为 |
|---|---|
| `onJsAlert` | 自动确认（静默关闭） |
| `onJsConfirm` | 自动确认 |
| `onPermissionRequest` | 自动授权所有权限 |
| `onGeolocationPermissionsShowPrompt` | 自动授权地理位置，不记住 |
| `onCreateWindow` | 创建新 WebView 处理弹窗页面，继承相同的 JS/DOM/MixedContent 设置 |
| `onShowFileChooser` | 文件上传支持（相机 + 文件选择器 + Gallery） |

**弹窗 WebView 的 settings：**
```kotlin
settings.javaScriptEnabled = true
settings.javaScriptCanOpenWindowsAutomatically = true
settings.domStorageEnabled = true
settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
```

**文件上传配置（FileProvider）：**
```kotlin
val photoFile = File(ctx.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", photoFile)
```

---

## 4. JavaScript Bridge

**位置：** `YoukaPage.kt:94-116`

```kotlin
addJavascriptInterface(object {
    @android.webkit.JavascriptInterface
    fun onToken(token: String)       // 接收 Token
    fun onCredentials(json: String)  // 接收凭据 JSON：{"user":"...","pass":"..."}
    fun onLogoutDetected()           // 登出通知
}, "YoukaBridge")
```

前端通过 `YoukaBridge.onToken(...)`、`YoukaBridge.onCredentials(...)`、`YoukaBridge.onLogoutDetected()` 与原生通信。

---

## 5. 会话管理

**位置：** `SessionManager.kt`

### 默认 URL

| 字段 | 值 |
|---|---|
| `youkaDefaultUrl` | `http://supplier.ukayun.cn/` |
| `youkaCurrentUrl` | 当前页面 URL（不包含 `javascript:` 协议） |

### Token 提取 JS

```javascript
(() => {
    try {
        var t = localStorage.getItem('admin_token');
        if (t) return t;
        var m = document.cookie.match(/admin_token=([^;]+)/);
        if (m) return m[1];
    } catch(e) {}
    return '';
})()
```

### 加载页面（含恢复 Token）

```kotlin
fun loadYoukaPage(storedToken: String? = null) {
    if (storedToken != null) {
        CookieManager.getInstance().setCookie(
            "http://supplier.ukayun.cn/",
            "admin_token=$storedToken; path=/"
        )
        CookieManager.getInstance().flush()
    }
    youkaWebView?.loadUrl("http://supplier.ukayun.cn/")
}
```

### 重新加载

```kotlin
fun reloadYoukaPage() {
    youkaWebView?.loadUrl(youkaCurrentUrl ?: youkaDefaultUrl)
}
```

---

## 6. AndroidManifest 配置

**位置：** `AndroidManifest.xml`

### 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- Android 13+ -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />  <!-- Android 14+ -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Application 标签

```xml
<application
    android:usesCleartextTraffic="true"                     <!-- 允许 HTTP（优卡云必须） -->
    android:networkSecurityConfig="@xml/network_security_config">
```

### FileProvider（用于文件上传）

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

---

## 7. 网络安全配置

**位置：** `res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- **cleartextTrafficPermitted="true"** — 允许明文 HTTP 流量（优卡云使用 HTTP）
- **仅信任系统证书**

### file_paths.xml（FileProvider 路径）

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```

---

## 8. 构建配置

**位置：** `build.gradle.kts`

### SDK 版本

```kotlin
compileSdk = 34
minSdk = 26    // Android 8.0
targetSdk = 34
```

### 关键依赖

```kotlin
// Compose BOM 2024.01.00
// Material 3
// Navigation Compose 2.7.7
// Lifecycle Runtime/ViewModel Compose 2.7.0
// OkHttp 4.12.0
// Gson 2.10.1
// Core KTX 1.12.0
// Activity Compose 1.8.2
```

### NDK ABI 过滤（仅 arm64-v8a）

```kotlin
ndk {
    abiFilters += "arm64-v8a"
}
```

### 编译选项

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
```

### Kotlin Compiler Extension

```kotlin
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}
```

---

## 9. 优卡云 API 特性（补充）

**位置：** `ukayun_api_analysis.md`、`YoukaApiClient.kt`

| 属性 | 值 |
|---|---|
| API 基础 URL | `http://supplier.ukayun.cn/` |
| Token 字段名 | `admin_token` |
| 存储位置 | `localStorage.admin_token`、Cookie `admin_token` |
| 时间戳 API | `GET /spa/auth/timestamp` |
| 订单查询 API | `POST /spa/order` |
| 加密方式 | AES-256-CBC（Key: `7aca3c37e3745f8768b0e559797d521f`, IV = MD5(key)[:16]） |
| 请求签名 | SHA256(排序参数 + nonce + salt + timestamp)，version=2 |
| Salt | MD5(timestamp[-5:] + nonce) |
| Nonce | 5 位随机字符（62 字母表） |
| 新订单检测 | 每隔 30 秒轮询一次 |

---

## 快速模板（直接复制粘贴）

### WebView 创建代码块

```kotlin
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
}
```

### SSL 错误处理（接受所有证书）

```kotlin
override fun onReceivedSslError(
    view: WebView?,
    handler: android.webkit.SslErrorHandler?,
    error: android.net.http.SslError?
) {
    handler?.proceed()
}
```

### shouldOverrideUrlLoading（不拦截）

```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    return false
}
```
