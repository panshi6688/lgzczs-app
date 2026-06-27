package com.lgzczs.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.network.YoukaApiClient
import com.lgzczs.app.service.PollingService
import com.lgzczs.app.ui.HuiPage
import com.lgzczs.app.ui.DataPage
import com.lgzczs.app.ui.YoukaPage
import com.lgzczs.app.ui.theme.LgzczsTheme
import com.lgzczs.app.util.PermissionHelper
import com.lgzczs.app.service.FloatWindowService
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
    data object HuiQuanYi : BottomNavItem("hui", "汇权益", { Icon(painterResource(R.drawable.ic_hui), "汇权益") })
    data object YouKaYun : BottomNavItem("youka", "优卡云", { Icon(painterResource(R.drawable.ic_youka), "优卡云") })
    data object Data : BottomNavItem("data", "数据", { Icon(Icons.Default.Dashboard, "数据") })
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

    DisposableEffect(context, tokenManager) {
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
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = item.icon,
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.HuiQuanYi.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.HuiQuanYi.route) {
                HuiPage(
                    tokenManager = tokenManager,
                    onStatusChange = { status ->
                        huiStatus = status
                    }
                )
            }
            composable(BottomNavItem.YouKaYun.route) {
                YoukaPage(
                    tokenManager = tokenManager,
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
