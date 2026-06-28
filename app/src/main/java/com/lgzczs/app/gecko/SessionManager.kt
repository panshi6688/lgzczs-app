package com.lgzczs.app.gecko

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

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
                override fun onLocationChange(session: GeckoSession, url: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
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

                override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                    onLog("HTTP_ERROR", uri ?: "", "Error ${error.code}: ${error.message}")
                    return null
                }
            }

            contentDelegate = object : ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {}
                override fun onCrash(session: GeckoSession) {
                    onLog("ERROR", platform, "Session crashed, reopening")
                    session.open(runtime)
                }

                override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
                }

                override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                    onLog("NETWORK_REQ", response.uri ?: "", "External response")
                }

                override fun onFirstComposite(session: GeckoSession) {}

                override fun onFocusRequest(session: GeckoSession) {}
            }

            permissionDelegate = object : PermissionDelegate {
                override fun onContentPermissionRequest(session: GeckoSession, perm: PermissionDelegate.ContentPermission): GeckoResult<Int>? {
                    return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
            }

            progressDelegate = object : ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) {}
                override fun onPageStart(session: GeckoSession, url: String) {
                    onStatusChange(platform, true)
                    onLog("NAVIGATE", url, "Page started loading")
                }
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    onStatusChange(platform, false)
                    if (!success) {
                        onLog("ERROR", platform, "Page load failed")
                        return
                    }
                    onPageLoaded(session)
                }
            }

            promptDelegate = object : PromptDelegate {
                override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
                    onLog("JS_LOG", platform, "Alert: ${prompt.message}")
                    return GeckoResult.fromValue(prompt.dismiss())
                }

                override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
                    return GeckoResult.fromValue(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }

                override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
                    return GeckoResult.fromValue(prompt.confirm(prompt.defaultValue))
                }
            }

            consoleCallback = object : GeckoSession.ConsoleCallback {
                override fun onMessage(message: GeckoSession.ConsoleCallback.Message) {
                    val msg = message.message()
                    val src = "${message.sourceId()}:${message.lineNumber()}"
                    when (message.level()) {
                        GeckoSession.ConsoleCallback.Level.ERROR -> onLog("JS_ERROR", src, msg)
                        GeckoSession.ConsoleCallback.Level.WARN -> onLog("JS_WARN", src, msg)
                        else -> onLog("JS_LOG", src, msg)
                    }
                }
            }

            open(runtime)
        }
    }

    init {
        youkaSession = createSession(
            platform = "youka",
            onPageLoaded = { session ->
                session.evaluateJavascript("document.cookie") { cookieStr ->
                    if (cookieStr != null && cookieStr != "null" && cookieStr.isNotEmpty()) {
                        val raw = cookieStr.trim('"')
                        val token = raw.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("admin_token=") }
                            ?.removePrefix("admin_token=")
                        if (token != null && token.isNotEmpty()) {
                            onYoukaToken(token)
                            onLog("JS_LOG", "youka", "Token extracted: ${token.take(8)}...")
                        }
                    }
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
