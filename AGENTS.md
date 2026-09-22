# AGENTS.md — Документация для AI-агентов (yTRD Android)

Этот файл предназначен для coding-агентов, работающих с Android-приложением в каталоге `android-app/`.

---

## 1. Стек и окружение

- **Платформа**: Android (minSdk 26, targetSdk 35, compileSdk 35).
- **Язык**: Java 17 (без Kotlin, без Compose).
- **Сборка**: Gradle 8.9 Wrapper, Android Gradle Plugin 8.7.3.
- **Архитектура**:
  - `domain/` — immutable модели (`DownloadRequest`, `TaskSnapshot`, `MediaPlan`, `DownloadError`). Без Android SDK.
  - `application/` — `DownloadCoordinator`, `CancellationToken`. Однопоточный планировщик очереди, ownership исполнения (UUID `executionId`).
  - `data/` — `persistence` (Room 2.6.1: `AppDatabase`, `TaskDao`, `ArtifactDao`), `storage` (`WorkspaceManager`, `DestinationWriter`), `ytdlp` (`RuntimeManager`, `YtDlpMetadataParser`), `ffmpeg` (`FfmpegCommandBuilder`), `settings` (`SettingsRepository`), `network` (`NetworkPolicy`).
  - `service/` — `DownloadService`: адаптер жизненного цикла Android (Foreground Service). Не владеет состоянием очереди.
  - `ui/` — Java/XML интерфейс (`MainActivity`, `DownloadsFragment`, `FilesFragment`, `ParamsFragment`, `SettingsActivity`).
  - `di/AppContainer` — процессный контейнер зависимостей.

---

## 2. Ключевые контракты

### Очередь и многопоточность
1. **Ровно 1 активный execution**: `DownloadCoordinator` сериализует все команды через однопоточный serial executor (FIFO).
2. **Execution ownership**: каждый запуск, повтор (retry) или возобновление (resume) получает уникальный `executionId` (UUID).
3. **Отмена и пауза**:
   - Задача сначала переходит в `PAUSING` или `CANCELLING`.
   - Следующий воркер **не запускается**, пока текущий execution не подтвердит остановку (`destroyForcibly`, отмена HTTP, прерывание потока).
   - Поздние события старого execution отбрасываются и не могут изменить состояние новой задачи.
4. **Хранилище**:
   - Временные файлы изолированы в `filesDir/work/<taskId>/<executionId>/` (`WorkspaceManager`). Не использовать `cacheDir` для рабочих файлов.
   - Завершённость этапов проверяется маркерами (`.audio_complete`, `.video_complete`), а не эвристикой размера.
   - Публикация в `DestinationWriter`: автоматическое разрешение коллизий с суффиксом ` (1)`, ` (2)`. Атомарная запись через `.part` с проверкой размера.

---

## 3. Версионирование

- Единственный источник версии приложения: `version.properties`.
- Правила версионирования описаны в `VERSIONING.md`.
- Формат: SemVer `MAJOR.MINOR.PATCH` и возрастающий целочисленный `VERSION_CODE`.
- Коммиты оформлять на русском языке в формате Conventional Commits.

---

## 4. Команды проверки (Quality Gate)

```bash
# Тесты, debug-сборка и lint
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

# Проверка бюджета предупреждений lint (не допускает ошибок и роста предупреждений)
python3 tools/check_lint.py
```

Перед предложением коммита убедиться, что:
1. Все JVM-тесты проходят (0 failures, 0 errors).
2. `tools/check_lint.py` сообщает `Lint gate passed`.
3. `git diff --check` не выдаёт замечаний по пробелам и форматированию.
