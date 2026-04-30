// MainActivity.kt
package com.guardapp

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.guardapp.data.Settings
import com.guardapp.service.AnchorOverlayService
import com.guardapp.service.ScreenAnalysisService
import com.guardapp.ui.log.LogScreen
import com.guardapp.ui.main.MainScreen
import com.guardapp.ui.onboarding.OnboardingScreen
import com.guardapp.ui.settings.AnchorSetupScreen
import com.guardapp.ui.settings.SettingsScreen
import com.guardapp.ui.theme.GuardAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private lateinit var mpm: MediaProjectionManager

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = ScreenAnalysisService.startIntent(this, result.resultCode, result.data!!)
            ContextCompat.startForegroundService(this, intent)
            lifecycleScope.launch { settings.setProtectionEnabled(true) }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* запрос показан, продолжаем */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.getInstance(this)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            GuardAppTheme {
                val onboardingDone by settings.onboardingCompleted.collectAsState(initial = false)
                if (onboardingDone) {
                    AppNavigation(
                        onStartProtection = {
                            val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val config = android.media.projection.MediaProjectionConfig
                                    .createConfigForDefaultDisplay()
                                mpm.createScreenCaptureIntent(config)
                            } else {
                                mpm.createScreenCaptureIntent()
                            }
                            mediaProjectionLauncher.launch(captureIntent)
                        },
                        onStopProtection = {
                            startService(ScreenAnalysisService.stopIntent(this@MainActivity))
                            lifecycleScope.launch { settings.setProtectionEnabled(false) }
                        },
                        onTestTrigger = {
                            // Валидация якоря и overlay permission уже выполнена в MainScreen.
                            // Здесь только запуск сервиса.
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                AnchorOverlayService.startIntent(this@MainActivity, -1L)
                            )
                        },
                        onOpenOverlaySettings = {
                            // Только переход в системные настройки разрешения.
                            // Не запускает якорь ни при каких условиях.
                            startActivity(Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:$packageName")
                            ))
                        }
                    )
                } else {
                    OnboardingScreen(onComplete = {
                        lifecycleScope.launch { settings.setOnboardingCompleted(true) }
                    })
                }
            }
        }
    }
}

// ─────────────────────────────────────────
// NAVIGATION
// ─────────────────────────────────────────

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Main : Tab("main", "Главная", Icons.Default.Shield)
    object Log : Tab("log", "Журнал", Icons.AutoMirrored.Filled.List)
    object Settings : Tab("settings", "Настройки", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    onStartProtection: () -> Unit,
    onStopProtection: () -> Unit,
    onTestTrigger: () -> Unit,
    onOpenOverlaySettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Main, Tab.Log, Tab.Settings)

    // Маппинг вложенных маршрутов → родительская вкладка.
    // Нужен чтобы правильно подсвечивать вкладку когда открыт вложенный экран.
    val routeToTab = mapOf(
        Tab.Main.route    to Tab.Main.route,
        Tab.Log.route     to Tab.Log.route,
        Tab.Settings.route to Tab.Settings.route,
        "anchor_setup"    to Tab.Main.route,   // Экран Просветления → вкладка Главная
        "contract_setup"  to Tab.Settings.route
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                // Определяем активную вкладку: если вложенный экран — берём его родителя
                val activeTab = routeToTab[currentRoute] ?: Tab.Main.route

                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Очищаем весь стек до стартового маршрута включительно
                                // saveState = false → вложенные экраны (anchor_setup и др.)
                                // не сохраняются при переключении вкладок
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Main.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Main.route) {
                MainScreen(
                    onStartProtection = onStartProtection,
                    onStopProtection = onStopProtection,
                    onTestTrigger = onTestTrigger,
                    onOpenAnchorSetup = { navController.navigate("anchor_setup") },
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenLog = {
                        navController.navigate(Tab.Log.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Tab.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                )
            }
            composable(Tab.Log.route) { LogScreen() }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onOpenAnchorSetup = { navController.navigate("anchor_setup") }
                )
            }
            // Вложенные экраны — часть общего NavHost,
            // popUpTo в bottom nav их корректно убирает из стека
            composable("anchor_setup") {
                AnchorSetupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
