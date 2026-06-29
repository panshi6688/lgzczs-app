package com.lgzczs.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.util.PermissionHelper
import com.lgzczs.app.util.TokenManager

@Composable
fun DataPage(
    tokenManager: TokenManager,
    huiStatus: PlatformStatus,
    youkaStatus: PlatformStatus,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current

    var notificationGranted by remember { mutableStateOf(PermissionHelper.isNotificationEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context)) }
    var alertDialogEnabled by remember { mutableStateOf(tokenManager.alertDialogEnabled) }
    var notificationEnabled by remember { mutableStateOf(tokenManager.notificationEnabled) }
    var floatWindowEnabled by remember { mutableStateOf(tokenManager.floatWindowEnabled) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showUsageGuide by remember { mutableStateOf(false) }

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "流光之城",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 使用说明：同时监控两个平台的新订单并发出通知",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { showUsageGuide = true },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text("查看", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("平台状态")
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onRefresh) {
                        Text("🔄 刷新", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PlatformStatusRow("汇权益", huiStatus)
                PlatformStatusRow("优卡云", youkaStatus)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("权限")
                Spacer(modifier = Modifier.height(8.dp))
                PermissionStatusRow(
                    label = "系统通知",
                    granted = notificationGranted,
                    onSettingsClick = { PermissionHelper.openNotificationSettings(context) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                PermissionStatusRow(
                    label = "悬浮窗",
                    granted = overlayGranted,
                    onSettingsClick = { showOverlayGuide = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "作者QQ：248617489",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showOverlayGuide) {
        AlertDialog(
            onDismissRequest = { showOverlayGuide = false },
            title = { Text("悬浮窗权限") },
            text = { Text("在跳转的页面中找到【1流光之城】并点进去允许显示在其它应用上方后返回即可") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayGuide = false
                    PermissionHelper.openOverlaySettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayGuide = false }) { Text("取消") }
            }
        )
    }

    if (showUsageGuide) {
        AlertDialog(
            onDismissRequest = { showUsageGuide = false },
            title = { Text("使用说明") },
            text = {
                Text("① 依次登录汇权益和优卡云后台\n② 打开弹窗/通知/悬浮窗功能开关\n③ 保持 App 后台运行即可自动监控")
            },
            confirmButton = {
                TextButton(onClick = { showUsageGuide = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
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
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = statusText,
            fontSize = 15.sp,
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
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (granted) {
            Text(
                text = "✅ 已授权",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "❌ 未授权",
                    fontSize = 15.sp,
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
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
