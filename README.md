# Canasta Cerca — Android

Сейчас приложение — **оболочка над сайтом**: один WebView на весь экран открывает Canasta Cerca
(веб-проект `canasta-cerca`, тот же `server.js`). Всё, что умеет сайт, есть и в приложении, а новое на сайте
появляется в приложении само, без новой сборки.

Нативная версия (Compose, Metro, Navigation 3, шесть экранов) лежит в ветке `native`. Она отстаёт от сайта
(корзина на старых полях API) и ещё ни разу не собиралась.

## Запуск

1. В папке сайта: `npm run dev` (сервер на `http://localhost:3000`).
2. Открыть эту папку в Android Studio и запустить `app` (debug) на эмуляторе. Эмулятор видит компьютер как `10.0.2.2` —
   это адрес debug-сборки по умолчанию. Release-сборка по умолчанию открывает `https://canastacerca.com/`.

Ключ доступа и другой сервер задаются в `~/.gradle/gradle.properties`, **не** в `gradle.properties` проекта:
репозиторий публичный.

| Как | Что написать в `~/.gradle/gradle.properties` | Камера и «Mi ubicación» |
|---|---|---|
| Прод (сайт закрыт ключом, B80) | `canasta.accessKey=<k из ссылки>` | есть |
| Эмулятор или телефон по USB | `adb reverse tcp:3000 tcp:3000` и `canasta.baseUrl=http://localhost:3000/` | есть |
| Телефон в той же Wi-Fi | `HOST=0.0.0.0 npm start` и `canasta.baseUrl=http://<IP компьютера>:3000/` | нет (http) |
| Ссылка из `npm run share` | `canasta.baseUrl=https://….trycloudflare.com/` и `canasta.accessKey=<k из ссылки>` | есть |

`canasta.baseUrl` меняет адрес для обеих сборок. Камеру и геолокацию браузерный движок даёт сайту только по https и на
`localhost`. Если сайт не открылся, приложение показывает экран с Firu, и туда можно вставить новую ссылку целиком
(с `?k=…`) — пересобирать не нужно.

## Что приложение добавляет к сайту

- **Вид.** Строка состояния — синяя, как шапка сайта, полоса навигации — белая, как его нижнее меню. Пока сайт не
  нарисовал первый кадр — Firu на фоне сайта, а не белый экран.
- **«Назад»** — как в Chrome на телефоне: закрывает открытую панель сайта (адрес, оплата, сканер, покупка — `<dialog>`),
  потом листает историю сайта, с первой страницы — выход.
- **Мост `window.CanastaApp`** (`docs/specs/mobile.md` §3.2 в веб-проекте) — только на страницах нашего сайта
  (`WebViewCompat.addWebMessageListener` и `addDocumentStartJavaScript` с правилом по адресу):
  - `info()` → `{"platform":"android","version":"0.3.1+4","push":"off"}`: сайт знает, что он в приложении, — события
    идут с `pl: 'android'`, запросы — с `X-CC-Client: android/0.3.1+4 web/…`;
  - `share({text, url, image})` → системное меню «Поделиться», картинка «Compartir mi ahorro» уходит картинкой
    (файл в кэше, `FileProvider`). Ответ сайту — событием `canastaapp`.
  - `pushAsk`, `scan`, `openSettings` — в следующих версиях; без них сайт работает как в браузере. На старом WebView
    без нужных возможностей моста нет, сайт тоже работает как в браузере.
- **Хранение.** localStorage и cookie сохраняются между запусками: корзина, адрес, способы оплаты, покупки и ключ доступа.
- **Разрешения.** Камера — сканеру штрихкода, геолокация — «Mi ubicación»; системный запрос, только для нашего сайта.
  Вибрация — отклик сканера и таймер рецептов.
- **Чужие ссылки** (WhatsApp, «Cómo llegar», сайты сетей, `tel:`, `mailto:`) открываются в своих приложениях;
  `window.open` сайта — тоже.
- **Экран «не открылось»**: нет сети, сервер выключен, туннель сменился (5xx) или ключ неверный (401) — «Reintentar»
  и поле для новой ссылки. Тексты ES/RU по языку телефона.
- **User-Agent** — системный плюс `CanastaCercaAndroid/<версия>` (по системному сервер определяет `os`).
- **Отладка.** В debug-сборке сайт внутри приложения виден в Chrome на компьютере: `chrome://inspect`.

## Чего пока нет (T1)

Пуши (FCM, `POST_NOTIFICATIONS`), наши ссылки сразу в приложении (App Links, `assetlinks.json`), флаги и `minVersion`
из `/api/config`.

## Сборка для Google Play

1. **Ключ загрузки** — один раз, пароли вводите сами (не в git, копию ключа — в надёжное место):
   ```
   mkdir -p ~/canasta-keys && "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" -genkeypair -v \
     -keystore ~/canasta-keys/canasta-upload.jks -alias canasta-upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **`~/.gradle/gradle.properties`** (абсолютный путь, без `~`):
   ```
   canasta.accessKey=<ключ прода из ссылки ?k=…>
   canasta.upload.storeFile=/Users/<вы>/canasta-keys/canasta-upload.jks
   canasta.upload.storePassword=<пароль хранилища>
   canasta.upload.keyAlias=canasta-upload
   canasta.upload.keyPassword=<пароль ключа>
   ```
   Без `canasta.upload.*` релиз подписывается отладочным ключом: ставится на телефон, но Play его не примет.
3. **Сборка:**
   ```
   cd ~/StudioProjects/canasta-cerca-android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew bundleRelease
   ```
   Файл — `app/build/outputs/bundle/release/app-release.aab`. В Play Console: «Внутреннее тестирование» → новый выпуск →
   загрузить AAB (подпись приложения — Google, ключ загрузки — ваш). Каждая новая загрузка — `versionCode` на 1 больше
   (`app/build.gradle.kts`).

Ключ прода в сборке нужен, пока сайт закрыт (B80): с ним тестеры и проверка Google открывают сайт без ссылки.
После `PUBLIC=1` строку `canasta.accessKey` убрать.

## Проверка

- **Мост против настоящего сайта** — `tools/bridge_test.py`: берёт JS моста прямо из `MainActivity.kt` и прогоняет сайт
  в демо-режиме в Chrome с экраном телефона и без `navigator.share`, как в WebView. 15 проверок: `appInfo()`,
  `X-CC-Client`, `pl: 'android'`, «Compartir» текстом и картинкой, «назад» закрывает панель, `window.open` → WhatsApp,
  ширина страниц 390 px, ошибки JS. Запуск — в шапке файла.
- **Пробная сборка с сайтом с этого Mac** (то, что ещё не выложено) — двойной клик по `tools/test-build.command`:
  туннель Cloudflare из папки сайта (`scripts/share.js`), APK на эту ссылку, `canasta-test.apk` в Finder. Ставится
  поверх приложения из Play; работает, пока окно открыто. В Play не загружать.
- **На телефоне** (QA, `docs/specs/mobile.md` §6): первый запуск — Firu, потом сайт; строка состояния синяя;
  «назад» закрывает панели; «Escanear» — камера; «Mi ubicación»; «Compartir canasta» и «Compartir mi ahorro» — меню
  телефона; «Cómo llegar» — Карты; в режиме полёта — экран с Firu и «Reintentar».

## Сборка

Один модуль `:app`: AGP 9.2.1 (Kotlin встроенный), Gradle 9.6, compileSdk и targetSdk 36 (Play требует 36 для новых
приложений), minSdk 26; зависимости — `androidx.activity`, `androidx.core`, `androidx.webkit`, сторонних SDK нет.
Весь код — `app/src/main/kotlin/ar/canastacerca/MainActivity.kt`, экран — `app/src/main/res/layout/activity_main.xml`.
