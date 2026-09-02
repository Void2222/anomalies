### 1. Подсистема базовой сущности и компонентов (`core` + `components` + `anomaly`)

#### 1.3 `net.void_.anomalies.components.DamageComponent`

Модуль расчета и нанесения урона по сущностям.

* **Жизненный цикл:** Инициализируется и регистрируется в `AnomalyEntity` **единожды** при сборке в `ZoneFactory`. Переиспользуется на всем протяжении жизни сущности (Zero-GC стратегия).


* **Параметры:**
* `defaultDamageRange`: базовый диапазон случайных значений урона (`MinMaxRange`).


* `defaultDamageSource`: дефолтный тип урона Minecraft (`DamageSource`).




* **Механика работы:**
* `inflictDamage(@Nullable AnomalyEntity anomaly, Entity target, @Nullable ZoneConfig zone, @Nullable MinMaxRange specificRange, @Nullable DamageSource overrideSource)`: универсальный метод нанесения урона. Принимает переопределенный диапазон или источник урона (например, из конфигурации активной зоны `ZoneConfig`).


* Генерирует событие `AnomalyDamageEvent` на шине `MinecraftForge.EVENT_BUS`.


* При отсутствии отмены события сторонними модами наносит итоговый урон через `target.hurt(sourceToUse, finalDamage)`.





---

#### 1.4 `net.void_.anomalies.components.ImpulseComponent`

Модуль физики, гравитационного притяжения, вращения и позонного перемещения сущностей.

* **Параметры:**
* `xMultiplier`, `yMultiplier`, `zMultiplier`: базовые векторы линейного импульса.


* `pullToCenter`: флаг активации радиальной тяги к центру аномалии.


* `generalPhysics`: дефолтная конфигурация физики.


* `zones`: отсортированный по возрастанию радиуса список зон воздействия (`ZoneConfig`).


* `entityZones`: карта `Map<UUID, ZoneConfig>` для отслеживания текущих зон сущностей и регистрации переходов.


* `cleanupTimer`: счетчик тиков для сброса застрявших ссылок.




* **Механика работы:**
* **Защита от утечек памяти (Memory Leak Prevention):** В методе `serverTick()` раз в 20 тиков (1 секунда) вызывается метод `cleanupStaleEntities()`. Итератор проверяет карту `entityZones` и удаляет записи сущностей, которые погибли (`!isAlive()`), были удалены из мира, сменили измерение или покинули зону, с генерацией завершающего события `AnomalyZoneTransitionEvent`.


* **Декуплинг геометрии зон:** Не содержит локальных математических вычислений объема зон; определение текущего слоя делегировано в утилиту `ZoneUtils.getActiveZone()`.


* `applyImpulse(...)`: проверяет смену слоя (`currentZone != previousZone`) с генерацией события `AnomalyZoneTransitionEvent`. Вычисляет вектор движения `calculatedMovement` с учетом радиальной тяги, тангенциального вращения по нормали в центральной зоне (`spinForce`) и выталкивания по оси Y (`impulseY`).


* Ограничивает горизонтальную скорость пределами (`maxSpeed = 1.0`), предотвращая эффект "катапультирования".


* Публикует событие `AnomalyPhysicsEvent` перед изменением скорости и обновляет вектор перемещения через `target.setDeltaMovement()`.





---

#### 1.9 `net.void_.anomalies.anomaly.ZoneFactory`

Фабрика сущностей и декомпозированный сборщик функциональных компонентов. Отвечает за инстанцирование `AnomalyEntity`, последовательное наложение оверрайдов через `OverrideHelper` и регистрацию постоянных компонентов.

* **Фабричное создание (`create`):**
* `create(Level level, double x, double y, double z, String type)`: проверяет существование типа аномалии в `AnomalyReloadListener`. Инстанцирует `AnomalyEntity` через `EntityInit.ANOMALY.get().create(level)`, выставляет стартовые координаты $(x, y, z)$ и привязывает идентификатор типа `type.toLowerCase()`.




* **Декларативный пайплайн сборки (`applyComponents`):**
1. **Фиксация на сетке:** Автоматически добавляет `SnapToGridComponent`.


2. **Сборка размеров (`setupDimensions`):** Извлекает параметры габаритов из NBT или JSON через `OverrideHelper.getDimensions()` и применяет их в `setAnomalyDimensions()`.


3. **Сборка системы частиц (`setupParticles`):** Проходит по массиву `particles()`. Запрашивает скорректированные диапазоны интервалов и количества через `OverrideHelper`, после чего регистрирует экземпляры `ParticleComponent`.


4. **Сборка аудио-системы (`setupSound`):** Получает громкость, тональность и интервалы с учетом NBT-оверрайдов, подготавливает `SoundEvent` и добавляет `SoundComponent`.


5. **Сборка триггеров, физики и урона (`setupTriggerAndPhysics`):**
* **Подготовка зон:** Запрашивает отсортированный список `ZoneConfig` у `OverrideHelper.getZones()`.


* **Постоянный компонент урона:** Единожды конструирует `DamageComponent` и регистрирует его в сущности (`anomaly.addComponent(damageComp)`).


* **Постоянный компонент физики:** При наличии зон конструирует `ImpulseComponent` и регистрирует его в сущности (`anomaly.addComponent(impulseComp)`).


* **Регистрация триггера:** Конструирует `TriggerComponent`, связывая его с вызовом приватного обработчика `handleTriggerTarget()`.






* **Обработка целей триггера (`handleTriggerTarget`):**
* Игнорирует другие аномалии при `ignoreOtherAnomalies == true`.


* Вызывает `impulseComp.applyImpulse(anomaly, target)` при наличии физического модуля.


* Независимо определяет активную зону через `ZoneUtils.getActiveZone(zones, anomaly, target)`.


* Для `ItemEntity`: публикует `AnomalyItemInteractEvent`.


* Для остальных сущностей: публикует `AnomalyTriggerEvent`, накладывает эффект поджога `target.setSecondsOnFire()` и наносит урон через ранее зарегистрированный `damageComp.inflictDamage()` без пересоздания объектов.





---

### 2. Подсистема загрузки данных и конфигураций (`loader` + `data`)

#### 2.2 DTO-модели конфигураций (`anomaly.data`)

##### `ZoneConfig` (Record)

Конфигурация изолированного слоя аномалии:

* `radius`: радиус границы зоны.


* `damage`: параметры урона (`DamageConfig`).


* `physics`: специфичная физика зоны (`PhysicsConfig`).


* **Геометрический расчет (`contains`):**
* `contains(AnomalyEntity anomaly, Entity target)`: чистый математический метод проверки вхождения сущности в цилиндрический объем зоны с учетом радиуса и высоты хитбокса аномалии ($Y \in [\text{minY} - 0.5, \text{maxY} + 0.5]$). Не имеет побочных эффектов.





