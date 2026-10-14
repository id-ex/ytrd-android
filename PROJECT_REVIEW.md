# Комплексный аудит проекта yTRD Android (v0.5.0)

**Дата проверки:** 13 октября 2026 года  
**Объект:** `android-app/` (`io.github.idex.ytrdroid`, пакет `com.idex.ytrd`)  
**Версия:** `0.5.0` (сборка `versionCode = 11`)  
**Git HEAD:** `f44a50d` (ветка `master`)  
**Методика проверки:** Статический и динамический анализ исходного кода, запуск unit-тестов, проверка линтера (`check_lint.py`), аудит соблюдения контрактов `AGENTS.md` и сопоставление с первичным аудитом `REVIEW_ANDROID.md` от 22.09.2026.

---

## 1. Резюме и общая оценка

За период с 22 сентября по 13 октября 2026 года проект проделал масштабную эволюцию:
- **Объём кода:** вырос с 15 файлов (2 973 строки) до **53 Java-файлов (7 922 строки)**.
- **Тестовое покрытие:** с 0 тестов выросло до **107 JVM unit-тестов в 22 тестовых наборах** (100% passing).
- **Архитектурный фундамент:** внедрены слои `domain/`, `application/`, `data/`, процессный DI-контейнер `AppContainer`, единый планировщик очереди `DownloadCoordinator` с владением `executionId` и токенами отмены `CancellationToken`, база данных SQLite через Room 2.6.1, работа с Scoped Storage через SAF (`TreePublisher`).
- **Функциональность:** интегрирован NewPipeExtractor (ускорение анализа до ~1 сек), WebView-авторизация YouTube с экспортом Netscape cookies, жестовое управление и пакетное удаление в `FilesFragment` с кастомным аниматором `SlideRightItemAnimator`, фоновое автообновление через GitHub Releases API.

### Качественная оценка в сравнении с аудитом v0.1.0 (22.09.2026)

| Критерий | Было (v0.1.0, 22.09.2026) | Стало (v0.5.0, 13.10.2026) | Комментарий |
|---|---|---|---|
| **Понятность структуры** | Средняя | **Высокая** | Чёткое разделение пакетов (`domain`, `application`, `data`, `service`, `ui`), соблюдение Java 17 |
| **Архитектура** | Ниже средней | **Выше средней** | Строгий контракт очереди `DownloadCoordinator`, однако сохраняется мост `LegacyDownloadPipeline` |
| **Расширяемость** | Низкая–средняя | **Хорошая** | Появление доменных моделей и DAO упростило добавление фич без ломки пайплайна |
| **Читаемость** | Средняя | **Высокая** | Код хорошо структурирован, строгие инварианты, отсутствие магии |
| **Отказоустойчивость** | Низкая | **Средняя–Хорошая** | Устранены гонки воркеров и оверлей миниатюр, но выявлены утечки диска и I/O спам |
| **Тестируемость** | Низкая (0 тестов) | **Высокая (107 тестов)** | Отличная изоляция бизнес-логики и утилит; не хватает тестов UI и Room DAO |
| **Готовность к релизу** | Недостаточная | **Бета / Предрелиз** | Требуется устранение 2 дефектов P1 (утечка диска и баг ранней постановки) |

---

## 2. Статус Quality Gate

1. **Unit-тесты (JVM):**  
   `./gradlew :app:testDebugUnitTest --rerun-tasks` — **107 тестов успешно** (0 failures, 0 errors, 0 skipped). Время выполнения: ~1.5 мин.
2. **Lint Gate:**  
   `python3 tools/check_lint.py` — **Passed**. 0 ошибок, 78 предупреждений (в пределах установленного лимита).
3. **Форматирование Git:**  
   `git diff --check` — замечаний нет, переводы строк и пробелы корректны.

---

## 3. Анализ устранения замечаний предыдущего аудита (REVIEW_ANDROID.md)

| Замечание из первого аудита | Статус в v0.5.0 | Как решено в кодовой базе |
|---|---|---|
| Пауза перезапускала задачу, гонки при отмене | **Исправлено** | `DownloadCoordinator` с `CancellationToken`, явными состояниями `PAUSING`/`CANCELLING` и блокировкой слота до завершения потока |
| Неатомарная публикация и перезапись файлов | **Исправлено** | `DestinationWriter` проверяет коллизии суффиксами ` (1)`, копирует через `.part` и сверяет размер; `TreePublisher` безопасно пишет в SAF DocumentTree |
| Утечка памяти при загрузке обложек | **Исправлено** | Создан `ThumbnailLoader` с LruCache 8 МБ, даунсэмплингом до 256×144 и защитой от рециклинга View через теги |
| Обрыв звука при микшировании FFmpeg | **Исправлено** | `FfmpegCommandBuilder` использует `duration=first`, сохраняет длительность видео и корректно встраивает субтитры |
| Неприменяемость настроек («только Wi-Fi», папка) | **Исправлено** | Внедрены `SettingsRepository` и `NetworkPolicy`, проверяющие тип соединения перед запуском |
| Отсутствие сохранения истории | **Исправлено** | Room 2.6.1 (`ArtifactDao`, `ArtifactEntity`) с фоновой миграцией старого `history.json` |

---

## 4. Детальные находки и дефекты текущей версии (v0.5.0)

### 4.1. Дефекты категории P1 (Критические — исправить до стабильного релиза)

#### [P1-1] Утечка дискового пространства в рабочей директории (`filesDir/work`)
- **Компоненты:** `io.github.idex.ytrdroid.engine.DownloadEngine`, `io.github.idex.ytrdroid.data.storage.WorkspaceManager`.
- **Суть проблемы:**
  В `WorkspaceManager` файлы разделены на уровень задачи (`baseDir = filesDir/work/<taskId>`) и уровень исполнения (`executionDir = baseDir/<executionId>`).
  Промежуточные видеофайлы (`temp_video.mp4`, `temp_video.mkv`) и аудио перевода (`vot_audio.mp3`) сохраняются в `baseDir`.
  В `DownloadEngine.java` (строки 191, 338, 364) после успешного скачивания или сборки вызывается:
  ```java
  workspace.cleanupExecution();
  ```
  Метод `cleanupExecution()` удаляет только подкаталог `executionDir`, который практически пуст!
  Сам метод `workspace.cleanupTask()`, удаляющий `baseDir` целиком вместе с гигабайтными `temp_video.mp4`, **нигде в кодовой базе не вызывается**. Ни после публикации файла, ни при удалении задачи из списка, ни при старте приложения.
  После копирования в целевую папку исходный `tempVideo` не удаляется (`tempVideo.delete()` отсутствует).
  Более того, кнопка «Очистить кэш» в `SettingsActivity` удаляет только `context.getCacheDir()`, не затрагивая `filesDir/work`.
- **Последствия:** Каждое загруженное видео дублируется во внутренней памяти приложения навсегда. 5–10 скачанных видео могут полностью забить внутреннюю память устройства.
- **Рекомендация:**
  1. Вызывать `workspace.cleanupTask()` сразу после успешной публикации файла в `DownloadEngine`.
  2. Удалять временный `tempVideo` сразу после `DestinationWriter.copyAndVerify`.
  3. В `DownloadCoordinator.remove()` инициировать удаление папки задачи `workspace.cleanupTask()`.
  4. При старте `AppContainer` или в кнопке очистки кэша удалять устаревшие каталоги в `filesDir/work/`.

---

#### [P1-2] Логический баг «ранней постановки в очередь» (Early Enqueue Analysis Bypass)
- **Компоненты:** `io.github.idex.ytrdroid.ui.ParamsFragment`, `io.github.idex.ytrdroid.application.DownloadCoordinator`, `io.github.idex.ytrdroid.engine.LegacyDownloadPipeline`.
- **Суть проблемы:**
  В коммите `f3fae66` была добавлена мгновенная загрузка превью через oEmbed (`OEmbedClient.fetch()`), которая устанавливает поле `previewTitle`.
  Если пользователь нажимает кнопку «Скачать» до завершения полного анализа NewPipe/yt-dlp (`info == null`), в строке 484 `ParamsFragment.java` создаётся задача:
  ```java
  task.title = previewTitle; // НЕ null!
  task.duration = 0;
  ```
  Далее задача передаётся в координатор, где проверка необходимости фонового анализа очереди сформулирована так:
  - `DownloadCoordinator.java:159`:
    ```java
    task.request.title == null && task.request.duration <= 0 ? ANALYZING : ...
    ```
  - `LegacyDownloadPipeline.java:85`:
    ```java
    if (request.title == null && request.duration <= 0) {
        // Запуск fetchInfo, определение форматов, длительности, языка
    }
    ```
  Поскольку `request.title` уже не `null` (заполнено из `previewTitle`), выражение `request.title == null && ...` **всегда ложно** (`false`)!
- **Последствия:**
  Фоновый анализ видео в очереди полностью пропускается:
  - Задача не переходит в статус `ANALYZING`.
  - Метод `engine.fetchInfo()` не вызывается.
  - Длительность `duration` остаётся `0.0` (что ломает тайминги VOT и прогресс-бар сборки ffmpeg).
  - Язык `language` принудительно захардкожен как `"en"`. Если видео на русском языке — приложение всё равно пытается запросить озвучку через VOT, получая ошибку или зависание.
- **Рекомендация:**
  Маркером неполного анализа должна быть длительность (`request.duration <= 0`) либо явный флаг в `DownloadRequest`, а не равенство `title == null`.
  Изменить условие в `DownloadCoordinator` и `LegacyDownloadPipeline` на:
  ```java
  if (request.duration <= 0) { ... }
  ```

---

### 4.2. Дефекты категории P2 (Существенные — технический долг и оптимизация)

#### [P2-1] База данных Room `TaskEntity` является Write-Only раковиной и вызывает I/O спам
- **Компоненты:** `io.github.idex.ytrdroid.di.AppContainer`, `io.github.idex.ytrdroid.data.persistence.TaskRepository`.
- **Суть проблемы:**
  1. **I/O спам:** При скачивании yt-dlp выводит строки прогресса с высокой частотой (до 20–50 раз в секунду). Каждая строка прогресса через колбэк `onSnapshots` в `AppContainer` вызывает:
     ```java
     for (TaskSnapshot s : snapshots) {
         tasks.save(s, null); // dao.upsert(entity)
     }
     ```
     Это генерирует непрерывный поток SQLite-транзакций, нагревая процессор и расходуя ресурс накопителя.
  2. **Очередь не восстанавливается:** Несмотря на непрерывную запись, `TaskRepository.getAll()` **нигде в коде не вызывается**. При перезапуске приложения или уничтожении процесса `DownloadCoordinator` инициализируется с пустой картой задач. Пользователь видит пустой экран, а незавершённые задачи (даже помеченные `markActiveAsInterrupted`) невозможно просмотреть или перезапустить.
- **Рекомендация:**
  1. Ввести троттлинг сохранения прогресса в БД (сохранять прогресс в Room не чаще 1 раза в 1–2 секунды, а также обязательно при смене `State`).
  2. Реализовать загрузку сохранённых незавершённых задач из Room при старте `AppContainer` для отображения в `DownloadsFragment`.

---

#### [P2-2] Удаление активной задачи из очереди не удаляет её из списка (`DownloadCoordinator.remove`)
- **Компонент:** `io.github.idex.ytrdroid.application.DownloadCoordinator`.
- **Суть проблемы:**
  В методе `remove(UUID taskId)` (строки 131–135):
  ```java
  if (active != null && active.taskId.equals(taskId)) {
      cancel(taskId);
      return;
  }
  tasks.remove(taskId);
  ```
  Если задача активна, вызывается `cancel()`. Но когда воркер подтверждает остановку, метод `finish()` переводит её в статус `CANCELLED` и оставляет в `tasks`. Отложенного удаления задачи из `tasks` не происходит.
- **Последствия:** Пользователь нажимает «Удалить» для текущей загрузки, ожидая, что она исчезнет из списка, но она навсегда остаётся в списке с пометкой «Отменено».
- **Рекомендация:** Ввести флаг или очередь `pendingRemoval`, чтобы после завершения отмены воркера задача автоматически удалялась из `tasks`.

---

#### [P2-3] Постоянное пересоздание `new OkHttpClient()` в `DownloadEngine`
- **Компонент:** `io.github.idex.ytrdroid.engine.DownloadEngine`.
- **Суть проблемы:**
  В строках 548 и 618 на каждый вызов `downloadFileWithProgress` и `downloadFile` создаётся новый экземпляр `new OkHttpClient()`.
- **Последствия:** Каждый экземпляр порождает собственный пул соединений и диспетчер потоков, которые не переиспользуются.
- **Рекомендация:** Использовать единый разделяемый экземпляр `OkHttpClient` из `AppContainer` или синглтон-клиент с настроенными таймаутами.

---

#### [P2-4] Синхронные блокирующие I/O операции на UI-потоке в `SettingsActivity`
- **Компонент:** `io.github.idex.ytrdroid.ui.SettingsActivity`.
- **Суть проблемы:**
  В строках 312–325 очистка кэша `deleteRecursively(cacheDir)` выполняется синхронно прямо в `OnPreferenceClickListener` на UI-потоке.
- **Последствия:** Если в кэше накопились сотни мегабайт или тысячи файлов от yt-dlp, это приводит к пропуску кадров (jank) или диалогу ANR ("Application Not Responding").
- **Рекомендация:** Выносить обход и удаление файлов кэша в фоновый поток (например, через `Executors` или `AsyncTask`/`CompletableFuture`).

---

#### [P2-5] Прерывание UX при запросе системного разрешения на установку APK
- **Компонент:** `io.github.idex.ytrdroid.data.updater.AppUpdateInstaller`.
- **Суть проблемы:**
  Если у приложения нет разрешения `canRequestPackageInstalls()`, открывается системный экран `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`. После того как пользователь даёт разрешение и возвращается по кнопке «Назад», установка не продолжается автоматически.
- **Рекомендация:** Использовать `ActivityResultLauncher` для отслеживания возврата из настроек установки неизвестных приложений и автоматического вызова `installApk`.

---

### 4.3. Замечания категории P3 (Локальные улучшения и кодстайл)

1. **Неиспользуемый `bindService` в `MainActivity`:**
   В `MainActivity` инициализируется `ServiceConnection` и вызывается `bindService(..., DownloadService.class)`. Поле `downloadService` сохраняется, но нигде в коде не используется (все экраны обращаются к `app.container().downloads`). Рекомендуется удалить лишний биндинг, оставив запуск сервиса только через `startForegroundService` при наличии активных задач.
2. **Создание неуправляемых `new Thread()` в UI-компонентах:**
   В `ParamsFragment` (3 параллельных потока на каждый ввод символа) и `FilesFragment` (`new Thread("batch-delete")`). Рекомендуется использовать единый пул потоков из `AppContainer`.
3. **Предупреждения Android Lint (78 шт.):**
   - 32 `HardcodedText`: строковые литералы в XML-макетах без выноса в `strings.xml`.
   - 9 `DefaultLocale`: вызовы `String.format()` и `toLowerCase()` без указания `Locale.ROOT` или `Locale.getDefault()`.
   - 5 `Overdraw`: дублирующиеся фоны в корневых и дочерних контейнерах.
   - `android:extractNativeLibs="true"` в `AndroidManifest.xml` (устаревший флаг, не рекомендуемый AGP).
4. **Тестовые пробелы:**
   Отсутствуют тесты на Room DAO (in-memory SQLite), `NewPipeMetadataExtractor`, `TreePublisher`, а также компонентные UI-тесты на базе Robolectric.

---

## 5. Архитектурная диаграмма текущего состояния

```mermaid
flowchart TD
    subgraph UI ["Слой представления (UI)"]
        MA[MainActivity]
        DF[DownloadsFragment]
        FF[FilesFragment]
        PF[ParamsFragment]
        SA[SettingsActivity]
        YLA[YouTubeLoginActivity]
    end

    subgraph AppState ["DI & Координация"]
        AC[AppContainer]
        DC[DownloadCoordinator]
        DS[DownloadService (Foreground)]
    end

    subgraph CoreEngine ["Движок исполнения"]
        LDP[LegacyDownloadPipeline]
        DE[DownloadEngine]
        NPE[NewPipeMetadataExtractor]
        VOT[VotClient]
        YTDL[YoutubeDL / Python]
        FFM[FfmpegCommandBuilder / FFmpeg]
    end

    subgraph StorageData ["Хранилище и БД"]
        WM[WorkspaceManager (filesDir/work)]
        DW[DestinationWriter / TreePublisher]
        ROOM[(Room DB: TaskEntity, ArtifactEntity)]
        PREF[SettingsRepository]
    end

    MA -.->|bindService (не используется)| DS
    MA --> PF
    PF -->|early download| DS
    DS -->|enqueue / setExecutionEnabled| DC
    DF -->|observe / pause / retry / remove| DC
    FF -->|getAllVisible / deleteAll| ROOM

    DC -->|workers executor| LDP
    LDP --> DE
    DE --> NPE
    DE --> VOT
    DE --> YTDL
    DE --> FFM

    DE -->|запись временных файлов| WM
    DE -->|публикация готового файла| DW
    DE -->|сохранение истории| ROOM

    DC -.->|I/O спам: 50 upsert/сек| ROOM
```

---

## 6. Приоритетный план действий (Action Plan)

### Этап 1. Неотложные исправления стабильности (P1)
1. **Устранить утечку диска в `WorkspaceManager` / `DownloadEngine`:**
   - Добавить вызов `workspace.cleanupTask()` после публикации файла в `DownloadEngine` (строки 191, 338, 364).
   - Удалять локальный `tempVideo` сразу после успешного `copyAndVerify`.
   - Добавить очистку рабочей папки задачи при вызове `coordinator.remove()`.
   - В кнопку «Очистить кэш» в `SettingsActivity` включить очистку папки `filesDir/work`.
2. **Исправить проверку ранней постановки задачи:**
   - В `DownloadCoordinator.java:159` и `LegacyDownloadPipeline.java:85` заменить условие `request.title == null && request.duration <= 0` на `request.duration <= 0`.
   - Убедиться, что при постановке с известным `previewTitle` фоновый анализ очереди выполняется штатно.

### Этап 2. Оптимизация производительности и очередей (P2)
3. **Оптимизировать сохранение в Room:**
   - Добавить троттлинг записи прогресса в SQLite (не чаще 1 раза в секунду при `DOWNLOADING`).
   - Реализовать загрузку списка прерванных задач из Room при старте приложения.
4. **Исправить отложенное удаление активных задач в `DownloadCoordinator`:**
   - При `remove(activeTaskId)` помечать задачу как `pendingRemoval` и гарантированно удалять её из мапы при получении события остановки воркера.
5. **Централизовать `OkHttpClient`:**
   - Заменить `new OkHttpClient()` в `DownloadEngine` на разделяемый экземпляр.
6. **Вынести очистку кэша в фоновый поток:**
   - В `SettingsActivity` обернуть `deleteRecursively` в фоновый executor.

### Этап 3. Качество и чистота кода (P3)
7. **Удалить неиспользуемый `bindService` в `MainActivity`.**
8. **Устранить основные предупреждения Lint (32 `HardcodedText`, `android:extractNativeLibs`).**
9. **Добавить unit-тесты на Room DAO и NewPipeExtractor.**
