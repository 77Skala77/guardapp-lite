// core/NSFWDetector.kt
package com.guardapp.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Результат анализа кадра.
 *
 * @param score       nsfwScore = hentai + porn + sexy (0.0–1.0)
 * @param safeScore   safeScore = drawings + neutral
 * @param topClass    наиболее вероятный класс
 * @param topProb     вероятность наиболее вероятного класса
 * @param isAboveThreshold  нужно ли запускать Экран Просветления
 */
data class NSFWResult(
    val score: Float,
    val safeScore: Float = 0f,
    val topClass: String = "unknown",
    val topProb: Float = 0f,
    val isAboveThreshold: Boolean
)

/**
 * Интерфейс детектора.
 * Реализации:
 *  1. MobileNetNSFWDetector — реальная модель nsfw_mobilenet_v2.tflite
 *  2. StubNSFWDetector      — заглушка, если модель недоступна
 */
interface NSFWDetector {
    fun analyze(bitmap: Bitmap): NSFWResult
    fun close()

    companion object {
        /** Создаёт лучший доступный детектор */
        fun create(context: Context, threshold: Float): NSFWDetector {
            return try {
                MobileNetNSFWDetector(context, threshold).also {
                    Log.i("NSFWDetector", "✅ Используется реальная TFLite-модель")
                }
            } catch (e: Exception) {
                Log.w("NSFWDetector", "⚠️ Модель не найдена, работает заглушка: ${e.message}")
                StubNSFWDetector(threshold)
            }
        }
    }
}

// ─────────────────────────────────────────
// STUB
// ─────────────────────────────────────────

/** Заглушка — всегда безопасна. Используйте "Тестовое срабатывание" для проверки UI. */
class StubNSFWDetector(private val threshold: Float) : NSFWDetector {
    override fun analyze(bitmap: Bitmap) =
        NSFWResult(score = 0.05f, safeScore = 0.95f, topClass = "neutral",
            topProb = 0.95f, isAboveThreshold = false)
    override fun close() {}
}

// ─────────────────────────────────────────
// REAL MODEL
// ─────────────────────────────────────────

/**
 * TFLite детектор на базе nsfw_mobilenet_v2.tflite.
 *
 * Классы (class_labels.txt):
 *   0 = drawings  ← безопасный
 *   1 = hentai    ← NSFW
 *   2 = neutral   ← безопасный
 *   3 = porn      ← NSFW
 *   4 = sexy      ← NSFW
 *
 * nsfwScore = P(hentai) + P(porn) + P(sexy)
 * safeScore = P(drawings) + P(neutral)
 *
 * Вход модели: 224×224 RGB, float32, нормализован в [0, 1]
 * Выход: массив [1][5] с вероятностями
 *
 * Все кадры обрабатываются в памяти и сразу удаляются после анализа.
 * Никакие изображения не сохраняются и не передаются на сервер.
 */
class MobileNetNSFWDetector(
    private val context: Context,
    private val threshold: Float
) : NSFWDetector {

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile()
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(2)
            setUseNNAPI(false)   // NNAPI иногда ломает float32 на некоторых чипах
        })

        // Проверяем форму выхода
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.i(TAG, "Модель загружена. Выход: ${outputShape.toList()}")
        check(outputShape.last() == NUM_CLASSES) {
            "Ожидается $NUM_CLASSES классов, модель вернула ${outputShape.last()}"
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILENAME)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    override fun analyze(bitmap: Bitmap): NSFWResult {
        return try {
            // 1. Resize до 224×224 без сохранения
            val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 2. Конвертируем в ByteBuffer float32 [0,1]
            val inputBuffer = bitmapToFloatBuffer(scaled)
            scaled.recycle()  // сразу освобождаем — не храним

            // 3. Inference
            val output = Array(1) { FloatArray(NUM_CLASSES) }
            interpreter.run(inputBuffer, output)

            val probs = output[0]

            // 4. Проверяем что получили 5 значений
            if (probs.size != NUM_CLASSES) {
                Log.e(TAG, "Неожиданный размер выхода: ${probs.size}, ожидалось $NUM_CLASSES")
                return NSFWResult(0f, 1f, "error", 0f, false)
            }

            // 5. Считаем score
            // indices: 0=drawings, 1=hentai, 2=neutral, 3=porn, 4=sexy
            val pDrawings = probs[0]
            val pHentai   = probs[1]
            val pNeutral  = probs[2]
            val pPorn     = probs[3]
            val pSexy     = probs[4]

            val nsfwScore = pHentai + pPorn + pSexy
            val safeScore = pDrawings + pNeutral

            // 6. Топ-класс для логов
            val topIdx   = probs.indices.maxByOrNull { probs[it] } ?: 2
            val topClass = LABELS.getOrElse(topIdx) { "unknown" }
            val topProb  = probs[topIdx]

            val triggered = nsfwScore >= threshold

            Log.i(TAG,
                "nsfwScore=%.3f safeScore=%.3f top=%s(%.3f) threshold=%.2f triggered=%b"
                    .format(nsfwScore, safeScore, topClass, topProb, threshold, triggered)
            )

            NSFWResult(
                score            = nsfwScore,
                safeScore        = safeScore,
                topClass         = topClass,
                topProb          = topProb,
                isAboveThreshold = triggered
            )

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка inference: $e")
            // При ошибке — не запускаем Экран Просветления
            NSFWResult(0f, 1f, "error", 0f, false)
        }
    }

    /**
     * Конвертирует bitmap 224×224 в ByteBuffer float32.
     * Нормализация: [0, 255] → [0.0, 1.0]
     */
    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * FLOAT_BYTES)
        buf.order(ByteOrder.nativeOrder())
        buf.rewind()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8)  and 0xFF) / 255f
            val b = ( pixel         and 0xFF) / 255f
            buf.putFloat(r)
            buf.putFloat(g)
            buf.putFloat(b)
        }
        buf.rewind()
        return buf
    }

    override fun close() {
        try { interpreter.close() } catch (_: Exception) {}
        Log.i(TAG, "Interpreter closed")
    }

    companion object {
        private const val TAG          = "MobileNetNSFW"
        private const val MODEL_FILENAME = "nsfw_mobilenet_v2.tflite"
        private const val INPUT_SIZE   = 224
        private const val NUM_CLASSES  = 5
        private const val FLOAT_BYTES  = 4

        // Соответствует class_labels.txt: drawings hentai neutral porn sexy
        val LABELS = listOf("drawings", "hentai", "neutral", "porn", "sexy")
    }
}
