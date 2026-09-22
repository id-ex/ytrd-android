# План реализации рефакторинга yTRD Android

**Основание:** [REVIEW_ANDROID.md](REVIEW_ANDROID.md).  
**Статус:** предложен; реализация не начата.  
**Область:** Android-приложение `android-app/`; Python CLI не переписываем.  
**Подход:** последовательная замена компонентов с тестами и работающей сборкой после каждого шага.

## 1. Цель и ограничения

Получить загрузчик, в котором:

- очередь, пауза, отмена и повтор работают предсказуемо;
- ошибки сети, остановка процесса и нехватка места не повреждают уже сохранённые файлы;
- выбранная папка, настройки и уведомления соответствуют реальному выполнению;
- результат содержит ожидаемые дорожки, субтитры и контейнер;
- состояние сохраняется и восстанавливается;
- UI не управляет процессами/файлами/сетевыми запросами напрямую;
- код разделён на небольшие проверяемые компоненты с понятными контрактами для разработчиков и агентов.

**Не включать в этот рефакторинг:** переход на Kotlin/Compose, редизайн, несколько одновременных загрузок, новые источники видео, облачную синхронизацию, собственный DI-framework, разбиение на множество Gradle-модулей. Сначала надёжность существующих возможностей.

Сохраняем Java 17, XML Views, один app-модуль и существующие yt-dlp/FFmpeg-библиотеки. Новые зависимости добавляем только под конкретную задачу: тесты, lifecycle, persistence, image loading.

## 2. Решения, которые нужно зафиксировать до реализации

Ниже — рекомендуемые defaults, а не уже реализованное поведение.

| Вопрос | Предлагаемое решение |
|---|---|
| Параллельность | Один активный execution; очередь сериализована |
| Пауза задачи | Задача остаётся PAUSED до явного resume; другие QUEUED могут выполняться после фактической остановки её worker |
| Пауза всей очереди | Не вводить отдельную функцию сейчас; убрать двусмысленный глобальный `isPaused` |
| Resume | Продолжить с последнего подтверждённого этапа. Частичную HTTP-загрузку сначала перекачивать; не обещать byte-resume |
| Пауза FFmpeg | Остановить процесс и при resume пересобрать результат из проверенных входов |
| Отмена | Дождаться остановки execution, убрать его временные файлы; уже опубликованные результаты не удалять |
| Совпадение имён | По умолчанию сохранять отдельную версию; никогда молча не перезаписывать |
| Смерть процесса | Восстановить очередь и пометить бывшую активную задачу INTERRUPTED; продолжать по явной команде пользователя, без обхода фоновых ограничений Android |
| Хранилище | Private workspace → проверка → публикация в MediaStore Downloads на API 29+ либо выбранный через SAF каталог |
| API 26–28 | Для публичного назначения использовать SAF, а не запрашивать широкие права без необходимости |
| Только Wi-Fi | Ограничивать связанные с загрузкой анализ, VOT, медиа, сетевые превью и обновление runtime; смена сети останавливает/откладывает операции по общей политике |
| Изменение настроек | Формат/качество/голос фиксируются в request при enqueue; ограничение сети действует актуальное, включая уже активные задачи |
| Обновление yt-dlp | Убрать безусловное nightly-обновление при старте. Явное управляемое обновление, выбранный канал и отсутствие активных executions |
| История | Одна запись на опубликованный артефакт, а не одна на URL; самостоятельный автоматический импорт каталога отключить |
| Неподдерживаемый перевод | Объяснять ограничение; не скачивать оригинал вместо перевода без согласия |

Отдельно согласовать container/codec policy: предпочтительно MP4 с совместимыми кодеками до 1080p, MKV для более высоких качеств/несовместимых комбинаций. Не считать разрешение единственным критерием; UI показывает фактически выбранный `MediaPlan`.

## 3. Безопасная подготовка

Перед изменениями кода предложить пользователю резервную ветку и согласовать фиксацию исходного состояния.

**Важно:** во время ревью весь `android-app/` был untracked. Создание ветки само по себе не сохранит эти файлы. Нужен согласованный baseline commit либо отдельная резервная копия исходников. Не включать build-каталоги, local.properties, cookies, ключи подписи и другие секреты.

Без отдельного запроса не выполнять `git add`, `commit`, `push` и создание веток. Планирование не является разрешением на эти операции.

Родительский `.gitignore` исключает `*.md`, кроме README/AGENTS. В рамках подготовки предложить точечные исключения для отчёта, плана и документации, иначе они не попадут в VCS.

## 4. Целевая структура

```text
io.github.idex.ytrdroid/
├── App.java
├── application/
│   ├── DownloadCoordinator
│   ├── DownloadPipeline
│   ├── AnalyzeVideoUseCase
│   └── TaskRecovery
├── domain/
│   ├── model/
│   │   ├── DownloadRequest
│   │   ├── TaskSnapshot
│   │   ├── DownloadResult
│   │   ├── MediaPlan
│   │   └── DownloadError
│   ├── queue/QueueStateMachine
│   └── port/                       # контракты внешних операций
├── data/
│   ├── ytdlp/                      # VideoSource + RuntimeManager
│   ├── vot/                        # ProtocolCodec + HTTP provider
│   ├── ffmpeg/                     # command builder + process runner
│   ├── storage/                    # workspace + destination writers
│   ├── persistence/                # Room, repositories, migration
│   ├── settings/
│   └── network/
├── service/                        # Android lifecycle + notifications
├── ui/                             # views, ViewModels, adapters
└── di/AppContainer                 # ручная сборка зависимостей
```

Правила зависимостей:

- `domain` не зависит от Android, OkHttp, Room или UI.
- `application` управляет сценариями через ports; не показывает Toast и не формирует notification.
- `data` реализует ports и содержит интеграции с библиотеками/платформой.
- `service` обеспечивает разрешённый Android lifecycle исполнения, но не дублирует scheduler.
- `ui` отправляет команды и наблюдает immutable snapshots.
- Пакеты создаём по мере переноса компонентов, а не массовым перемещением файлов заранее.

## 5. Порядок реализации

```text
0. Baseline и тестовый каркас
            ↓
1. Модели, контракты, типизированные ошибки
            ↓
2. Очередь, execution ownership, отмена + foreground lifecycle
            ↓
3. Runtime, VOT/HTTP, media plan и FFmpeg
            ↓
4. Устойчивое хранение задач и миграция истории
            ↓
5. Workspace, безопасная публикация и crash recovery
            ↓
6. Настройки и общая сетевая политика
            ↓
7. ViewModels, reactive UI, preview performance
            ↓
8. Удаление старого кода, документация, release-проверки
```

Это последовательные этапы интеграции. Изолированные codec/URL/FFmpeg tests можно писать параллельно после фиксации контрактов. Одновременное изменение старого `DownloadEngine`, `DownloadService` и storage разными агентами без согласованного интерфейса не допускается.

Каждый этап разбивается на небольшие reviewable-порции. Завершение этапа означает не просто создание классов, а подключение нового пути выполнения и прохождение критериев приёмки.

---

## Этап 0. Зафиксировать baseline и поставить проверки

**Закрывает:** отсутствие Android quality gate, часть R26/S03.

### Задачи

- [ ] Проверить текущее рабочее дерево и наличие изменений после ревью.
- [ ] Согласовать и создать резервную копию/baseline; учитывать untracked-файлы.
- [ ] Зафиксировать JDK 17, Android SDK, версии wrapper/AGP и build-команды.
- [ ] Добавить JVM-тесты (JUnit), HTTP fixtures (MockWebServer), Android test runner; Robolectric — только если требуется для конкретных lifecycle-тестов.
- [ ] Перенести воспроизводящие проверки из `/tmp/ytrd-review-checks/` в поддерживаемые tests; если временные файлы исчезли — восстановить по отчёту.
- [ ] Добавить Android CI: unit tests, assembleDebug, lintDebug. Python job сохранить.
- [ ] Зафиксировать существующие lint warnings; запрещать новые errors и рост согласованных категорий warnings, не скрывать всё suppression-ами.
- [ ] Переписать вводную Android README: никакого Chaquopy bridge в текущем runtime нет.

### Проверки и готовность

- Сборка и тестовый каркас запускаются на JDK 17 локально и в CI после включения исходников в VCS.
- Установлено ненулевое число реальных JVM tests; NO-SOURCE не считается успехом тестирования.
- Есть воспроизводимые regression cases для R01/R02/R11/R12/R15/R24. Их можно вводить парой «тест + исправление» в соответствующем шаге; основная интеграционная ветка не должна надолго оставаться красной.

---

## Этап 1. Разделить запрос, состояние, результат и ошибки

**Закрывает:** архитектурную причину ряда R01–R25.

### Задачи

- [ ] Ввести immutable `DownloadRequest`: video identity, качество, тип результата, перевод, голос, режим звука, subtitles policy, destination.
- [ ] Заменить строки `mix/dual/audio/mp3/auto` на enum/value objects с явными fallback-правилами.
- [ ] Ввести `TaskSnapshot`, отдельно progress текущего этапа и итоговое состояние.
- [ ] Ввести `DownloadResult`: artifact UUID, published URI/reference, фактические параметры и метаданные.
- [ ] Развести `destination` и `result`; удалить двойной смысл `outputPath` через временный compatibility mapper.
- [ ] Ввести `DownloadError`: category, stage, recoverability, безопасное user message, diagnostic cause.
- [ ] Определить ports только для реально выделяемых компонентов: VideoSource, TranslationProvider, MediaProcessor, repositories, DestinationWriter, RuntimeManager, NetworkPolicy.
- [ ] Создать `AppContainer` с constructor injection; зафиксировать scope общих ресурсов.

### Проверки и готовность

- Нельзя создать request с несовместимыми параметрами без явной ValidationError.
- Новые domain-классы тестируются без Android runtime.
- UI не может изменять опубликованный snapshot.
- `DownloadTask` временно остаётся только на legacy-границе, а не становится второй независимой моделью состояния.

---

## Этап 2. Исправить очередь, отмену и сопровождение службы

**Закрывает:** R01, R02, R04, R09, существенную часть R10.

### Задачи

- [ ] Ввести чистый `QueueStateMachine` и `DownloadCoordinator`, обрабатывающий commands/events в одном serial executor.
- [ ] Оставить отдельный bounded worker executor для блокирующего pipeline; worker не меняет очередь.
- [ ] Использовать UUID задачи и новый `executionId` при каждом retry/resume.
- [ ] Ввести execution-owned `CancellationToken` и локальные handles HTTP/process/yt-dlp.
- [ ] Добавить промежуточные PAUSING/CANCELLING; следующий worker стартует только после termination acknowledgement.
- [ ] Отбрасывать events старого execution и запретить PAUSED/CANCELLED → DONE.
- [ ] Разделить запрос остановки и подтверждение остановки; очистить handles в finally конкретного execution.
- [ ] Если worker не удалось остановить за deadline: не запускать следующий поверх него; показать recoverable stopping error, сохранить возможность диагностики.
- [ ] Подключить старый pipeline через адаптер: он возвращает result/events, но больше не владеет task.state.
- [ ] Объединить enqueue/retry/resume в один Android start path: foreground до начала работы, согласованные stopForeground/stopSelf.
- [ ] Сделать уведомления производными от snapshot; действия привязать к task/execution, чтобы устаревшая кнопка не отменила следующую задачу.
- [ ] Обработать onDestroy, target-API timeout и отсутствие разрешения уведомлений без ложного обещания бессмертного фонового процесса.
- [ ] Убрать общие `current*` handles из singleton engine и глобальный `isPaused`.

### Проверки и готовность

Детерминированные tests с fake worker/clock, без `Thread.sleep` как способа синхронизации:

- A → cancel A → start B → позднее завершение A не запускает C.
- Пауза единственной задачи не вызывает повторный start.
- Несколько задач: paused пропускается до resume.
- Отмена на VOT, HTTP, yt-dlp, FFmpeg, publication не приводит к DONE.
- `maxActiveExecutions == 1` для набора команд pause/resume/retry/cancel.
- Retry после последней ошибки действительно возвращает foreground execution.

**Граница этапа:** только coordinator владеет состояниями и очередью; service выполняет lifecycle-функцию. Полное восстановление после смерти процесса добавляется после persistence.

---

## Этап 3. Разобрать DownloadEngine на проверяемые интеграции

**Закрывает:** R11–R17, R24–R25, части S01/S02/S04.

### 3.1. Runtime и metadata

- [ ] `RuntimeManager`: shared readiness future/state, init error для UI, single-flight update.
- [ ] Запретить обновление во время активных runtime operations; version/channel писать в диагностику.
- [ ] Убрать обновление nightly на любую metadata error и при каждом старте App.
- [ ] `YtDlpVideoSource`: metadata/download/cancel за одним адаптером.
- [ ] Использовать только корректный Gson parsing с валидацией; удалить ручной JSON fallback.
- [ ] `YoutubeUrlParser`: разбор host/path/query и canonical ID; тесты ложных доменов и суффиксов ID.

### 3.2. VOT и HTTP

- [ ] Выделить `VotProtocolCodec` без HTTP/Android.
- [ ] Проверять длины, varint termination/overflow, wire types, schema expectations; ограничить response size до выделения памяти.
- [ ] Использовать общий настроенный OkHttpClient, request-owned Call и cancellation token.
- [ ] Разделить Waiting polling, transient retry и permanent failure; добавить deadline, Retry-After, bounded backoff/jitter.
- [ ] Оформить capability языка/длительности; неизвестное значение не превращать молча в подтверждённое `en/341`.
- [ ] Редактировать URL/token-like параметры в логах.

### 3.3. Media plan и FFmpeg

- [ ] `MediaPlan` определяет контейнер, кодеки, качество, аудио и subtitles mapping; UI и pipeline используют один план.
- [ ] Выделить чистый `FfmpegCommandBuilder` и отдельный `FfmpegRunner`.
- [ ] Исправить subtitle mapping и убрать повторное/двусмысленное embedding.
- [ ] Сохранять весь оригинальный звук в Mix при коротком переводе; согласовать normalization и уровни.
- [ ] Правильные language metadata; явная обработка отсутствующей audio stream.
- [ ] Убрать unlimited quality fallback без разрешения пользователя.
- [ ] Получать реальный выходной путь yt-dlp и валидировать фактическое медиа через ffprobe.
- [ ] Сохранять ограниченный stderr tail и классифицировать exit errors.
- [ ] Изолировать знания о native library paths в runtime adapter.

### Проверки и готовность

- Malformed protobuf не создаёт неконтролируемых allocation/loops и даёт ProtocolError.
- HTTP tests: 429/5xx/403, truncation, cancel, missing body/length, timeout.
- Fixture media tests: Mix/Dual, короткий/длинный перевод, ru/en subtitles, no-audio input, 1080p/4K/container policy.
- `ffprobe` подтверждает ожидаемые потоки, языки и длительности.
- Android smoke-test проверяет реальные bundled binaries; Linux FFmpeg tests его не заменяют.

---

## Этап 4. Добавить persistence и мигрировать историю

**Закрывает:** R19, R20, основу R10/R21.

### Задачи

- [ ] Подключить Room с Java annotation processor; все запросы/миграции выполнять вне main thread.
- [ ] Ввести entities: Task, Artifact/Result, Checkpoint, PublicationAttempt, при необходимости отдельные settings migration markers.
- [ ] Сохранять request, state, execution generation и ссылки на workspace; не сохранять signed audio URL как долговечный источник истины.
- [ ] Progress persistence throttling: не делать транзакцию на каждый процент/HTTP callback.
- [ ] Перед стартом execution сохранять намерение/состояние; persistence failure не игнорировать.
- [ ] Перенести старый history.json однократной идемпотентной миграцией; сохранить исходный JSON до подтверждённого успеха.
- [ ] Каждому legacy artifact присвоить новый UUID; валидировать null/неполные записи и недоступные пути.
- [ ] Не удалять legacy media и не пытаться автоматически угадывать URL по title.
- [ ] Старые raw file paths моделировать отдельно от новых content URI, разрешая пользователю восстановить доступ через SAF.
- [ ] Удаление из истории сохранять как скрытие/удаление записи без автоматического re-import.
- [ ] Разделить результат скачивания и ошибку его индексации; не скачивать заново уже опубликованный артефакт из-за ошибки БД.

### Проверки и готовность

- Миграция дважды не создаёт дубли и не теряет исходную историю.
- Повреждённый JSON и null entries дают контролируемое предупреждение, не crash.
- Аудио и видео одной ссылки видны как отдельные artifacts.
- Persistence operations не блокируют UI.
- Schema migrations и unique constraints покрыты tests.

---

## Этап 5. Сделать рабочие файлы, публикацию и recovery безопасными

**Закрывает:** R03, R05, R06, R18, завершает R10/R20.

### Задачи

- [ ] `WorkspaceManager`: `filesDir/work/<task UUID>/`, а не disposable cacheDir; отдельные ресурсы для retry и execution.
- [ ] Workspaces не включать в backup. Пересмотреть backup истории/URI grants: восстановление backup не гарантирует восстановление разрешений.
- [ ] Использовать `.part` и checkpoint manifest с identity/config/schema version.
- [ ] Complete marker устанавливать только после успешного завершения/валидации; `length > 1000` удалить.
- [ ] Отдельно хранить image cache; «Очистить кэш» не затрагивает workspaces активных/paused задач.
- [ ] `DestinationWriter`: prepare → copy → verify → publish/abort; результатом является URI, а не предполагаемый путь.
- [ ] MediaStore на API 29+: pending entry, запись, финализация, rollback по ошибке.
- [ ] SAF: persistable permission, проверка revoked access, аккуратная cleanup-политика неполных документов.
- [ ] Не обещать атомарный rename для произвольного SAF provider: поведение зависит от provider; временный документ и реестр PublicationAttempt обязательны там, где возможно.
- [ ] Уникальные имена с video ID/вариантом и suffix при коллизии; не молча overwrite.
- [ ] Перед записью проверять доступность назначения, оценивать место без обещания точного размера; всегда обрабатывать disk-full в ходе операции.
- [ ] Реализовать `TaskRecovery`: сверять сохранённую state machine с workspace и PublicationAttempt после запуска приложения.
- [ ] На старте сначала reconciliation, только затем разрешать cleanup или повтор публикации.
- [ ] Удаление результата проверяет provider outcome; история не сообщает «файл удалён» при отказе.

### Протокол публикации и сбоя

Room и MediaStore/SAF **не образуют общую транзакцию**. Нужен журнал намерений:

1. Сохранить PublicationAttempt с artifact ID и выбранным назначением.
2. Создать pending/temporary output и записать известную ссылку в журнал.
3. Скопировать проверенный файл, закрыть поток, проверить результат доступным способом.
4. Финализировать документ и записать artifact/state в БД.
5. Удалять workspace только после согласованной фиксации.

Сбой возможен между любыми шагами, включая создание документа до записи его URI в БД. Для каждого provider определить возможности поиска/идентификации leftovers. Если автоматическое сопоставление невозможно, не удалять неизвестные файлы и не публиковать повторно вслепую: показать восстановление/подтверждение пользователю.

### Проверки и готовность

- Обрыв после 2 КБ не даёт cache hit или DONE.
- Остаток workspace видео A никогда не читается задачей B.
- Не перезаписываются два ролика с одинаковым title и разные варианты одного ролика.
- Выбранная папка действительно используется; открытие результата работает через content URI.
- Process-death fault injection на каждой границе publication не приводит к молчаливой потере/дублированию готового результата.
- API 26–28 SAF; API 29+ MediaStore/SAF; permission revoked, disk full, cancel во время копирования.
- После рестарта бывший active execution — INTERRUPTED, а не ложный DONE; продолжение требует валидных checkpoints и разрешённого lifecycle.

---

## Этап 6. Подключить реальные настройки и сетевую политику

**Закрывает:** R07, часть R14/R16/S02.

### Задачи

- [ ] `SettingsRepository` — единственное чтение/запись preference keys с defaults и migration.
- [ ] Применить default quality, translate, voice, audio mode, тему, destination.
- [ ] Качество по умолчанию выбирать из доступных по явно заданному правилу, а не первым максимальным элементом.
- [ ] `NetworkPolicy`: наблюдение capabilities и состояния сети; Wi-Fi не путать с просто unmetered-сетью.
- [ ] При запрете сети не оставлять блокирующее ожидание внутри занятого worker; scheduler переводит работу в WAITING_FOR_NETWORK с сохранением checkpoint.
- [ ] Учитывать пользовательскую PAUSED отдельно от автоматического ожидания сети.
- [ ] Если Android не разрешает продолжение в фоне после возвращения сети, ждать разрешённого старта/действия пользователя, а не запускать FGS безусловно.
- [ ] Применять policy ко всем сетевым адаптерам, включая thumbnails/update; UI объясняет, почему анализ ещё не идёт.
- [ ] Реализовать тему через AppCompat; убрать принудительные цвета, блокирующие выбранную тему.

### Проверки и готовность

- Каждый видимый setting имеет тест чтения и фактического применения.
- При Wi-Fi only и мобильной сети не начинается ни один новый запрещённый сетевой запрос.
- Переключение Wi-Fi → mobile останавливает/откладывает операции по контракту; уже переданные до сигнала байты не выдаются за гарантированно предотвращённые.
- Settings изменений формата не мутируют request уже поставленной задачи; network restrictions применяются оперативно.

---

## Этап 7. Перевести UI на наблюдаемое состояние и устранить нагрузку превью

**Закрывает:** R08, R21–R23, UI-часть R25/R27.

### Задачи

- [ ] ViewModels для Params, Downloads, Files, Settings; LiveData и SavedStateHandle для Java/XML стека.
- [ ] Фрагменты только отображают UiState и отправляют команды; убрать HTTP, создание Threads, прямое удаление файлов и runtime update из UI.
- [ ] Отменяемый metadata analysis с debounce и request generation; сохранить ввод/параметры при recreation.
- [ ] Видимые validation errors и Retry; запуск загрузки явно недоступен, пока backend не ready.
- [ ] Подписать Files на ResultRepository, чтобы завершение сразу отражалось в открытом списке.
- [ ] ListAdapter/DiffUtil и payload updates прогресса, stable task/artifact identity.
- [ ] Подключить один lifecycle-aware image loader, например Glide, с сетью через настроенный клиент/policy.
- [ ] Downsampling, cache по URI/version, memory budget в байтах, bounded thumbnail extraction.
- [ ] Identity/lifecycle guards для всех результатов; закрытие MediaMetadataRetriever в finally/try-with-resources по доступному API.
- [ ] Ошибки представлены кратким пользовательским объяснением и доступной подробной диагностикой без секретов.
- [ ] Ограничить хранение завершённых task snapshots в памяти; историю брать из repository.

### Проверки и готовность

- Повторные progress events не инициируют новый fetch одного thumbnail; retries/eviction учитываются отдельно.
- Быстрый scroll не показывает чужое превью, rotation не создаёт второй активный анализ.
- После закрытия view нет обращений к старым widgets или requireContext в stale callback.
- Увеличение очереди не порождает неограниченные threads/bitmap allocations.
- Результат загрузки появляется в Files без ручного повторного открытия экрана.

---

## Этап 8. Удалить legacy-путь и подготовить поддерживаемый релиз

**Закрывает:** R26/R27, оставшиеся S01–S04.

### Задачи

- [ ] Удалить compatibility mapper, старый DownloadTask, старую orchestration-логику DownloadEngine и статический HistoryManager после миграции callers.
- [ ] Удалить неактивный DownloadFragment/navigation path, pendingUrl и неиспользуемые ресурсы после проверки references.
- [ ] String/plural resources, Locale.ROOT для протокола, UI locale для отображения, общие formatters/dimens/theme tokens.
- [ ] Touch targets, TalkBack, большие шрифты, landscape, insets/keyboard на целевых API.
- [ ] Исправить density/adaptive icons и проверить расход памяти графики.
- [ ] Обновить README, добавить AGENTS и документы architecture/storage/testing; указать threading и cancellation contracts.
- [ ] Зафиксировать runtime update policy, безопасную диагностику, backup/data-extraction policy.
- [ ] Проверить release signing process без ключей в репозитории, ABI splits/AAB по выбранному каналу распространения.
- [ ] Добавить wrapper checksum/dependency verification по согласованной политике.
- [ ] Собрать список third-party компонентов, лицензий и bundled runtime versions; отдельно проверить FFmpeg build obligations.
- [ ] Выполнить полный regression run и ручные Android smoke/E2E; оформить release checklist и известные ограничения.

### Проверки и готовность

- В проекте один путь выполнения каждой операции; нет одновременно живых old/new schedulers.
- Agent по README/AGENTS находит точки входа, state machine, storage и команды проверки без обращения к устаревшему CLI-описанию.
- Tests/lint/build/release smoke проходят; остаточные warnings рассмотрены, а не массово подавлены.
- Native runtime работает на поддержанных ABI; 16KB-page-size проверен на соответствующем устройстве/эмуляторе, а не только readelf.

## 6. Правила интеграции и отката

1. Сначала контракт и tests, затем адаптер, затем переключение callers, затем удаление legacy.
2. Не запускать old/new pipelines одновременно на одном request/workspace.
3. На переходе допускается один composition-level переключатель реализации; не разбрасывать feature flags по всем Fragment.
4. Изменения БД — versioned и additive, где возможно. `fallbackToDestructiveMigration` для пользовательских данных не использовать.
5. Downgrade APK после schema migration не считать безопасным автоматически: baseline backup и схема совместимости обязательны.
6. Не удалять old history/workspaces до успешной миграции/reconciliation; не удалять пользовательские output-файлы при откате кода.
7. После каждого шага: tests, assembleDebug, lint; для lifecycle/storage/native шагов — профильный Android smoke.
8. Исправления подтверждённых багов отделять от форматирования/массового перемещения файлов, чтобы review оставался понятным.
9. Каждую выполненную порцию отмечать в плане: дата, изменённые компоненты, команды/результаты, оставшиеся ограничения.

## 7. Минимальный quality gate

Рабочий каталог: `android-app/`. Использовать JDK 17 и настроенный Android SDK.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

# Для подключённого тестового устройства/эмулятора:
./gradlew :app:connectedDebugAndroidTest
```

Дополнительно запускать media fixture tests по документированной команде. Python CI остаётся независимым; запуск pytest нужен при затрагивании общих файлов/CLI, но не является заменой Android-проверок.

Обязательный регрессионный набор перед релизом:

- очередь и cancel/pause/retry на каждой стадии;
- задержанные callbacks старого execution;
- частичный download, сеть пропала, 429/5xx, отказ VOT;
- правильные потоки и длительности Mix/Dual/subtitles;
- одинаковые title/URL с разными режимами;
- выбор назначения, revoked grants, disk full;
- crash/process death до/во время/после публикации;
- миграция истории, повторное удаление, недоступный legacy file;
- foreground restart/timeout, отказ notification permission;
- rotation/back/scroll/повторный анализ;
- соблюдение Wi-Fi policy всеми сетевыми компонентами.

## 8. Контрольные точки

| Точка | После этапа | Что должно быть доказано |
|---|---|---|
| M0: воспроизводимый baseline | 0 | Исходники сохранены, проверки запускаются, реальные tests существуют |
| M1: управляемое выполнение | 2 | Один worker, корректные pause/cancel/retry и foreground lifecycle |
| M2: корректные интеграции | 3 | Bounded VOT parsing, runtime readiness, правильные media streams |
| M3: целостность и восстановление | 5 | Persistence, безопасные файлы/URI, recovery без молчаливого overwrite/дублирования |
| M4: честный и отзывчивый UI | 7 | Настройки применяются, previews bounded, lifecycle-safe отображение |
| M5: готовность к выпуску | 8 | Legacy удалён, документация актуальна, device/release matrix пройдена |

**При нехватке времени сокращать косметику и новые возможности, а не проверки M1–M3.** Не выпускать частично переделанную storage/queue систему как стабильную.

## 9. Рекомендуемый первый рабочий заход

Начать не с большого перемещения классов, а с небольшой проверяемой поставки:

1. Согласовать baseline backup и семантику pause/resume.
2. Поднять unit tests и добавить fake execution с управляемым завершением.
3. Ввести TaskId/ExecutionId и минимальную state machine.
4. Исправить R01/R02/R04: paused не стартует, старый callback не освобождает чужой слот, отмена не даёт DONE.
5. Объединить lifecycle-path для enqueue/retry/resume.
6. Прогнать scheduler tests и Android smoke, затем переходить к декомпозиции pipeline.

Это даёт первый измеримый результат и устраняет архитектурную гонку, которая иначе будет мешать безопасно внедрять все последующие изменения.
