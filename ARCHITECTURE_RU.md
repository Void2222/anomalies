# Anomalies Mod — Архитектура и Структура Проекта

> **Назначение документа:** Полная спецификация архитектуры, структуры пакетов, моделей данных и жизненного цикла движка аномалий для разработчиков и создателей аддонов.
> **Целевая платформа:** `Minecraft Forge / Java 17+`

---

## 📐 Архитектурные Принципы и Стандарты

1. **Composition Over Inheritance:** Сущность `AnomalyEntity` является легким runtime-контейнером. Вся прикладная логика (физика, визуализация, нанесение урона, триггеры) вынесена в независимые компоненты (`IAnomalyComponent`).
2. **Data-Driven & Overrides:** Конфигурация аномалии определяется базовым шаблоном в JSON (`AnomalyDefinition`), но может быть переопределена для конкретной сущности в мире через NBT (`customOverrides`). Приоритет NBT всегда выше шаблона.
3. **Server Authority & Client Presentation:** Игровая логика, расчет зон, наложение эффектов и фильтрация целей происходят строго на сервере. Клиентская часть отвечает за рендеринг, частицы (`ParticleComponent`) и пространственный звук (`SoundComponent`).
4. **Единый Стандарт Описания Классов:**
* **Назначение:** Четкая зона ответственности.
* **Технические детали:** Константы, NBT-ключи, события, тикеры, формулы.
* **Связи:** Взаимодействие с другими компонентами, событиями и реестрами.

---

## 🗺 Дерево Пакетов

```text
net.void_.anomalies
├── Anomalies.java  # Главный класс мода / Точка входа (@Mod)
├── api/            # Публичный Extension API
│   ├── behavior/   # Интерфейсы и реестр стейт-машины (IAnomalyStateBehavior)
│   └── event/      # Перехватываемые события триггеров, урона, физики и зон
├── anomaly/        # Data-driven слой, фабрика сборки и загрузчик JSON
│   ├── data/       # Immutable Record-классы конфигурации
│   ├── loader/     # Datapack Reload Listeners (мультифазовая загрузка)
│   └── util/       # Математические утилиты расчета зон и оверрайдов
├── client/         # Клиентская часть (Рендереры, клиентские эвенты)
├── components/     # Реализации компонентов поведения (ECS + StateMachineComponent)
├── config/         # Конфигурационные менеджеры и хранилища данных
├── core/           # Ядро движка (AnomalyEntity, IAnomalyComponent)
├── dsl/            # Подсистема скриптов и парсер DSL
│   ├── ast/        # Узлы абстрактного синтаксического дерева
│   ├── cache/      # Кэширование скомпилированных скриптов
│   ├── context/    # Контекст выполнения скриптов
│   ├── event/      # События скриптового движка
│   ├── loader/     # Загрузчик DSL-скриптов
│   ├── model/      # Модели данных DSL
│   ├── registry/   # Реестр функций и операторов
│   └── visitor/    # Посетители AST (AnomalyAstBuilder и др.)
├── item/           # Мультитул и процессоры управления (Strategy Pattern)
│   └── processor/  # Процессоры режимов (Analyze, Modify, Relocate, Delete)
└── setup/          # Инициализация, регистрация сущностей и команды
```

---

## 1. Ядро Движка (`core`)

*Пакет:* `net.void_.anomalies.core`

### 1.1. Runtime-Контейнер Сущности (`core.AnomalyEntity`)

* **Класс:** `AnomalyEntity` (расширяет `net.minecraft.world.entity.Entity`)
* **Назначение:** Базовая сущность аномалии в мире. Выступает точкой привязки в пространстве, контейнером для компонентов `IAnomalyComponent` и хранителем стейт-машины и оверрайдов.
* **Технические детали:**
* **Свойства в конструкторе:** `noPhysics = true`.
* **Флаги переопределения:** `isPickable() = true`, `isInvulnerable() = true`, `canBeCollidedWith() = false`.
* **Оптимизированная итерация тика (Snapshot Array Swap):** В методе `tick()` итерация происходит по `volatile`-массиву `activeComponents` без создания новых объектов `new ArrayList<>()`. Это гарантирует абсолютный 0 allocation memory в тиках и защищает от `ConcurrentModificationException` при пересборке компонентов.
* **Синхронизация (SynchedEntityData):**
* `ANOMALY_TYPE` (`String`) — базовый тип аномалии.
* `CURRENT_STATE` (`String`) — активная фаза (по умолчанию `"idle"`, всегда приводится к нижнему регистру `toLowerCase()`).
* `onSyncedDataUpdated()` отслеживает изменения `ANOMALY_TYPE` и `CURRENT_STATE` **строго на клиенте** (`isClientSide`), вызывая `rebuildComponents()`, чтобы моментально пересоздать визуал и звук. На сервере двойной вызов исключен.
* **Размеры сущности:** По умолчанию `1.0F x 1.0F`. Метод `setAnomalyDimensions(width, height)` обновляет локальные поля `anomalyWidth`/`anomalyHeight` и вызывает `refreshDimensions()`. Динамический хитбокс возвращается через `getDimensions(Pose pose) -> EntityDimensions.scalable(width, height)`.
* **Атомарная загрузка NBT:** При считывании мира (`readAdditionalSaveData`) последовательно считываются `AnomalyType`, `CurrentState` (с приведением к `toLowerCase()`, фоллбэк: `"idle"`) и `CustomOverrides`, после чего вызывается единственный `rebuildComponents()`.
* **Двухфазное исполнение серверного тика (`tick()`):**
* На клиенте (`level().isClientSide`): итеративно вызывает `component.clientTick(this)` для всех компонентов из `activeComponents`.
* На сервере:

1. **Первый проход:** Исполняются все базовые компоненты, за исключением `StateMachineComponent` (включая `RecipeProcessorComponent`).
2. **Второй проход:** Строго последней исполняется `StateMachineComponent`. Это гарантирует, что крафты и переработка предметов успевают завершиться на граничном тике прямо перед возможной сменой фазы аномалии.

* **Управление NBT-оверрайдами и компонентами:**
* `getCustomOverrides()` / `setCustomOverrides(CompoundTag tag)` — возвращает или подменяет тег оверрайдов (защитная копия) с вызовом `rebuildComponents()`.
* `setOverrideDouble(key, value)` — запись `double`-параметра в NBT с авто-вызовом `rebuildComponents()`.
* `getComponent(Class<T> type)` — дженерик-метод для поиска и извлечения активного экземпляра компонента по его классу ($O(N)$ проход по списку `components`).
* **Пересборка (`rebuildComponents()`):** Очищает `components.clear()`, пересобирает компоненты через `ZoneFactory.applyComponents(this, type, getCurrentState())` и обновляет снапшот `activeComponents`.
* **Спавн-пакет:** `getAddEntityPacket()` возвращает `NetworkHooks.getEntitySpawningPacket(this)`.
* **Связи:** `ZoneFactory`, `IAnomalyComponent`, `CompoundTag`, `NetworkHooks`, `SynchedEntityData`, `StateMachineComponent`, `RecipeProcessorComponent`.

---

### 1.2. Контракт Компонента (`core.IAnomalyComponent`)

* **Интерфейс:** `IAnomalyComponent`
* **Назначение:** Единый контракт поведения аномалии.
* **Технические детали:**
* `default void serverTick(AnomalyEntity anomaly)` — вызывается каждый тик на сервере (триггеры, физика, очистка).
* `default void clientTick(AnomalyEntity anomaly)` — вызывается каждый тик на клиенте (частицы, аудио).
* `default void save(CompoundTag tag)` / `default void load(CompoundTag tag)` — интерфейсные методы сериализации состояния.

* **Связи:** `AnomalyEntity`.

---

### 1.3. Менеджер Игнорирования Игроков (`config.AnomalyIgnoreManager`)

* **Класс:** `AnomalyIgnoreManager`
* **Назначение:** Персистентное хранение и проверка списка игроков, защищенных от воздействия аномалий.
* **Технические детали:**
    * **Путь к файлу:** `config/anomalies_ignored_players.json`.
    * **Кэш в памяти:** `Set<UUID> IGNORED_PLAYERS` с ленивой загрузкой при первом обращении (`ensureLoaded()`).
    * `isIgnored(Player player)` / `isIgnored(UUID uuid)` — проверка статуса игрока.
    * `setIgnored(UUID uuid, boolean ignore)` — добавляет или удаляет UUID из набора и атомарно перезаписывает JSON-файл на диске via GSON.
* **Связи:** `ZoneFactory`, `TriggerComponent`, `AnomalyCommands`.

---

## 2. Фабрика и Данные Конфигурации (`anomaly`)

*Пакет:* `net.void_.anomalies.anomaly`

### 2.1. Фабрика Сборки (`anomaly.ZoneFactory`)

* **Класс:** `ZoneFactory`
* **Назначение:** Composition Root. Фабричный класс, отвечающий за сборку, инстанцирование и dynamic-rebuild всех физических, визуальных и логических компонентов аномалии при объединении данных JSON-конфигураций (`AnomalyDefinition`) и NBT-оверрайдов (`customOverrides`).
* **Технические детали:**
* **Создание сущности (`create`):** Проверяет существование типа через `AnomalyReloadListener.exists(type)`. При успехе создаёт `AnomalyEntity` через `EntityInit.ANOMALY.get().create(level)`, выставляет координаты и принудительно переводит тип в нижний регистр (`type.toLowerCase()`).
* **Метод `applyComponents(AnomalyEntity anomaly, String type, String state)`:**

1. Запрашивает `AnomalyDefinition` для фазы (`state`) из `AnomalyReloadListener.get(type, state)`. При отсутствии конфигурации прерывает выполнение.
2. Всегда навешивает `SnapToGridComponent`.
3. На серверной стороне (`!isClientSide`):

* Всегда добавляет `StateMachineComponent(type)` (оркестратор фазовых переходов).
* **Условное подключение рецептов:** Проверяет наличие DSL-скрипта через `AnomalyScriptRegistry.get(type)`. Если скрипт найден и содержит рецепты для текущей фазы (`!script.get().getRecipesForState(state).isEmpty()`), навешивает `RecipeProcessorComponent`.

4. Извлекает NBT-оверрайды `anomaly.getCustomOverrides()`.
5. `setupDimensions()`: считывает габариты через `OverrideHelper.getDimensions()` и обновляет размеры хитбокса аномалии.
6. `setupParticles()`: регистрирует частицы через `BuiltInRegistries.PARTICLE_TYPE` с автофоллбэком на `ParticleTypes.FLAME`, парсит `Shape` enum и интервалы/количество с учетом оверрайдов.
7. `setupSound()`: разрешает звуковой ивент через `BuiltInRegistries.SOUND_EVENT` (с фоллбэком на `SoundEvent.createVariableRangeEvent`), парсит `SoundSource` enum и задает громкость/pitch.
8. `setupTriggerAndPhysics()`: настраивает компоненты урона, физики и сканера целей.

* **Динамическая настройка физики и триггера (`setupTriggerAndPhysics`):**
* **Авторасчет радиуса сканирования:** Вычисляет `expandRadius` как максимальный радиус среди всех зон (`ZoneConfig::radius`) с фоллбеком на NBT-оверрайд `expandRadius` (дефолт: `1.0`).
* **Отзывчивый сканер:** Создает `TriggerComponent` с фиксированным интервалом проверки `(1, 1)` тиков для мгновенной реакции на вход/выход сущностей.
* **Базовый урон:** Навешивает `DamageComponent` с дефолтным диапазоном `(0, 0)` и источником `level().damageSources().generic()`.
* **Физика (`ImpulseComponent`):** Создается только при наличии зон (`!zones.isEmpty()`) с учетом параметров `pullToCenter` и импульсов по X/Y/Z из `PhysicsConfig`.
* **Логика обработки целей (`handleTriggerTarget`):**

1. Проверяет флаг `ignoreOtherAnomalies == true` для сущностей класса `AnomalyEntity`.
2. Проверяет регистрацию игрока в `AnomalyIgnoreManager.isIgnored(player)`.
3. При наличии `impulseComp` исполняет `impulseComp.applyImpulse(anomaly, target)`.
4. Определяет текущую зону через `ZoneUtils.getActiveZone()`. Если `activeZone == null` — прекращает обработку.
5. Для предметов (`ItemEntity`) публикует `AnomalyItemInteractEvent`.
6. Для остальных сущностей публикует `AnomalyTriggerEvent` (с прерыванием при отмене).
7. При наличии параметров урона (`activeZone.damage() != null`):

* Применяет эффект поджога `target.setSecondsOnFire(fireSeconds)`.
* При `damage > 0` выбирает источник через `getDamageSource()` (`fire` -> `inFire()`, `lightning` -> `lightningBolt()`, `magic` -> `magic()`, дефолт -> `generic()`).
* Наносит урон через `damageComp.inflictDamage()`.
* **Перегрузка `applyComponents(anomaly, type)`:** По умолчанию собирает аномалию для состояния `"idle"`.
* **Связи:** `AnomalyEntity`, `AnomalyReloadListener`, `OverrideHelper`, `ZoneUtils`, `ImpulseComponent`, `DamageComponent`, `TriggerComponent`, `ParticleComponent`, `SoundComponent`, `StateMachineComponent`, `RecipeProcessorComponent`, `AnomalyScriptRegistry`, `EntityInit`, `AnomalyIgnoreManager`.

---

### 2.2. Конфигурационные Модели Данных (`anomaly.data`)

#### `AnomalyDefinition` (Record)

* **Назначение:** DTO шаблона аномалии из JSON.
* **Поля:** `SizeConfig size`, `List<ParticleConfig> particles`, `SoundConfig sound`, `TriggerConfig trigger`, `List<ZoneConfig> zones`, `PhysicsConfig physics`, `Boolean ignoreOtherAnomalies`.

#### `ZoneConfig` (Record)

* **Назначение:** DTO параметров конкретной пространственной зоны (слоя) аномалии.
* **Поля:** `double radius`, `DamageConfig damage`, `PhysicsConfig physics`.
* **Метод `contains(AnomalyEntity anomaly, Entity target)`:**
* Проверяет цилиндрический объем зоны.
* Рассчитывает горизонтальное расстояние: dx² + dz² ≤ radius²
* Проверяет попадание по высоте Y: Y-координата цели находится в отрезке [anomaly.y - 0.5, anomaly.y + anomaly.height + 0.5]


* **Связи:** `DamageConfig`, `PhysicsConfig`, `AnomalyEntity`, `Entity`.

#### `CraftingSession`

* **Класс:** `CraftingSession`
* **Назначение:** Mutable DTO текущего состояния одной сессии крафта/трансмутации предметов внутри аномалии.
* **Поля:**
* `String recipeId` — строковый идентификатор исполняемого рецепта.
* `int progress` — текущий наработанный прогресс в тиках.
* `Map<UUID, Integer> reservedItems` — мапа забронированных ингредиентов (`UUID` сущности `ItemEntity` -> забронированное количество штук из стака).

* **Технические детали:**
* `incrementProgress()` — атомарный инкремент тиков переработки.
* `serializeNBT()` / `deserializeNBT(CompoundTag)` — полная сериализация/десериализация сессии в NBT (`RecipeId`, `Progress`, `ReservedItems` со списком параметр-тегов `UUID` и `Count`).

* **Связи:** `RecipeProcessorComponent`, `CompoundTag`, `ListTag`.

#### `AnomalyRecipeDefinition`

* **Класс:** `AnomalyRecipeDefinition`
* **Назначение:** DTO-модель JSON-конфигурации рецепта аномалии.
* **Поля:**
* `IngredientData input` — одиночный входной ингредиент (поддержка устаревшего формата JSON).
* `List<IngredientData> inputs` — список входных ингредиентов рецепта.
* `ResultData output` — целевой выходящий предмет.
* `int time` — длительность процесса крафта в тиках.

* **Вложенные DTO:**
* `IngredientData` (`String item`, `int count = 1`) — входной ResourceLocation предмета и требуемое количество.
* `ResultData` (`String item`, `int count = 1`) — результирующий ResourceLocation предмета и выходящий количество.

* **Технические детали:**
* `getInputs()` — метод-адаптер, обеспечивающий обратную совместимость: если заполнен массив `inputs`, возвращает его, иначе возвращает единичный `input` в виде `List.of(input)`.

* **Связи:** `RecipeProcessorComponent`, `AnomalyReloadListener`.

---
#### `MinMaxRange` и `MinMaxRange.Deserializer`

* **Класс:** `MinMaxRange`
* **Назначение:** Обертка для работы с диапазонами случайных величин (интервалы тиков, урон, радиусы, количество частиц). Устраняет жесткую привязку к статичным значениям в JSON.
* **Поля:**
* `double min` — минимальная граница (автоматически нормализуется через `Math.min`).
* `double max` — максимальная граница (автоматически нормализуется через `Math.max`).

* **Ключевые методы:**
* `getInt()` — генерирует случайное целое число в диапазоне $[min, max]$ включительно. Если $min == max$, возвращает значение без вызова ГСЧ.
* `getDouble()` — генерирует случайное вещественное число в диапазоне $[min, max)$.
* `isZero()` — предикат проверки равенства максимальной границы нулю или ниже ($max \le 0.0$).

* **Кастомная десериализация (`MinMaxRange.Deserializer`):**
* Реализует `JsonDeserializer<MinMaxRange>` для гибкой поддержки двух синтаксисов в JSON:
* **Числовой примитив:** `"interval": 40` $\rightarrow$ конструирует `MinMaxRange(40, 40)`.
* **Массив из 2 элементов:** `"interval": [30, 60]` $\rightarrow$ конструирует `MinMaxRange(30, 60)`.

---

#### `DamageConfig`

* **Тип:** Record `DamageConfig(MinMaxRange outerAmount, MinMaxRange innerAmount, int fireSeconds, String damageType)`
* **Назначение:** DTO параметров нанесения урона сущностям во внешних и внутренних слоях аномалии.
* **Поля:**
* `MinMaxRange outerAmount` — случайный диапазон урона во внешней зоне.
* `MinMaxRange innerAmount` — случайный диапазон урона в эпицентре (внутренней зоне).
* `int fireSeconds` — длительность поджигания цели в секундах.
* `String damageType` — строковый идентификатор типа урона (ResourceLocation).

* **Обратная совместимость:** Вторичный конструктор `DamageConfig(MinMaxRange amount, int fireSeconds, String damageType)` перенаправляет устаревшие JSON-конфиги с единым полем `amount` в `innerAmount`, устанавливая `outerAmount` в нуль `MinMaxRange(0, 0)`.

---

#### `PhysicsConfig`

* **Тип:** Record `PhysicsConfig(double impulseX, double impulseY, double impulseZ, boolean pullToCenter, double pullForce, double spinForce)`
* **Назначение:** DTO конфигурации физических векторов, импульсов и гравитационных аффектов аномалии.
* **Поля:**
* `impulseX`, `impulseY`, `impulseZ` — базовые направленные векторные толкающие импульсы.
* `boolean pullToCenter` — флаг активации векторного притягивания сущностей к центру аномалии.
* `double pullForce` — сила притяжения к эпицентру.
* `double spinForce` — тангенциальная сила для тангенциального (вихревого/орбитального) вращения сущностей вокруг центра.

---

#### `ParticleConfig`

* **Тип:** Record `ParticleConfig(String type, String shape, MinMaxRange interval, double radius, double height, MinMaxRange count)`
* **Назначение:** DTO настроек визуальных эффектов частиц.
* **Поля:**
* `String type` — ResourceLocation частицы.
* `String shape` — геометрия спавна (`SPHERE`, `CYLINDER`, `RING` и т.д.).
* `MinMaxRange interval` — диапазон паузы в тиках между эмиссиями.
* `double radius` / `double height` — пространственные габариты зоны спавна.
* `MinMaxRange count` — диапазон количества частиц за одну эмиссию.

* **Фабричный метод `toComponent()`:**
* Парсит `type` через `BuiltInRegistries.PARTICLE_TYPE`. При отсутствии совпадений ставит фоллбэк `ParticleTypes.FLAME`.
* Транслирует строку `shape` в `ParticleComponent.Shape` (фоллбэк: `SPHERE`).
* Возвращает готовый к работе `ParticleComponent`.

---

#### `SoundConfig`

* **Тип:** Record `SoundConfig(String event, MinMaxRange interval, String source, float volume, float pitch)`
* **Назначение:** DTO конфигурации фоновых звуков и звуковых эффектов фазы.
* **Поля:**
* `String event` — ResourceLocation звукового события.
* `MinMaxRange interval` — диапазон интервалов проигрывания звука в тиках.
* `String source` — имя категории источника звука (например, `"BLOCKS"`, `"AMBIENT"`).
* `float volume` / `float pitch` — громкость и высота тона.

* **Фабричный метод `toComponent()`:**
* Резолвит `event` в `BuiltInRegistries.SOUND_EVENT`. **Трюк для кастома:** если звук не найден в ванильном реестре, динамически создает `SoundEvent.createVariableRangeEvent(loc)`, предотвращая NPE при вызове кастомных модовых звуковых эвентов.
* Безопасно парсит `SoundSource` через `valueOf` (фоллбэк: `SoundSource.BLOCKS`).
* Возвращает экземпляр `SoundComponent`.

---

#### `TriggerConfig`

* **Тип:** Record `TriggerConfig(double expandRadius, MinMaxRange interval)`
* **Назначение:** DTO настроек триггера активности аномалии.
* **Поля:**
* `double expandRadius` — дополнительное расширение радиуса детектирования сущностей.
* `MinMaxRange interval` — интервал проверки триггера со случайным разбросом в тиках.

---

#### `SizeConfig`

* **Тип:** Record `SizeConfig(float width, float height)`
* **Назначение:** DTO пространственных габаритов сущности аномалии для динамической пересборки BoundingBox (`AABB`).
* **Поля:** `width` (ширина/диаметр), `height` (высота).

---

### 2.3. Вспомогательные Утилиты и Загрузчик JSON (`anomaly.util` / `anomaly.loader`)

*Пакеты:* `net.void_.anomalies.anomaly.util`, `net.void_.anomalies.anomaly.loader`

#### `OverrideHelper` (`anomaly.util`)

* **Назначение:** Безопасное извлечение кастомных переопределений из NBT-тега (`CompoundTag`) сущности с автоматическим фоллбэком на базовые значения из JSON-конфигураций.
* **Технические детали:**
* `getDimensions(CompoundTag tag, SizeConfig defaultConfig)` — считывает NBT-ключи `"width"` и `"height"` (сохраненные как `double` и кастуемые к `float`). При их отсутствии использует значения из `SizeConfig` (дефолт: `1.0f`).
* `getZones(CompoundTag tag, List<ZoneConfig> defaultZones)` — если в NBT присутствует ключ `"zones_count"`, считывает целое число слоев `count`. Для каждого индекса `i` (от `0` до `count - 1`) последовательно извлекает:
* `"zone_i_radius"` (`double`)
* `"zone_i_damage"` (`double`) — оборачивается в `DamageConfig` с диапазоном `MinMaxRange(dmgAmount, dmgAmount)` и `damageType = "generic"`.
* `"zone_i_pullForce"` (`double`), `"zone_i_spinForce"` (`double`), `"zone_i_impulseY"` (`double`) — оборачиваются в `PhysicsConfig(0, impY, 0, true, pullForce, spinForce)`.
* Возвращает новый динамический список `List<ZoneConfig>`. При отсутствии `"zones_count"` возвращает `defaultZones` или пустой список `List.of()`.

* `getParticleInterval(CompoundTag tag, ParticleConfig pConfig)` — проверяет ключи `"particleIntervalMin"` / `"particleIntervalMax"`. Фоллбэк: `pConfig.interval().getDouble()` или `1`.
* `getParticleCount(CompoundTag tag, ParticleConfig pConfig)` — проверяет ключи `"particleCountMin"` / `"particleCountMax"`. Фоллбэк: `pConfig.count().getDouble()` или `1`.
* `getSoundInterval(CompoundTag tag, SoundConfig s)` — проверяет ключи `"soundIntervalMin"` / `"soundIntervalMax"`. Фоллбэк: `s.interval().getDouble()` или `20`.
* `getTriggerInterval(CompoundTag tag, TriggerConfig t)` — проверяет ключ `"triggerInterval"`. Генерирует симметричный диапазон `MinMaxRange(min, min)`. Фоллбэк: `t.interval().getDouble()` или `5`.


* **Связи:** `CompoundTag`, `ZoneConfig`, `DamageConfig`, `PhysicsConfig`, `MinMaxRange`, `ParticleConfig`, `SoundConfig`, `TriggerConfig`.

#### `ZoneUtils` (`anomaly.util`)

* **Назначение:** Алгоритм поиска активной зоны воздействия для сущности.
* **Технические детали:**
* `getActiveZone(List<ZoneConfig> zones, AnomalyEntity anomaly, Entity target)`:
1. Проверяет валидность входных данных (`target != null`, `target.isAlive()`, список `zones` не пуст).
2. Последовательно итерируется по списку `zones`.
3. Так как список зон предварительно отсортирован в `ImpulseComponent` по возрастанию радиуса (`radius`), первый же слой, у которого метод `zone.contains(anomaly, target)` возвращает `true`, гарантированно является наименьшей (наиболее внутренней и опасной) активной зоной.

* **Связи:** `ZoneConfig`, `AnomalyEntity`, `Entity`.

---

#### `AnomalyReloadListener` (`anomaly.loader`)

* **Класс:** `AnomalyReloadListener` (расширяет `SimpleJsonResourceReloadListener`)
* **Назначение:** Datapack-загрузчик шаблонов аномалий, их фазовых состояний и рецептов крафта из JSON-файлов по пути `data/<mod_id>/anomalies/`.
* **Технические детали:**
* **Статические реестры:**
* `REGISTRY`: Двухуровневая карта `Map<String, AnomalyDefinition Map<String,>>` (Тип аномалии -> Имя фазы -> Конфигурация).
* `RECIPE_REGISTRY`: Двухуровневая карта `Map<String, AnomalyRecipeDefinition Map<String,>>` (Тип аномалии -> Имя рецепта -> Конфигурация рецепта).

* **Конфигурация GSON:** Инициализируется с поддержкой `.setLenient()` и зарегистрированным десериализатором `MinMaxRange.Deserializer`.
* **Двухмаршрутный динамический парсинг (`apply`):**
1. **Маршрут рецептов:** Производит динамический поиск сегмента `"recipes"` в пути ресурса (`.../<type>/recipes/<recipe_name>.json`). Извлекает идентификатор типа из сегмента перед `"recipes"`, десериализует `AnomalyRecipeDefinition` и заносит в `RECIPE_REGISTRY`.
2. **Маршрут состояний (фаз):**
* **Структура с папкой состояний (`<type>/states/<state>.json`):** Извлекает `type` из `parts[0]` и `state` из `parts[2]`.
* **Упрощенная папка (`<type>/<state>.json`):** Извлекает `type` из `parts[0]` и `state` из `parts[1]`.
* **Одиночный файл (Легаси / `<type>.json`):** Извлекает `type` из имени файла и автоматически привязывает дефолтное имя фазы `"idle"`.
* Десериализует `AnomalyDefinition` и регистрирует в `REGISTRY`.

* **Иерархическая логика получения состояний (`get(type, state)`):**
1. Приводит ключи к нижнему регистру (`toLowerCase()`) и ищет точное совпадение по запрошенной фазе `state`.
2. При отсутствии запрошенного состояния выполняет автоматический фоллбэк на базовую фазу `"idle"`.
3. Если фаза `"idle"` также отсутствует в реестре, возвращает первое попавшееся зарегистрированное состояние для данного типа или `null`.

* **Публичный интерфейс:**
* `get(String type, String state)` — считывает `AnomalyDefinition` с трехуровневым фоллбэком.
* `getRecipe(String type, String recipeName)` — извлекает конфигурацию рецепта `AnomalyRecipeDefinition` из `RECIPE_REGISTRY`.
* `hasRecipe(String type, String recipeName)` — проверяет наличие зарегистрированного рецепта.
* `exists(String type)` — проверяет существование зарегистрированного типа аномалии в `REGISTRY`.
* `hasState(String type, String state)` — точечная проверка существования конкретной фазы.
* `getStates(String type)` — возвращает `Set<String>` всех доступных фаз для типа.
* `getKeys()` — возвращает `Set<String>` всех зарегистрированных типов аномалий (для автокомплита в командах).
* **Связи:** `SimpleJsonResourceReloadListener`, `AnomalyDefinition`, `AnomalyRecipeDefinition`, `MinMaxRange`, `Gson`, `ResourceLocation`.

## 3. Расширяемый API Событий (`api.event`)

*Пакет:* `net.void_.anomalies.api.event`

Все события наследуются от `net.minecraftforge.eventbus.api.Event`, помечены аннотацией `@Cancelable` и публикуются на `MinecraftForge.EVENT_BUS`.
* **`AnomalyTriggerEvent`**: Вызывается при попадании сущности в зону обнаружения аномалии. Отмена полностью блокирует дальнейшее воздействие (урон, эффекты).
* **`AnomalyDamageEvent`**: Вызывается перед нанесением урона сущности активной зоной. Позволяет отменить урон или изменить его значение через `setAmount(float)`.
* **`AnomalyPhysicsEvent`**: Вызывается перед применением вектора притяжения/выталкивания. Позволяет отменить физику или изменить вектор через `setDeltaMovement(Vec3)`.
* **`AnomalyItemInteractEvent`**: Вызывается при попадании `ItemEntity` в область аномалии. Позволяет отменить ванильную реакцию аномалии на выпадающие предметы.
* **`AnomalyZoneTransitionEvent`**: Вызывается при пересечении сущностью границы между зонами. Предоставляет методы `isEntering()` и `isLeaving()`. Позволяет заблокировать реакцию на смену зоны.

---

### 3.1. Логика Состояний (Behavior API) (`api.behavior`)

* **Интерфейс:** `IAnomalyStateBehavior`
* **Назначение:** Java-контракт для реализации программной логики переключения состояний аномалии.
* **Методы:**
* `default void onEnter(AnomalyEntity anomaly)` — вызов при активации состояния.
* `default String onTick(AnomalyEntity anomaly, int ticksInState)` — исполняется каждый тик. Возвращает имя следующего состояния для перехода или `null`, если смена не требуется.
* `default void onExit(AnomalyEntity anomaly)` — деструктор/очистка при выходе из состояния.

* **Реестр:** `AnomalyBehaviorRegistry`
* **Назначение:** Центральное хранилище ассоциаций типов аномалий с их Java-поведениями.
* **Технические детали:**
* `Map<String, IAnomalyStateBehavior> BEHAVIORS`
* `register(String type, IAnomalyStateBehavior behavior)` — привязка логики к типу.
* `get(String type)` — возвращает привязанное поведение или `DefaultBehavior` (возвращает `null` на `onTick`), если логика не задана.

* *Архитектурное примечание:* Текущая регистрация через вызов в Java является временным решением до внедрения data-driven поведения.

---

## 4. Компоненты Поведения (`components`)

*Пакет:* `net.void_.anomalies.components`

Модульные блоки реализации логики `IAnomalyComponent`, отвечающие за оптимизированное выполнение конкретных физических, визуальных и механических функций.

---

### 4.1. `DamageComponent` (`components.DamageComponent`)

* **Класс:** `DamageComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Расчет, модификация через события и нанесение урона.
* **Технические детали:**
* Поля: `defaultDamageRange`, `defaultDamageSource`.
* `serverTick()` и `clientTick()` — пустые реализации.
* **Метод `inflictDamage(anomaly, target, zone, specificRange, overrideSource)`:**
1. Проверяет жизнеспособность цели (`target.isAlive()`).
2. Извлекает урон из `specificRange` (или фоллбэк на `defaultDamageRange`). При `damage <= 0` прерывает выполнение.
3. Выбирает источник урона (`overrideSource` или `defaultDamageSource`).
4. Публикует `AnomalyDamageEvent` на `MinecraftForge.EVENT_BUS`.
5. Если событие отменено — урон не наносится.
6. Извлекает финальный урон через `damageEvent.getAmount()` и, если `amount > 0`, вызывает `target.hurt(sourceToUse, finalDamage)`.

* **Связи:** `AnomalyDamageEvent`, `DamageSource`, `ZoneConfig`, `MinMaxRange`, `AnomalyEntity`.


---

### 4.2. `ImpulseComponent` (`components.ImpulseComponent`)

* **Класс:** `ImpulseComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Физический движок расчета притяжения, выталкивания, тангенциального вращения и непрерывного отслеживания смены зон сущностями.
* **Технические детали:**
* **Конструктор:** Автоматически сортирует передаваемый список зон по возрастанию радиуса (`zones.stream().sorted(Comparator.comparingDouble(ZoneConfig::radius)).toList()`) либо инициализирует пустой список при `null`.
* **Отслеживание зон:** `Map<UUID, ZoneConfig> entityZones` хранит последнюю зафиксированную зону для каждой сущности.
* **Непрерывная очистка (`serverTick`):** В каждом серверном тике без искусственных задержек вызывает `cleanupStaleEntities(anomaly)`. Метод итерируется по `entityZones`:
* Если сущность не найдена (`target == null`), умерла (`!target.isAlive()`), перешла в другой измерение/уровень (`target.level() != anomaly.level()`) или вышла из всех зон (`currentZone == null`), она удаляется из карты.
* Для еще живых сущностей, покинувших область аномалии, публикуется `AnomalyZoneTransitionEvent` с `newZone = null`.

* **Сравнение зон (`isSameZone`):** Сравнение зон выполняет проверку по равенству их радиусов с погрешностью `Math.abs(a.radius() - b.radius()) < 0.0001` (защита от несовпадающих ссылок при пересоздании объектовых экземпляров `ZoneConfig`).
* **Метод `applyImpulse(anomaly, target, precalculatedZone)`:**
1. **Определение зоны:** Берет `precalculatedZone` или вычисляет актуальную зону через `ZoneUtils.getActiveZone(zones, anomaly, target)`.
2. **Переход между зонами:** При изменении зоны относительно `entityZones` публикует `AnomalyZoneTransitionEvent`. Если событие отменено — расчет физики прерывается. При успешном событии обновляет или удаляет запись в `entityZones`.
3. **Конфигурация:** Извлекает `PhysicsConfig` из `currentZone.physics()` с фоллбеком на `generalPhysics` и дефолтные значения (`pullForce = 0.05`, `spinForce = 0.0`, `impulseY`).
4. **Расчет вектора притяжения (`pullToCenter == true`):**
* Вычисляет центр аномалии `(ax, ay + bbHeight * 0.5, az)` и вектор до цели `(dx, dy, dz)`.
* Использует инвертированный квадратный корень `invDist = 1.0 / Math.sqrt(distSq)` для оптимизации нормализации направления.
* **Тангенциальное вращение (Spin Force):** Применяется при `zones.size() > 1` для самой внутренней зоны (`isSameZone(currentZone, zones.get(0))`). Вычисляет коэффициенты близости `closenessFactor = max(0.0, 1.0 - (dist / radius))` и динамическую силу вращения `aggressiveSpin = spinForce * (1.5 + closenessFactor * 3.0)`.
* **Ограничение скорости:** Если `moveX² + moveZ² > 1.0`, горизонтальный вектор нормализуется ровно до `1.0` блока/тик.
5. **Расчет отталкивания (`pullToCenter == false`):** Применяет фиксированное смещение по осям X, Y, Z.
6. **Событие и импульс:** Публикует `AnomalyPhysicsEvent`. В случае отсутствия отмены применяет результирующий вектор `Vec3` через `target.setDeltaMovement(...)` и выставляет `target.hurtMarked = true`.

* **Связи:** `AnomalyPhysicsEvent`, `AnomalyZoneTransitionEvent`, `ZoneUtils`, `PhysicsConfig`, `ZoneConfig`, `AnomalyEntity`.

---

### 4.3. `ParticleComponent` (`components.ParticleComponent`)

* **Класс:** `ParticleComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Клиентский компонент генерации частиц заданной геометрической формы с двухуровневой системой Culling-фильтрации (Frustum + Occlusion) для максимального экономии ресурсов CPU.
* **Технические детали:**
* **Перечисление `Shape`:** `SPHERE`, `CYLINDER`, `DISC`.
* **Константа LOD-расстояния:** `RENDER_DISTANCE_SQ = 4900.0D` (70² блоков).
* **Слой 1 — Frustum Culling (Конус видимости):** В `clientTick()` считывает актуальный `Frustum` из `ClientSetup.getLatestFrustum()`. Вычисляет объемный AABB аномалии с учетом спавна частиц (`inflate(radius, height * 0.5, radius)`). Если аномалия за спиной или вне экрана — выполнение тика мгновенно прекращается.
* **Слой 2 — Occlusion Culling (Проверка за стенами):** Выполняет проверку видимости за сплошными блоками с интервалом `OCCLUSION_CHECK_INTERVAL = 8` тиков (~0.4 сек):
* **Распределенная нагрузка:** Начальное значение `occlusionCheckTimer` рандомизируется в конструкторе (`0..7`), равномерно распределяя рейкасты разных аномалий по кадрам без пиковых фризов.
* **Bypass вплотную:** Если игрок находится внутри или на границе аномалии (`distanceSq <= effectRadiusSq`), рейкаст автоматически отключается (`isOccluded = false`).
* **Короткий рейкаст:** Проводит `ClipContext` (`Block.COLLIDER`, `Fluid.NONE`) от позиции камеры до центра аномалии. При столкновении со сплошным блоком выставляет `isOccluded = true`.

* **Мгновенный старт и защитный таймер:** В конструкторе `nextTriggerTick = 0` (спавн на 0-м тике новой фазы). Следующая пауза рассчитывается как `Math.max(1, intervalRange.getInt())`, защищая клиент от зацикливания.
* **Оптимизация Loop Unswitching:** Проверка `switch (shape)` вынесена за пределы цикла генерации частиц.
* **Rejection Sampling с лимитом:** Точки генерируются циклами `do-while` на примитивах без вызова тригонометрии (`Math.sin`/`cos`) с защитным ограничением `attempts < 10`:
* **`SPHERE`:** Отбраковка `rx² + ry² + rz² > 1.0`. Спавн с базовым импульсом Y = 0.02.
* **`CYLINDER`:** Отбраковка `rx² + rz² > 1.0`, случайная высота Y в пределах `[0..height]`. Базовый импульс Y = 0.05.
* **`DISC`:** Отбраковка `rx² + rz² > 1.0`, микро-смещение Y в пределах ±0.05. Базовый импульс Y = 0.01.

* **Связи:** `ParticleOptions`, `MinMaxRange`, `Minecraft`, `AnomalyEntity`, `Level`, `ClientSetup`, `Frustum`, `ClipContext`.

---

### 4.4. `SoundComponent` (`components.SoundComponent`)

* **Класс:** `SoundComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Клиентский компонент воспроизведения пространственных звуковых эффектов с поддержкой мгновенного старта фазовых звуков и динамической рандомизацией интервалов.
* **Технические детали:**
* **Поля:** `soundEvent` (`SoundEvent`), `intervalRange` (`MinMaxRange`), `soundSource` (`SoundSource`), `volume` (`float`), `pitch` (`float`), `clientTickCounter` (`int`), `nextTriggerTick` (`int`).
* **Мгновенный старт (0-й тик):** В конструкторе выставляется `nextTriggerTick = 0`. Это обеспечивает мгновенное воспроизведение ключевого звука фазы (например, взрыва или гула) сразу в момент перехода сущности в новое состояние, без ожидания случайного интервала.
* **Серверный тик (`serverTick`):** Пустая реализация (Client Authority для локального звукового движка).
* **Клиентский тик (`clientTick`):**
* Увеличивает внутренний счетчик `clientTickCounter++`.
* По достижению `nextTriggerTick` сбрасывает счетчик `clientTickCounter = 0`.
* **Защитный таймер:** Расчет следующего задержки выполняется через `nextTriggerTick = Math.max(1, intervalRange.getInt())`, что гарантирует минимум 1 тик задержки и полностью защищает клиент от зацикливания и микрофризов при некорректных конфигах.
* Воспроизводит локальный звук через `level.playLocalSound(pos.x, pos.y, pos.z, soundEvent, soundSource, volume, pitch, false)`. Флаг `distanceDelay = false` гарантирует отсутствие искусственной задержки распространения звуковой волны.

* **Связи:** `SoundEvent`, `SoundSource`, `MinMaxRange`, `AnomalyEntity`, `Level`.

---


### 4.5. `TriggerComponent` (`components.TriggerComponent`)

* **Класс:** `TriggerComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Серверный пространственный сканер обнаружения целей в области действия аномалии.
* **Технические детали:**
* **Константа радиуса сна:** `ACTIVATION_DISTANCE = 48.0D` блоков.
* **Серверная LOD-оптимизация (Спящий режим):** В `serverTick()` проверяет наличие ближайшего игрока через `level.getNearestPlayer(x, y, z, 48.0, false)`. Если игроков в радиусе 48 блоков нет — сканирование AABB отменяется, экономится процессорное время.
* **Интервальное сканирование:** По истечении `currentIntervalTicks` (получаемого из `interval.getInt()`) производит поиск сущностей.
* **AABB и фильтрация целей:**
    * Расширяет хитбокс аномалии во все стороны на `expandRadius`: `getBoundingBox().inflate(expandRadius)`.
    * Извлекает список целей через `getEntitiesOfClass()`, с фильтром:
        * Отсекает игроков, для которых `AnomalyIgnoreManager.isIgnored(player) == true`.
        * Пропускает только `LivingEntity` и `ItemEntity`.

* **Вызов обработчика:** Последовательно передает каждую найденную сущность в `onTrigger.accept(anomaly, target)` (в лямбду-оркестратор `ZoneFactory.handleTriggerTarget`).

* **Связи:** `AABB`, `LivingEntity`, `ItemEntity`, `Player`, `MinMaxRange`, `BiConsumer`, `AnomalyEntity`, `ZoneFactory`.

### 4.6. `SnapToGridComponent` (`components.SnapToGridComponent`)

* **Класс:** `SnapToGridComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Автоматическое выравнивание позиционирования аномалии по сетке блоков.
* **Технические детали:**
* Содержит флаг однократного выполнения `private boolean isSnapped = false`.
* В `serverTick(AnomalyEntity anomaly)` исполняется только при `!isSnapped`:
* Рассчитывает точный геометрический центр блока:
* x = floor(anomaly.getX()) + 0.5
* y = floor(anomaly.getY()) (нижняя граница блока)
* z = floor(anomaly.getZ()) + 0.5

* Обновляет позицию сущности через `anomaly.setPos(x, y, z)`.
* Устанавливает `isSnapped = true`, предотвращая дергание и постоянный сдвиг позиции при последующих тиках сервера.

* **Связи:** `AnomalyEntity`, `IAnomalyComponent`.

### 4.7. `StateMachineComponent` (`components.StateMachineComponent`)

* **Класс:** `StateMachineComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Серверный компонент-оркестратор жизненного цикла аномалии. Управляет исполнением DSL-скриптов и Java-поведений `IAnomalyStateBehavior`, а также корректным переключением фаз с обнулением локального состояния.
* **Технические детали:**
* **Инициализация и гибридный приоритет:** В конструкторе принимает `anomalyType` и запрашивает поведение из `AnomalyScriptRegistry`:
* **Приоритет 1 (DSL):** Если зарегистрирован DSL-скрипт (`AnomalyScriptModel`), он приводится к интерфейсу `IAnomalyStateBehavior`.
* **Приоритет 2 (Java Fallback):** Если DSL-скрипт отсутствует, берется fallback-поведение из `AnomalyBehaviorRegistry`.
* **Поля:** `behavior` (`IAnomalyStateBehavior`), `ticksInState` (счетчик времени пребывания в фазе), `initialized` (флаг активации фазы).
* **Серверный тик (`serverTick`):**

1. **Инициализация состояния:** При первом тике в текущем состоянии (`!initialized`) единоразово вызывает `behavior.onEnter(anomaly)` и выставляет `initialized = true`.
2. **Инкремент времени:** Увеличивает внутренний счетчик `ticksInState++`.
3. **Вычисление переходов:** Вызывает `String nextState = behavior.onTick(anomaly, ticksInState)` (в случае DSL-скриптов это передает управление `AnomalyScriptModel`, который собирает snapshot зон из `TransientZoneCache` и запрашивает вычисление AST-условий).
4. **Переключение фазы и сброс состояния:** Если `nextState != null` и не равно текущему состоянию (`!nextState.equalsIgnoreCase(anomaly.getCurrentState())`):

* Вызывает `behavior.onExit(anomaly)` для завершения предыдущей фазы.
* **Обнуление счетчиков:** Принудительно сбрасывает `ticksInState = 0` и `initialized = false`, обеспечивая корректный старт следующего состояния.
* **Сброс одноразовых событий:** Вызывает `TransientZoneCache.flushTickEvents(anomaly)` для очистки сгоревших триггеров входа/выхода, **сохраняя** список `activeZones`. Это предотвращает "застревание" стейт-машины при проверке непрерывных условий `in_zone`.
* **Смена состояния:** Устанавливает новое состояние через `anomaly.setCurrentState(nextState)`, инициируя пересборку компонентов сущности.
* **Клиентский тик (`clientTick`):** Пустая реализация (Server Authority).
* **Связи:** `AnomalyEntity`, `IAnomalyStateBehavior`, `AnomalyScriptRegistry`, `AnomalyBehaviorRegistry`, `TransientZoneCache`, `ZoneFactory`.

---

#### `4.8. RecipeProcessorComponent`

* **Класс:** `RecipeProcessorComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Серверный компонент логики рецептов трансмутации и крафта предметов. Выполняет сканирование сущностей предметов (`ItemEntity`) в эпицентре аномалии, ведет параллельные сессии переработки с виртуальным бронированием ингредиентов и сохраняет прогресс в NBT.
* **Алгоритм и Логика Выполнения (`serverTick`):**
1. **Фильтрация разрешенных рецептов:** Запрашивает у `AnomalyScriptRegistry` список допустимых рецептов (`getRecipesForState`) для текущей фазы аномалии (`currentState`). Если список пуст — обработка завершается.
2. **Определение зоны сканирования:** Извлекает конфигурацию зоны `zone0` с минимальным радиусом из `AnomalyDefinition`. Если зона задана, формирует `AABB` со сдвигом на ее радиус и проверяет предикат `zone0.contains(anomaly, item)`; иначе использует базовый радиус $2.5$ блока (`closerThan`).
3. **Валидация и очистка активных сессий:**
* Считывает текущие сессии из NBT (`CraftingSessions`).
* Проверяет существование забронированных `ItemEntity` на сервере и наличие нужного количества предметов в их стаках.
* Если предмета нет или его стак уменьшился ниже забронированного объёма — сессия признается невалидной, сбрасывается и спавнит визуальные частицы `ParticleTypes.SMOKE`.

4. **Матчинг и регистрация новых рецептов:**
* Рассчитывает доступный виртуальный остаток предметов: $\text{AvailableVirtual} = \text{RealCount} - \text{ReservedCount}$.
* Сопоставляет предметы в зоне со списком `inputs` из `AnomalyRecipeDefinition`.
* Поддерживает параллельное создание нескольких одинаковых или разных рецептов за один тик в рамках доступного количества ресурсов. Наденные совпадения оборачиваются в новую `CraftingSession` с `progress = 0` и добавляются в NBT-список.

5. **Инкремент прогресса и Выдача результата:**
* Инкрементирует `progress` всех валидных сессий.
* При достижении `progress >= recipeDef.getTime()` извлекает требуемое количество предметов из `ItemEntity` (`stack.shrink(...)`), удаляя пустые стаки через `item.discard()`.
* Создает и спавнит новый `ItemEntity` с результатом `output` в координатах аномалии (`Y + 0.5`), после чего завершает сессию.

* **Хранение состояния:** Сериализует сессии в `CompoundTag` сущности аномалии по ключу `CraftingSessions`.
* **Связи:** `IAnomalyComponent`, `CraftingSession`, `AnomalyRecipeDefinition`, `AnomalyScriptRegistry`, `AnomalyReloadListener`, `ItemEntity`, `BuiltInRegistries.ITEM`.

---

## 5. Система Управления и Мультитул (`item`)

*Пакет:* `net.void_.anomalies.item`

Полное описание архитектуры взаимодействия с мультитулом, логики обработки событий и встроенных процессоров режимов управления аномалиями.

---

### 5.1. Обработчик Событий и Шина Взаимодействия

* **Класс:** `AnomalyMultitoolHandler`
* **Назначение:** Шина перехвата игровых событий Forge (`@Mod.EventBusSubscriber`) для маршрутизации команд игрока в соответствующие процессоры режимов.
* **Технические детали:**
* **Перехват ПКМ (`onEntityInteract`):**
* Проверяет удерживаемый предмет в `MAIN_HAND` через `isMultitool()`.
* Отменяет ванильное действие (`setCanceled(true)`).
* При смене текущего режима с `MODIFY` на другой автоматически вызывает `ModifyProcessor.clearSession()` для очистки стейта в NBT.
* Перенаправляет вызов в `AnalyzeProcessor`, `ModifyProcessor`, `RelocateProcessor` или `DeleteProcessor` в зависимости от `MultitoolMode`.

* **Перехват ЛКМ по аномалии (`onEntityAttacked`):** В режиме `DELETE` блокирует нанесение ванильного урона и вызывает `DeleteProcessor.process()`.
* **Перехват ЛКМ по блоку (`onLeftClickBlock`):** Если в NBT мультитула активирован захват аномалии в режиме `RELOCATE`, отменяет ломание блоков и вызывает `RelocateProcessor.onLeftClickBlock()`.
* **Перехват ЧАТА (`onServerChat`):** Перехватывает сообщения игрока на сервере (`ServerChatEvent`). При активных режимах `MODIFY` или `RELOCATE` сообщения поглощаются (`setCanceled(true)`), а их текст передается в `onChat()` соответствующего процессора.

* **Связи:** `AnomalyMultitoolItem`, `MultitoolMode`, `AnalyzeProcessor`, `ModifyProcessor`, `RelocateProcessor`, `DeleteProcessor`, `ServerChatEvent`, `PlayerInteractEvent`.

---

### 5.2. Процессоры Режимов Мультитула (`item.processor`)

#### `AnalyzeProcessor`

* **Назначение:** Диагностический сканер-рентген. Считывает и выводит полную структуру параметров аномалии по всем её зарегистрированным фазам из датапаков с учетом NBT-оверрайдов.
* **Технические детали:**
* **Метод:** `process(Player player, AnomalyEntity anomaly)`.
* Выводит заголовок с типом аномалии, короткой формой UUID и текущей активной фазой (`currentState`).
* **Проверка оверрайдов:** Если `customOverrides` не пуст, выводит предупреждение о наличии локальных NBT-блокировок.
* **Итерация по фазам:** Считывает все фазы типа через `AnomalyReloadListener.getStates(type)`. Для каждого состояния выводит его статус (`[ТЕКУЩАЯ ФАЗА]` или `[ИНАКТИВНА]`) и разворачивает 5 блоков конфигурации:
1. **Размеры (`width` x `height`)**.
2. **Слои зон:** Количество зон, радиусы, урон, силы тяги (`pullForce`), вращения (`spinForce`) и вертикального импульса (`impulseY`).
3. **Зона триггера:** Радиус активации (`expandRadius`).
4. **Звук:** Идентификатор события, громкость (`volume`) и pitch.
5. **Частицы:** Тип частиц, радиус и высота спавна.

* **Форматирование оверрайдов (`formatVal`):** Если параметр присутствует в `customOverrides`, значение выводится цветом с пометкой `§e§l[Value] §6[NBT]`, иначе отображается стандартное значение из JSON (`§f[Value]`).

* **Связи:** `AnomalyEntity`, `AnomalyDefinition`, `AnomalyReloadListener`, `CompoundTag`, `Component`.

* **Класс:** `ModifyProcessor`
* **Назначение:** Чат-процессор и контроллер интерактивного редактирования NBT-оверрайдов аномалий в реальном времени с помощью предмета-мультитула («Изменятор»).
* **Технические детали:**
* **Сессия взаимодействия (`onInteract`):**
* **Shift + ПКМ:** Мгновенно очищает NBT-тег оверрайдов сущности (`anomaly.setCustomOverrides(new CompoundTag())`), вызывает `anomaly.rebuildComponents()` и сбрасывает сессию редактирования в NBT предмета (`clearSession`).
* **Обычный ПКМ:** Запоминает `UUID` аномалии в NBT предмета (`SelectedAnomaly`), устанавливает флаг `WaitingForParams = true`, логирует закрытие предыдущей сессии при смене целевой аномалии и выводит сводную карточку параметров текущей фазы (`printCurrentState`).

* **Перехват и обработка чат-команд (`onChat`):**
* Перехватывает сообщения игрока в чате, пока в предмете активен флаг `WaitingForParams`. При отсутствии сущности в `ServerLevel` оповещает пользователя и закрывает сессию.
* **Пакетное исполнение:** Поддерживает выполнение цепочки команд в одном сообщении через разделитель `;` (`trimmed.split(";")`).
* **Завершение сессии:** Команды `done`, `exit`, `save` сбрасывают теги предмета через `clearSession()` и выходят из режима редактирования.
* **Диагностика и справка:** `show` / `list` повторно выводят текущие параметры; `help` / `?` открывают справочник доступных команд и алиасов.
* **Ленивая инициализация зон (`ensureZonesInitialized`):** Если в NBT оверрайдов отсутствует `zones_count`, автоматически предзаполняет оверрайды зон на основе JSON-конфигурации текущего состояния (`AnomalyReloadListener.get(type, anomaly.getCurrentState())`).

* **Синтаксис и разделы команд:**
* **Сброс параметров (`reset`):**
* `reset all` — полный сброс NBT-оверрайдов сущности с возвратом к баночным JSON-значениям.
* `reset zone <idx>` — сброс оверрайдов конкретной зоны по её индексу.
* `reset <param>` — точечное удаление переопределения с удалением ключа из NBT.

* **Переключение флагов (`toggle`):** `toggle pull` (или `pullToCenter`) — инвертирует boolean-значение с учетом фоллбэка на физический конфиг из JSON.
* **Управление зонами (`zone`):**
* `zone add <r> <d> <pf> <sf>` — добавление новой зоны (радиус, урон, тяга, вращение) с дефолтным `impulseY = 0.1` и инкрементом `zones_count`.
* `zone remove <idx>` — удаление зоны по индексу со сдвигом слоев и декрементом `zones_count`.
* `zone <idx> <param> <val>` — точечная модификация параметров зоны (`radius`, `damage`, `pullForce`, `spinForce`, `impulseY`).

* **Система алиасов и строгая валидация (`resolveAlias`, `validateAndApply`):**
* **Алиасы параметров:** `w` (`width`), `h` (`height`), `r` (`expandRadius`), `pull` (`pullToCenter`), `fire` (`fireSeconds`), `vol`/`volume` (`soundVolume`), `pitch` (`soundPitch`), `prad` (`particleRadius`), `pheight` (`particleHeight`).
* **Валидация диапазонов:**
* Размеры и радиусы (`width`, `height`, `expandRadius`, `particleRadius`, `particleHeight`, `zone radius`): `[0.05 ... 64.0]`.
* Звук (`soundVolume`, `soundPitch`): `[0.0 ... 10.0]`.
* Интервал триггера (`triggerInterval`): `[0.05 ... 60.0]` сек.
* Время горения (`fireSeconds`): `[0 ... 3600]` сек.
* Силы зоны (`pullForce`, `spinForce`): `[-10.0 ... 10.0]`.

* **Синхронизация и отображение:** При наличии примененных изменений обновляет `customOverrides` в аномалии и вызывает `anomaly.rebuildComponents()`. В выводе состояния (`printCurrentState`) переопределенные значения выделяются отдельным цветовым маркером `§e§l<val> §6[Override]`.
* **Связи:** `AnomalyEntity`, `AnomalyDefinition`, `AnomalyReloadListener`, `CompoundTag`, `ItemStack`, `Player`, `ServerLevel`.

#### `DeleteProcessor`

* **Назначение:** Безопасное удаление сущности аномалии из мира с двухуровневым подтверждением.
* **Технические детали:**
* **Кэш подтверждений:** Статическая карта `CONFIRM_MAP` типа `Map<UUID, ConfirmData>` с таймаутом `CONFIRM_TIMEOUT_MS = 5000` (5 секунд).
* **Логика удаления:**
* **Shift + ЛКМ / ПКМ:** Мгновенный вызов `anomaly.discard()`, минуя стадию подтверждения.
* **Обычный клик:** При первом клике заносит `UUID` аномалии и timestamp в кэш и отправляет предупреждение в чат. Повторный клик по той же аномалии в течение 5 секунд вызывает `anomaly.discard()` и очищает запись из кэша.

* **Связи:** `AnomalyEntity`, `Player`, `ConfirmData`.

#### `RelocateProcessor`

* **Назначение:** Интерактивный перенос сущности аномалии в пространстве (по клику на блок или по координатным смещениям через чат).
* **Технические детали:**
* **Захват (`onInteract`):** Сохраняет `SelectedAnomaly` (UUID) в NBT предмета.
* **Режим 1: Перенос по клику (Обычный ПКМ -> ЛКМ по блоку):**
* `onLeftClickBlock`: При клике по блоку вычислит целевую позицию `(x + 0.5, y + 1.0, z + 0.5)`, устанавливает новые координаты сущности через `anomaly.setPos()` и стирает `SelectedAnomaly` из NBT предмета.

* **Режим 2: Перенос по смещению (Shift + ПКМ -> Чат):**
* Устанавливает флаг NBT `WaitingForOffset = true`.
* `onChat`: Парсит сообщение формата `dx dy dz` (например, `0.5 0 -0.5`). Извлекает координаты `position()`, добавляет смещение, перемещает сущность и сбрасывает флаги NBT.

* **Связи:** `AnomalyEntity`, `ItemStack`, `CompoundTag`, `ServerLevel`, `BlockPos`.

### 5.3. Предмет Мультитула и Перечисление Режимов (`item`)

#### `AnomalyMultitoolItem` (`item.AnomalyMultitoolItem`)

* **Класс:** `AnomalyMultitoolItem` (расширяет `net.minecraft.world.item.Item`)
* **Назначение:** Админский/отладочный инструмент для интерактивного управления, анализа, модификации, перемещения и удаления сущностей аномалий прямо в игре.
* **Хранение состояния в NBT:**
* `getMode(ItemStack stack)` — извлекает имя текущего режима из NBT-тега `"Mode"`. При отсутствии тега или ошибке парсинга безопасно возвращает фоллбэк `MultitoolMode.ANALYZE`.
* `setMode(ItemStack stack, MultitoolMode mode)` — записывает `mode.name()` в NBT-тег `"Mode"`.

* **Механика переключения режимов (`use`):**
* Обрабатывается строго в главной руке (`InteractionHand.MAIN_HAND`).
* При зажатой клавише **Shift + ПКМ по воздуху**:
1. **Сброс сессионных данных:** На стороне сервера принудительно очищает из NBT предмета временные теги активных диалогов/вводов: `"WaitingForOffset"`, `"WaitingForParams"` и `"SelectedAnomaly"`.
2. **Циклическая ротация:** Получает текущий режим и переключает его на следующий via `getMode().next()`.
3. **Обратная связь:** Выводит динамическое цветное сообщение о смене режима в Action Bar игрока (`displayClientMessage`).
4. **Синхронизация:** Возвращает `InteractionResultHolder.sidedSuccess`, гарантируя отправку клиентом сетевого пакета использования предмета.

* **Отображение подсказок (`appendHoverText`):**
* Формирует динамический тултип с цветовым выделением текущего режима (`mode.getColor()`), его подробным описанием и подсказкой по горячим клавишам переключения (`Shift + ПКМ`).

* **Связи:** `MultitoolMode`, `InteractionResultHolder`, `CompoundTag`, `Component`, `ChatFormatting`.

---

#### `MultitoolMode` (`item.MultitoolMode`)

* **Перечисление:** `MultitoolMode`
* **Назначение:** Набор поддерживаемых режимов работы мультитула.
* **Значения:**
* `ANALYZE` ("Анализ", `ChatFormatting.AQUA`): вывод характеристик аномалии в чат.
* `MODIFY` ("Изменение", `ChatFormatting.GOLD`): запуск интерактивной сессии редактирования через чат или сброс оверрайдов.
* `RELOCATE` ("Перемещение", `ChatFormatting.GREEN`): захват аномалии, сдвиг по координатам через чат или перенос кликом по блоку.
* `DELETE` ("Удаление", `ChatFormatting.RED`): мгновенное уничтожение аномалии.

* **Метод циклической ротации (`next()`):** `values()[(this.ordinal() + 1) % values().length]` — обеспечивает зацикленное переключение режимов по кругу.
* **Связи:** `AnomalyMultitoolItem`, `ChatFormatting`.
## 6. Клиентская Часть (`client`)

*Пакет:* `net.void_.anomalies.client`

### 6.1. Невидимый Рендерер Сущности (`client.AnomalyRenderer`)

* **Класс:** `AnomalyRenderer` (расширяет `EntityRenderer<AnomalyEntity>`)
* **Назначение:** Отключение стандартной трехмерной полигональной модели сущности при сохранении активного клиентского тикинга компонентов.
* **Технические детали:**
* **Текстура-заглушка:** `getTextureLocation()` возвращает путь `anomalies:textures/entity/anomaly.png`.
* **Управление видимостью (`shouldRender`):** Метод явно возвращает `true`. Это предотвращает отсечение (culling) самой сущности движком рендеринга Minecraft и гарантирует постоянный вызов `clientTick()` у компонентов (`ParticleComponent`, `SoundComponent`). Локальное отсечение видимости визуала вынесено непосредственно внутрь `ParticleComponent`, чтобы не затыкать воспроизведение пространственного звука за стеной.

* **Связи:** `EntityRenderer`, `AnomalyEntity`, `ParticleComponent`, `SoundComponent`.

---

### 6.2. Регистратор Клиентской Части (`client.ClientSetup`)

* **Класс:** `ClientSetup`
* **Назначение:** Регистрация визуализаторов сущностей на стороне клиента и перехват матрицы видимости `Frustum` для работы оптимизатора частиц.
* **Технические детали:**
* **Потокобезопасный кэш `Frustum`:** Содержит `private static volatile Frustum latestFrustum` и публичный геттер `getLatestFrustum()`.
* **Разделение шин событий (`EventBus`):**
* **`ModBusEvents`** (`@Mod.EventBusSubscriber(bus = Bus.MOD, value = Dist.CLIENT)`): Подписан на `EntityRenderersEvent.RegisterRenderers`. Связывает зарегистрированный тип `EntityInit.ANOMALY.get()` с `AnomalyRenderer::new`.
* **`ForgeBusEvents`** (`@Mod.EventBusSubscriber(bus = Bus.FORGE, value = Dist.CLIENT)`): Подписан на `RenderLevelStageEvent`. На этапе `Stage.AFTER_PARTICLES` захватывает актуальный `Frustum` текущего кадра и сохраняет его в `latestFrustum`.

* **Связи:** `EntityRenderersEvent.RegisterRenderers`, `RenderLevelStageEvent`, `EntityInit`, `AnomalyRenderer`, `Frustum`, `Dist.CLIENT`.

## 7. Инициализация, Команды и Точка Входа (`setup` / `root`)

*Пакеты:* `net.void_.anomalies.setup`, `net.void_.anomalies`

### 7.1. Командный Интерфейс Администрирования (`setup.AnomalyCommands`)

* **Класс:** `AnomalyCommands`
* **Назначение:** Регистрация и обработка консольных команд администрирования аномалий.
* **Технические детали:**
* **Уровень доступа:** Требует `hasPermission(2)` (уровень оператора/администратора).
* **Структура команд:**
* `/anomaly create <type>` — Конструирует сущность через `ZoneFactory.create()` по координатам игрока (`x`, `y`, `z`) и спавнит её в мире через `level.addFreshEntity()`. Динамический автокомплит аргумента `type` запрашивает ключи из `AnomalyReloadListener.getKeys()`.
* `/anomaly ignore <player> <state>` — Добавляет или удаляет игрока из глобального списка игнорирования триггерами аномалий. Управляет записью `UUID` игрока через `AnomalyIgnoreManager.setIgnored(target.getUUID(), state)`.
* `/anomaly state set <state>` — Принудительно меняет фазу жизненного цикла для ближайшей аномалии в радиусе 10 блоков (`player.getBoundingBox().inflate(10.0)`).
* **Динамический автокомплит:** Автоматически ищет ближайшую `AnomalyEntity` к игроку и предлагает список валидных фаз, зарегистрированных для этого типа через `AnomalyReloadListener.getStates()`.
* **Валидация:** Перед сменой проверяет существование запрошенного состояния в JSON через `AnomalyReloadListener.hasState()`, после чего применяет фазу вызовом `anomaly.setCurrentState(newState)`.

* **Связи:** `CommandDispatcher`, `CommandSourceStack`, `ZoneFactory`, `AnomalyReloadListener`, `AnomalyIgnoreManager`, `AnomalyEntity`, `ServerPlayer`, `ServerLevel`.

---

### 7.2. Инициализация Сущностей (`setup.EntityInit`)

* **Класс:** `EntityInit`
* **Назначение:** Регистрация типа сущности `AnomalyEntity` в реестрах Minecraft Forge.
* **Технические детали:**
* **Реестр:** `DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "anomalies")`.
* **Регистрационная запись `ANOMALY` (`RegistryObject<EntityType<AnomalyEntity>>`):**
* Категория сущности: `MobCategory.MISC`.
* Базовый хитбокс: `sized(1.0F, 1.0F)`.
* Дистанция отслеживания пакетов клиентом: `clientTrackingRange(64)` (64 блока).
* Частота обновления сетевых пакетов: `updateInterval(20)` (раз в 20 тиков / 1 секунду).
* **Метод `register(IEventBus eventBus)`:** Подключает реестр `ENTITIES` к шине событий мода.
* **Связи:** `DeferredRegister`, `RegistryObject`, `EntityType`, `MobCategory`, `AnomalyEntity`, `Anomalies`.

---

### 7.3. Точка Входа и Инициализация (`Anomalies.java`)

* **Класс:** `Anomalies`
* **Назначение:** Главный модуль мода (`@Mod("anomalies")`), связывающий жизненный цикл Forge, шины событий, регистрацию контента и подключение загрузчиков датапаков.
* **Технические детали:**
* **Регистрация предметов:** Инициализирует `DeferredRegister<Item> ITEMS` и регистрирует предмет-мультитул `ANOMALY_MULTITOOL` (`AnomalyMultitoolItem` с ограничением `stacksTo(1)`).
* **Связывание шин:** Регистрирует шины предметов `ITEMS.register(modEventBus)` и сущностей `EntityInit.register(modEventBus)`. Подписывает текущий экземпляр на шину событий Forge (`MinecraftForge.EVENT_BUS.register(this)`).
* **Слушатели загрузки датапаков (`onAddReloadListeners`):** На событие `AddReloadListenerEvent` регистрирует в серверном менеджере ресурсов два ключевых релоадера:
* `AnomalyReloadListener` — парсер JSON-конфигураций состояний и рецептов.
* `AnomalyScriptLoader` — компилятор DSL-скриптов поведения.

* **Регистрация команд (`onRegisterCommands`):** Передает серверный диспетчер команд `event.getDispatcher()` в `AnomalyCommands.register()`.
* **Связи:** `AnomalyReloadListener`, `AnomalyScriptLoader`, `AnomalyCommands`, `EntityInit`, `AnomalyMultitoolItem`, `DeferredRegister`, `IEventBus`, `MinecraftForge`.

---

## 8. Движок Скриптов DSL (`dsl`)

*Пакеты:* `net.void_.anomalies.grammar`, `net.void_.anomalies.dsl`

Полная техническая спецификация подсистемы интерпретации, загрузки и runtime-выполнения DSL-скриптов управления поведением аномалий.

---

### 8.1. Грамматика и Синтаксис Языка (`net.void_.anomalies.grammar.AnomalyDSL.g4`)

* **Назначение:** ANTLR4-спецификация формальной грамматики DSL. Определяет правила лексического и синтаксического анализа скриптов поведений `.anom`.
* **Структура Деклараций и Дерево Разбора (Parser Rules):**
* **`script` / `statement`:** Корень скрипта, состоящий из произвольной последовательности деклараций и заканчивающийся `EOF`.
* **`bindClause`:** Декларация связывания состояния с JSON-файлом конфигурации фазы.
* *Синтаксис:* `state <ID> --> "<jsonPath>";` *(пример: `state idle --> "zharka/idle.json";`)*.

* **`recipeBindClause`:** Декларация связывания имени рецепта с JSON-файлом рецепта.
* *Синтаксис:* `recipe <ID> --> "<jsonPath>";` *(пример: `recipe cook_pork --> "recipes/cook_pork.json";`)*.

* **`initialStateClause`:** Назначение стартового состояния стейт-машины.
* *Синтаксис:* `initial = <ID>;` *(пример: `initial = idle;`)*.

* **`stateBlock` / `stateElement`:** Блок описания логики фазы. Содержит переходы между состояниями (`transitionRule`) и привязки активных рецептов (`recipeBlock`).
* *Синтаксис:* `state <ID> { recipe { <recipeRef>* } <transitionRule>* }`.

* **`transitionRule`:** Правило фазового перехода. Вычисляет логическое выражение `expression` и, при `true`, инициирует смену состояния.
* *Синтаксис:* `when <expression> -> <targetState>;`.

* **Приоритет Логических Операторов (`expression`):**
  Порядок вычисления выражений зафиксирован на уровне синтаксического дерева (от высшего приоритета к низшему):
1. **Группировка:** Скобки `( expr )` (`ParenExpr`).
2. **Унарное отрицание:** `not` / `!` (`NotExpr`).
3. **Конъюнкция:** `and` / `&&` (`AndExpr`).
4. **Дизъюнкция:** `or` / `||` (`OrExpr`).
5. **Атомарные условия:** `primaryCondition` (`PrimaryExpr`).

* **Атомарные Предикаты (`primaryCondition`):**
* `timer(INT)` — проверка нахождения в состоянии не менее указанного количества тиков.
* `chance(NUMBER)` — вероятностный генератор (`0.0 ... 1.0`).
* `player.<eventName>(zone)` — событийный предикат взаимодействия игрока с зоной (принимает имя или числовой индекс зоны).

* **Лексический Анализ (Lexer Rules):**
* **Ключевые слова:** `state`, `initial`, `when`, `timer`, `chance`, `player`, `recipe`.
* **Строгое разделение стрелок:** Оператор связывания JSON `-->` (`BIND_ARROW`) синтаксически изолирован от оператора перехода состояния `->` (`TRANSITION_ARROW`), что полностью исключает коллизии при парсинге.
* **Синонимы операторов:** Поддерживаются как классические текстовые ключевые слова (`and`, `or`, `not`), так и их символьные Java/C-эквиваленты (`&&`, `||`, `!`).
* **Игнорирование мусора:** Комментарии (`// ...`, `/* ... */`) и пробельные символы (`WS`) автоматически сбрасываются на этапе лексического анализа через каналы `skip`.

* **Связи:** ANTLR Tool Chain, `AnomalyDSLLexer`, `AnomalyDSLParser`, `AnomalyAstBuilder`.

---

### 8.2. Модели Данных и Реестр Скриптов (`dsl.model`, `dsl.registry`)

#### `TransitionRule` (`dsl.model`)

* **Тип:** Record `TransitionRule(ICondition condition, String targetState)`
* **Назначение:** Immutable DTO правила перехода между фазами аномалии.
* **Поля:**
* `ICondition condition` — AST-дерево условия перехода.
* `String targetState` — имя целевого состояния.

* **Связи:** `ICondition`, `AnomalyScriptModel`.

#### `AnomalyScriptModel` (`dsl.model`)

* **Класс:** `AnomalyScriptModel` (имплементирует `IAnomalyStateBehavior`)
* **Назначение:** Исполняемый runtime-скрипт аномалии. Хранит связки фаз и рецептов с JSON-конфигурациями, маппинг рецептов по состояниям, граф переходов и исполняет роль стейт-машины при тике сущности.
* **Поля:**
* `Map<String, String> binds` — маппинг имени состояния на путь к JSON-файлу фазы (`state` -> `jsonPath`).
* `Map<String, String> recipeBinds` — маппинг имени рецепта на путь к JSON-файлу рецепта (`recipeName` -> `jsonPath`).
* `Map<String, List<TransitionRule>> transitions` — граф переходов (`state` -> список правил `TransitionRule`).
* `Map<String, List<String>> stateRecipes` — привязка рецептов к состояниям (`state` -> список имён рецептов).
* `String initialState` — стартовое состояние (по умолчанию `"idle"`).

* **Технические детали:**
* `addBind(state, jsonPath)` / `addRecipeBind(recipeName, jsonPath)` / `addRecipeToState(state, recipeName)` — методы наполнения модели при парсинге.
* `getRecipesForState(state)` — возвращает список активных рецептов для указанной фазы (или пустой список при отсутствии).
* **Исполнение тика (`onTick`):**
1. Извлекает текущее состояние `anomaly.getCurrentState()`.
2. Если для текущего состояния отсутствуют правила в `transitions`, возвращает `null`.
3. Запрашивает snapshot событий зон с авто-очисткой сгоревших фреймов через `TransientZoneCache.getSnapshotAndFlush(anomaly)`.
4. Создает `EvaluationContext(anomaly, ticksInState, zoneEvents)`.
5. Последовательно проверяет условия `rule.condition().test(ctx)` в порядке добавления. Возвращает `targetState` первого сработавшего правила.

* **Связи:** `IAnomalyStateBehavior`, `TransitionRule`, `TransientZoneCache`, `EvaluationContext`, `AnomalyEntity`.

#### `AnomalyScriptRegistry` (`dsl.registry`)

* **Класс:** `AnomalyScriptRegistry`
* **Назначение:** Глобальный потокобезопасный реестр скомпилированных скриптов (`ConcurrentHashMap<String, AnomalyScriptModel>`).
* **Технические детали:** Нормализация ключей к `toLowerCase()`, методы `register`, `get` (`Optional`), `hasScript`, `clear`.
* **Связи:** `AnomalyScriptModel`, `StateMachineComponent`, `AnomalyScriptLoader`.

---

### 8.3. Абстрактное Синтаксическое Дерево (AST) Условий (`dsl.ast`)

#### `ICondition` (`dsl.ast`)

* **Интерфейс:** `@FunctionalInterface ICondition`
* **Метод:** `boolean test(EvaluationContext ctx)` — вычисление логического условия в заданном контексте.

#### Листовые Предикаты (`dsl.ast.leaf`)

* **`TimerCondition(int targetTicks)`** (Record): проверяет `ctx.ticksInState() >= targetTicks`.
* **`ChanceCondition(double chance)`** (Class): вероятностная проверка с использованием `RandomSource.create()`. Крайние значения (`>= 1.0` / `<= 0.0`) оптимизированы без вызова ГСЧ.
* **`PlayerZoneCondition(ZoneEventType eventType, String zoneName)`** (Record): проверяет наличие записи `ZoneEvent(eventType, zoneName)` в наборе `ctx.activeZoneEvents()`.

#### Логические Операторы (`dsl.ast.logical`)

* **`AndCondition` / `OrCondition`:** бинарные логические узлы с поддержкой Short-circuit evaluation (короткого замыкания вычислений).
* **`NotCondition`:** логическое инвертирование результата поддерева.

---

### 8.4. Парсинг и Загрузка Скриптов (`dsl.visitor`, `dsl.loader`)

#### `AnomalyAstBuilder` (`dsl.visitor`)

* **Класс:** `AnomalyAstBuilder` (расширяет `AnomalyDSLBaseVisitor<Object>`)
* **Назначение:** AST-посетитель (Visitor) дерева разбора ANTLR. Преобразует синтаксическое дерево файла `.anom` в модель `AnomalyScriptModel` и узлы `ICondition`.
* **Технические детали:**
* **`visitScript`:** Обход элементов верхнего уровня:
* `bindClause` — регистрирует связку состояния и JSON-конфига в `model.addBind()` (со снятием кавычек).
* `recipeBindClause` — регистрирует связку рецепта и JSON-файла рецепта в `model.addRecipeBind()`.
* `initialStateClause` — переопределяет `initialState`.
* `stateBlock` — парсит имя фазы и обходит внутренние элементы:
* `transitionRule` — строит AST условия `ICondition` и добавляет `TransitionRule` в `model.addTransition()`.
* `recipeBlock` — обходит ссылки `recipeRef` и привязывает имена рецептов к фазе через `model.addRecipeToState()`.

* **Логические условия и логика:**
* `visitParenExpr`, `visitNotExpr`, `visitAndExpr`, `visitOrExpr` — рекурсивно собирают дерево логических операций (`NotCondition`, `AndCondition`, `OrCondition`).
* `visitTimerCondition` / `visitChanceCondition` — конструируют листовые условия таймера и вероятности.
* `visitPlayerZoneCondition` — транслирует строки событий (`entered_zone` -> `ENTERED`, `exited_zone` -> `EXITED`, `in_zone` -> `IN_ZONE`), очищает имя зоны от кавычек и создает `PlayerZoneCondition`.

* **Связи:** `AnomalyDSLBaseVisitor`, `AnomalyDSLParser`, `AnomalyScriptModel`, `TransitionRule`, `ICondition`, `ZoneEventType`, `NotCondition`, `AndCondition`, `OrCondition`, `TimerCondition`, `ChanceCondition`, `PlayerZoneCondition`.

#### `AnomalyScriptLoader` (`dsl.loader`)

* **Класс:** `AnomalyScriptLoader` (имплементирует `PreparableReloadListener`)
* **Назначение:** Двухфазный асинхронный датапак-загрузчик DSL-скриптов из папок `data/<mod_id>/anomalies/<folder>/<folder>.anom`.
* **Технические детали:**
* **Асинхронная фаза (`loadScripts`):** Сканирует ресурсы `.anom`. Рассчитывает `folderIndex` с учетом наличия префикса `anomalies/` и группирует файлы по родительским папкам.
* **Валидация структуры файлов:**
* Корневые файлы в `anomalies/` без подпапки блокируются с ошибкой `CRITICAL DSL ERROR`.
* Отсутствие файла `<folderName>.anom` внутри подпапки логируется как `CRITICAL DSL ERROR`.
* При наличии нескольких `.anom` файлов в одной папке выводится `DSL WARNING`, а исполняется строго `<folderName>.anom`.

* **Компиляция:** Считывает UTF-8 стрим, пропускает через `AnomalyDSLLexer` -> `AnomalyDSLParser` -> `AnomalyAstBuilder` и формирует карту скомпилированных моделей.
* **Синхронная фаза (`apply`):** Вызывает `AnomalyScriptRegistry.clear()`, валидирует связки фаз и рецептов через `validateScriptBinds` и регистрирует скрипты в реестре.
* **Двухуровневая валидация связок (`validateScriptBinds`):**
1. **Валидация состояний (`binds`):** Требует явное наличие расширения `.json` в путях и проверяет существование фазы в датапаках через `AnomalyReloadListener.hasState(anomalyType, state)`.
2. **Валидация рецептов (`recipeBinds`):** Требует явное наличие расширения `.json` в путях и проверяет наличие зарегистрированного JSON-рецепта через `AnomalyReloadListener.hasRecipe(anomalyType, recipeName)`.

* **Связи:** `PreparableReloadListener`, `AnomalyDSLLexer`, `AnomalyDSLParser`, `AnomalyAstBuilder`, `AnomalyScriptModel`, `AnomalyScriptRegistry`, `AnomalyReloadListener`.

---

### 8.5. Контекст Исполнения и Кэширование (`dsl.context`, `dsl.cache`)

#### `EvaluationContext` (`dsl.context`)

* **Тип:** Record `EvaluationContext(AnomalyEntity anomaly, int ticksInState, Set<ZoneEvent> activeZoneEvents)`
* **Назначение:** Immutable-контекст состояния аномалии в конкретный тик.
* **Вложенные типы:**
* `enum ZoneEventType` — типы событий (`ENTERED`, `EXITED`, `IN_ZONE`).
* `record ZoneEvent(ZoneEventType type, String zoneName)` — структура события зоны.

* **Фабрика:** `simple(anomaly, ticksInState)` — облегченный вариант с пустым сетом событий.

#### `TransientZoneCache` (`dsl.cache`)

* **Класс:** `TransientZoneCache`
* **Назначение:** Потокобезопасный кэш событий зон для игроков (`ConcurrentHashMap<UUID, AnomalyCacheData>`).
* **Внутренняя структура `AnomalyCacheData`:**
* `tickEvents` (`Set<ZoneEvent>`) — одноразовые триггеры кадров (`ENTERED`, `EXITED`).
* `activeZones` (`Set<String>`) — список активных слоев, в которых игрок находится прямо сейчас (`IN_ZONE`).

* **Логика записи (`recordTransition`):**
* Игнорирует любые сущности, кроме игроков (`Player`).
* При выходе из зоны: удаляет индекс из `activeZones` и генерирует `EXITED`. Если индекс покинутой зоны не определился или сущность полностью покинула область аномалии (`currentZone == null`), принудительно очищает `activeZones.clear()`.
* При входе в зону: добавляет индекс в `activeZones` и генерирует `ENTERED`.

* **Запрос снимка (`getSnapshotAndFlush`):** Объединяет одноразовые `tickEvents` и непрерывные состояния `IN_ZONE` для всех `activeZones`, после чего атомарно очищает `tickEvents.clear()` (одноразовые события сгорают до следующего кадра).
* **Идентификация зон (`getZoneIdentifier`):** Динамически сопоставляет радиус зоны из события с конфигурацией `AnomalyDefinition` (через `AnomalyReloadListener.get(...)`) с точностью `Math.abs(...) < 0.0001`.
* **Управление памятью:** `flushTickEvents` очищает одноразовые ивенты при смене фазы, `clear(anomaly)` полностью удаляет UUID из кэша при уничтожении/деспавне энтити (`AnomalyEntity.onRemove`).
* **Связи:** `AnomalyZoneTransitionEvent`, `AnomalyDefinition`, `AnomalyReloadListener`, `Player`, `AnomalyEntity`, `EvaluationContext`.

---

### 8.6. Событийно-Ориентированная Шина (`dsl.event`)

#### `AnomalyEventListener` (`dsl.event`)

* **Класс:** `AnomalyEventListener` (`@Mod.EventBusSubscriber`)
* **Назначение:** Слушатель событий шины Forge. Подписывается на `AnomalyZoneTransitionEvent` (`@SubscribeEvent`) и перенаправляет их в `TransientZoneCache.recordTransition(event)`.
* **Связи:** `AnomalyZoneTransitionEvent`, `TransientZoneCache`.