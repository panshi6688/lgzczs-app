package com.lgzczs.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.util.TokenManager
import org.mozilla.geckoview.GeckoView

@Composable
fun HuiPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    huiToken: String?
) {
    var pageLoaded by rememberSaveable { mutableStateOf(false) }
    if (!pageLoaded) {
        LaunchedEffect(Unit) {
            sessionManager.loadHuiPage()
            pageLoaded = true
        }
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
    }
}
