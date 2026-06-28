# GeckoView Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate both platform pages from Android system WebView to Mozilla GeckoView, resolving rendering and popup issues.

**Architecture:** Replace single-object WebView with GeckoView (display) + GeckoSession (state) + GeckoRuntime (engine singleton). Each platform maintains its own persistent GeckoSession. Remove all WebView-specific CSS hacks and compatibility workarounds.

**Tech Stack:** GeckoView 130.0, Kotlin, Jetpack Compose

---

### Task 1: Add GeckoView Dependency

**Files:**
- Modify: `settings.gradle.kts:9-14`
- Modify: `app/build.gradle.kts:49-81`

- [ ] **Step 1: Add Mozilla Maven repository**

Edit `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}
```

- [ ] **Step 2: Add GeckoView dependency**

Edit `app/build.gradle.kts`, add before the last `}` of `dependencies` block:

```kotlin
    // GeckoView
    implementation("org.mozilla.geckoview:geckoview:130.0")
```

- [ ] **Step 3: Sync project**

Run: `./gradlew :app:clean` (or sync in IDE)

---

### Task 2: Create GeckoRuntimeManager

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/gecko/GeckoRuntimeManager.kt`

- [ ] **Step 1: Write GeckoRuntimeManager**

```kotlin
package com.lgzczs.app.gecko

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null

    fun get(application: Application): GeckoRuntime {
        if (runtime == null) {
            runtime = GeckoRuntime.create(application, GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(true)
                .build())
        }
        return runtime!!
    }
}
```

---

### Task 3: Create SessionManager

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/gecko/SessionManager.kt`

- [ ] **Step 1: Write SessionManager with delegates**

```kotlin
package com.lgzczs.app.gecko

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult

class SessionManager(
    private val runtime: GeckoRuntime,
    private val onYoukaToken: (String) -> Unit,
    private val onHuiToken: (String) -> Unit,
    private val onLog: (type: String, source: String, message: String) -> Unit,
    private val onStatusChange: (platform: String, isLoading: Boolean) -> Unit
) {
    val youkaSession: GeckoSession
    val huiSession: GeckoSession

    private fun createSession(
        platform: String,
        onPageLoaded: (GeckoSession) -> Unit
    ): GeckoSession {
        return GeckoSession().apply {
            navigationDelegate = object : NavigationDelegate {
                override fun onLocationChange(session: GeckoSession, url: String?) {
                    onLog("NAVIGATE", url ?: "", "Location changed")
                }

                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {}
                override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {}

                override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                    val newSession = GeckoSession().also { it.open(runtime) }
                    return GeckoResult.fromValue(newSession)
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    onStatusChange(platform, false)
                    if (!success) {
                        onLog("ERROR", platform, "Page load failed")
                        return
                    }
                    onPageLoaded(session)
                }

                override fun onPageStart(session: GeckoSession, url: String?) {
                    onStatusChange(platform, true)
                    onLog("NAVIGATE", url ?: "", "Page started loading")
                }

                override fun onLoadError(session: GeckoSession, uri: String?, error: Int, errorMsg: String?): GeckoResult<String>? {
                    onLog("HTTP_ERROR", uri ?: "", "Error $error: $errorMsg")
                    return null
                }
            }

            contentDelegate = object : ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {}
                override fun onCrash(session: GeckoSession) {
                    onLog("ERROR", platform, "Session crashed, reopening")
                    session.open(runtime)
                }

                override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, uri: String?, elementUri: String?, elementType: Int): GeckoResult<AllowOrDeny>? {
                    return null
                }

                override fun onExternalResponse(session: GeckoSession, response: GeckoSession.WebResponseInfo) {
                    onLog("NETWORK_REQ", response.uri ?: "", "${response.contentType} (${response.contentLength})")
                }

                override fun onPermissionRequest(session: GeckoSession, request: ContentDelegate.PermissionRequest) {
                    request.grant()
                }

                override fun onFirstComposite(session: GeckoSession) {}

                override fun onFocusRequest(session: GeckoSession) {}
            }

            progressDelegate = object : ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) {}
            }

            promptDelegate = object : PromptDelegate {
                override fun onAlert(session: GeckoSession, request: PromptDelegate.AlertRequest) {
                    onLog("JS_LOG", platform, "Alert: ${request.message}")
                    request.confirm()
                }

                override fun onButtonPrompt(session: GeckoSession, request: PromptDelegate.ButtonPromptRequest) {
                    request.confirm(PromptDelegate.ButtonPromptRequest.Type.POSITIVE)
                }

                override fun onTextPrompt(session: GeckoSession, request: PromptDelegate.TextPromptRequest) {
                    request.confirm(request.defaultValue)
                }
            }

            consoleCallback = { message ->
                val msg = message?.message() ?: ""
                val src = "${message?.sourceId()}:${message?.lineNumber()}"
                when (message?.level()) {
                    GeckoSession.ConsoleCallback.Level.ERROR -> onLog("JS_ERROR", src, msg)
                    GeckoSession.ConsoleCallback.Level.WARN -> onLog("JS_WARN", src, msg)
                    else -> onLog("JS_LOG", src, msg)
                }
            }

            open(runtime)
        }
    }

    init {
        youkaSession = createSession(
            platform = "youka",
            onPageLoaded = { session ->
                android.webkit.CookieManager.getInstance()
                    .getCookie("http://supplier.ukayun.cn")
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("admin_token=") }
                    ?.removePrefix("admin_token=")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { token ->
                        onYoukaToken(token)
                        onLog("JS_LOG", "youka", "Token extracted: ${token.take(8)}...")
                    }
            }
        )

        huiSession = createSession(
            platform = "hui",
            onPageLoaded = { session ->
                session.evaluateJavascript(
                    "(function(){ return localStorage.getItem('access_token') })()"
                ) { value ->
                    if (value != null && value != "null" && value.isNotEmpty()) {
                        val token = value.trim('"')
                        if (token.isNotEmpty()) {
                            onHuiToken(token)
                            onLog("JS_LOG", "hui", "Token extracted: ${token.take(8)}...")
                        }
                    }
                }
            }
        )
    }

    fun loadHuiPage() {
        huiSession.loadUri("https://sup.78k.cn/")
    }

    fun loadYoukaPage() {
        youkaSession.loadUri("http://supplier.ukayun.cn/")
    }
}
```

---

### Task 4: Rewrite YoukaPage with GeckoView

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/YoukaPage.kt`

- [ ] **Step 1: Replace entire YoukaPage.kt**

```kotlin
package com.lgzczs.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import org.mozilla.geckoview.GeckoView

@Composable
fun YoukaPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    debugMode: Boolean = false,
    onStatusChange: (PlatformStatus) -> Unit
) {
    var hasToken by remember { mutableStateOf(tokenManager.youkaToken != null) }
    var showDebugPanel by remember { mutableStateOf(false) }

    LaunchedEffect(hasToken) {
        onStatusChange(if (hasToken) PlatformStatus.LOGGED_IN else PlatformStatus.NOT_LOGGED_IN)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                GeckoView(context).apply {
                    setSession(sessionManager.youkaSession)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (debugMode) {
            FloatingActionButton(
                onClick = { showDebugPanel = !showDebugPanel },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp),
                containerColor = Color(0x99000000),
                contentColor = Color.White
            ) {
                Text("🐛", fontSize = 18.sp)
            }
        }

        if (showDebugPanel) {
            DebugPanel(session = sessionManager.youkaSession, onClose = { showDebugPanel = false })
        }
    }
}
```

---

### Task 5: Rewrite HuiPage with GeckoView

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/HuiPage.kt`

- [ ] **Step 1: Replace entire HuiPage.kt**

```kotlin
package com.lgzczs.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import org.mozilla.geckoview.GeckoView

@Composable
fun HuiPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    debugMode: Boolean = false,
    onStatusChange: (PlatformStatus) -> Unit
) {
    var hasToken by remember { mutableStateOf(tokenManager.huiToken != null) }
    var showDebugPanel by remember { mutableStateOf(false) }

    LaunchedEffect(hasToken) {
        onStatusChange(if (hasToken) PlatformStatus.LOGGED_IN else PlatformStatus.NOT_LOGGED_IN)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                GeckoView(context).apply {
                    setSession(sessionManager.huiSession)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (debugMode) {
            FloatingActionButton(
                onClick = { showDebugPanel = !showDebugPanel },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp),
                containerColor = Color(0x99000000),
                contentColor = Color.White
            ) {
                Text("🐛", fontSize = 18.sp)
            }
        }

        if (showDebugPanel) {
            DebugPanel(session = sessionManager.huiSession, onClose = { showDebugPanel = false })
        }
    }
}
```

---

### Task 6: Update MainActivity to wire up SessionManager

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Update imports in MainActivity.kt**

Remove: `import com.lgzczs.app.ui.HuiPage`
Remove: `import com.lgzczs.app.ui.YoukaPage`

Add:
```kotlin
import com.lgzczs.app.gecko.GeckoRuntimeManager
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.util.WebViewDiagnostics
import com.lgzczs.app.util.LogType
```

- [ ] **Step 2: Add sessionManager to MainScreen composable**

In `MainScreen()`, after `val tokenManager = remember { TokenManager(context.applicationContext) }`, add:

```kotlin
val application = context.applicationContext as android.app.Application
val runtime = GeckoRuntimeManager.get(application)
val sessionManager = remember {
    SessionManager(
        runtime = runtime,
        onYoukaToken = { token ->
            tokenManager.youkaToken = token
        },
        onHuiToken = { token ->
            tokenManager.huiToken = token
        },
        onLog = { type, source, message ->
            WebViewDiagnostics.add(
                when (type) {
                    "JS_ERROR" -> LogType.JS_ERROR
                    "JS_WARN" -> LogType.JS_WARN
                    "JS_LOG" -> LogType.JS_LOG
                    "ERROR" -> LogType.ERROR
                    "HTTP_ERROR" -> LogType.HTTP_ERROR
                    "NETWORK_REQ" -> LogType.NETWORK_REQ
                    else -> LogType.JS_DEBUG
                }, source, message
            )
        },
        onStatusChange = { platform, isLoading ->
            // optional: update loading indicator per platform
        }
    )
}
```

- [ ] **Step 3: Add LaunchedEffect to trigger first page loads**

After the existing `LaunchedEffect` blocks, add:

```kotlin
LaunchedEffect(Unit) {
    sessionManager.loadYoukaPage()
    sessionManager.loadHuiPage()
}
```

- [ ] **Step 4: Update composable calls to pass sessionManager**

Change:
```kotlin
HuiPage(
    tokenManager = tokenManager,
    debugMode = tokenManager.debugModeEnabled,
    onStatusChange = { status -> huiStatus = status }
)
```
To:
```kotlin
HuiPage(
    tokenManager = tokenManager,
    sessionManager = sessionManager,
    debugMode = tokenManager.debugModeEnabled,
    onStatusChange = { status -> huiStatus = status }
)
```

Change:
```kotlin
YoukaPage(
    tokenManager = tokenManager,
    debugMode = tokenManager.debugModeEnabled,
    onStatusChange = { status -> youkaStatus = status }
)
```
To:
```kotlin
YoukaPage(
    tokenManager = tokenManager,
    sessionManager = sessionManager,
    debugMode = tokenManager.debugModeEnabled,
    onStatusChange = { status -> youkaStatus = status }
)
```

---

### Task 7: Update DebugPanel to work with GeckoSession

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/DebugPanel.kt`

- [ ] **Step 1: Change parameter from WebView to GeckoSession**

In `DebugPanel` composable, change:
```kotlin
fun DebugPanel(
    webView: WebView? = null,
    onClose: () -> Unit
)
```
To:
```kotlin
fun DebugPanel(
    session: org.mozilla.geckoview.GeckoSession? = null,
    onClose: () -> Unit
)
```

- [ ] **Step 2: Remove WebView import, add GeckoSession import**

Remove: `import android.webkit.WebView`
Keep all other imports.

- [ ] **Step 3: Update DOM diagnostics function to use GeckoSession**

Change `runDomDiagnostics` parameter from `WebView` to `org.mozilla.geckoview.GeckoSession`, and change:
```kotlin
webView.evaluateJavascript(script) { result ->
```
To:
```kotlin
session.evaluateJavascript(script) { result ->
```

- [ ] **Step 4: Update callers in the file**

In `DebugPanel` composable, change:
```kotlin
TextButtonSmall(if (running) "..." else "DOM") {
    if (!running && webView != null) {
        running = true
        runDomDiagnostics(webView) { running = false; refresh() }
    }
}
```
To:
```kotlin
TextButtonSmall(if (running) "..." else "DOM") {
    if (!running && session != null) {
        running = true
        runDomDiagnostics(session) { running = false; refresh() }
    }
}
```

- [ ] **Step 5: Update the hint text at bottom**

Change:
```kotlin
Text(
    "连接电脑 → Chrome → chrome://inspect 可远程调试",
    ...
)
```
To:
```kotlin
Text(
    "连接电脑 → Firefox → about:debugging 可远程调试",
    ...
)
```

---

### Task 8: Build and verify

- [ ] **Step 1: Build the project**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify APK size change**

Check APK size in `app/build/outputs/apk/debug/`. GeckoView adds ~50-80MB.

---

### Task 9: Clean up removed WebView files

**Files:**
- Delete (if empty after migration): The `WebViewDiagnostics.kt` file is still used by the new logging path, so keep it.
- No other files need deletion.

- [ ] **Step 1: Remove unused Network Security Config (optional)**

If no other HTTP-only URLs remain, the `network_security_config.xml` and `android:usesCleartextTraffic` can be left as-is since Youka still uses HTTP.
