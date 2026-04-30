// data/AnchorConfig.kt
package com.guardapp.data

import kotlinx.serialization.Serializable

/**
 * Тип медиа-якоря (Экрана Просветления).
 */
enum class AnchorType(val displayName: String) {
    VIDEO("Видео"),
    IMAGE("Изображение"),
    AUDIO("Аудио"),
    IMAGE_AND_AUDIO("Изображение + аудио");

    val maxDurationSec: Int
        get() = when (this) {
            VIDEO -> 180          // 3 минуты
            AUDIO, IMAGE_AND_AUDIO -> 300  // 5 минут
            IMAGE -> 0            // нет ограничения
        }
}

/**
 * Чувствительность NSFW-модели.
 */
enum class Sensitivity(val displayName: String, val threshold: Float) {
    SOFT("Мягче", 0.93f),
    BALANCED("Баланс", 0.85f),
    STRICT("Строже", 0.70f);

    val warning: String?
        get() = when (this) {
            SOFT -> "Низкая чувствительность может пропустить часть триггерного контента."
            BALANCED -> null
            STRICT -> "Высокая чувствительность увеличивает риск ложных срабатываний на медицинских, спортивных или художественных изображениях."
        }
}

/**
 * Частота анализа экрана.
 */
enum class AnalysisFrequency(val seconds: Int) {
    SEC_10(10), SEC_20(20), SEC_30(30),
    SEC_40(40), SEC_50(50), SEC_60(60);

    val displayName: String get() = "$seconds секунд"
    val warning: String?
        get() = if (seconds > 30)
            "Чем реже проверка, тем больше времени остаётся импульсу."
        else null
}

/**
 * Конфиг медиа-якоря.
 * Файлы хранятся через persistable URI permissions от Photo Picker.
 */
@Serializable
data class AnchorConfig(
    val type: AnchorType = AnchorType.IMAGE,
    val primaryUri: String? = null,        // главный файл
    val secondaryUri: String? = null,      // только для image+audio (audio)
    val volumePercent: Int = 70,

    // Опциональный текст overlay
    val textEnabled: Boolean = false,
    val text: String = "Вернись к себе.",
    val textSize: TextSize = TextSize.MEDIUM,
    val textPosition: TextPosition = TextPosition.CENTER,
    val textColor: TextColor = TextColor.WHITE
) {
    enum class TextSize(val displayName: String, val sp: Int) {
        SMALL("Маленький", 20),
        MEDIUM("Средний", 32),
        LARGE("Большой", 48)
    }
    enum class TextPosition(val displayName: String) {
        TOP("Сверху"), CENTER("По центру"), BOTTOM("Снизу")
    }
    enum class TextColor(val displayName: String, val argb: Long) {
        WHITE("Белый", 0xFFFFFFFF),
        BLACK("Чёрный", 0xFF000000),
        BLUE("Синий", 0xFF6FA8FF),
        GOLD("Золотой", 0xFFE4C46E)
    }

    /** Готов ли якорь к использованию */
    val isReady: Boolean
        get() = primaryUri != null && (type != AnchorType.IMAGE_AND_AUDIO || secondaryUri != null)
}
