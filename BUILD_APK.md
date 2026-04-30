# 🔨 Как собрать APK GuardApp Lite

## Важно — прочитай сначала

Есть два пути:
- **Android Studio** — самый простой, работает из коробки ✅
- **Командная строка** — требует чуть больше действий

---

## 🎯 Способ 1 — Android Studio (РЕКОМЕНДУЕТСЯ)

### Шаги

1. Скачайте [Android Studio](https://developer.android.com/studio) и установите
2. Распакуйте `GuardAppLite.tar.gz`
3. Откройте Android Studio → **File → Open** → выберите папку `GuardAppLite`
4. Android Studio автоматически определит версии и предложит Gradle Sync
   *(Если предложит обновить AGP — нажмите Remind me later, не обновляйте пока)*
5. Подождите Gradle Sync (5–10 минут в первый раз — скачиваются зависимости)
6. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
7. APK готов: `app/build/outputs/apk/debug/app-debug.apk`
8. Перенесите APK на телефон → установите

### Запуск сразу на телефоне
1. Settings → About → нажмите Build Number 7 раз → Developer Options включены
2. Developer Options → USB Debugging → включить
3. Подключите телефон по USB → нажмите ▶ Run в Android Studio

---

## 🎯 Способ 2 — Командная строка (macOS / Linux)

```bash
# 1. Распакуйте
tar -xzf GuardAppLite.tar.gz
cd GuardAppLite

# 2. Убедитесь что ANDROID_HOME установлен
echo $ANDROID_HOME
# Должно быть что-то вроде /Users/имя/Library/Android/sdk
# Если нет — установите Android Studio, откройте проект,
# сделайте Gradle Sync и потом возвращайтесь сюда.

# 3. Сделайте gradlew исполняемым
chmod +x gradlew

# 4. Соберите APK
./gradlew assembleDebug

# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎯 Способ 3 — GitHub Actions (ничего не устанавливать локально)

1. Создайте репозиторий на [github.com](https://github.com)
2. Загрузите содержимое папки `GuardAppLite/` в репозиторий
3. Создайте файл `.github/workflows/build.yml`:

```yaml
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
      - name: Make gradlew executable
        run: chmod +x gradlew
      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace
      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

4. Запушьте → Actions tab → подождите ~5 мин → скачайте APK из артефактов

---

## 🤔 Частые ошибки и решения

### "Cannot mutate the dependencies after configuration was resolved"
**Причина:** Несовместимость версий AGP и Gradle.
**Решение:** Уже исправлено в этом архиве (AGP 8.3.2 + Gradle 8.4). Если всё ещё видите — в Android Studio: **File → Invalidate Caches → Invalidate and Restart**.

### Android Studio предлагает обновить AGP
Нажмите **Remind me later**. Обновлять не нужно — текущие версии уже совместимы.

### "Unresolved reference: Icons.AutoMirrored..."
Замените в коде:
```kotlin
Icons.AutoMirrored.Filled.List      → Icons.Default.List
Icons.AutoMirrored.Filled.ArrowBack → Icons.Default.ArrowBack
Icons.AutoMirrored.Filled.VolumeUp  → Icons.Default.VolumeUp
```

### "Gradle sync failed: Could not resolve..."
Проверьте интернет-соединение. Gradle качает зависимости (~300 MB в первый раз).

### Приложение установилось, но при запуске краш
Выдайте разрешения вручную:
**Settings → Apps → GuardApp → Permissions** → разрешите всё.

### MediaProjection запрашивает разрешение каждый раз
Это нормально — Android требует подтверждение при каждом старте для безопасности.

### NSFW не срабатывает
Без файла `nsfw_model.tflite` в папке `assets/` работает заглушка (score всегда 0.05).
Для проверки UI используйте кнопку **"Тестовое срабатывание"** в главном экране.

---

## 📦 Подписанный Release APK

```bash
# 1. Создайте keystore (один раз):
keytool -genkey -v -keystore guardapp.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias guardapp

# 2. Добавьте в app/build.gradle.kts перед buildTypes:
signingConfigs {
    create("release") {
        storeFile = file("../guardapp.jks")
        storePassword = "ваш_пароль"
        keyAlias = "guardapp"
        keyPassword = "ваш_пароль"
    }
}
# И в buildTypes.release добавьте:
# signingConfig = signingConfigs.getByName("release")

# 3. Соберите:
./gradlew assembleRelease
```

---

## Быстрая шпаргалка

```
Android Studio → File → Open → GuardAppLite
→ подождите Gradle Sync (5-10 мин)
→ Build → Build APK(s)
→ app/build/outputs/apk/debug/app-debug.apk
→ перенесите на телефон → установите
```
