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
├── api/          # Публичный Extension API (Forge Events)
│   └── event/    # Перехватываемые события триггеров, урона, физики и зон
├── anomaly/      # Data-driven слой, фабрика сборки и загрузчик JSON
│   ├── data/     # Immutable Record-классы конфигурации
│   ├── loader/   # Datapack Reload Listeners
│   └── util/     # Математические утилиты расчета зон и оверрайдов
├── client/       # Клиентская часть (Рендереры, клиентские эвенты)
├── components/   # Реализации компонентов поведения (ECS)
├── config/       # Конфигурационные менеджеры и хранилища данных (JSON/Forge Config)
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
* **Назначение:** Базовая сущность аномалии в мире. Выступает точкой привязки в пространстве, контейнером для компонентов `IAnomalyComponent` и хранителем состояния оверрайдов.
* **Технические детали:**
* **Свойства в конструкторе:** `noPhysics = true`.
* **Флаги переопределения:** `isPickable() = true`, `isInvulnerable() = true`, `canBeCollidedWith() = false`.
* **Синхронизация:** `ANOMALY_TYPE` (`SynchedEntityData.defineId(AnomalyEntity.class, EntityDataSerializers.STRING)`). При вызове `onSyncedDataUpdated()` на клиенте авто-вызывается `rebuildComponents()`.
* **Размеры сущности:** По умолчанию `1.0F x 1.0F`. Метод `setAnomalyDimensions(width, height)` вызивает `refreshDimensions()`. Динамический хитбокс возвращается через `getDimensions(Pose pose) -> EntityDimensions.scalable(width, height)`.
* **Структура NBT в сохранении мира:**
* `"AnomalyType"` (`String`) — идентификатор шаблона аномалии.
* `"CustomOverrides"` (`CompoundTag`) — NBT-тег локальных переопределений параметров.

* **Управление NBT-оверрайдами:**
* `getCustomOverrides()` / `setCustomOverrides(CompoundTag tag)` — возвращает или полностью подменяет тег с вызовом `rebuildComponents()`.
* `setOverrideDouble(String key, double value)` — записывает значение и сразу пересобирает компоненты.

* **Исполнение тика (`tick()`):**
* На клиенте (`level().isClientSide`): итеративно вызывает `component.clientTick(this)`.
* На сервере: итеративно вызывает `component.serverTick(this)`.

* **Пересборка (`rebuildComponents()`):** Очищает `components.clear()` и, если `getAnomalyType()` не пуст, вызывает `ZoneFactory.applyComponents(this, type)`.
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
* **Назначение:** Composition Root. Настраивает геометрию, визуализацию, звук и логику взаимодействия сущности при скрещивании `AnomalyDefinition` (JSON) и `customOverrides` (NBT).
* **Технические детали:**
* `create(Level, x, y, z, type)` — проверяет существование `type` в `AnomalyReloadListener`, инстанцирует сущность через `EntityInit.ANOMALY.get().create(level)` и устанавливает стартовый тип.
* `applyComponents(AnomalyEntity anomaly, String type)`:
1. Всегда добавляет `SnapToGridComponent`.
2. Извлекает `customOverrides` через `anomaly.getCustomOverrides()`.
3. `setupDimensions()`: вызывает `OverrideHelper.getDimensions()` и обновляет габариты.
4. `setupParticles()`: парсит `ParticleConfig`, считывает NBT-ключи `particleRadius`, `particleHeight`, регистрирует партиклы через `BuiltInRegistries.PARTICLE_TYPE`. Парсит `ParticleComponent.Shape` через `valueOf()` с фоллбэком на `SPHERE`.
5. `setupSound()`: считывает NBT-ключи `soundVolume`, `soundPitch`, парсит `SoundSource` через `valueOf()` с фоллбэком на `BLOCKS`.
6. `setupTriggerAndPhysics()`:
* Создает базовый `DamageComponent` с диапазоном `MinMaxRange(0, 0)` и дефолтным `damageSources().generic()`.
* Если список зон не пуст — сортирует зоны и инстанцирует `ImpulseComponent`.
* Добавляет `TriggerComponent`, передавая в него лямбду `handleTriggerTarget`.

* **Логика `handleTriggerTarget`:**
    1. Если `ignoreOtherAnomalies == true` и `target instanceof AnomalyEntity` — обработка прерывается.
    2. Если `target instanceof Player player` и `AnomalyIgnoreManager.isIgnored(player)` — обработка прерывается (игрок игнорируется аномалией).
    3. Вызывает `impulseComp.applyImpulse(anomaly, target)`.
    4. Определяет активную зону цели через `ZoneUtils.getActiveZone()`. Если `activeZone == null` — прерывает обработку.
    5. Если `target instanceof ItemEntity itemEntity` — публикует `AnomalyItemInteractEvent`.
    6. Для остальных сущностей публикует `AnomalyTriggerEvent`. При отмене события — прерывается.
    7. Если `activeZone.damage()` задан:
        * Накладывает поджигание: `target.setSecondsOnFire(fireSeconds)`.
        * Определяет источник урона через `getDamageSource()`.
        * Наносит урон через `damageComp.inflictDamage()`.

* **Связи:** `AnomalyEntity`, `AnomalyReloadListener`, `OverrideHelper`, `ZoneUtils`, `ImpulseComponent`, `DamageComponent`, `TriggerComponent`, `ParticleComponent`, `SoundComponent`, `EntityInit`.

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

* **Назначение:** Datapack-загрузчик шаблонов аномалий из JSON-файлов по пути `data/<mod_id>/anomalies/*.json`.
* **Технические детали:**
* Наследует `SimpleJsonResourceReloadListener`.
* **Настройка GSON:** Создается через `GsonBuilder` с включенным режимом `.setLenient()` и зарегистрированным кастомным адаптером `MinMaxRange.Deserializer`.
* **Статический реестр:** `Map<String, AnomalyDefinition> REGISTRY`.
* **Загрузка датапаков (`apply`):**
1. Очищает текущий реестр: `REGISTRY.clear()`.
2. Парсит JSON-ресурсы в `AnomalyDefinition`.
3. Регистрирует ключ строго по чистому нижнему регистру пути: `location.getPath().toLowerCase()` (например, ключ `"zharka"` вместо `"anomalies:zharka"`).

* **Публичный интерфейс:**
* `get(String type)` — считывает `AnomalyDefinition` из реестра по имени типа (приводя запрос к lowerCase).
* `getKeys()` — возвращает `Set<String>` всех зарегистрированных типов (используется для автокомплита в командах).
* `exists(String type)` — проверяет наличие ключа аномалии в реестре.

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
* **Назначение:** Физический движок расчета притяжения, выталкивания, вращения и отслеживания смены зон.
* **Технические детали:**
* **Конструктор:** Автоматически сортирует передаваемый список зон по возрастанию радиуса: `zones.stream().sorted(Comparator.comparingDouble(ZoneConfig::radius)).toList()`.
* **Отслеживание зон:** `Map<UUID, ZoneConfig> entityZones`.
* **Таймер очистки (`serverTick`):** Каждые 20 тиков (`cleanupTimer >= 20`) вызывает `cleanupStaleEntities()`. Проверяет сущности из `entityZones` через `serverLevel.getEntity(uuid)`. Если сущность умерла, удалена или вышла из всех зон — удаляет из карты и публикует `AnomalyZoneTransitionEvent` с `newZone = null`.
* **Метод `applyImpulse(anomaly, target, precalculatedZone)`:**
1. Проверяет смену текущей зоны цели относительно `entityZones.get(targetUuid)`.
2. При изменении зоны публикует `AnomalyZoneTransitionEvent`. Если событие отменено — прерывает расчет физики.
3. Извлекает конфигурацию `PhysicsConfig` (из `currentZone.physics()` или дефолтную `generalPhysics`).
4. **Расчет вектора притяжения (`pullToCenter == true`):**
* Центр аномалии: (ax, ay + height * 0.5, az)
* Вектор до цели: (dx, dy, dz)
* Нормализация направления с использованием единственного `Math.sqrt(distSq)`
* **Тангенциальное вращение (Spin Force):** Применяется **только** если аномалия имеет более 1 зоны (`zones.size() > 1`) и цель находится в самой внутренней зоне (`currentZone == zones.get(0)`)
* Формула близости: closeness = max(0.0, 1.0 - (dist / radius))
* Сила вращения: aggressiveSpin = spinForce * (1.5 + closeness * 3.0)
* Перпендикулярный вектор вращения: (-dirZ * aggressiveSpin, dirX * aggressiveSpin)
* **Ограничение скорости:** Если moveX² + moveZ² > 1.0, горизонтальный вектор нормализуется до ровно 1.0 блока/тик
5. **Расчет отталкивания (`pullToCenter == false`):** Добавляет фиксированные `impulseX`, `impulseY`, `impulseZ`.
6. **Событие:** Публикует `AnomalyPhysicsEvent` с результирующим `Vec3`. Если не отменено — применяет вектор через `target.setDeltaMovement()` и устанавливает `target.hurtMarked = true`.

* **Связи:** `AnomalyPhysicsEvent`, `AnomalyZoneTransitionEvent`, `ZoneUtils`, `PhysicsConfig`, `ZoneConfig`, `AnomalyEntity`.

---
### 4.3. `ParticleComponent` (`components.ParticleComponent`)

* **Класс:** `ParticleComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Клиентский компонент генерации частиц заданной геометрической формы с оптимизацией производительности.
* **Технические детали:**
* **Перечисление `Shape`:** `SPHERE`, `CYLINDER`, `DISC`.
* **Константа дистанции рендеринга:** `RENDER_DISTANCE_SQ = 4900.0D` (70² блоков).
* **Клиентский LOD (Level of Detail):** В `clientTick()` проверяет расстояние до `Minecraft.getInstance().player`. Если игрок находится дальше 70 блоков — выполнение тика прерывается.
* **Динамический таймер:** Каждые `nextTriggerTick` тиков генерирует новое значение из `intervalRange.getInt()`, сбрасывает счетчик `clientTickCounter` и запрашивает случайное количество частиц `countRange.getInt()`.
* **Оптимизация Loop Unswitching:** Проверка `switch (shape)` вынесена за пределы цикла генерации точек, исключая повторные ветвления.
* **Оптимизация Rejection Sampling:** Точки внутри объемов генерируются с помощью циклов `do-while` на примитивах без использования тригонометрических функций (`Math.sin`, `Math.cos`):
* **`SPHERE`:** Точки отбраковываются по условию rx² + ry² + rz² > 1.0. Спавнятся с базовым импульсом по оси Y = 0.02.
* **`CYLINDER`:** Отбраковка по rx² + rz² > 1.0, высота Y выбирается случайно в пределах `random.nextDouble() * height`. Базовый импульс по оси Y = 0.05.
* **`DISC`:** Отбраковка по rx² + rz² > 1.0, смещение по Y задается микро-разбросом в пределах ± 0.05. Базовый импульс по оси Y = 0.01.
* **Связи:** `ParticleOptions`, `MinMaxRange`, `Minecraft`, `AnomalyEntity`, `Level`.

---

### 4.4. `SoundComponent` (`components.SoundComponent`)

* **Класс:** `SoundComponent` (имплементирует `IAnomalyComponent`)
* **Назначение:** Клиентский компонент проигрывания пространственных звуковых эффектов с динамическими интервалами задержки.
* **Технические детали:**
* **Поля:** `soundEvent`, `intervalRange`, `soundSource`, `volume`, `pitch`.
* **Серверный тик (`serverTick`):** Пустая реализация.
* **Клиентский тик (`clientTick`):**
* Увеличивает `clientTickCounter`.
* По достижению `nextTriggerTick` генерирует новый интервал `intervalRange.getInt()`.
* Вызывает `level.playLocalSound(pos.x, pos.y, pos.z, soundEvent, soundSource, volume, pitch, false)`. Параметр `distanceDelay = false` гарантирует мгновенный старт воспроизведения.

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

* **Назначение:** Считывание и вывод исчерпывающей диагностической информации об аномалии в чат игрока с учетом локальных NBT-оверрайдов.
* **Технические детали:**
* **Метод:** `process(Player player, AnomalyEntity anomaly)`.
* Сравнивает текущие значения параметров в `customOverrides` с базовым `AnomalyDefinition` из `AnomalyReloadListener`.
* Форматирует и выводит в системный чат игрока следующие блоки данных:

1. **Идентификация:** Тип аномалии и короткий UUID (первые 8 символов).
2. **Размеры (`width` x `height`):** Выводит текущие габариты хитбокса.
3. **Слои зон, Урон и Физика:**
* Считывает количество слоев из `zones_count` в NBT (если переопределено) или берет базовый размер из `def.zones()`.
* Для каждого слоя с индексацией `#i` проверяет оверрайды NBT с приоритетом над JSON:
* Радиус: `zone_i_radius`
* Урон: `zone_i_damage`
* Физика: `zone_i_pullForce` (Тяга), `zone_i_spinForce` (Вращение), `zone_i_impulseY` (ИмпульсY)
* Горение: `fireSeconds` из JSON (если применимо).
4. **Триггер:** Радиус обнаружения целей (`expandRadius`).
5. **Физика:** Флаг и вектор притяжения (`pullToCenter`).
6. **Звук и Частицы:** Громкость/высота тона звука и радиус/высота спавна частиц.

* **Маркировка оверрайдов (`formatVal`):** Если конкретный параметр переопределен локально в NBT сущности, выводит его с выделением `§e§l[Value] §6[Override]`, иначе с подсвечиванием базового значения `§f[Value]`.

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