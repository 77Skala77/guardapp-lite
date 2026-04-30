// ui/settings/SettingsScreen.kt
package com.guardapp.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardapp.data.EventLog
import com.guardapp.data.Sensitivity
import com.guardapp.data.Settings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenAnchorSetup: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings.getInstance(context) }
    val eventLog = remember { EventLog.getInstance(context) }
    val scope = rememberCoroutineScope()

    val sensitivity by settings.sensitivity.collectAsState(initial = Sensitivity.BALANCED)
    val freqSec by settings.analysisFreqSec.collectAsState(initial = 10)
    val audioMute by settings.audioMuteEnabled.collectAsState(initial = true)

    var showResetDialog by remember { mutableStateOf(false) }
    var showClearLogDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ── Anchor card ──
        Card(onClick = onOpenAnchorSetup, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Экран Просветления", fontWeight = FontWeight.SemiBold)
                    Text("Выбор медиа-якоря", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Sensitivity ──
        SettingsCard(title = "ЧУВСТВИТЕЛЬНОСТЬ", icon = Icons.Default.Visibility) {
            SegmentedControl(
                items = Sensitivity.values().map { it.displayName },
                selectedIndex = Sensitivity.values().indexOf(sensitivity),
                onItemSelected = { idx ->
                    scope.launch { settings.setSensitivity(Sensitivity.values()[idx]) }
                }
            )
            sensitivity.warning?.let {
                Spacer(Modifier.height(8.dp))
                WarningBox(it)
            }
        }

        // ── Frequency ──
        SettingsCard(title = "ЧАСТОТА АНАЛИЗА", icon = Icons.Default.Timer) {
            Text("Каждые $freqSec сек.")
            Slider(
                value = freqSec.toFloat(),
                onValueChange = { newVal ->
                    val rounded = (newVal / 10).toInt() * 10
                    scope.launch { settings.setAnalysisFreq(rounded.coerceIn(10, 60)) }
                },
                valueRange = 10f..60f,
                steps = 4
            )
            if (freqSec > 30) WarningBox("Чем реже проверка, тем больше времени остаётся импульсу")
        }

        // ── Audio mute ──
        SettingsCard(title = "ЗВУК", icon = Icons.AutoMirrored.Filled.VolumeOff) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Приглушать другие приложения")
                    Text("AudioFocus + ducking", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = audioMute,
                    onCheckedChange = { scope.launch { settings.setAudioMuteEnabled(it) } }
                )
            }
            Spacer(Modifier.height(6.dp))
            WarningBox("Per-app mute невозможен. Звонки (Phone, WhatsApp, Zoom) НЕ приглушаются.")
        }

        // ── Log management ──
        SettingsCard(title = "ЖУРНАЛ", icon = Icons.Default.History) {
            OutlinedButton(onClick = { showClearLogDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Очистить журнал")
            }
        }

        // ── Danger zone ──
        SettingsCard(title = "ОПАСНАЯ ЗОНА", icon = Icons.Default.Warning) {
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сбросить все настройки")
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить всё?") },
            text = { Text("Будут удалены все настройки и журнал. Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { settings.resetAll(); eventLog.clear() }
                    showResetDialog = false
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Отмена") } }
        )
    }

    if (showClearLogDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogDialog = false },
            title = { Text("Очистить журнал?") },
            text = { Text("Все записи будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { eventLog.clear() }
                    showClearLogDialog = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearLogDialog = false }) { Text("Отмена") } }
        )
    }
}

// ─────────────────────────────────────────
// Переиспользуемые компоненты
// ─────────────────────────────────────────

/**
 * Кастомный SegmentedControl без экспериментального Material3 API.
 * Использует стабильный Row + OutlinedButton.
 */
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val shape = when {
                items.size == 1 -> RoundedCornerShape(50)
                index == 0 -> RoundedCornerShape(topStart = 50f, bottomStart = 50f)
                index == items.size - 1 -> RoundedCornerShape(topEnd = 50f, bottomEnd = 50f)
                else -> RoundedCornerShape(0f)
            }
            OutlinedButton(
                onClick = { onItemSelected(index) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun WarningBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("⚠ $text", modifier = Modifier.padding(10.dp), fontSize = 11.sp)
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
