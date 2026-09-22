# Ytrd Droid

> Историческое описание MVP ниже устарело: текущий runtime использует Java,
> youtubedl-android и FFmpeg, а не Chaquopy. Актуализация — в этапе 0 плана.

## Версия и локальная конфигурация

Версия Android-приложения хранится в `version.properties`.
Правила выпусков: [VERSIONING.md](VERSIONING.md).

Для сборки нужны JDK 17 и Android SDK; Gradle Wrapper включён в репозиторий.
Ключ VOT передаётся через переменную окружения `VOT_HMAC_KEY` либо строку
`VOT_HMAC_KEY=ваш_ключ` в локальном `local.properties` (исключён из Git).
Переменная окружения имеет приоритет. Без ключа APK собирается, но перевод
недоступен. Ключ включается в APK и может быть извлечён из него; эта настройка
лишь исключает его хранение в Git. Не публикуйте сгенерированные BuildConfig.

```bash
JAVA_HOME=/путь/к/jdk17 ./gradlew assembleDebug
```

Минимальный Android GUI для `ytrd` без Termux.

## Как работает

Приложение встраивает Python через [Chaquopy](https://chaquo.com/chaquopy/) и кладёт пакет `ytrd` внутрь APK:

```text
app/src/main/python/ytrd/
app/src/main/python/ytrd_android_bridge.py
```

Поток:

1. пользователь вставляет ссылку YouTube;
2. выбирает режим, качество, субтитры и Live Voice;
3. приложение формирует CLI-аргументы `ytrd`;
4. Java вызывает Python bridge;
5. bridge запускает `ytrd.main.entry_point()` с аргументами как у CLI.

## Сборка APK

Нужны Android SDK и Gradle/Android Studio.

```bash
cd android-app
./gradlew assembleDebug
```

В этом черновике Gradle Wrapper пока не добавлен, поэтому можно открыть папку `android-app` в Android Studio — она сама предложит синхронизировать проект.

## Текущее состояние MVP

Есть:

- встроенный Python через Chaquopy;
- пакет `ytrd` внутри APK;
- поле для ссылки;
- диалог настроек скачивания;
- выбор режима: Dual, Mix, Audio, Auto/original;
- выбор качества;
- флаги subtitles, Live Voice, quiet;
- карточка запущенной загрузки;
- приём ссылки через Android Share;
- запуск `ytrd` внутри APK без Termux.

## Важное ограничение

`ytrd` использует внешний бинарник `ffmpeg`. Python уже встроен, но для полноценного скачивания/склейки видео нужно ещё добавить Android-совместимый FFmpeg:

- либо положить `ffmpeg` binary для ABI `arm64-v8a`/`armeabi-v7a`/`x86_64` в APK и указывать его путь;
- либо подключить FFmpeg-библиотеку/обёртку;
- либо для первого этапа поддержать только те операции, где FFmpeg не нужен.

Также нужно отдельно доработать сохранение в публичную папку Downloads через Android Storage Access Framework. Сейчас bridge передаёт `--output` во внешний каталог приложения (`Android/data/.../files`).

## Следующие задачи

1. Добавить Gradle Wrapper и проверить сборку.
2. Подключить/упаковать FFmpeg для Android.
3. Сделать анализ видео перед скачиванием (`--check`/информация/доступные качества).
4. Переделать `ytrd` на callback API для нормального прогресса в карточках.
5. Добавить выбор папки сохранения через SAF.
