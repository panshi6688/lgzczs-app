package com.lgzczs.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lgzczs.app.gecko.GeckoRuntimeManager
import com.lgzczs.app.gecko.SessionManager
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.service.PollingService
import com.lgzczs.app.ui.HuiPage
import com.lgzczs.app.ui.DataPage
import com.lgzczs.app.ui.YoukaPage
import com.lgzczs.app.ui.theme.LgzczsTheme
import com.lgzczs.app.util.PermissionHelper
import com.lgzczs.app.service.FloatWindowService
import com.lgzczs.app.service.KeepAliveService
import com.lgzczs.app.util.TokenManager

class MainActivity : ComponentActivity() {

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PollingService.ACTION_SHOW_ALERT) {
                val platform = intent.getStringExtra("platform") ?: return
                Intent(context, com.lgzczs.app.ui.GlobalAlertActivity::class.java).apply {
                    putExtra("platform", platform)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(this)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        startForegroundService(Intent(this, KeepAliveService::class.java))

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(alertReceiver, IntentFilter(PollingService.ACTION_SHOW_ALERT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alertReceiver, IntentFilter(PollingService.ACTION_SHOW_ALERT))
        }
        setContent {
            LgzczsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(alertReceiver)
    }
}

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
) {
    data object HuiQuanYi : BottomNavItem("hui", "汇权益", { Icon(painterResource(R.drawable.ic_hui), "汇权益", modifier = Modifier.size(20.dp)) })
    data object YouKaYun : BottomNavItem("youka", "优卡云", { Icon(painterResource(R.drawable.ic_youka), "优卡云", modifier = Modifier.size(20.dp)) })
    data object Data : BottomNavItem("data", "数据", { Icon(Icons.Default.Dashboard, "数据", modifier = Modifier.size(20.dp)) })
}

private val bottomNavItems = listOf(
    BottomNavItem.HuiQuanYi,
    BottomNavItem.YouKaYun,
    BottomNavItem.Data
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val application = context.applicationContext as android.app.Application
    val runtime = GeckoRuntimeManager.get(application)
    var huiTokenValue by remember { mutableStateOf(tokenManager.huiToken) }
    var youkaTokenValue by remember { mutableStateOf(tokenManager.youkaToken) }

    val sessionManager = remember {
        SessionManager(
            runtime = runtime,
            onYoukaToken = { token ->
                tokenManager.youkaToken = token
                youkaTokenValue = token
            },
            onHuiToken = { token ->
                tokenManager.huiToken = token
                huiTokenValue = token
            },
            onLog = { type, source, message ->
                // logging is disabled (DebugPanel removed)
            },
            onStatusChange = { platform, isLoading ->
                // optional: could update loading indicator
            }
        )
    }
    var huiStatus by remember { mutableStateOf(PlatformStatus.NOT_LOGGED_IN) }
    var youkaStatus by remember { mutableStateOf(PlatformStatus.NOT_LOGGED_IN) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(huiStatus) {
        when (huiStatus) {
            PlatformStatus.LOGGED_IN -> {
                Intent(context, PollingService::class.java).apply {
                    action = PollingService.ACTION_START_HUI
                    context.startForegroundService(this)
                }
            }
            PlatformStatus.NOT_LOGGED_IN, PlatformStatus.TOKEN_EXPIRED -> {
                Intent(context, PollingService::class.java).apply {
                    action = PollingService.ACTION_STOP_HUI
                    context.startForegroundService(this)
                }
            }
            else -> { }
        }
    }

    LaunchedEffect(youkaStatus) {
        when (youkaStatus) {
            PlatformStatus.LOGGED_IN -> {
                Intent(context, PollingService::class.java).apply {
                    action = PollingService.ACTION_START_YOUKA
                    context.startForegroundService(this)
                }
            }
            PlatformStatus.NOT_LOGGED_IN, PlatformStatus.TOKEN_EXPIRED -> {
                Intent(context, PollingService::class.java).apply {
                    action = PollingService.ACTION_STOP_YOUKA
                    context.startForegroundService(this)
                }
            }
            else -> { }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, tokenManager, sessionManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (tokenManager.floatWindowEnabled && PermissionHelper.isOverlayPermissionGranted(context)) {
                        Intent(context, FloatWindowService::class.java).also {
                            context.startService(it)
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    Intent(context, FloatWindowService::class.java).also {
                        context.stopService(it)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    sessionManager.reloadAll()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        sessionManager.loadYoukaPage()
        sessionManager.loadHuiPage()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.height(64.dp)) {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { item.icon() },
                        label = { Text(item.label, fontSize = 11.sp) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.height(64.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Data.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.HuiQuanYi.route) {
                HuiPage(
                    tokenManager = tokenManager,
                    sessionManager = sessionManager,
                    huiToken = huiTokenValue,
                    onStatusChange = { status ->
                        huiStatus = status
                    }
                )
            }
            composable(BottomNavItem.YouKaYun.route) {
                YoukaPage(
                    tokenManager = tokenManager,
                    sessionManager = sessionManager,
                    youkaToken = youkaTokenValue,
                    onStatusChange = { status ->
                        youkaStatus = status
                    }
                )
            }
            composable(BottomNavItem.Data.route) {
                DataPage(
                    tokenManager = tokenManager,
                    huiStatus = huiStatus,
                    youkaStatus = youkaStatus
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
