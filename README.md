# yTRD Android

Самостоятельное Android-приложение для загрузки YouTube-видео и получения русской закадровой озвучки через Yandex Voice-Over Translation (VOT). Это отдельный Android-проект: он не требует и не является оболочкой Python CLI `ytrd`.

Проект находится в alpha/beta-разработке; готовность и стабильность функций не гарантируются. Планируется alpha-релиз на GitHub, но на данный момент он ещё не опубликован.

## Требования для сборки

- JDK 17;
- Android SDK Platform 35 и Build Tools 34.0.0;
- Gradle Wrapper 8.9, Android Gradle Plugin 8.7.3;
- minSdk 26, targetSdk 35.

Укажите путь к Android SDK через `ANDROID_HOME` либо `sdk.dir` в `local.properties`.

```bash
export JAVA_HOME=/путь/к/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
```

APK сборки: `app/build/outputs/apk/`. Поддерживаемые ABI: `arm64-v8a` и `armeabi-v7a`. Gradle создаёт `app-arm64-v8a-*.apk`, `app-armeabi-v7a-*.apk` и `app-universal-*.apk` (в GitHub Releases последний переименовывается в `universal-arm`). В универсальном APK нет x86-библиотек. Сборка требует интернет-доступа для первоначального получения зависимостей и Android SDK.

Версия берётся из `version.properties`; правила — [VERSIONING.md](VERSIONING.md).

## VOT и ключ сборки

Для доступности VOT ключ `VOT_HMAC_KEY` должен быть задан **сборщиком** через окружение или локальное свойство `local.properties`. Пользователю предварительно собранного APK вводить ключ не нужно. Если ключ при сборке не задан, перевод в этом APK недоступен.

Ключ включается в `BuildConfig` и может быть извлечён из публично распространяемого APK. Это не секретное хранилище: не используйте в релизах ключ, который должен оставаться конфиденциальным, и учитывайте, что распространение APK раскрывает встроенное значение. Не записывайте ключи и другие секреты в Git; локальные конфигурации, ключи подписи и секреты должны оставаться вне репозитория и игнорироваться `.gitignore`.

## Подписи и тестовый alpha-релиз

Android принимает обновление только при совпадении сертификата подписи и `applicationId`. Debug APK подписывается локальным debug-ключом; публичные alpha APK должны подписываться **одним постоянным release-ключом**. Уже установленный debug APK нельзя обновить release APK поверх него: перед удалением сохраните нужные файлы и настройки, так как локальные данные приложения могут быть стёрты. Все следующие alpha-версии следует подписывать тем же release-ключом; его потеря потребует переустановки приложения у тестировщиков.

Для локальной подписанной сборки поместите `key.properties` рядом с `version.properties` (файл игнорируется Git):

```properties
storeFile=/абсолютный/путь/к/release-signing.p12
storePassword=<локальный пароль>
keyAlias=ytrd-release
keyPassword=<локальный пароль>
```

Без `key.properties` Gradle собирает **неподписанный** release APK, непригодный для публикации. Ключ подписи и этот файл нужно хранить вне репозитория и резервировать в безопасном месте. Проверка, именование и контрольные суммы трёх подписанных APK:

```bash
./gradlew :app:assembleRelease
python3 tools/prepare_release.py --output-dir /путь/к/пустой/каталогу/release \
  --apksigner /путь/к/Android/Sdk/build-tools/35.0.0/apksigner
```

Скрипт отклоняет неподписанные APK и неверный состав ABI. Он **не** создаёт тег, не загружает файлы и не публикует релиз.

## Проверки качества

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 tools/check_lint.py
```

Проверки включают JVM unit-тесты, debug-сборку, Android lint и отдельную проверку установленного бюджета предупреждений lint. Количество тестов намеренно не фиксируется. Отчёты Gradle находятся в `app/build/reports/`, результаты тестов — в `app/build/test-results/`.

## Архитектура

Исходники: `app/src/main/java/io/github/idex/ytrdroid/`:

- `domain/` — модели запросов, задач и ошибок;
- `application/` — сериализованный координатор очереди и отмена;
- `data/` — Room persistence, рабочие файлы, публикация результатов, yt-dlp, FFmpeg, настройки и сетевые политики;
- `service/` — Android Foreground Service;
- `ui/` — Java/XML интерфейс;
- `di/AppContainer` — контейнер зависимостей.

Лицензия: [MIT](LICENSE).
