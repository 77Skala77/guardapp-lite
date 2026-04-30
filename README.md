# GuardApp Lite — Простой Android-якорь от NSFW

## Главная идея

Один экран → одна задача:

**Обнаружение NSFW → Экран Просветления → 60 секунд тишины → возврат к телефону**

Никаких 30-минутных пауз, контрактов, донатов или recovery-кодов.

## Что делает приложение

1. Локально анализирует кадры экрана (раз в 10/20/30/40/50/60 сек)
2. При обнаружении NSFW → открывает полноэкранный медиа-якорь
3. 60 секунд экран нельзя закрыть
4. После 60 секунд — кнопка "Закрыть и вернуться"
5. Опционально спрашивает: "Это была ошибка?" → отмечает в журнале

## Архитектура

```
MainActivity (UI с табами)
    ↓
ScreenAnalysisService (Foreground)
    ↓ MediaProjection → Bitmap → TFLite → score
    ↓
если score >= threshold:
    AnchorActivity (полный экран)
    ├── Видео (ExoPlayer)
    ├── Изображение (Bitmap)
    ├── Аудио (MediaPlayer)
    └── Изобр+Аудио (Bitmap + MediaPlayer)
    ↓
    AudioFocus exclusive → другие приложения приглушаются
    ↓
    60 секунд lock → кнопка "Закрыть"
    ↓
    "Это была ошибка?" → пометка в журнале
```

## Структура файлов

```
GuardAppLite/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/guardapp/
│   │   │   ├── GuardApp.kt                       (Application)
│   │   │   ├── MainActivity.kt                   (UI с табами)
│   │   │   ├── AnchorActivity.kt                 ★ полноэкранный якорь
│   │   │   ├── service/
│   │   │   │   └── ScreenAnalysisService.kt     ★ MediaProjection + анализ
│   │   │   ├── core/
│   │   │   │   ├── NSFWDetector.kt              ★ интерфейс + Stub + TFLite
│   │   │   │   ├── AnchorPlayer.kt              (ExoPlayer/MediaPlayer)
│   │   │   │   └── AudioFocusManager.kt
│   │   │   ├── data/
│   │   │   │   ├── Settings.kt                  (DataStore)
│   │   │   │   ├── AnchorConfig.kt
│   │   │   │   └── EventLog.kt                  (Room)
│   │   │   └── ui/
│   │   │       ├── theme/Theme.kt
│   │   │       ├── onboarding/OnboardingScreen.kt
│   │   │       ├── main/MainScreen.kt
│   │   │       ├── settings/SettingsScreen.kt
│   │   │       ├── settings/AnchorSetupScreen.kt
│   │   │       └── log/LogScreen.kt
│   │   └── res/
│   │       ├── values/{strings,themes,colors}.xml
│   │       ├── drawable/{ic_shield,ic_stop,ic_launcher_foreground}.xml
│   │       ├── xml/{backup_rules,data_extraction_rules}.xml
│   │       └── mipmap-anydpi-v26/ic_launcher.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── BUILD_APK.md   ← как собрать APK
└── README.md
```

## Стек

- Kotlin + Jetpack Compose
- MediaProjection API (захват экрана)
- TensorFlow Lite (с заглушкой)
- ExoPlayer / Media3 (видео)
- MediaPlayer (аудио)
- AudioFocusRequest (приглушение)
- DataStore (настройки)
- Room (журнал)

## Разрешения

| Разрешение | Зачем |
|---|---|
| MediaProjection | Захват экрана для анализа |
| FOREGROUND_SERVICE | Стабильная работа в фоне |
| POST_NOTIFICATIONS | Уведомление о работе сервиса (Android 13+) |
| READ_MEDIA_VIDEO/IMAGES/AUDIO | Photo Picker для медиа-якоря |
| MODIFY_AUDIO_SETTINGS | Audio focus для приглушения других |

## Минимальные требования
- Android 10 (API 29) или новее
- ~50 MB свободного места

## MVP-заглушки (что нужно заменить для production)

1. **NSFWDetector** — сейчас работает StubNSFWDetector (всегда возвращает 0.05).
   Чтобы реально работало:
   - Скачайте модель из https://github.com/gantman/nsfw_model
   - Положите файл `nsfw_model.tflite` в `app/src/main/assets/`
   - Подгоните `NUM_CLASSES` в `NSFWDetector.kt` под вашу модель

2. **Stripe Payment Link** — отсутствует (упрощённая версия)

## Как собрать APK

См. **BUILD_APK.md** — 3 простых способа: Android Studio, командная строка, GitHub Actions.

## Принципиальный вывод

GuardApp Lite — это инструмент **разрыва импульса**, не блокировки телефона.

60 секунд — достаточно, чтобы вернуться к себе. Не 30 минут наказания.

Home-кнопка работает всегда. Это правильно — приложение не должно превращаться в ловушку.

> "Вернуть тебя к себе, когда ты уже почти перестал быть тем, кто выбирает."
