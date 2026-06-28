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
