// ui/main/MainScreen.kt
package com.guardapp.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import com.guardapp.data.AnchorConfig
import com.guardapp.data.AnchorType
import com.guardapp.data.EventLog
import com.guardapp.data.Sensitivity
import com.guardapp.data.Settings

@Composable
fun MainScreen(
    onStartProtection: () -> Unit,
    onStopProtection: () -> Unit,
    onTestTrigger: () -> Unit,
    onOpenAnchorSetup: () -> Unit,
    onOpenOverlaySettings: () -> Unit = {},  // только открывает системные настройки
    onOpenLog: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { Settings.getInstance(context) }
    val eventLog = remember { EventLog.getInstance(context) }

    val protectionEnabled by settings.protectionEnabled.collectAsState(initial = false)
    val anchorConfig by settings.anchorConfig.collectAsState(initial = AnchorConfig())
    val sensitivity by settings.sensitivity.collectAsState(initial = Sensitivity.BALANCED)
    val freqSec by settings.analysisFreqSec.collectAsState(initial = 10)
    val entries by eventLog.recent().collectAsState(initial = emptyList())

    // ── Overlay permission: обновляется при каждом onResume ──
    var hasOverlayPermission by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayCount = remember(entries) {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        entries.count { it.timestamp >= startOfDay && it.eventType == "triggered" }
    }

    // Снэкбар для сообщений об ошибках
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .padding(scaffoldPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Status card ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (protectionEnabled)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (protectionEnabled) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                    null,
                    modifier = Modifier.size(44.dp),
                    tint = if (protectionEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (protectionEnabled) "Защита активна" else "Защита выключена",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (protectionEnabled)
                            "Анализ каждые $freqSec сек · $sensitivity"
                        else
                            "Нажмите «Запустить» чтобы начать",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Stats ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Сегодня", "$todayCount", "срабатываний", Modifier.weight(1f), onOpenLog)
            StatCard("Чувствительность", sensitivity.displayName, "", Modifier.weight(1f), onOpenSettings)
            StatCard("Частота", "${freqSec}с", "анализа", Modifier.weight(1f), onOpenSettings)
        }

        Spacer(Modifier.height(16.dp))

        // ── Anchor card ──
        Card(onClick = onOpenAnchorSetup, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Экран Просветления", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (anchorConfig.isReady)
                            "Тип: ${anchorConfig.type.displayName}"
                        else
                            "Не настроен — выберите медиа-файл",
                        fontSize = 12.sp,
                        color = if (anchorConfig.isReady)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }

        // ── Предупреждения ──

        // 1. Якорь не настроен
        if (!anchorConfig.isReady) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⚠️ Без медиа-якоря Экран Просветления не откроется",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp
                )
            }
        }

        // 2. Нет разрешения overlay
        //    onClick → только открыть системные настройки, ни в коем случае не запускать якорь
        if (!hasOverlayPermission) {
            Spacer(Modifier.height(8.dp))
            Card(
                onClick = onOpenOverlaySettings,  // открывает Settings, не якорь
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Нужно разрешение «Поверх других приложений»",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Без него Экран Просветления убирается Home-кнопкой.\nНажмите чтобы выдать разрешение.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Main button: start/stop ──
        if (protectionEnabled) {
            Button(
                onClick = onStopProtection,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("Остановить защиту", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = {
                    if (!anchorConfig.isReady) {
                        // Показываем сообщение, не запускаем
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Сначала выберите файл для Экрана Просветления")
                        }
                    } else {
                        onStartProtection()
                    }
                },
                enabled = true,  // всегда кликабельна, валидация внутри
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Запустить защиту", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Test trigger ──
        OutlinedButton(
            onClick = {
                when {
                    !anchorConfig.isReady -> {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Сначала выберите файл для Экрана Просветления")
                        }
                    }
                    !hasOverlayPermission -> {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Выдайте разрешение «Поверх других приложений»")
                        }
                    }
                    else -> onTestTrigger()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayCircleOutline, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Тестовое срабатывание (показать якорь сейчас)")
        }

        Spacer(Modifier.height(16.dp))

        // ── Privacy reminder ──
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Lock, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Все кадры обрабатываются локально и сразу удаляются. Ничего не передаётся по сети.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Экран Просветления остаётся поверх приложений. Системная шторка Android остаётся доступной — это нормальное поведение Android.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
