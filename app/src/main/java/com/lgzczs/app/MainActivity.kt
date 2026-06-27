package com.lgzczs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lgzczs.app.ui.HuiPage
import com.lgzczs.app.ui.YoukaPage
import com.lgzczs.app.ui.theme.LgzczsTheme
import com.lgzczs.app.util.TokenManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object HuiQuanYi : BottomNavItem("hui", "汇权益", Icons.Default.AccountBalance)
    data object YouKaYun : BottomNavItem("youka", "优卡云", Icons.Default.Cloud)
    data object Data : BottomNavItem("data", "数据", Icons.Default.Dashboard)
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
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
                PlaceholderScreen("数据")
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
