# Canasta Cerca — Android

Android-клиент к веб-версии [Canasta Cerca](../../Downloads/canasta-cerca): скидки дня, каталог с ценами по сетям, рецепты из сегодняшних акций и корзина, которую приложение сравнивает по магазинам рядом с вашим адресом. Данные отдаёт тот же Node-сервер (`server.js`), приложение ходит в его `/api/*`.

## Стек

| Что | Чем |
|---|---|
| Язык | Kotlin 2.4.20, корутины и Flow |
| Сборка | AGP 9.5 (встроенный Kotlin, без `kotlin-android`), Gradle 9.6, convention-плагины в `build-logic`, version catalog |
| DI | [Metro](https://zacsweers.github.io/metro/) 1.5 — компиляторный плагин, без kapt/ksp; MetroX ViewModel и MetroX Android |
| UI | Jetpack Compose, Material 3, одна Activity |
| Навигация | Navigation 3 (1.2): back stack — это обычный список ключей, у каждой вкладки свой стек |
| Архитектура | MVVM, на каждом экране одно состояние `StateFlow<XxxUiState>` и `onAction(XxxAction)` |
| Сеть | Ktor 3 (OkHttp) + kotlinx.serialization, HttpCache (сервер отдаёт ETag) |
| Хранение | DataStore Preferences (корзина, адрес, способы оплаты) |
| Картинки | Coil 3 |

## Запуск

1. В папке веб-проекта: `npm run dev` (сервер на `http://localhost:3000`).
2. Открыть эту папку в Android Studio (нужна версия, которая поддерживает AGP 9.5; если Studio старее — понизьте `agp` в `gradle/libs.versions.toml`).
3. Запустить `app` на эмуляторе — он видит компьютер как `10.0.2.2`, это адрес по умолчанию.

Другие варианты — в `gradle.properties`:

- **телефон в той же Wi-Fi**: `HOST=0.0.0.0 npm start`, а в `canasta.baseUrl` — `http://<IP компьютера>:3000/`;
- **ссылка из `npm run share`**: `canasta.baseUrl=https://…trycloudflare.com/` и ключ из `?k=…` в `canasta.accessKey` (приложение передаёт его в cookie `cc_k`, как браузер). Обе строки — в `~/.gradle/gradle.properties`, а не в `gradle.properties` проекта: репозиторий публичный, ключ в него попасть не должен.

## Модули

```
app                      Application, граф Metro, MainActivity, корневой Compose (NavDisplay + нижняя панель)
build-logic/convention   canasta.android.application / library / compose, canasta.metro, canasta.serialization,
                         canasta.feature.api, canasta.feature.impl
core/
  common                 AppResult / AppError, язык интерфейса (es / ru), диспетчеры
  model                  сети (Jumbo, Día, Coto…), цены по сетям, ItemScope
  designsystem           тема (цвета веб-версии), форматирование песо, общие компоненты, иконки
  navigation             Navigator, NavigationState (стек на каждую вкладку), EntryProviderInstaller, TopLevelDestination
  network/api | impl     контракт сети (HttpClient, NetworkConfig, safeApiCall) | настроенный Ktor-клиент
  storage/api | impl     KeyValueStore | DataStore
feature/
  offers/api | impl      скидки сегодня: топ-100, фильтр по сети
  catalog/api | impl     разделы → товары подраздела, цены по сетям, сортировка, поиск
  recipes/api | impl     рецепты из скидок → блюдо: варианты по сетям, ингредиенты, «в корзину»
  basket/api | impl      корзина и сравнение по магазинам рядом (POST /api/basket)
  location/api | impl    выбор адреса (Georef / Nominatim через /api/geocode), радиус
  payments/api | impl    способы оплаты для скидок банков и кошельков (/api/promos)
```

Правило зависимостей: `impl` зависит только от `api` других фич, `api` ни от кого из фич не зависит. Например, корзина знает ключи `AddressPickerKey`, `PaymentsKey`, `CatalogKey` и интерфейсы `LocationRepository`, `PaymentsRepository`, но не их реализации. Собирает всё вместе только `:app`.

## Как устроено

**DI без ручной регистрации.** Каждая фича сама вносит в граф `AppScope` всё своё:

- репозитории — `@ContributesBinding`;
- ViewModel — `@ContributesIntoMap` + `@ViewModelKey`; экраны с аргументами (категория, блюдо) — через assisted injection: `@AssistedInject`, фабрика `ManualViewModelAssistedFactory`, на экране `assistedMetroViewModel<VM, VM.Factory> { create(key) }`;
- экраны — `EntryProviderInstaller` в `Set` через `@ContributesIntoSet`;
- вкладки нижней панели — `TopLevelDestination` в `Set` (корзина отдаёт ещё и счётчик для значка).

`AppGraph` в `:app` почти пуст: Metro находит вклады всех модулей на этапе компиляции. `MainActivity` создаёт сам граф (MetroX Android, `AppComponentFactory`), поэтому зависимости приходят в конструктор — отсюда `minSdk 28`.

**Навигация.** Ключи — `@Serializable` объекты и data-классы `NavKey` в api-модулях. `NavigationState` держит по `NavBackStack` на вкладку (переживает поворот и смерть процесса), у каждого стека свои декораторы `SaveableStateHolder` и `ViewModelStore`, так что при переключении вкладок не теряются ни ViewModel, ни прокрутка. Выход из приложения — через вкладку «Скидки», повторное нажатие на вкладку возвращает к её корню.

**Одно состояние на экран.** ViewModel собирает `UiState` через `combine` из своей части состояния и общих потоков (корзина, адрес, способы оплаты) и отдаёт `StateFlow`. Действия пользователя — `sealed interface XxxAction`. Разовые сообщения (снекбар) — тоже часть состояния: `UiMessage` с id, экран показывает его и отвечает `MessageShown`.

**Корзина считает сама.** Любое изменение товаров, адреса, радиуса или способов оплаты пересчитывает сравнение (с паузой 600 мс, прошлый результат виден приглушённо). Если сервер ещё грузит цены каких-то сетей (`CHAIN_PENDING`), запрос повторяется, как в веб-версии.

**Списки, которые сервер собирает в фоне** (скидки дня, рецепты, раздел «Всё»): пока `building: true`, репозиторий повторяет запрос каждые 4–5 секунд и показывает прогресс.

## Что дальше

- Проверить первую сборку и прогнать на эмуляторе с `npm run dev`.
- Адрес по GPS (`/api/reverse`), карта магазинов.
- Unit-тесты ViewModel (диспетчеры уже приходят через DI — `AppDispatchers`) и маппинга DTO на JSON из `.cache/`.
- Глубокие ссылки `canastacerca://ofertas/coto`, `/recetas/milanesas` через `DeepLinkMatcher` Navigation 3 — адреса уже совпадают с веб-версией.
