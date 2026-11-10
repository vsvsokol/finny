# Финни в браузере (web/)

Веб-версия — та же игра, что на Android, собранная Compose Multiplatform под
**Kotlin/Wasm** (`wasmJs`). Своего UI и игровой логики в `web/` нет: модуль при
сборке копирует исходники `app/src/main/java` и заменяет только Android-части
своими файлами из `src/wasmJsMain/kotlin`.

## Сборка и запуск

Нужен JDK 17+ (как для `app/`). Node.js, Yarn и Binaryen Gradle скачает сам
(репозитории — в `settings.gradle.kts`).

```bash
# дев-сервер с пересборкой при правках: http://localhost:8080
./gradlew :web:wasmJsBrowserDevelopmentRun --continuous

# готовая папка для любого статического хостинга
./gradlew :web:wasmJsBrowserDistribution        # → web/build/dist/wasmJs/productionExecutable
./gradlew :web:wasmJsBrowserDevelopmentExecutableDistribution   # то же без оптимизации, быстрее
```

Нужен браузер с WebAssembly GC (Firefox 120+, Chrome 119+, Safari 18.2+) и WebGL.

### Флаги адреса (для отладки)

| Флаг | Что делает |
|---|---|
| `?reset=1` | стереть всё сохранённое Финни в этом браузере |
| `?layout=compact\|medium\|expanded` | принудительный режим экрана (иначе — по размеру окна) |
| `?debug=1` | отладочная панель, как в debug-сборке Android (в локальной сборке включена и так) |

Демо-профиль со всеми открытыми играми — ссылка «Режим проверки (демо)» на последнем
экране знакомства.

## Как устроено

| Где | Что |
|---|---|
| `build.gradle.kts` | `syncAppSources` копирует `app/src/main/java` без файлов из списка `androidOnly`; `syncAppResources` + `generateWebR` превращают `res/` в ресурсы Compose (`R.drawable.*` работает как на Android) |
| `src/wasmJsMain/kotlin/shims/` | заглушки Android API, которые встречаются в общем коде (аннотации, `SystemClock`, `BackHandler`, `R`-ресурсы) |
| `src/wasmJsMain/kotlin/ru/finney/pet/AppContainer.kt` | ручной DI веба: какие реализации хранилищ взять |
| `.../web/WebGameStorage.kt` | сохранение игры (см. ниже) |
| `.../web/WebPrefs.kt` | открытый профиль и настройки звука |
| `.../web/Browser.kt` | `localStorage` (ключи с префиксом `finny.`) и флаги адреса |
| `.../web/main.kt` | точка входа: загрузка контента, выбор режима экрана |

Правило: **новый общий код пишется в `app/`**, а не в `web/`. Если в нём понадобилось
Android-API — выносим за интерфейс или в `ui/platform/Platform.kt` (у веба своя копия
этого файла) и добавляем Android-файл в `androidOnly`.

### Режимы экрана

`app/.../ui/layout/WindowLayout.kt`: телефон (`COMPACT`) — как на Android; планшет
(`MEDIUM`) — портретная колонка по центру; компьютер (`EXPANDED`) — комната на сцене,
экраны (план, магазин, копилка…) выезжают панелью справа (`navigation/WideShell.kt`).
В широком режиме по бокам сцены продолжаются обои и пол комнаты (`ui/room/RoomSides.kt`),
а у мини-игр фон тянется до краёв окна, игра остаётся колонкой (`SceneStage`).

---

## Передача: профили питомцев на сервере

Сейчас всё хранится **только в браузере** (`localStorage`). Задача — хранить профили
на сервере. Игре ничего менять не нужно: она работает через два интерфейса из
`app/src/main/java/ru/finney/pet/domain/game/`.

### Точки подключения

1. **`GameStorage`** — профили и их прогресс. Реализация сейчас — `WebGameStorage`.
   - `observeProfiles()` / `observeGame(id)` — `Flow`, по ним UI перерисовывается;
     после записи значение должно прийти в эти потоки (локально — сразу, не дожидаясь сервера).
   - `create` возвращает id нового профиля.
   - `save` — **только дописывает**: операции кошелька (`ledger`), попытки игр
     (`attempts`) и расход с полки (`uses`) — журналы, новое состояние всегда их
     продолжение (см. `applyRows` и `check(...)` в `WebGameStorage`). Периоды и
     состояние питомца перезаписываются. Это удобно для сервера: можно слать только хвост.
   - `replace` — сброс прогресса (журналы целиком заменяются).
2. **`ActiveProfileStorage`** — какой профиль открыт. Сейчас `WebActiveProfileStorage`
   (ключ `finny.activeProfile`). Скорее всего, остаётся локальным.

Подменить реализацию — одна строка в `web/src/wasmJsMain/kotlin/ru/finney/pet/AppContainer.kt`
(`val storage = ...`).

### Формат данных

`StoredGames` в `WebGameStorage.kt` — готовый JSON всего сохранения:
`{ version, nextId, profiles: [ { profile, pet, periods, ledger, attempts, uses } ] }`.
Строки — те же `data class`, что таблицы Room на Android (`app/.../data/db/Entities.kt`),
`version` (`FORMAT_VERSION`) меняется вместе с ними, как версия базы.
`exportJson()` / `importJson()` уже есть — можно начать с синхронизации целым документом.

### На что обратить внимание

- **Офлайн и первый кадр.** Игра читает сохранение синхронно при старте
  (`MutableStateFlow(read())`). Разумный путь — оставить `localStorage` как кэш
  и синхронизировать с сервером поверх него, а не ждать сеть перед первым кадром.
- **id профилей** сейчас выдаёт браузер (`nextId`). При серверных профилях id
  должен давать сервер, иначе два устройства создадут одинаковые.
- **Конфликты.** Журналы только дописываются, поэтому слияние по ним простое;
  периоды/питомец — «последняя запись выигрывает» или версия на профиль.
- **Время.** Игра считает периоды и голод по `nowMillis()` (`Browser.kt`) — часам
  устройства; сервер их не проверяет.
- **Демо-профиль** (`isDemo`) — для проверки, на сервер его, вероятно, слать не нужно.
- **Хостинг.** Сборка — статика (`productionExecutable`); при API на другом домене
  понадобится CORS.

### Проверка

После правок: `./gradlew :web:wasmJsBrowserDevelopmentRun`, пройти знакомство,
поиграть, перезагрузить страницу — прогресс должен остаться. Android не должен
сломаться: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
