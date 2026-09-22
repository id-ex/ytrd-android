# yTRD Android

Android-приложение для загрузки YouTube-видео с русской закадровой озвучкой
Yandex VOT. Самостоятельный Java runtime: Python, Chaquopy и Termux не используются.

## Сборка

- JDK 17;
- Android SDK Platform 35, Build Tools 34.0.0;
- Gradle Wrapper 8.9 (в репозитории), Android Gradle Plugin 8.7.3;
- minSdk 26, targetSdk 35; ABI: arm64-v8a, armeabi-v7a, x86_64.

Укажите SDK через `ANDROID_HOME` или `sdk.dir` в `local.properties`.
Ключ VOT задаётся переменной окружения `VOT_HMAC_KEY` либо одноимённым
параметром в `local.properties` (исключён из Git). Без ключа приложение
собирается, но перевод недоступен.

```bash
export JAVA_HOME=/путь/к/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Версия берётся из `version.properties`; правила — [VERSIONING.md](VERSIONING.md).

## Проверки (Quality Gate)

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 tools/check_lint.py
```

- **75 JVM-тестов** (координатор очереди, отмена, кодек VOT, парсер метаданных, сборщик FFmpeg, хранилище, мигратор истории, настройки, URL).
- Отчёты: `app/build/reports/`, XML результатов тестов: `app/build/test-results/`.
- `tools/check_lint.py` контролирует бюджет предупреждений lint (80 warnings, 0 errors).

## Архитектура

Исходники: `app/src/main/java/io/github/idex/ytrdroid/`:

- `domain/` — неизменяемые модели (`DownloadRequest`, `TaskSnapshot`, `MediaPlan`, `DownloadError`).
- `application/` — `DownloadCoordinator` (сериализованная FIFO-очередь, ровно один активный воркер), `CancellationToken`.
- `data/`
  - `persistence/` — Room 2.6.1 (`AppDatabase`, `TaskDao`, `ArtifactDao`, `LegacyHistoryMigrator`);
  - `storage/` — `WorkspaceManager` (изоляция рабочих файлов по задачам и запускам), `DestinationWriter` (защита от перезаписи, атомарное копирование);
  - `ytdlp/` — `RuntimeManager` (готовность и single-flight обновление), `YtDlpMetadataParser` (строгий Gson-парсинг);
  - `ffmpeg/` — `FfmpegCommandBuilder` (Mix duration=first, Dual languages, mov_text / srt);
  - `settings/` — `SettingsRepository` (единое хранилище настроек с миграцией);
  - `network/` — `NetworkPolicy` (проверка сети и Wi-Fi).
- `service/` — `DownloadService`: адаптер Foreground Service, действия уведомлений привязаны к конкретному `executionId`.
- `ui/` — экраны приложения (`MainActivity`, `DownloadsFragment`, `FilesFragment`, `ParamsFragment`, `SettingsActivity`).
- `util/` — `ThumbnailLoader` (LRU-кэш 8 МБ, даунсэмплинг, защита от гонок в списках), `UrlUtil`.
- `di/AppContainer` — процессный контейнер зависимостей.

Документация для агентов: [AGENTS.md](AGENTS.md).
План рефакторинга и журнал: [REFACTORING_PLAN.md](REFACTORING_PLAN.md).
Ревью исходного состояния: [REVIEW_ANDROID.md](REVIEW_ANDROID.md).
