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
`localhost`. Если сайт не открылся, **debug-сборка** показывает на экране с Firu поле, куда можно вставить новую ссылку
целиком (с `?k=…`) — пересобирать не нужно. В release этого поля нет (T4): адрес сайта — только из сборки.

## Что приложение добавляет к сайту

- **Вид.** Строка состояния — синяя, как шапка сайта, полоса навигации — белая, как его нижнее меню. Пока сайт не
  выполнил свои скрипты, — Firu на фоне сайта (с 0.3.4 — не по первому кадру: сохранённая сайтом главная до скриптов
  не нажимается; не дольше 8 с после первого кадра, `SPLASH_MAX_MS`). Под значком — «Canasta» (`launcher_name`): «Canasta
  Cerca» в лаунчерах режется; полное имя — у приложения (настройки телефона, Google Play).
- **«Назад»** — как в Chrome на телефоне: закрывает открытую панель сайта (адрес, оплата, сканер, покупка — `<dialog>`),
  потом листает историю сайта, с первой страницы — выход.
- **«Потянуть вниз — обновить»** (T3, `SitePull.kt`) — только когда страница в самом верху, нет открытой панели и палец
  не на карусели, карте, в поле ввода или в области со своей прокруткой. Решает сайт в момент касания (`PULL_OK` в
  `MainActivity.kt`): ответ приходит через несколько миллисекунд, до него касание идёт в сайт как обычно. С 0.3.4
  страница перезагружается под снимком экрана (`freshReload`, `PixelCopy`): заглушки и промежуточный вид сайта не
  мелькают, снимок уходит, когда сайт дорисовался (`SETTLED`: нет `*skel*` и `aria-busy`), но не позже 3 с после
  загрузки. «Растягивания» за край у WebView нет (`OVER_SCROLL_NEVER`) — оно спорило с жестом.
- **Мост `window.CanastaApp`** (`docs/specs/mobile.md` §3.2 в веб-проекте) — только на страницах нашего сайта
  (`WebViewCompat.addWebMessageListener` и `addDocumentStartJavaScript` с правилом по адресу):
  - `info()` → `{"platform":"android","version":"0.3.5+8","push":"off","fontScale":1}`: сайт знает, что он в приложении, —
    события идут с `pl: 'android'`, запросы — с `X-CC-Client: android/0.3.5+8 web/…`; `fontScale` (с 0.3.5, T5) — масштаб
    шрифта телефона, два знака: от 1,15 сайт один раз предлагает «Letra grande» (`docs/specs/letra-grande.md` §3);
  - `share({text, url, image})` → системное меню «Поделиться», картинка «Compartir mi ahorro» уходит картинкой
    (файл в кэше, `FileProvider`). Ответ сайту — событием `canastaapp`. Флаг `app.flags.share = false` в `/api/config` —
    `share` в мосте нет, сайт делится как в браузере.
  - `haptic(kind)` → лёгкий отклик касания (`performHapticFeedback`, без разрешения), без ответа. Сайт зовёт его при
    «Agregar» (W192). `kind` пока один — `'tap'`.
  - `pushAsk`, `scan`, `openSettings` — в следующих версиях; без них сайт работает как в браузере. На старом WebView
    без нужных возможностей моста нет, сайт тоже работает как в браузере.
- **Масштаб пальцами** (с 0.3.5, T5) — как в Chrome, без кнопок «+/−» (`builtInControls`, `displayZoomControls = false`):
  рассмотреть мелкое на товаре или чеке. Увеличенную страницу «потянуть» не обновляет. Ширина страницы — из её
  `meta viewport` (`useWideViewPort`, как в Chrome): так работает «Letra grande» сайта (W239). `textZoom = 100` — как было.
- **Хранение.** localStorage и cookie сохраняются между запусками: корзина, адрес, способы оплаты, покупки и ключ доступа.
- **Свежий сайт** (с 0.3.2). Android держит приложение в памяти, и значок возвращает ту же страницу хоть через сутки,
  а service worker сайта может показать сохранённую страницу. Поэтому приложение сверяет версию сайта с сервером:
  раз в 30 минут, пока открыто (`CHECK_EVERY_MS`), при возврате (если с прошлой сверки больше 5 минут) и после загрузки
  страницы. Версия — набор файлов `…js?v=…`/`…css?v=…` в HTML той же страницы с сервера против загруженных на экране
  (`SITE_ASSET`, `PAGE_ASSETS`). Есть новый — перезагрузка: в свёрнутом приложении — сразу (при сворачивании тоже
  сверяем), на экране — когда 30 с без касаний (`IDLE_MS`), нет открытой панели и курсора в поле, с кругом сверху; сразу
  после возврата — никогда (0.3.4, тестер 24.11: «открыла — подвисло»). Мимо кэша сайта: из Cache Storage убираются сохранённые страницы, файлы с версией и данные остаются
  (`FRESH_RELOAD`). «Потянуть вниз» обновляет так же. Корзина, адрес и оплата — в localStorage, не теряются.
- **Разрешения.** Камера — сканеру штрихкода, геолокация — «Mi ubicación»; системный запрос, только для нашего сайта —
  той же схемы, хоста и порта, что адрес из сборки (`BuildConfig.CANASTA_BASE_URL`). Вибрация (`VIBRATE`, без запроса) —
  `navigator.vibrate` сайта: отклик сканера и таймер рецептов.
- **Наши ссылки — сразу в приложении** (App Links, §3.5): `https://canastacerca.com/…` из WhatsApp или пуша открывается
  здесь, в том же окне (`singleTask`, `onNewIntent`). Если cookie с ключом ещё нет — к ссылке добавляется `?k=…`.
  Android подтверждает ссылки, когда сервер отдаёт `/.well-known/assetlinks.json` с отпечатком ключа из Play Console
  (B88); до того ссылки открываются как раньше (на Android 8–11 — с выбором приложения).
- **Флаги и обязательное обновление** (§3.6): при запуске — `GET /api/config` в фоне (`X-CC-Client: android/…`,
  cookie `cc_k`), из ответа берётся раздел `app` и запоминается. `app.minVersion.android` — число `versionCode`
  (или строка «0.4.0» — сравнение с `versionName`): версия ниже — экран «Actualizá la app» с кнопкой в Google Play.
  Нет раздела или сети — всё как раньше.
- **Чужие ссылки** (WhatsApp, «Cómo llegar», сайты сетей, `tel:`, `mailto:`) открываются в своих приложениях;
  `window.open` сайта — тоже.
- **Экран «не открылось»**: нет сети, сервер выключен, туннель сменился (5xx) или ключ неверный (401) — «Reintentar»
  (синяя кнопка, как на сайте). С 0.3.5 и когда сеть «висит» (соединение не обрывается, а ждёт): при запуске и после
  «Reintentar» первого кадра сайта нет 30 с (`FIRST_FRAME_MS`, W329 п. 5) — загрузка останавливается, вместо Firu этот
  экран. Код ошибки и поле для новой ссылки на сервер — только в debug. Тексты ES/RU по языку телефона.
- **Старый WebView** (с 0.3.5, ГД 25.11, W329 п. 3): сайту нужен WebView 105+ (`BuildConfig.MIN_WEBVIEW`: `?.`/`??` — с 80,
  `:has()` и `@container` — со 105). Ниже — сайт не открывается, вместо него экран «Actualizá Android System WebView»
  (имя — как у WebView в телефоне; на Android 8–9 это бывает Chrome) с кнопкой на его страницу в Google Play; в
  Crashlytics — раз за запуск. Живые телефоны обновляют WebView из Play сами; Android 8–9 застыл на 138 — им хватает.
  После обновления WebView система сама перезапускает приложение, и сайт открывается.
- **User-Agent** — системный плюс `CanastaCercaAndroid/<версия>` (по системному сервер определяет `os`).
- **Отладка.** В debug-сборке сайт внутри приложения виден в Chrome на компьютере: `chrome://inspect`.

## Чего пока нет (T1)

Пуши (FCM, `POST_NOTIFICATIONS`) — ждут проект Firebase (H11) и сервер (B88); `pushAsk`, `openSettings` в мосте —
вместе с ними. Подтверждение App Links и раздел `app` в `/api/config` — на сервере (B88): код приложения готов с 0.3.2.

## Firebase Crashlytics (отчёты о падениях)

Подключается файлом: есть `app/google-services.json` — Crashlytics в сборке, нет — Firebase в приложении нет вовсе
(сборка как раньше). Файл в `.gitignore`: репозиторий публичный. Плагины google-services и Crashlytics не используем
(плагин Crashlytics падает на AGP 9): `app/build.gradle.kts` сам кладёт в ресурсы то же, что они (`google_app_id` и др.,
build id Crashlytics). Отчёты шлёт только release; debug — нет. Кроме падений приложения, в Crashlytics уходит смерть
процесса сайта в WebView (упал или выгружен системой при нехватке памяти) — приложение при этом не падает, а открывает
экран заново, и зависание сайта: не отвечает на касания 5 с — ошибка с адресом страницы, 15 с при открытом приложении —
процесс сайта завершается и экран открывается заново (`watchRenderer`, не чаще раза в 2 минуты). Рекламного идентификатора (AD_ID) в манифесте нет при любых библиотеках (`tools:node="remove"`).

Один раз (ГД):
1. [console.firebase.google.com](https://console.firebase.google.com), вход canastacerca@gmail.com → «Создать проект»
   «Canasta Cerca». **Google Analytics — выключить** (не нужен Crashlytics; иначе рекламный id и ещё строка в анкете).
2. В проекте — значок Android: пакет `ar.canastacerca`, псевдоним «Canasta Cerca Android», SHA-1 не нужен →
   «Зарегистрировать» → скачать `google-services.json` → положить в `app/` этой папки (рядом с `build.gradle.kts`).
   Шаги консоли «Добавить Firebase SDK» пропустить — это уже в коде.
3. Слева «Crashlytics» → «Включить».
4. Сборка — `tools/build-play.command`; в логе строка «Crashlytics: есть». **До загрузки в Play** — анкета «Безопасность
   данных» с Crashlytics (`docs/specs/opiniones-y-privacidad.md` §7 в веб-проекте).

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

- **Эмулятор в GitHub Actions** (T7, `.github/workflows/android-emulator.yml`) — на каждый пуш и в каждом PR, без Mac:
  debug-сборка на Android 14 (412 dp; WebView 113) и Android 10 (WebView образа 74 — ниже минимума 105: там проверяется
  экран «Actualizá Android System WebView», проверки сайта пропускаются), сайт — демо-сервер из `canasteCercaWeb`
  (секрет `WEB_REPO_TOKEN`, только чтение). Тест оболочки `app/src/androidTest` (UiAutomator): запуск и «Saltar», «назад»
  закрывает окно сайта, ссылки `canastacerca.com` с нуля и в открытом приложении, без сети — «Reintentar», сервер
  принял соединение и молчит — через 30 с «Reintentar», «потянуть — обновить», `CanastaApp.info()` (с `fontScale`),
  шрифт системы 1,3 и главная без прокрутки вбок на 412 и 360 dp, старый WebView — экран обновления. Потом — смоук QA из `qa/android/`
  сайта (Q124, Playwright для Android), если он там есть, и рядом — `tools/bridge_test.py`. В артефактах прогона —
  снимки, видео, logcat, вывод тестов и лог демо-сервера. Красный прогон нарочно: Actions → «Android — эмулятор» →
  Run workflow → `break_check`. Локально — `tools/ci/demo-server.sh <папка сайта>`, эмулятор, `./gradlew
  installDebug installDebugAndroidTest` и `CC_OUT=… CC_APK_DIR=app/build/outputs/apk bash tools/ci/emulator-run.sh`.
- **Снимки для дизайнера** (D96, ГД 25.11) — в задании Android 14 после теста оболочки: `app/src/androidTest/…/DesignTest.kt`
  открывает 12 экранов сайта в приложении на 412 и 360 dp, снимает первый экран и после прокрутки и замеряет по правилам
  `design/specs` сайта (`assets/design-measure.js`: текст от 12 px, не больше 7 размеров, без 500 и 900, нажимаемое от
  44 px, обрезанный текст, закреплённое ≤ 40 % / 18 %). `tools/ci/design/report.js` — `index.html`, отметка «Дизайн» на
  странице прогона, таблица в сводке; артефакт `design-android14-412`. На Mac — `tools/ci/design-pull.command`.
- **Тест-кейсы QA P0/P1 в CI** (ГД 25.11) — в том же workflow, два задания. Кейсы — строки `qa/TEST-CASES.md` сайта
  с P0 или P1 (`tools/ci/qa/cases.js`), автотесты — те же, что у QA, по номеру кейса в имени. «UI» и «e2e» — внутри
  приложения на эмуляторе Android 14: `tools/ci/qa/android-preload.js` подменяет `launch`, `openPage` и `start` из
  `qa/helpers` (файлы QA не меняются) — страница теста открывается в WebView приложения с нуля, сервер — общий демо
  `http://10.0.2.2:3000`. «API» и «DATA» — без эмулятора (`tools/ci/qa/api.sh`). Итог — в сводке прогона: сколько
  прошло, что упало и какие кейсы без автотеста. Пока QA не отобрал кейсы для приложения (Q124), красными прогон не
  делают. Кейсы на базе дня (`snapshot`) в CI пропускаются: базы там нет.
- **Мост против настоящего сайта** — `tools/bridge_test.py`: берёт JS моста прямо из `MainActivity.kt` и прогоняет сайт
  в демо-режиме в Chrome с экраном телефона и без `navigator.share`, как в WebView. 29 проверок: `appInfo()`, `fontScale`,
  `X-CC-Client`, `pl: 'android'`, «Compartir» текстом и картинкой, `haptic()`, «назад» закрывает панель,
  «потянуть — обновить» (`PULL_OK`: наверху — да; с открытой панелью, не наверху, на карусели — нет),
  `window.open` → WhatsApp, ширина страниц 390 px, ошибки JS, сверка версии сайта (на 6 страницах без ложной
  перезагрузки, новый `?v=` заметен), перезагрузка ждёт закрытия панели, свежая перезагрузка чистит из кэша сайта
  только страницы, `SETTLED` (во время заглушек — нет, потом — да), флаг `share` выключен. Версию берёт из
  `app/build.gradle.kts`. Запуск — в шапке файла.
- **Пробная сборка с сайтом с этого Mac** (то, что ещё не выложено) — двойной клик по `tools/test-build.command`:
  туннель Cloudflare из папки сайта (`scripts/share.js`), APK на эту ссылку, `canasta-test.apk` в Finder. Ставится
  поверх приложения из Play; работает, пока окно открыто. В Play не загружать.
- **На телефоне** (QA, `docs/specs/mobile.md` §6): первый запуск — Firu, потом сайт; строка состояния синяя;
  «назад» закрывает панели; «Escanear» — камера; «Mi ubicación»; «Compartir canasta» и «Compartir mi ahorro» — меню
  телефона; «Cómo llegar» — Карты; в режиме полёта — экран с Firu и «Reintentar» (в release — без поля для ссылки);
  наверху страницы потянуть вниз — круг и перезагрузка, на карусели и с открытой панелью — нет; «Agregar» — лёгкий отклик;
  страница увеличивается двумя пальцами; с крупным шрифтом в настройках телефона (со второго открытия) — подсказка
  «Letra grande»; иконка — Firu без очков (обычная и тематическая на Android 13+).

## Сборка

Один модуль `:app`: AGP 9.2.1 (Kotlin встроенный), Gradle 9.6, compileSdk и targetSdk 36 (Play требует 36 для новых
приложений), minSdk 26; зависимости — `androidx.activity`, `androidx.core`, `androidx.webkit`,
`androidx.swiperefreshlayout` (с 0.3.2); иконка — `res/drawable/ic_launcher_foreground.xml` и
`ic_launcher_monochrome.xml` из `design/mascot/firu-icon.svg` сайта (Firu без очков, 0.3.5); Firebase Crashlytics — только с `app/google-services.json` (раздел выше). Код — `app/src/main/kotlin/ar/canastacerca/MainActivity.kt`
и `SitePull.kt` («потянуть — обновить»), экран — `app/src/main/res/layout/activity_main.xml`.
