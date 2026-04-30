// AnchorActivity.kt
package com.guardapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.guardapp.core.AnchorPlayer
import com.guardapp.core.AudioFocusManager
import com.guardapp.data.AnchorConfig
import com.guardapp.data.AnchorType
import com.guardapp.data.EventLog
import com.guardapp.data.LogEntry
import com.guardapp.data.Settings
import com.guardapp.service.ScreenAnalysisService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Полноэкранная активити Экрана Просветления.
 *
 * Логика:
 *  1. Запускается медиа-якорь (видео / изображение / аудио / изобр+аудио)
 *  2. Audio focus exclusive — приглушаем другие приложения
 *  3. 60 секунд экран заблокирован (нет кнопки закрытия)
 *  4. После 60 секунд появляется кнопка "Закрыть"
 *  5. Пользователь нажимает "Закрыть" → вопрос про ложное срабатывание → возврат к телефону
 *
 * Ограничения Android:
 *  - Home-кнопку и Power button заблокировать нельзя (это правильно)
 *  - onBackPressed заблокирован в первые 60 секунд
 */
class AnchorActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private lateinit var eventLog: EventLog
    private lateinit var player: AnchorPlayer
    private lateinit var audioMgr: AudioFocusManager

    private var canClose = false
    private var entryId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = Settings.getInstance(this)
        eventLog = EventLog.getInstance(this)
        player = AnchorPlayer(this)
        audioMgr = AudioFocusManager(this)

        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)

        lifecycleScope.launch {
            if (settings.audioMuteEnabled.first()) {
                audioMgr.requestFocus()
            }
            val config = settings.anchorConfig.first()
            if (config.isReady) {
                player.play(config)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AnchorScreen(
                    settings = settings,
                    player = player,
                    onCloseRequest = { askFalsePositiveAndExit() }
                )
            }
        }

        // Вызываем после setContent — DecorView уже прикреплён к window
        window.decorView.post { hideSystemBars() }
    }

    private fun hideSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception) {
            Log.e("AnchorActivity", "Failed to hide system bars", e)
        }
    }

    @Deprecated("Suppressed: cannot close in first 60s")
    override fun onBackPressed() {
        // Игнорируем back-кнопку — пользователь должен нажать на UI кнопку после 60 сек
        // Если canClose уже true — сразу вызываем закрытие
        if (canClose) askFalsePositiveAndExit()
    }

    /** Помечает, что 60 секунд истекли (вызывается из Compose) */
    fun setCanClose() { canClose = true }

    /** Спрашиваем про ложное срабатывание и закрываемся */
    private fun askFalsePositiveAndExit() {
        // Показываем диалог "Это была ошибка?"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FalsePositiveDialog(
                    onAnswer = { isFalsePositive ->
                        lifecycleScope.launch {
                            if (entryId > 0) {
                                eventLog.findById(entryId)?.let { entry ->
                                    eventLog.update(entry.copy(
                                        isFalsePositive = isFalsePositive,
                                        closedAfter60s = true
                                    ))
                                }
                            }
                            cleanupAndFinish()
                        }
                    },
                    onSkip = {
                        lifecycleScope.launch {
                            if (entryId > 0) {
                                eventLog.findById(entryId)?.let { entry ->
                                    eventLog.update(entry.copy(closedAfter60s = true))
                                }
                            }
                            cleanupAndFinish()
                        }
                    }
                )
            }
        }
    }

    private fun cleanupAndFinish() {
        player.stop()
        audioMgr.release()
        startService(ScreenAnalysisService.anchorClosedIntent(this))
        finish()
        // overridePendingTransition убран — deprecated на API 34+, анимация не критична
    }

    override fun onDestroy() {
        try { player.stop() } catch (_: Exception) {}
        audioMgr.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }
}

// ─────────────────────────────────────────
// COMPOSE UI
// ─────────────────────────────────────────

@Composable
fun AnchorScreen(
    settings: Settings,
    player: AnchorPlayer,
    onCloseRequest: () -> Unit
) {
    val config by settings.anchorConfig.collectAsState(initial = AnchorConfig())
    var secondsLeft by remember { mutableStateOf(60) }
    var canClose by remember { mutableStateOf(false) }

    // 60-секундный обратный отсчёт
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        canClose = true
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // ── Медиа-контент ──
        when (config.type) {
            AnchorType.VIDEO -> {
                player.getExoPlayer()?.let { exo ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exo
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            AnchorType.IMAGE, AnchorType.IMAGE_AND_AUDIO -> {
                val uriString = config.primaryUri
                if (uriString != null) {
                    SafeAnchorImage(
                        uri = Uri.parse(uriString),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    GroundingBackground()
                }
            }
            AnchorType.AUDIO -> {
                // Для аудио — экран заземления + большая иконка + текст таймера
                GroundingBackground(showAudioIcon = true)
            }
        }

        // ── Текст overlay ──
        if (config.textEnabled && config.text.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                contentAlignment = when (config.textPosition) {
                    AnchorConfig.TextPosition.TOP -> Alignment.TopCenter
                    AnchorConfig.TextPosition.CENTER -> Alignment.Center
                    AnchorConfig.TextPosition.BOTTOM -> Alignment.BottomCenter
                }
            ) {
                Text(
                    text = config.text,
                    fontSize = config.textSize.sp.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(config.textColor.argb),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Bottom controls ──
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (canClose) {
                // Большая кнопка "Закрыть"
                Button(
                    onClick = onCloseRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Закрыть и вернуться",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Таймер
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$secondsLeft",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Останься здесь ещё ${secondsLeft}с",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroundingBackground(showAudioIcon: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0D1530),
                    Color(0xFF1A0D2E),
                    Color(0xFF0D0F14)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (showAudioIcon) {
            Text("🎵", fontSize = 100.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun FalsePositiveDialog(
    onAnswer: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp).widthIn(max = 380.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Это было ложное срабатывание?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Отметка сохраняется в журнале для статистики.\nНе создаёт исключений и не отключает защиту.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Нет, это был реальный триггер")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onAnswer(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Да, это была ошибка")
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Не хочу отмечать")
                }
            }
        }
    }
}

/**
 * Безопасный показ изображения через Coil с автоматическим downsampling.
 * Ограничивает размер загружаемого изображения размером экрана —
 * предотвращает "Canvas: trying to draw too large bitmap".
 */
@Composable
fun SafeAnchorImage(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val widthPx = displayMetrics.widthPixels
    val heightPx = displayMetrics.heightPixels

    val model = ImageRequest.Builder(context)
        .data(uri)
        .size(widthPx, heightPx)   // downsampling — никакого OOM
        .crossfade(false)
        .build()

    SubcomposeAsyncImage(
        model = model,
        contentDescription = "Экран Просветления",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        loading = {
            // Пока грузится — тёмный фон
            GroundingBackground()
        },
        error = {
            // Файл недоступен или повреждён — показываем нейтральный фон
            Log.e("SafeAnchorImage", "Failed to load: $uri")
            GroundingBackground()
        }
    )
}
