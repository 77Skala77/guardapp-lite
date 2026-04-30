// core/NSFWDetector.kt
package com.guardapp.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Результат анализа кадра.
 */
data class NSFWResult(val score: Float, val isAboveThreshold: Boolean)

/**
 * Интерфейс детектора. Реализаций две:
 *  1. StubNSFWDetector — заглушка для MVP. Всегда возвращает 0.05.
 *  2. TFLiteNSFWDetector — production-версия с моделью.
 *
 * Так можно начать разработку без модели и подменить детектор позже.
 */
interface NSFWDetector {
    fun analyze(bitmap: Bitmap): NSFWResult
    fun close()

    companion object {
        /**
         * Создаёт лучший доступный детектор:
         *  - если nsfw_model.tflite есть в assets → TFLite
         *  - иначе → Stub (всегда возвращает 0.05)
         */
        fun create(context: Context, threshold: Float): NSFWDetector {
            return try {
                TFLiteNSFWDetector(context, threshold)
            } catch (e: Exception) {
                Log.w("NSFWDetector", "TFLite модель не найдена, работает заглушка: ${e.message}")
                StubNSFWDetector(threshold)
            }
        }
    }
}

/**
 * Заглушка для MVP. Возвращает score=0.05 (значит ничего не срабатывает).
 *
 * Чтобы вручную проверить UI, используйте кнопку "Тестовое срабатывание"
 * в MainScreen — она запускает AnchorActivity напрямую.
 */
class StubNSFWDetector(private val threshold: Float) : NSFWDetector {
    override fun analyze(bitmap: Bitmap): NSFWResult {
        return NSFWResult(score = 0.05f, isAboveThreshold = false)
    }
    override fun close() {}
}

/**
 * TFLite детектор. Загружает модель из assets/nsfw_model.tflite.
 *
 * Откуда взять модель:
 *  - GantMan/nsfw_model: https://github.com/gantman/nsfw_model (есть .tflite в форках)
 *  - NudeNet: https://github.com/notAI-tech/NudeNet
 *  - Yahoo Open NSFW: https://github.com/yahoo/open_nsfw
 *
 * Под вашу модель отрегулируйте:
 *  - INPUT_SIZE (обычно 224 или 299)
 *  - NUM_CLASSES (бинарная = 2, GantMan = 5)
 *  - индексы "опасных" классов в analyze()
 */
class TFLiteNSFWDetector(
    context: Context,
    private val threshold: Float
) : NSFWDetector {

    private val interpreter: Interpreter
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(127.5f, 127.5f))
        .build()

    init {
        val modelBuffer = loadModelFile(context)
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(2)
            setUseNNAPI(true)
        })
        Log.i(TAG, "✅ TFLite модель загружена")
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd("nsfw_model.tflite")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    override fun analyze(bitmap: Bitmap): NSFWResult {
        return try {
            var tensorImage = TensorImage.fromBitmap(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val output = Array(1) { FloatArray(NUM_CLASSES) }
            interpreter.run(tensorImage.buffer, output)

            // Для бинарной модели [SFW, NSFW]:
            val nsfwScore = output[0].getOrElse(1) { 0.05f }
            NSFWResult(score = nsfwScore, isAboveThreshold = nsfwScore >= threshold)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: $e")
            NSFWResult(score = 0.05f, isAboveThreshold = false)
        }
    }

    override fun close() {
        try { interpreter.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "TFLiteNSFW"
        const val INPUT_SIZE = 224
        const val NUM_CLASSES = 2
    }
}
