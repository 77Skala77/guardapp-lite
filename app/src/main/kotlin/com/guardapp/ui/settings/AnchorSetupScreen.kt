// ui/settings/AnchorSetupScreen.kt
package com.guardapp.ui.settings

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardapp.data.AnchorConfig
import com.guardapp.data.AnchorType
import com.guardapp.data.Settings
import kotlinx.coroutines.launch

/**
 * Настройка медиа-якоря.
 * Photo Picker для видео/изображений, GetContent для аудио.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings.getInstance(context) }
    val scope = rememberCoroutineScope()
    val config by settings.anchorConfig.collectAsState(initial = AnchorConfig())

    var fileError by remember { mutableStateOf<String?>(null) }
    var primaryName by remember { mutableStateOf("") }
    var secondaryName by remember { mutableStateOf("") }

    // Photo Picker — видео
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleFilePicked(context, it, true, AnchorType.VIDEO,
            scope, settings,
            onError = { e -> fileError = e },
            onSuccess = { name -> primaryName = name; fileError = null }
        )}
    }

    // Photo Picker — изображения
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleFilePicked(context, it, true, AnchorType.IMAGE,
            scope, settings,
            onError = { e -> fileError = e },
            onSuccess = { name -> primaryName = name; fileError = null }
        )}
    }

    // GetContent — аудио (Photo Picker не поддерживает audio напрямую)
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            handleFilePicked(context, it, true, AnchorType.AUDIO,
                scope, settings,
                onError = { e -> fileError = e },
                onSuccess = { name -> primaryName = name; fileError = null }
            )
        }
    }

    val secondaryAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            handleFilePicked(context, it, false, AnchorType.AUDIO,
                scope, settings,
                onError = { e -> fileError = e },
                onSuccess = { name -> secondaryName = name; fileError = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экран Просветления") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // ── Type ──
            SettingsCard(title = "ТИП МЕДИА", icon = Icons.Default.PhotoLibrary) {
                AnchorType.values().forEach { type ->
                    val isSelected = config.type == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        settings.setAnchorConfig(
                                            config.copy(
                                                type = type,
                                                primaryUri = null,
                                                secondaryUri = null
                                            )
                                        )
                                    }
                                    primaryName = ""; secondaryName = ""
                                }
                            )
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null   // обработчик на Row, не на кнопке
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = type.displayName,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    typeDescription(config.type),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── File picker ──
            SettingsCard(title = "ФАЙЛ", icon = Icons.Default.Folder) {
                when (config.type) {
                    AnchorType.VIDEO -> FilePickerRow(
                        "Видео",
                        primaryName.ifEmpty { config.primaryUri?.let { "Файл выбран" } ?: "" },
                        onPick = { videoPicker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        )) }
                    )
                    AnchorType.IMAGE -> FilePickerRow(
                        "Изображение",
                        primaryName.ifEmpty { config.primaryUri?.let { "Файл выбран" } ?: "" },
                        onPick = { imagePicker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )) }
                    )
                    AnchorType.AUDIO -> FilePickerRow(
                        "Аудио",
                        primaryName.ifEmpty { config.primaryUri?.let { "Файл выбран" } ?: "" },
                        onPick = { audioPicker.launch("audio/*") }
                    )
                    AnchorType.IMAGE_AND_AUDIO -> {
                        FilePickerRow(
                            "Изображение",
                            primaryName.ifEmpty { config.primaryUri?.let { "Файл выбран" } ?: "" },
                            onPick = { imagePicker.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )) }
                        )
                        FilePickerRow(
                            "Аудио",
                            secondaryName.ifEmpty { config.secondaryUri?.let { "Файл выбран" } ?: "" },
                            onPick = { secondaryAudioPicker.launch("audio/*") }
                        )
                    }
                }

                fileError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "🔒 Файл хранится локально, не передаётся на сервер",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // ── Volume ──
            if (config.type != AnchorType.IMAGE) {
                SettingsCard(title = "ГРОМКОСТЬ", icon = Icons.AutoMirrored.Filled.VolumeUp) {
                    Text("${config.volumePercent}%", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = config.volumePercent.toFloat(),
                        onValueChange = { newVal ->
                            scope.launch {
                                settings.setAnchorConfig(config.copy(volumePercent = newVal.toInt()))
                            }
                        },
                        valueRange = 0f..100f
                    )
                }
            }

            // ── Text overlay ──
            if (config.type != AnchorType.AUDIO) {
                SettingsCard(title = "ТЕКСТ ПОВЕРХ МЕДИА", icon = Icons.Default.TextFields) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Показывать текст", modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.textEnabled,
                            onCheckedChange = { newVal ->
                                scope.launch {
                                    settings.setAnchorConfig(config.copy(textEnabled = newVal))
                                }
                            }
                        )
                    }
                    if (config.textEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.text,
                            onValueChange = { newText ->
                                scope.launch {
                                    settings.setAnchorConfig(config.copy(text = newText))
                                }
                            },
                            placeholder = { Text("Ваша фраза") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Например: «Вернись к себе.»") }
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Размер", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        SegmentedControl(
                            items = AnchorConfig.TextSize.values().map { it.displayName },
                            selectedIndex = AnchorConfig.TextSize.values().indexOf(config.textSize),
                            onItemSelected = { idx ->
                                scope.launch {
                                    settings.setAnchorConfig(config.copy(textSize = AnchorConfig.TextSize.values()[idx]))
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Положение", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        SegmentedControl(
                            items = AnchorConfig.TextPosition.values().map { it.displayName },
                            selectedIndex = AnchorConfig.TextPosition.values().indexOf(config.textPosition),
                            onItemSelected = { idx ->
                                scope.launch {
                                    settings.setAnchorConfig(config.copy(textPosition = AnchorConfig.TextPosition.values()[idx]))
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Цвет", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        SegmentedControl(
                            items = AnchorConfig.TextColor.values().map { it.displayName },
                            selectedIndex = AnchorConfig.TextColor.values().indexOf(config.textColor),
                            onItemSelected = { idx ->
                                scope.launch {
                                    settings.setAnchorConfig(config.copy(textColor = AnchorConfig.TextColor.values()[idx]))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickerRow(label: String, fileName: String, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                fileName.ifEmpty { "Файл не выбран" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        OutlinedButton(onClick = onPick) {
            Text(if (fileName.isEmpty()) "Выбрать" else "Изменить")
        }
    }
}

private fun typeDescription(type: AnchorType): String = when (type) {
    AnchorType.VIDEO -> "Локальное видео до 3 минут (.mp4, .mkv, .webm)"
    AnchorType.IMAGE -> "Локальное изображение (.jpg, .png, .webp)"
    AnchorType.AUDIO -> "Локальный аудиофайл до 5 минут (.mp3, .m4a, .wav)"
    AnchorType.IMAGE_AND_AUDIO -> "Изображение + аудио (до 5 минут)"
}

private fun handleFilePicked(
    context: android.content.Context,
    uri: Uri,
    isPrimary: Boolean,
    type: AnchorType,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: Settings,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    try {
        // Persistable URI permission — нужна для доступа к файлу после рестарта
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Photo Picker иногда не даёт persistable — используем обычный access
        }

        // Проверка длительности для видео/аудио
        if (type == AnchorType.VIDEO || type == AnchorType.AUDIO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val durationSec = (durationMs / 1000).toInt()
                if (durationSec > type.maxDurationSec) {
                    onError("Длительность ${durationSec}с превышает максимум ${type.maxDurationSec}с")
                    return
                }
            } finally {
                retriever.release()
            }
        }

        // Сохраняем URI
        scope.launch {
            val current = settings.getAnchorConfigSync()
            settings.setAnchorConfig(
                if (isPrimary) current.copy(primaryUri = uri.toString())
                else current.copy(secondaryUri = uri.toString())
            )
        }

        onSuccess(uri.lastPathSegment ?: "Файл")
    } catch (e: Exception) {
        onError("Ошибка: ${e.localizedMessage}")
    }
}
