// service/ScreenAnalysisService.kt
package com.guardapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.guardapp.MainActivity
import com.guardapp.R
import com.guardapp.core.NSFWDetector
import com.guardapp.data.EventLog
import com.guardapp.data.LogEntry
import com.guardapp.data.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground service: захват экрана через MediaProjection + локальный анализ.
 *
 * Принципы:
 *  - Кадры обрабатываются в памяти и сразу освобождаются
 *  - Никаких сетевых запросов
 *  - Постоянное уведомление в шторке (пользователь видит работу сервиса)
	 *  - При обнаружении NSFW → запуск AnchorOverlayService (WindowManager overlay)
 */
class ScreenAnalysisService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var detector: NSFWDetector? = null
    private lateinit var settings: Settings
    private lateinit var eventLog: EventLog
    private var analysisJob: Job? = null

    // Защита от повторного срабатывания, пока показан Экран Просветления
    @Volatile private var anchorIsActive = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings.getInstance(applicationContext)
        eventLog = EventLog.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_DATA)
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForegroundWithNotification()
                    startCapture(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> stopAndRelease()
            ACTION_ANCHOR_CLOSED -> {
                anchorIsActive = false
                Log.i(TAG, "Anchor closed, resume analysis")
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Анализ экрана", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Локальный анализ NSFW-контента"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenAnalysisService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardApp активен")
            .setContentText("Локальный анализ экрана · Данные не покидают устройство")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_stop, "Остановить", stopPi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(resultCode, data)

            // Android 14+ требует registerCallback ДО createVirtualDisplay
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    analysisJob?.cancel()
                    analysisJob = null
                    virtualDisplay?.release()
                    virtualDisplay = null
                    imageReader?.close()
                    imageReader = null
                    projection = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            projection = proj

            // Создаём детектор асинхронно
            scope.launch {
                val sensitivity = settings.sensitivity.first()
                detector = NSFWDetector.create(applicationContext, sensitivity.threshold)
            }

            // Размеры экрана — совместимо с API 29+
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val screenWidth: Int
            val screenHeight: Int
            val density: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
                density = resources.displayMetrics.densityDpi
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
                density = metrics.densityDpi
            }

            val captureWidth = 640
            val captureHeight = if (screenWidth > 0)
                (screenHeight.toFloat() / screenWidth * captureWidth).toInt()
            else 1136

            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

            virtualDisplay = projection?.createVirtualDisplay(
                "GuardCapture",
                captureWidth, captureHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, null
            )

            startAnalysisLoop()
            Log.i(TAG, "Capture started: ${captureWidth}x${captureHeight}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startAnalysisLoop() {
        analysisJob = scope.launch {
            while (isActive) {
                val freqSec = settings.analysisFreqSec.first()
                delay(freqSec * 1000L)

                // Если показан Экран Просветления — пропускаем анализ
                if (anchorIsActive) continue

                analyzeOneFrame()
            }
        }
    }

    private suspend fun analyzeOneFrame() {
        val reader = imageReader ?: return
        val image: Image = try {
            reader.acquireLatestImage() ?: return
        } catch (e: Exception) { return }

        try {
            val bitmap = imageToBitmap(image)
            image.close()  // освобождаем сразу

            val det = detector ?: return
            val result = det.analyze(bitmap)
            bitmap.recycle()  // освобождаем bitmap

            if (result.isAboveThreshold) {
                val sensitivity = settings.sensitivity.first()
                Log.i(TAG, "🚨 NSFW: score=${result.score} safe=${result.safeScore} " +
                    "top=${result.topClass}(${result.topProb}) threshold=${sensitivity.threshold}")

                val anchorConfig = settings.anchorConfig.first()

                val entryId = eventLog.append(LogEntry(
                    eventType = "triggered",
                    sensitivity = sensitivity.name,
                    score = result.score,
                    anchorType = anchorConfig.type.name
                ))

                anchorIsActive = true
                triggerAnchor(entryId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error: $e")
            try { image.close() } catch (_: Exception) {}
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun triggerAnchor(entryId: Long) {
        // Запускаем WindowManager overlay — не убирается Home-кнопкой
        val intent = AnchorOverlayService.startIntent(this, entryId)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAndRelease() {
        analysisJob?.cancel()
        analysisJob = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        detector?.close()
        detector = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAndRelease()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenAnalysisService"
        private const val CHANNEL_ID = "guardapp_lite_analysis"
        private const val NOTIF_ID = 1001

        const val ACTION_START = "com.guardapp.lite.START"
        const val ACTION_STOP = "com.guardapp.lite.STOP"
        const val ACTION_ANCHOR_CLOSED = "com.guardapp.lite.ANCHOR_CLOSED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenAnalysisService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenAnalysisService::class.java).apply { action = ACTION_STOP }

        fun anchorClosedIntent(context: Context): Intent =
            Intent(context, ScreenAnalysisService::class.java).apply { action = ACTION_ANCHOR_CLOSED }
    }
}
