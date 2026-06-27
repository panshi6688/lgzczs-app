package com.lgzczs.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.PermissionHelper
import com.lgzczs.app.util.TokenManager

@Composable
fun DataPage(
    tokenManager: TokenManager,
    huiStatus: PlatformStatus,
    youkaStatus: PlatformStatus
) {
    val context = LocalContext.current

    var notificationGranted by remember { mutableStateOf(PermissionHelper.isNotificationEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context)) }
    var alertDialogEnabled by remember { mutableStateOf(tokenManager.alertDialogEnabled) }
    var notificationEnabled by remember { mutableStateOf(tokenManager.notificationEnabled) }
    var floatWindowEnabled by remember { mutableStateOf(tokenManager.floatWindowEnabled) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = PermissionHelper.isNotificationEnabled(context)
                overlayGranted = PermissionHelper.isOverlayPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "流光之城出卡助手",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("平台状态")

        Spacer(modifier = Modifier.height(8.dp))

        PlatformStatusRow("汇权益", huiStatus)
        PlatformStatusRow("优卡云", youkaStatus)

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("通知权限")

        Spacer(modifier = Modifier.height(8.dp))

        PermissionStatusRow(
            label = "系统通知",
            granted = notificationGranted,
            onSettingsClick = { PermissionHelper.openNotificationSettings(context) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("悬浮窗权限")

        Spacer(modifier = Modifier.height(8.dp))

        PermissionStatusRow(
            label = "悬浮窗",
            granted = overlayGranted,
            onSettingsClick = { PermissionHelper.openOverlaySettings(context) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("功能开关")

        Spacer(modifier = Modifier.height(8.dp))

        ToggleRow(
            label = "弹窗通知",
            description = "有订单时在所有 App 上方弹窗提示",
            checked = alertDialogEnabled,
            onCheckedChange = {
                alertDialogEnabled = it
                tokenManager.alertDialogEnabled = it
            }
        )

        ToggleRow(
            label = "状态栏通知",
            description = "有订单时发送系统通知栏通知",
            checked = notificationEnabled,
            onCheckedChange = {
                notificationEnabled = it
                tokenManager.notificationEnabled = it
            }
        )

        ToggleRow(
            label = "悬浮窗",
            description = "后台运行时显示可拖动的悬浮图标",
            checked = floatWindowEnabled,
            onCheckedChange = {
                floatWindowEnabled = it
                tokenManager.floatWindowEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("调试选项")

        Spacer(modifier = Modifier.height(8.dp))

        var debugMode by remember { mutableStateOf(tokenManager.debugModeEnabled) }
        ToggleRow(
            label = "调试模式",
            description = "在 WebView 页面显示调试按钮，捕获 JS 错误和网络请求日志",
            checked = debugMode,
            onCheckedChange = {
                debugMode = it
                tokenManager.debugModeEnabled = it
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun PlatformStatusRow(name: String, status: PlatformStatus) {
    val statusText = when (status) {
        PlatformStatus.NOT_LOGGED_IN -> "❌ 未登录"
        PlatformStatus.LOGGING_IN -> "⏳ 登录中"
        PlatformStatus.LOGGED_IN -> "✅ 已登录"
        PlatformStatus.TOKEN_EXPIRED -> "⚠️ 登录已过期"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = statusText,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (granted) {
            Text(
                text = "✅ 已授权",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "❌ 未授权",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSettingsClick) {
                    Text("去设置")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
