// service/AnchorOverlayService.kt
package com.guardapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.request.ImageRequest
import com.guardapp.MainActivity
import com.guardapp.R
import com.guardapp.core.AudioFocusManager
import com.guardapp.data.AnchorConfig
import com.guardapp.data.AnchorType
import com.guardapp.data.EventLog
import com.guardapp.data.LogEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground Service + WindowManager overlay.
 *
 * Почему не Activity:
 *  - Activity убирается Home-кнопкой → сессия уходит в background
 *  - WindowManager overlay остаётся поверх ВСЕГО, пока сервис жив
 *
 * Порядок:
 *  1. startForeground() — сразу, чтобы Android не убил сервис
 *  2. addView() — добавляем overlay поверх всего
 *  3. play() — запускаем медиа
 *  4. countdown 60с → показываем кнопку закрытия
 *  5. close() → removeView → release → stopSelf
 */
class AnchorOverlayService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var exoPlayer: ExoPlayer? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var audioMgr: AudioFocusManager? = null
    private var isOverlayAdded = false
    private var currentEntryId: Long = -1L

    // UI elements, set after view inflate
    private var timerText: TextView? = null
    private var closeButton: Button? = null
    private var errorText: TextView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // 1. Сразу startForeground — Android требует в течение 5 сек
                startForegroundWithNotification()

                // 2. Проверяем overlay permission
                if (!Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "No overlay permission!")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // 3. Защита от двойного запуска
                if (isOverlayAdded) {
                    Log.w(TAG, "Overlay already shown, ignoring")
                    return START_NOT_STICKY
                }

                val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
                currentEntryId = entryId
                showOverlay()
            }
            ACTION_CLOSE -> closeOverlay()
        }
        return START_NOT_STICKY
    }

    // ─────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val channelId = "anchor_overlay"
        val channel = NotificationChannel(
            channelId, "Экран Просветления", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Медиа-якорь помогает выйти из импульса"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Экран Просветления активен")
            .setContentText("Медиа-якорь помогает выйти из импульса")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ─────────────────────────────────────────
    // OVERLAY
    // ─────────────────────────────────────────

    private fun showOverlay() {
        scope.launch {
            try {
                val settings = com.guardapp.data.Settings.getInstance(applicationContext)
                val config = settings.anchorConfig.first()

                // Audio focus
                val mute = settings.audioMuteEnabled.first()
                if (mute) {
                    audioMgr = AudioFocusManager(applicationContext).also { it.requestFocus() }
                }

                addOverlayView(config)
                startMedia(config)
                startCountdown()
                Log.i(TAG, "Overlay added, countdown started")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay", e)
                stopSelf()
            }
        }
    }

    private fun addOverlayView(config: AnchorConfig) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Скрываем status/navigation bars best-effort
            // Шторка Android остаётся доступной — это нормальное поведение Android.
            // Overlay продолжает работать после открытия/закрытия шторки.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Строим overlay через programmatic views (без Compose — сервис не имеет lifecycle)
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
        }

        // Медиа-контент
        when (config.type) {
            AnchorType.VIDEO -> {
                // PlayerView добавим после создания ExoPlayer в startMedia
            }
            AnchorType.IMAGE, AnchorType.IMAGE_AND_AUDIO -> {
                addImageView(root, config.primaryUri)
            }
            AnchorType.AUDIO -> {
                addAudioGroundingView(root)
            }
        }

        // Overlay text
        if (config.textEnabled && config.text.isNotEmpty() &&
            config.type != AnchorType.AUDIO) {
            val tv = TextView(this).apply {
                text = config.text
                textSize = config.textSize.sp.toFloat()
                setTextColor(android.graphics.Color.parseColor(
                    when (config.textColor) {
                        AnchorConfig.TextColor.WHITE -> "#FFFFFF"
                        AnchorConfig.TextColor.BLACK -> "#000000"
                        AnchorConfig.TextColor.BLUE  -> "#6FA8FF"
                        AnchorConfig.TextColor.GOLD  -> "#E4C46E"
                    }
                ))
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setPadding(48, 48, 48, 48)
            }
            val tvParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = when (config.textPosition) {
                    AnchorConfig.TextPosition.TOP    -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    AnchorConfig.TextPosition.CENTER -> Gravity.CENTER
                    AnchorConfig.TextPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                val m = 80
                setMargins(m, m, m, m)
            }
            root.addView(tv, tvParams)
        }

        // Error text (скрыт по умолчанию)
        val errTv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(48, 0, 48, 0)
            visibility = android.view.View.GONE
        }
        root.addView(errTv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
        errorText = errTv

        // Timer label
        val timerTv = TextView(this).apply {
            text = "Останься здесь ещё 60с"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 15f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }
        val timerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, 160)
        }
        root.addView(timerTv, timerParams)
        timerText = timerTv

        // Close button (скрыт первые 60с)
        val closeBtn = Button(this).apply {
            text = "Закрыть и вернуться"
            setBackgroundColor(0x33FFFFFF)
            setTextColor(0xFFFFFFFF.toInt())
            visibility = android.view.View.GONE
            setOnClickListener { closeOverlay() }
        }
        val closeBtnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            160
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(60, 0, 60, 60)
        }
        root.addView(closeBtn, closeBtnParams)
        closeButton = closeBtn

        // Добавляем overlay
        try {
            windowManager.addView(root, params)
            overlayView = root
            isOverlayAdded = true
            Log.i(TAG, "Overlay added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to addView", e)
            stopSelf()
        }
    }

    private fun addImageView(root: FrameLayout, uriString: String?) {
        if (uriString == null) {
            showError("Файл медиа-якоря недоступен.\nВыберите файл заново.")
            return
        }

        val uri = Uri.parse(uriString)
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        // ImageView — placeholder пока грузится
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(iv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Coil ImageLoader — читает EXIF orientation автоматически,
        // ограничивает размер экраном, нет OOM.
        // Используем enqueue() вместо execute() чтобы не блокировать Main thread.
        val imageLoader = ImageLoader.Builder(applicationContext)
            .respectCacheHeaders(false)
            .build()

        val request = ImageRequest.Builder(applicationContext)
            .data(uri)
            .size(w, h)                    // downsampling под экран
            .allowHardware(false)          // нужен software bitmap для WindowManager
            .target(
                onSuccess = { drawable ->
                    iv.setImageDrawable(drawable)
                    Log.i(TAG, "Image loaded via Coil (EXIF applied): $uri")
                },
                onError = { _ ->
                    Log.e(TAG, "Coil failed to load image: $uri — trying ExifInterface fallback")
                    loadWithExifFallback(iv, uri, w, h)
                }
            )
            .build()

        imageLoader.enqueue(request)
    }

    /**
     * Fallback: BitmapFactory с ручным применением EXIF orientation.
     * Используется только если Coil не смог загрузить.
     */
    private fun loadWithExifFallback(iv: ImageView, uri: Uri, maxW: Int, maxH: Int) {
        try {
            // 1. Читаем EXIF orientation
            val rotation = contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

            val degrees = when (rotation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            // 2. Читаем размеры для downsampling
            val boundsOpts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val sampleSize = maxOf(boundsOpts.outWidth / maxW, boundsOpts.outHeight / maxH, 1)

            // 3. Загружаем с downsampling
            val bitmapOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            var bitmap = contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, bitmapOpts)
            } ?: run {
                showError("Не удалось загрузить изображение.")
                return
            }

            // 4. Применяем EXIF поворот если нужно
            if (degrees != 0f) {
                val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
                val rotated = android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle()
                bitmap = rotated
            }

            iv.setImageBitmap(bitmap)
            Log.i(TAG, "Image loaded via ExifInterface fallback, rotated ${degrees}°")

        } catch (e: Exception) {
            Log.e(TAG, "ExifInterface fallback failed", e)
            showError("Ошибка загрузки изображения.")
        }
    }

    private fun addAudioGroundingView(root: FrameLayout) {
        val tv = TextView(this).apply {
            text = "🎵"
            textSize = 72f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(0x66FFFFFF)
        }
        root.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
    }

    private fun showError(msg: String) {
        errorText?.let {
            it.text = msg
            it.visibility = android.view.View.VISIBLE
        }
    }

    // ─────────────────────────────────────────
    // MEDIA
    // ─────────────────────────────────────────

    private fun startMedia(config: AnchorConfig) {
        when (config.type) {
            AnchorType.VIDEO -> startVideo(config)
            AnchorType.AUDIO -> startAudio(config.primaryUri, config.volumePercent)
            AnchorType.IMAGE_AND_AUDIO -> startAudio(config.secondaryUri, config.volumePercent)
            AnchorType.IMAGE -> { /* только изображение, уже добавлено */ }
        }
    }

    private fun startVideo(config: AnchorConfig) {
        val uriString = config.primaryUri ?: run {
            showError("Файл видео недоступен. Выберите файл заново.")
            return
        }
        try {
            val player = ExoPlayer.Builder(applicationContext).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                volume = config.volumePercent / 100f
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
            Log.i(TAG, "ExoPlayer started (video, looping)")

            // Добавляем PlayerView в overlay
            val playerView = androidx.media3.ui.PlayerView(this).apply {
                this.player = player
                useController = false
            }
            (overlayView as? FrameLayout)?.addView(playerView, 0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video start error", e)
            showError("Ошибка воспроизведения видео.")
        }
    }

    private fun startAudio(uriString: String?, volumePercent: Int) {
        if (uriString == null) {
            showError("Файл аудио недоступен. Выберите файл заново.")
            return
        }
        try {
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(applicationContext, Uri.parse(uriString))
                val v = volumePercent / 100f
                setVolume(v, v)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "MediaPlayer started (audio, looping)")
        } catch (e: Exception) {
            Log.e(TAG, "Audio start error", e)
            showError("Ошибка воспроизведения аудио.")
        }
    }

    // ─────────────────────────────────────────
    // COUNTDOWN
    // ─────────────────────────────────────────

    private fun startCountdown() {
        val totalMs = 60_000L
        val tickMs  = 100L   // обновляем каждые 100мс для точной видимости

        scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            Log.i(TAG, "Countdown started")

            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - startTime

                if (elapsed >= totalMs) {
                    // 60 секунд истекли
                    timerText?.visibility = android.view.View.GONE
                    closeButton?.visibility = android.view.View.VISIBLE
                    Log.i(TAG, "Close button shown")
                    break
                }

                // Логика видимости:
                //   0–10 сек:           скрыт
                //   каждые 15 сек: 2 сек виден, 13 сек скрыт
                //   (первое окно: 10–12 сек → нет, сдвиг от 0: виден на 15–17, 30–32, 45–47)
                val shouldShow = elapsed >= 10_000L &&
                        elapsed % 15_000L < 2_000L

                if (shouldShow) {
                    val secondsLeft = ((totalMs - elapsed) / 1000L + 1).coerceIn(1, 60)
                    timerText?.text = "Останься здесь ещё ${secondsLeft}с"
                    timerText?.visibility = android.view.View.VISIBLE
                } else {
                    timerText?.visibility = android.view.View.INVISIBLE  // занимает место, но не виден
                }

                delay(tickMs)
            }
        }
    }

    // ─────────────────────────────────────────
    // CLOSE
    // ─────────────────────────────────────────

    private fun closeOverlay() {
        Log.i(TAG, "Closing overlay")

        // Обновляем запись в журнале
        if (currentEntryId > 0) {
            scope.launch {
                val eventLog = com.guardapp.data.EventLog.getInstance(applicationContext)
                eventLog.findById(currentEntryId)?.let { entry ->
                    eventLog.update(entry.copy(closedAfter60s = true))
                }
            }
        }

        // Останавливаем медиа
        try { exoPlayer?.release() } catch (_: Exception) {}
        exoPlayer = null
        Log.i(TAG, "ExoPlayer released")

        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        Log.i(TAG, "MediaPlayer released")

        // Восстанавливаем audio focus
        audioMgr?.release()
        audioMgr = null

        // Удаляем overlay
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeView error", e)
        }
        overlayView = null
        isOverlayAdded = false
        Log.i(TAG, "Overlay removed")

        // Уведомляем ScreenAnalysisService что якорь закрыт
        startService(ScreenAnalysisService.anchorClosedIntent(applicationContext))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Service stopped")
    }

    override fun onDestroy() {
        scope.cancel()
        if (isOverlayAdded) {
            try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        }
        try { exoPlayer?.release() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        audioMgr?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AnchorOverlayService"
        private const val NOTIF_ID = 1002
        const val ACTION_START = "com.guardapp.lite.ANCHOR_START"
        const val ACTION_CLOSE = "com.guardapp.lite.ANCHOR_CLOSE"
        const val EXTRA_ENTRY_ID = "entry_id"

        fun startIntent(context: Context, entryId: Long = -1L): Intent =
            Intent(context, AnchorOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ENTRY_ID, entryId)
            }
    }
}
