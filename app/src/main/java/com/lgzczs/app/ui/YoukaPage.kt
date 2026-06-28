package com.lgzczs.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.TokenManager
import org.mozilla.geckoview.GeckoView

@Composable
fun YoukaPage(
    tokenManager: TokenManager,
    sessionManager: SessionManager,
    youkaToken: String?,
    onStatusChange: (PlatformStatus) -> Unit
) {
    val hasToken = youkaToken != null

    LaunchedEffect(hasToken) {
        onStatusChange(if (hasToken) PlatformStatus.LOGGED_IN else PlatformStatus.NOT_LOGGED_IN)
    }

    Box(modifier = Modifier.fillMaxSize().graphicsLayer(clip = true)) {
        AndroidView(
            factory = { context ->
                GeckoView(context).apply {
                    setSession(sessionManager.youkaSession)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
