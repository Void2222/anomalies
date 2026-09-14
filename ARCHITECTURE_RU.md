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
├── api/          # Публичный Extension API
│   ├── behavior/ # Интерфейсы и реестр стейт-машины (IAnomalyStateBehavior)
│   └── event/    # Перехватываемые события триггеров, урона, физики и зон
├── anomaly/      # Data-driven слой, фабрика сборки и загрузчик JSON
│   ├── data/     # Immutable Record-классы конфигурации
│   ├── loader/   # Datapack Reload Listeners (мультифазовая загрузка)
│   └── util/     # Математические утилиты расчета зон и оверрайдов
├── client/       # Клиентская часть (Рендереры, клиентские эвенты)
├── components/   # Реализации компонентов поведения (ECS + StateMachineComponent)
├── config/       # Конфигурационные менеджеры и хранилища данных
├── core/         # Ядро движка (Entity, Component Interface)
├── item/         # Мультитул и процессоры управления (Strategy Pattern)
│   └── processor/# Процессоры режимов (Analyze, Modify, Relocate, Delete)
└── setup/        # Инициализация, регистрация сущностей и команды
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

* **Безопасная итерация тика:** В методах `tick()` на клиенте и сервере итерация происходит по потокобезопасной локальной копии списка `new ArrayList<>(this.components)`, что предотвращает `ConcurrentModificationException` при динамической пересборке компонентов во время работы тикера.

* **Синхронизация (SynchedEntityData):**
* `ANOMALY_TYPE` (`String`) — базовый тип аномалии.
* `CURRENT_STATE` (`String`) — активная фаза (по умолчанию `"idle"`, при установке автоматически приводится к нижнему регистру `toLowerCase()`).
* При вызове `onSyncedDataUpdated()` на клиенте отслеживается изменение `ANOMALY_TYPE` или `CURRENT_STATE` и авто-вызывается `rebuildComponents()`, чтобы визуал и звук моментально подстроились под новую фазу.
* **Размеры сущности:** По умолчанию `1.0F x 1.0F`. Метод `setAnomalyDimensions(width, height)` вызывает `refreshDimensions()`. Динамический хитбокс возвращается через `getDimensions(Pose pose) -> EntityDimensions.scalable(width, height)`.

* **Управление NBT-оверрайдами и компонентами:**
* `getCustomOverrides()` / `setCustomOverrides(CompoundTag tag)` — возвращает или подменяет тег оверрайдов с вызовом `rebuildComponents()`.
* `setOverrideDouble(key, value)` — удобный метод запись конкретного `double`-параметра в NBT с авто-вызовом `rebuildComponents()`.
* `getComponent(Class<T> type)` — дженерик-метод для поиска и извлечения активного экземпляра компонента по его классу.


* **Структура NBT в сохранении мира:**
* `"AnomalyType"` (`String`) — идентификатор шаблона аномалии.
* `"CurrentState"` (`String`) — текущая фаза жизненного цикла.
* `"CustomOverrides"` (`CompoundTag`) — NBT-тег локальных переопределений параметров.


* **Управление NBT-оверрайдами:**
* `getCustomOverrides()` / `setCustomOverrides(CompoundTag tag)` — возвращает или полностью подменяет тег с вызовом `rebuildComponents()`.

* **Исполнение тика (`tick()`):**
* На клиенте (`level().isClientSide`): итеративно вызывает `component.clientTick(this)`.
* На сервере: итеративно вызывает `component.serverTick(this)`.

* **Пересборка (`rebuildComponents()`):** Очищает `components.clear()` и вызывает `ZoneFactory.applyComponents(this, type, getCurrentState())` для динамической подмены логики на лету.
* **Спавн-пакет:** `getAddEntityPacket()` возвращает `NetworkHooks.getEntitySpawningPacket(this)`.

* **Связи:** `ZoneFactory`, `IAnomalyComponent`, `CompoundTag`, `NetworkHooks`, `SynchedEntityData`.

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
* **Создание сущности (`create`):** Проверяет наличие `type` в `AnomalyReloadListener`, создаёт `AnomalyEntity` через `EntityInit.ANOMALY.get().create(level)`, выставляет координаты и регистронезависимый тип.
* **Метод `applyComponents(AnomalyEntity anomaly, String type, String state)`:**
1. Всегда добавляет `SnapToGridComponent`.
2. На серверной стороне **всегда** навешивает `StateMachineComponent` (оркестратор фазовых переходов).
3. Запрашивает `AnomalyDefinition` для текущей фазы (`state`) из `AnomalyReloadListener.get(type, state)`.
4. Получает NBT-оверрайды `anomaly.getCustomOverrides()` (имеют приоритет над свойствами JSON).
5. `setupDimensions()`: считывает габариты через `OverrideHelper.getDimensions()` и обновляет размеры хитбокса.
6. `setupParticles()`: парсит и навешивает `ParticleComponent` для каждой конфигурации частицы (с поддержкой LOD и мгновенного спавна).
7. `setupSound()`: конструирует `SoundComponent` под выбранный источник звука (`SoundSource`) и регистрирует звуковой ивент.
8. `setupTriggerAndPhysics()`: настраивает компоненты урона, физики и области обнаружения.

* **Динамическая настройка физики и триггера (`setupTriggerAndPhysics`):**
* **Авторасчет радиуса сканирования:** Вычисляет `expandRadius` как максимальный радиус среди всех зон (`ZoneConfig::radius`) с фоллбеком на NBT-оверрайд `expandRadius`.
* **Отзывчивый сканер:** Автоматически создает `TriggerComponent` с интервалом проверки `(1, 1)` тиков для мгновенной реакции на вход/выход сущностей.
* **Физика (`ImpulseComponent`):** Создается и навешивается только если список зон не пуст (`!zones.isEmpty()`).

* **Логика обработки целей (`handleTriggerTarget`):**
1. Проверяет флаг `ignoreOtherAnomalies == true` для сущностей класса `AnomalyEntity`.
2. Проверяет регистрацию игрока в `AnomalyIgnoreManager.isIgnored(player)`.
3. При наличии `impulseComp` исполняет `impulseComp.applyImpulse(anomaly, target)`.
4. Определяет текущую зону через `ZoneUtils.getActiveZone()`. Если `activeZone == null` — прекращает обработку.
5. Для предметов (`ItemEntity`) публикует `AnomalyItemInteractEvent`.
6. Для остальных сущностей публикует `AnomalyTriggerEvent` (с прерыванием при отмене).
7. При наличии параметров урона (`activeZone.damage() != null`):
* Применяет эффект поджога `target.setSecondsOnFire(fireSeconds)`.
* Вычисляет источник урона `getDamageSource()` (`fire`, `lightning`, `magic`, `generic`).
* Наносит урон через `damageComp.inflictDamage()`.

* **Связи:** `AnomalyEntity`, `AnomalyReloadListener`, `OverrideHelper`, `ZoneUtils`, `ImpulseComponent`, `DamageComponent`, `TriggerComponent`, `ParticleComponent`, `SoundComponent`, `StateMachineComponent`, `EntityInit`, `AnomalyIgnoreManager`.

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

#### `AnomalyReloadListener` (`anomaly.loader`)

* **Назначение:** Datapack-загрузчик шаблонов аномалий и их состояний из JSON-файлов по пути `data/<mod_id>/anomalies/`.
* **Технические детали:**
* Наследует `SimpleJsonResourceReloadListener`.
* **Статический реестр:** Вложенная карта `Map<String, AnomalyDefinition Map<String,>> REGISTRY` (Тип -> Состояние -> Конфиг).
* **Загрузка датапаков (`apply`):**
1. Парсит структуру папок. Файлы вида `anomalies/zharka/idle.json` ложатся по ключам `zharka` -> `idle`.
2. **Фоллбэк для легаси:** Если найден файл `anomalies/zharka.json` (без подпапки), он автоматически регистрируется как состояние `idle` для типа `zharka`. Старые датапаки не ломаются.

* **Публичный интерфейс:**
* `get(String type, String state)` — считывает `AnomalyDefinition` для конкретной фазы.
* `getStates(String type)` — возвращает набор загруженных состояний для аномалии.
* `getKeys()` — возвращает `Set<String>` всех зарегистрированных типов (для автокомплита).

* **Связи:** `SimpleJsonResourceReloadListener`, `AnomalyDefinition`, `MinMaxRange`, `Gson`, `ResourceLocation`.

---

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
* **Назначение:** Клиентский компонент генерации частиц заданной геометрической формы с оптимизацией производительности, мгновенной реакцией на смену фаз и защитой от зацикливания.
* **Технические детали:**
* **Перечисление `Shape`:** `SPHERE`, `CYLINDER`, `DISC`.
* **Константа дистанции рендеринга:** `RENDER_DISTANCE_SQ = 4900.0D` (70² блоков).
* **Клиентский LOD (Level of Detail):** В `clientTick()` проверяет расстояние до `Minecraft.getInstance().player`. Если игрок находится дальше 70 блоков — спавн частиц отменяется.
* **Мгновенный старт и безопасный таймер:**
* В конструкторе выставляется `nextTriggerTick = 0`, обеспечивая спавн визуала **сразу на первом же тике (0-й тик)** при инициализации новой фазы.
* При наступлении срабатывания следующий интервал вычисляется как `nextTriggerTick = Math.max(1, intervalRange.getInt())`, что полностью исключает нулевые интервалы и предотвращает микрофризы клиента.

* **Оптимизация Loop Unswitching:** Проверка `switch (shape)` вынесена за пределы цикла спавна, исключая ветвления внутри итераций.
* **Оптимизация Rejection Sampling и защита от зацикливания:** Точки внутри объемов генерируются с помощью циклов `do-while` на примитивах без использования тяжелых тригонометрических функций (`Math.sin`, `Math.cos`). Каждая итерация ограничена счетчиком попыток (`attempts < 10`), что гарантирует защиту потока рендеринга от бесконечных циклов:
* **`SPHERE`:** Отбраковка по `rx² + ry² + rz² > 1.0`. Спавн с базовым импульсом по оси Y = 0.02.
* **`CYLINDER`:** Отбраковка по `rx² + rz² > 1.0`, случайная высота Y в пределах `random.nextDouble() * height`. Базовый импульс по оси Y = 0.05.
* **`DISC`:** Отбраковка по `rx² + rz² > 1.0`, случайное смещение Y в пределах ±0.05. Базовый импульс по оси Y = 0.01.

* **Связи:** `ParticleOptions`, `MinMaxRange`, `Minecraft`, `AnomalyEntity`, `Level`.

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
* **Очистка временного кэша:** Вызывает `TransientZoneCache.clear(anomaly)` для сброса устаревших событий зон и одноразовых триггеров.
* **Смена состояния:** Устанавливает новое состояние через `anomaly.setCurrentState(nextState)`, инициируя пересборку компонентов сущности.

* **Клиентский тик (`clientTick`):** Пустая реализация (Server Authority).
* **Связи:** `AnomalyEntity`, `IAnomalyStateBehavior`, `AnomalyScriptRegistry`, `AnomalyBehaviorRegistry`, `TransientZoneCache`, `ZoneFactory`.

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
* `onLeftClickBlock`: При клике по блоку вычисляет целевую позицию `(x + 0.5, y + 1.0, z + 0.5)`, устанавливает новые координаты сущности через `anomaly.setPos()` и стирает `SelectedAnomaly` из NBT предмета.

* **Режим 2: Перенос по смещению (Shift + ПКМ -> Чат):**
* Устанавливает флаг NBT `WaitingForOffset = true`.
* `onChat`: Парсит сообщение формата `dx dy dz` (например, `0.5 0 -0.5`). Извлекает координаты `position()`, добавляет смещение, перемещает сущность и сбрасывает флаги NBT.

* **Связи:** `AnomalyEntity`, `ItemStack`, `CompoundTag`, `ServerLevel`, `BlockPos`.


### 5.3. Предмет Мультитула и Перечисление Режимов (`item`)

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

---

## 6. Клиентская Часть (`client`)

*Пакет:* `net.void_.anomalies.client`

### 6.1. Невидимый Рендерер Сущности

* **Класс:** `AnomalyRenderer` (расширяет `EntityRenderer<AnomalyEntity>`)
* **Назначение:** Отключение стандартной трехмерной полигональной модели сущности при сохранении активного клиенского тикинга.
* **Технические детали:**
* **Текстура-заглушка:** `getTextureLocation()` возвращает путь `anomalies:textures/entity/anomaly.png`.
* **Управление видимостью (`shouldRender`):** Метод явно возвращает `true`. Это предотвращает отсечение (culling) сущности движком рендеринга Minecraft и гарантирует постоянное исполнение клиентских компонентов (`ParticleComponent`, `SoundComponent`), несмотря на отсутствие физической геометрии или полигональной модели.

* **Связи:** `EntityRenderer`, `AnomalyEntity`, `ParticleComponent`, `SoundComponent`.

---

### 6.2. Регистратор Клиентской Части (`client.ClientSetup`)

* **Класс:** `ClientSetup`
* **Назначение:** Привязка визуализаторов сущностей на стороне клиента.
* **Технические детали:**
* Помечен аннотацией `@Mod.EventBusSubscriber(modid = "anomalies", value = Dist.CLIENT, bus = Bus.MOD)`.
* **Перехват события (`registerRenderers`):** Подписан на `EntityRenderersEvent.RegisterRenderers` на шине мода.
* Связывает зарегистрированный тип сущности `EntityInit.ANOMALY.get()` с пустой моделью рендерера `AnomalyRenderer::new`.

* **Связи:** `EntityRenderersEvent.RegisterRenderers`, `EntityInit`, `AnomalyRenderer`, `Dist.CLIENT`.

---

## 7. Инициализация, Команды и Точка Входа (`setup` / `root`)

*Пакеты:* `net.void_.anomalies.setup`, `net.void_.anomalies`

### 7.1. Командный Интерфейс Администрирования

* **Класс:** `AnomalyCommands`
* **Назначение:** Регистрация и обработка консольных команд администрирования аномалий.
* **Технические детали:**
    * **Уровень доступа:** Требует `hasPermission(2)` (уровень оператора/администратора).
    * **Структура команд:**
        * `/anomaly create <type>` — Спавнит аномалию указанного типа по координатам игрока. Поддерживает динамический автокомплит типов из `AnomalyReloadListener.getKeys()`.
        * `/anomaly ignore <player> <state>` — Устанавливает статус игнорирования игрока аномалиями (`true`/`false`). Сохраняет значение через `AnomalyIgnoreManager.setIgnored()`.
        * `/anomaly state set <state>` — принудительно переключает фазу аномалии (через которую проходит луч зрения) для тестирования переходов и визуальных эффектов в реальном времени. Автокомплит фаз подтягивается динамически.

* **Связи:** `CommandDispatcher`, `CommandSourceStack`, `ZoneFactory`, `AnomalyReloadListener`, `AnomalyIgnoreManager`, `AnomalyEntity`.

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

### 8.1. Модели Данных и Реестр Скриптов (`dsl.model`, `dsl.registry`)

*Пакеты:* `net.void_.anomalies.dsl.model`, `net.void_.anomalies.dsl.registry`

#### `TransitionRule` (`dsl.model`)

* **Тип:** Record
* **Назначение:** Immutable DTO правила перехода между фазами аномалии.
* **Поля:**
* `ICondition condition` — AST-дерево условия, необходимое для совершения перехода.
* `String targetState` — имя целевого состояния, в которое переходит аномалия при истинности условия.


* **Связи:** `ICondition`, `AnomalyScriptModel`.

#### `AnomalyScriptModel` (`dsl.model`)

* **Класс:** `AnomalyScriptModel` (имплементирует `api.behavior.IAnomalyStateBehavior`)
* **Назначение:** Исполняемый runtime-скрипт аномалии. Хранит связки фаз с конфигурациями, графы переходов и выполняет роль стейт-машины при тике сущности.
* **Поля:**
* `Map<String, String> binds` — маппинг имени состояния на путь к JSON-файлу его конфигурации (например, `"idle" -> "anomalies/zharka/idle.json"`).
* `Map<String, List<TransitionRule>> transitions` — граф переходов (состояние -> список правил `TransitionRule`).
* `String initialState` — стартовое состояние жизненного цикла (по умолчанию `"idle"`).

* **Технические детали:**
* `addBind(state, jsonPath)` / `addTransition(state, rule)` — методы наполнения модели при парсинге.
* **Исполнение тика (`onTick`):**
1. Извлекает активное состояние сущности `anomaly.getCurrentState()`.
2. Если для текущего состояния отсутствуют правила `transitions`, возвращает `null` (переход не требуется).
3. Запрашивает снимки событий зон через `TransientZoneCache.getSnapshotAndFlush(anomaly)` (с авто-очисткой сгоревших фреймов).
4. Создает экземпляр `EvaluationContext(anomaly, ticksInState, zoneEvents)`.
5. Последовательно проверяет правила в порядке их добавления (`rule.condition().test(ctx)`). Первый сработавший `rule` возвращает `targetState`.

* **Связи:** `IAnomalyStateBehavior`, `TransitionRule`, `TransientZoneCache`, `EvaluationContext`, `AnomalyEntity`.

#### `AnomalyScriptRegistry` (`dsl.registry`)

* **Класс:** `AnomalyScriptRegistry`
* **Назначение:** Глобальный потокобезопасный реестр для хранения и доступа к скомпилированным DSL-скриптам аномалий (`AnomalyScriptModel`).
* **Технические детали:**
* **Потокобезопасный кэш:** `Map<String, AnomalyScriptModel> SCRIPTS = new ConcurrentHashMap<>()`. Использование `ConcurrentHashMap` гарантирует корректный параллельный доступ и безопасность данных при фоновой асинхронной перезагрузке датапаков (команда `/reload`).
* **Нормализация ключей:** Все типы аномалий автоматически приводятся к нижнему регистру (`toLowerCase()`) при записи и чтении для исключения ошибок регистра.
* **Методы:**
* `register(String type, AnomalyScriptModel script)` — регистрирует скрипт с предварительной валидацией на `null`.
* `get(String type)` — возвращает `Optional<AnomalyScriptModel>`, безопасно обрабатывая `null`-запросы и отсутствующие ключи.
* `hasScript(String type)` — выполняет быстрый чек наличия кастомного DSL-скрипта для типа аномалии.
* `clear()` — атомарно очищает реестр перед повторным парсингом скриптов при перезагрузке ресурсов.

* **Связи:** `AnomalyScriptModel`, `StateMachineComponent`, `AnomalyScriptLoader`.

---


### 8.2. Абстрактное Синтаксическое Дерево (AST) Условий (`dsl.ast`)

*Пакеты:* `net.void_.anomalies.dsl.ast`, `net.void_.anomalies.dsl.ast.leaf`, `net.void_.anomalies.dsl.ast.logical`

#### `ICondition` (`dsl.ast`)

* **Интерфейс:** `@FunctionalInterface ICondition`
* **Назначение:** Единый контракт узла синтаксического дерева условий.

* **Метод:** `boolean test(EvaluationContext ctx)` — выполняет вычисление условия в заданном контексте исполнения `EvaluationContext`.

* **Связи:** `EvaluationContext`.

#### Листовые Предикаты / Leaf Conditions (`dsl.ast.leaf`)

* **`TimerCondition(int targetTicks)`** (Record)
* **Назначение:** Проверка времени пребывания аномалии в текущей фазе.

* **Вычисление:** Возвращает `true`, если счетчик тиков сущности `ctx.ticksInState()` достиг или превысил `targetTicks`.

* **`ChanceCondition`** (Class)
* **Назначение:** Вероятностная проверка совершения перехода.

* **Технические детали:** При инициализации принимает `chance` (`double`). Пограничные значения `chance >= 1.0` возвращают `true`, `chance <= 0.0` — `false`. В остальных случаях использует генератор `RandomSource.create()` и сравнивает `random.nextDouble() < chance`.

* **`PlayerZoneCondition`** (Record)
* **Назначение:** Проверка наступления события взаимодействия игрока с зоной аномалии.

* **Поля:** `EvaluationContext.ZoneEventType eventType`, `String zoneName`.

* **Вычисление:** Проверяет наличие записи `ZoneEvent(eventType, zoneName)` в сете активных ивентов `ctx.activeZoneEvents()`.

#### Логические Операторы / Logical Nodes (`dsl.ast.logical`)

* **`AndCondition(ICondition left, ICondition right)`**
* **Назначение:** Логическое "И" (`&&`).

* **Оптимизация:** Short-circuit evaluation — если вычисление `left.test(ctx)` возвращает `false`, правое дерево `right` не вычисляется.

* **`OrCondition(ICondition left, ICondition right)`**
* **Назначение:** Логическое "ИЛИ" (`||`).

* **Оптимизация:** Short-circuit evaluation — если `left.test(ctx)` возвращает `true`, правое дерево `right` не вычисляется.

* **`NotCondition(ICondition target)`**
* **Назначение:** Логическое инвертирование "НЕ" (`!`) результата подчиненного условия `target.test(ctx)`.

* **Связи:** `ICondition`, `EvaluationContext`, `RandomSource`.

### 8.3. Парсинг и Загрузка Скриптов (`dsl.visitor`, `dsl.loader`)

*Пакеты:* `net.void_.anomalies.dsl.visitor`, `net.void_.anomalies.dsl.loader`

#### `AnomalyAstBuilder` (`dsl.visitor`)

* **Класс:** `AnomalyAstBuilder` (расширяет `AnomalyDSLBaseVisitor<Object>`)
* **Назначение:** AST-посетитель (Visitor) дерева разбора ANTLR. Преобразует синтаксическое дерево файла `.anom` в исполняемую модель `AnomalyScriptModel` и вложенные узлы `ICondition`.

* **Технические детали:**
* `visitScript`: главный входной метод. Итерируется по инструкциям файла (`StatementContext`), наполняет маппинг `binds`, задает `initialState` и транслирует блоки `stateBlock` в списки правил `TransitionRule`.
* `visitParenExpr`, `visitNotExpr`, `visitAndExpr`, `visitOrExpr`: создают логические узлы AST (`NotCondition`, `AndCondition`, `OrCondition`) с рекурсивным обходом поддеревьев.
* `visitTimerCondition` / `visitChanceCondition`: считывают примитивные значения из токенов и формируют `TimerCondition` и `ChanceCondition`.
* `visitPlayerZoneCondition`: очищает кавычки имени зоны, считывает имя события и приводит его к enum `ZoneEventType` (`entered_zone` -> `ENTERED`, `exited_zone` -> `EXITED`, `in_zone` -> `IN_ZONE`).При неизвестном типе события выбрасывает `IllegalArgumentException`.

* **Связи:** `AnomalyDSLBaseVisitor`, `AnomalyDSLParser`, `AnomalyScriptModel`, `TransitionRule`, `ICondition`, `ZoneEventType`.

#### `AnomalyScriptLoader` (`dsl.loader`)

* **Класс:** `AnomalyScriptLoader` (имплементирует `PreparableReloadListener`)
* **Назначение:** Асинхронный датапак-загрузчик DSL-скриптов с расширением `.anom` из каталога `data/<mod_id>/anomalies/`.

* **Технические детали:**
* **Двухфазная загрузка (`reload`):**
1. **Асинхронная фаза (`loadScripts`):** сканирует ресурсы датапаков на файлы `.anom` через `ResourceManager`. Для каждого файла запускает лексер ANTLR (`AnomalyDSLLexer`) и парсер (`AnomalyDSLParser`), после чего передает дерево разбора в `AnomalyAstBuilder`. Имя типа аномалии автоматически извлекается из имени файла (например, `zharka.anom` -> `"zharka"`).
2. **Синхронная фаза (`apply`):** очищает `AnomalyScriptRegistry.clear()`, выполняет валидацию связок фаз `validateScriptBinds` и регистрирует новые скрипты в `AnomalyScriptRegistry`.

* **Валидация (`validateScriptBinds`):** проверяет соответствие декларированных в DSL связок (`binds`) реальным загруженным JSON-конфигурациям из `AnomalyReloadListener`. При отсутствии совпадения выводит предупреждение в лог (`LOGGER.warn`).

* **Связи:** `PreparableReloadListener`, `ResourceManager`, `AnomalyDSLLexer`, `AnomalyDSLParser`, `AnomalyAstBuilder`, `AnomalyScriptModel`, `AnomalyScriptRegistry`, `AnomalyReloadListener`.

---

### 8.4. Контекст Исполнения и Кэширование (`dsl.context`, `dsl.cache`)

*Пакеты:* `net.void_.anomalies.dsl.context`, `net.void_.anomalies.dsl.cache`

#### `EvaluationContext` (`dsl.context`)

* **Тип:** Record
* **Назначение:** Immutable-контекст состояния аномалии в конкретный момент тика. Передается в AST-условия `ICondition.test(ctx)` для проверки логических выражений DSL.

* **Поля:**
* `AnomalyEntity anomaly` — ссылка на сущность аномалии.
* `int ticksInState` — количество тиков, проведенное аномалией в текущей фазе.
* `Set<ZoneEvent> activeZoneEvents` — набо снимков событий и состояний зон.

* **Вложенные типы:**
* `enum ZoneEventType` — типы триггеров взаимодействия с зоной (`ENTERED`, `EXITED`, `IN_ZONE`).
* `record ZoneEvent(ZoneEventType type, String zoneName)` — структура конкретного события/состояния зоны.

* **Статический фабричный метод:** `simple(anomaly, ticksInState)` — создает упрощенный контекст с пустым множеством событий `Collections.emptySet()`.

* **Связи:** `AnomalyEntity`, `ICondition`, `TransientZoneCache`.

#### `TransientZoneCache` (`dsl.cache`)

* **Класс:** `TransientZoneCache`
* **Назначение:** Высокопроизводительный потокобезопасный кэш одноразовых (`ENTERED`, `EXITED`) и непрерывных (`IN_ZONE`) событий нахождения игроков в зонах аномалии для DSL-движка.
* **Технические детали:**
* **Хранилище:** `Map<UUID, AnomalyCacheData> CACHE = new ConcurrentHashMap<>()` для доступа по UUID сущности аномалии.
* **Структура `AnomalyCacheData`:**
* `tickEvents` (`Set<ZoneEvent>`) — кэш одноразовых событий тика с первоначальной емкостью 4.
* `activeZones` (`Set<String>`) — долгосрочный набор строковых индексов зон, в которых игрок находится в текущий момент.

* **Запись переходов (`recordTransition`):**
1. Игнорирует все сущности, кроме игроков (`!(event.getTarget() instanceof Player)`).
2. При выходе из зоны (`previousZone != null`) удаляет индекс из `activeZones` и генерирует `ZoneEventType.EXITED`.
3. **Страховочный сброс:** Если индекс покинутой зоны не удалось определить или если сущность полностью покинула область аномалии (`currentZone == null`), принудительно очищает `activeZones.clear()`.
4. При входе в зону (`currentZone != null`) добавляет индекс в `activeZones` и генерирует `ZoneEventType.ENTERED`.

* **Извлечение снимка (`getSnapshotAndFlush`):**
1. Объединяет сгоревшие одноразовые триггеры `tickEvents` и актуальные статусы `IN_ZONE` для всех элементов `activeZones`.
2. Очищает `data.tickEvents.clear()` (одноразовые события входа/выхода сгорают до следующего кадра).
* **Прямая идентификация зон (`getZoneIdentifier`):** Вычисляет строковый индекс слоя (0, 1, 2...) напрямую из определения `AnomalyDefinition` через `AnomalyReloadListener.get(...)`, сравнивая радиусы зон с погрешностью `Math.abs(...) < 0.0001` (отвязка от `ImpulseComponent`).
* **Очистка (`clear`):** Полностью удаляет запись аномалии из кэша при смене фазы или уничтожении сущности.

* **Связи:** `AnomalyZoneTransitionEvent`, `AnomalyDefinition`, `AnomalyReloadListener`, `Player`, `AnomalyEntity`, `ZoneConfig`, `EvaluationContext`.

### 8.5. Событийно-Ориентированная Шина (`dsl.event`)

*Пакет:* `net.void_.anomalies.dsl.event`

#### `AnomalyEventListener` (`dsl.event`)

* **Класс:** `AnomalyEventListener`
* **Назначение:** Слушатель событий шины Forge, связывающий игровое взаимодействие сущностей с кэшем DSL-движка.
* **Технические детали:**
* Аннотирован `@Mod.EventBusSubscriber` для автоматической регистрации на главной шине событий `MinecraftForge.EVENT_BUS`.
* **Перехват смены зон (`onZoneTransition`):**
* Подписан на событие `AnomalyZoneTransitionEvent` через аннотацию `@SubscribeEvent`.
* Перенаправляет полученное событие в `TransientZoneCache.recordTransition(event)`, обеспечивая своевременную фиксацию входа, выхода и присутствия игроков в зонах аномалий для последующего вычисления AST-условий `PlayerZoneCondition`.

* **Связи:** `AnomalyZoneTransitionEvent`, `TransientZoneCache`, `@Mod.EventBusSubscriber`, `@SubscribeEvent`.