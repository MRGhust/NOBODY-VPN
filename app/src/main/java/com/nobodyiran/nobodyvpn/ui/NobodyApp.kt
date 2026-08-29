package com.nobodyiran.nobodyvpn.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.ui.home.HomeScreen
import com.nobodyiran.nobodyvpn.ui.home.LogsScreen
import com.nobodyiran.nobodyvpn.ui.servers.QrScanScreen
import com.nobodyiran.nobodyvpn.ui.servers.ServersScreen
import com.nobodyiran.nobodyvpn.ui.settings.PerAppScreen
import com.nobodyiran.nobodyvpn.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    const val PER_APP = "perapp"
    const val QR = "qr"
}

@Composable
fun NobodyApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        UiBus.snackbar.collect { msg ->
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                UiBus.snackbar.value = null
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = setOf(Routes.HOME, Routes.SERVERS, Routes.SETTINGS)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in topLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SERVERS,
                        onClick = {
                            navController.navigate(Routes.SERVERS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_servers)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenServers = { navController.navigate(Routes.SERVERS) },
                    onOpenLogs = { navController.navigate(Routes.LOGS) }
                )
            }
            composable(Routes.SERVERS) {
                ServersScreen(onScanQr = { navController.navigate(Routes.QR) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenPerApp = { navController.navigate(Routes.PER_APP) },
                    onOpenLogs = { navController.navigate(Routes.LOGS) }
                )
            }
            composable(Routes.LOGS) {
                LogsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PER_APP) {
                PerAppScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.QR) {
                QrScanScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
