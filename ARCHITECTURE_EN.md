# Anomalies Mod — Architecture & Project Structure

> **Document Purpose:** Complete specification of the anomaly engine architecture, package structure, data models, and lifecycle for developers and addon creators.
> **Target Platform:** `Minecraft Forge / Java 17+`

---

## 📐 Architectural Principles & Standards

1. **Composition Over Inheritance:** The `AnomalyEntity` entity serves as a lightweight runtime container. All application logic (physics, rendering, damage dealing, triggers) is extracted into independent components (`IAnomalyComponent`).
2. **Data-Driven & Overrides:** Anomaly configuration is defined by a base JSON template (`AnomalyDefinition`), but can be overridden for a specific in-world entity via NBT (`customOverrides`). NBT always takes precedence over the template.
3. **Server Authority & Client Presentation:** Game logic, area calculations, effect application, and target filtering occur strictly on the server side. The client side handles rendering, particles (`ParticleComponent`), and spatial sound (`SoundComponent`).
4. **Unified Class Description Standard:**
    * **Purpose:** Clear single responsibility area.
    * **Technical Details:** Constants, NBT keys, events, tickers, formulas.
    * **Relationships:** Interaction with other components, events, and registries.

---

## 🗺 Package Tree

```text
net.void_.anomalies
├── api/          # Public Extension API (Forge Events)
│   └── event/    # Interceptable events for triggers, damage, physics, and zones
├── anomaly/      # Data-driven layer, assembly factory, and JSON loader
│   ├── data/     # Immutable Record configuration classes
│   ├── loader/   # Datapack Reload Listeners
│   └── util/     # Math utilities for zone calculations and overrides
├── client/       # Client side (Renderers, client events)
├── components/   # Behavior component implementations (ECS)
├── config/       # Configuration managers and data stores (JSON/Forge Config)
├── core/         # Engine core (Entity, Component Interface)
├── item/         # Multitool and management processors (Strategy Pattern)
│   └── processor/# Mode processors (Analyze, Modify, Relocate, Delete)
└── setup/        # Initialization, entity registration, and commands
```

---

## 1. Core Engine (`core`)

*Package:* `net.void_.anomalies.core`

### 1.1. Entity Runtime Container (`core.AnomalyEntity`)

* **Class:** `AnomalyEntity` (extends `net.minecraft.world.entity.Entity`)
* **Purpose:** Base anomaly entity in the world. Acts as a spatial anchor point, a container for `IAnomalyComponent` instances, and a holder of override state.
* **Technical Details:**
    * **Constructor Properties:** `noPhysics = true`.
    * **Override Flags:** `isPickable() = true`, `isInvulnerable() = true`, `canBeCollidedWith() = false`.
    * **Synchronization:** `ANOMALY_TYPE` (`SynchedEntityData.defineId(AnomalyEntity.class, EntityDataSerializers.STRING)`). Calling `onSyncedDataUpdated()` on the client automatically invokes `rebuildComponents()`.
    * **Entity Dimensions:** Defaults to `1.0F x 1.0F`. The `setAnomalyDimensions(width, height)` method calls `refreshDimensions()`. The dynamic hitbox is returned via `getDimensions(Pose pose) -> EntityDimensions.scalable(width, height)`.
    * **NBT Structure in World Save:**
        * `"AnomalyType"` (`String`) — Anomaly template identifier.
        * `"CustomOverrides"` (`CompoundTag`) — NBT tag containing local parameter overrides.
    * **NBT Overrides Management:**
        * `getCustomOverrides()` / `setCustomOverrides(CompoundTag tag)` — Returns or completely replaces the tag with a call to `rebuildComponents()`.
        * `setOverrideDouble(String key, double value)` — Writes a value and immediately rebuilds components.
    * **Tick Execution (`tick()`):**
        * On client (`level().isClientSide`): Iteratively calls `component.clientTick(this)`.
        * On server: Iteratively calls `component.serverTick(this)`.
    * **Rebuilding (`rebuildComponents()`):** Clears `components.clear()` and, if `getAnomalyType()` is not empty, calls `ZoneFactory.applyComponents(this, type)`.
    * **Spawn Packet:** `getAddEntityPacket()` returns `NetworkHooks.getEntitySpawningPacket(this)`.
    * **Relationships:** `ZoneFactory`, `IAnomalyComponent`, `CompoundTag`, `NetworkHooks`, `SynchedEntityData`.

---

### 1.2. Component Contract (`core.IAnomalyComponent`)

* **Interface:** `IAnomalyComponent`
* **Purpose:** Unified anomaly behavior contract.
* **Technical Details:**
    * `default void serverTick(AnomalyEntity anomaly)` — Called every tick on the server (triggers, physics, cleanup).
    * `default void clientTick(AnomalyEntity anomaly)` — Called every tick on the client (particles, audio).
    * `default void save(CompoundTag tag)` / `default void load(CompoundTag tag)` — Interface methods for state serialization.
    * **Relationships:** `AnomalyEntity`.

---

### 1.3. Player Ignore Manager (`config.AnomalyIgnoreManager`)

* **Class:** `AnomalyIgnoreManager`
* **Purpose:** Persistent storage and verification of the list of players immune/protected from anomaly effects.
* **Technical Details:**
    * **File Path:** `config/anomalies_ignored_players.json`.
    * **In-Memory Cache:** `Set<UUID> IGNORED_PLAYERS` with lazy loading on first access (`ensureLoaded()`).
    * `isIgnored(Player player)` / `isIgnored(UUID uuid)` — Checks player status.
    * `setIgnored(UUID uuid, boolean ignore)` — Adds or removes a UUID from the set and atomically rewrites the JSON file on disk via GSON.
* **Relationships:** `ZoneFactory`, `TriggerComponent`, `AnomalyCommands`.

---

## 2. Factory and Configuration Data (`anomaly`)

*Package:* `net.void_.anomalies.anomaly`

### 2.1. Assembly Factory (`anomaly.ZoneFactory`)

* **Class:** `ZoneFactory`
* **Purpose:** Composition Root. Configures spatial geometry, rendering, audio, and entity interaction logic by merging an `AnomalyDefinition` (JSON) with `customOverrides` (NBT).
* **Technical Details:**
    * `create(Level, x, y, z, type)` — Validates existence of `type` in `AnomalyReloadListener`, instantiates the entity via `EntityInit.ANOMALY.get().create(level)`, and sets its initial type.
    * `applyComponents(AnomalyEntity anomaly, String type)`:
        1. Always adds `SnapToGridComponent`.
        2. Extracts `customOverrides` via `anomaly.getCustomOverrides()`.
        3. `setupDimensions()`: Calls `OverrideHelper.getDimensions()` and updates dimensions.
        4. `setupParticles()`: Parses `ParticleConfig`, reads NBT keys `particleRadius`, `particleHeight`, and registers particles via `BuiltInRegistries.PARTICLE_TYPE`. Parses `ParticleComponent.Shape` via `valueOf()` with fallback to `SPHERE`.
        5. `setupSound()`: Reads NBT keys `soundVolume`, `soundPitch`, and parses `SoundSource` via `valueOf()` with fallback to `BLOCKS`.
        6. `setupTriggerAndPhysics()`:
            * Creates a base `DamageComponent` with range `MinMaxRange(0, 0)` and default `damageSources().generic()`.
            * If the zone list is not empty — sorts the zones and instantiates `ImpulseComponent`.
            * Adds `TriggerComponent`, passing the `handleTriggerTarget` lambda into it.

* **`handleTriggerTarget` Logic:**
    1. If `ignoreOtherAnomalies == true` and `target instanceof AnomalyEntity` — execution halts.
    2. If `target instanceof Player player` and `AnomalyIgnoreManager.isIgnored(player)` — execution halts (player is ignored by anomalies).
    3. Invokes `impulseComp.applyImpulse(anomaly, target)`.
    4. Determines target's active zone via `ZoneUtils.getActiveZone()`. If `activeZone == null` — execution halts.
    5. If `target instanceof ItemEntity itemEntity` — posts `AnomalyItemInteractEvent`.
    6. For all other entities — posts `AnomalyTriggerEvent`. If event is canceled — execution halts.
    7. If `activeZone.damage()` is present:
        * Applies ignite effect: `target.setSecondsOnFire(fireSeconds)`.
        * Resolves damage source via `getDamageSource()`.
        * Inflicts damage via `damageComp.inflictDamage()`.

* **Relationships:** `AnomalyEntity`, `AnomalyReloadListener`, `OverrideHelper`, `ZoneUtils`, `ImpulseComponent`, `DamageComponent`, `TriggerComponent`, `ParticleComponent`, `SoundComponent`, `EntityInit`.

---

### 2.2. Configuration Data Models (`anomaly.data`)

#### `AnomalyDefinition` (Record)

* **Purpose:** DTO representing an anomaly template loaded from JSON.
* **Fields:** `SizeConfig size`, `List<ParticleConfig> particles`, `SoundConfig sound`, `TriggerConfig trigger`, `List<ZoneConfig> zones`, `PhysicsConfig physics`, `Boolean ignoreOtherAnomalies`.

#### `ZoneConfig` (Record)

* **Purpose:** DTO representing parameters of a specific spatial zone (layer) within an anomaly.
* **Fields:** `double radius`, `DamageConfig damage`, `PhysicsConfig physics`.
* **Method `contains(AnomalyEntity anomaly, Entity target)`:**
    * Checks cylindrical volume of the zone.
    * Calculates horizontal distance: dx² + dz² <= radius²
    * Checks vertical Y alignment: Target's Y-coordinate falls within the bounds [anomaly.y - 0.5, anomaly.y + anomaly.height + 0.5]

* **Relationships:** `DamageConfig`, `PhysicsConfig`, `AnomalyEntity`, `Entity`.

---

### 2.3. Helper Utilities and JSON Loader (`anomaly.util` / `anomaly.loader`)

*Packages:* `net.void_.anomalies.anomaly.util`, `net.void_.anomalies.anomaly.loader`

#### `OverrideHelper` (`anomaly.util`)

* **Purpose:** Safely extracts custom overrides from an entity's NBT tag (`CompoundTag`) with automatic fallback to base values from JSON configurations.
* **Technical Details:**
    * `getDimensions(CompoundTag tag, SizeConfig defaultConfig)` — Reads NBT keys `"width"` and `"height"` (stored as `double` and cast to `float`). Fallback: Values from `SizeConfig` (default: `1.0f`).
    * `getZones(CompoundTag tag, List<ZoneConfig> defaultZones)` — If NBT contains key `"zones_count"`, reads integer layer count `count`. For each index `i` (from `0` to `count - 1`), sequentially extracts:
        * `"zone_i_radius"` (`double`)
        * `"zone_i_damage"` (`double`) — Wrapped into `DamageConfig` with range `MinMaxRange(dmgAmount, dmgAmount)` and `damageType = "generic"`.
        * `"zone_i_pullForce"` (`double`), `"zone_i_spinForce"` (`double`), `"zone_i_impulseY"` (`double`) — Wrapped into `PhysicsConfig(0, impY, 0, true, pullForce, spinForce)`.
        * Returns a new dynamic `List<ZoneConfig>`. If `"zones_count"` is missing, returns `defaultZones` or an empty `List.of()`.
    * `getParticleInterval(CompoundTag tag, ParticleConfig pConfig)` — Checks keys `"particleIntervalMin"` / `"particleIntervalMax"`. Fallback: `pConfig.interval().getDouble()` or `1`.
    * `getParticleCount(CompoundTag tag, ParticleConfig pConfig)` — Checks keys `"particleCountMin"` / `"particleCountMax"`. Fallback: `pConfig.count().getDouble()` or `1`.
    * `getSoundInterval(CompoundTag tag, SoundConfig s)` — Checks keys `"soundIntervalMin"` / `"soundIntervalMax"`. Fallback: `s.interval().getDouble()` or `20`.
    * `getTriggerInterval(CompoundTag tag, TriggerConfig t)` — Checks key `"triggerInterval"`. Generates a symmetric range `MinMaxRange(min, min)`. Fallback: `t.interval().getDouble()` or `5`.

* **Relationships:** `CompoundTag`, `ZoneConfig`, `DamageConfig`, `PhysicsConfig`, `MinMaxRange`, `ParticleConfig`, `SoundConfig`, `TriggerConfig`.

#### `ZoneUtils` (`anomaly.util`)

* **Purpose:** Target active zone selection algorithm.
* **Technical Details:**
    * `getActiveZone(List<ZoneConfig> zones, AnomalyEntity anomaly, Entity target)`:
        1. Validates input parameters (`target != null`, `target.isAlive()`, `zones` list is not empty).
        2. Sequentially iterates through `zones` list.
        3. Since the zone list is sorted ascending by radius (`radius`) in `ImpulseComponent`, the first layer where `zone.contains(anomaly, target)` returns `true` is guaranteed to be the smallest (innermost and most hazardous) active zone.

* **Relationships:** `ZoneConfig`, `AnomalyEntity`, `Entity`.

#### `AnomalyReloadListener` (`anomaly.loader`)

* **Purpose:** Datapack resource reload listener loading anomaly templates from JSON files under `data/<mod_id>/anomalies/*.json`.
* **Technical Details:**
    * Extends `SimpleJsonResourceReloadListener`.
    * **GSON Setup:** Configured via `GsonBuilder` with `.setLenient()` mode enabled and custom `MinMaxRange.Deserializer` adapter registered.
    * **Static Registry:** `Map<String, AnomalyDefinition> REGISTRY`.
    * **Datapack Loading (`apply`):**
        1. Clears existing registry: `REGISTRY.clear()`.
        2. Parses JSON resources into `AnomalyDefinition`.
        3. Registers keys strictly using lowercase path string: `location.getPath().toLowerCase()` (e.g., key `"zharka"` instead of `"anomalies:zharka"`).
    * **Public API:**
        * `get(String type)` — Retrieves `AnomalyDefinition` from registry by type name (converting query string to lowerCase).
        * `getKeys()` — Returns `Set<String>` of all registered types (used for command autocompletion).
        * `exists(String type)` — Verifies existence of anomaly key in registry.

* **Relationships:** `SimpleJsonResourceReloadListener`, `AnomalyDefinition`, `MinMaxRange`, `Gson`, `ResourceLocation`.

---
## 3. Extensible Event API (`api.event`)

*Package:* `net.void_.anomalies.api.event`

All events inherit from `net.minecraftforge.eventbus.api.Event`, are annotated with `@Cancelable`, and are posted to `MinecraftForge.EVENT_BUS`.
* **`AnomalyTriggerEvent`**: Fired when an entity enters an anomaly's detection zone. Canceling completely blocks further impact (damage, effects).
* **`AnomalyDamageEvent`**: Fired before damage is inflicted on an entity by an active zone. Allows canceling damage or modifying its value via `setAmount(float)`.
* **`AnomalyPhysicsEvent`**: Fired before applying pull/push velocity vector. Allows canceling physics or modifying the vector via `setDeltaMovement(Vec3)`.
* **`AnomalyItemInteractEvent`**: Fired when an `ItemEntity` enters an anomaly's area. Allows canceling default anomaly reactions to dropped items.
* **`AnomalyZoneTransitionEvent`**: Fired when an entity crosses boundaries between zones. Provides `isEntering()` and `isLeaving()` methods. Allows blocking reactions to zone transitions.

---

## 4. Behavior Components (`components`)

*Package:* `net.void_.anomalies.components`

Modular building blocks implementing `IAnomalyComponent` logic responsible for optimized execution of specific physical, visual, and mechanical functions.

---

### 4.1. `DamageComponent` (`components.DamageComponent`)

* **Class:** `DamageComponent` (implements `IAnomalyComponent`)
* **Purpose:** Damage calculation, event modification, and infliction.
* **Technical Details:**
    * **Fields:** `defaultDamageRange`, `defaultDamageSource`.
    * `serverTick()` and `clientTick()` — No-op implementations.
    * **Method `inflictDamage(anomaly, target, zone, specificRange, overrideSource)`:**
        1. Checks target viability (`target.isAlive()`).
        2. Extracts damage value from `specificRange` (or falls back to `defaultDamageRange`). If damage <= 0, halts execution.
        3. Selects damage source (`overrideSource` or `defaultDamageSource`).
        4. Posts `AnomalyDamageEvent` to `MinecraftForge.EVENT_BUS`.
        5. If event is canceled — damage is not inflicted.
        6. Extracts final damage via `damageEvent.getAmount()` and, if amount > 0, invokes `target.hurt(sourceToUse, finalDamage)`.
* **Relationships:** `AnomalyDamageEvent`, `DamageSource`, `ZoneConfig`, `MinMaxRange`, `AnomalyEntity`.

---

### 4.2. `ImpulseComponent` (`components.ImpulseComponent`)

* **Class:** `ImpulseComponent` (implements `IAnomalyComponent`)
* **Purpose:** Physics engine for calculating attraction, repulsion, spin, and tracking zone transitions.
* **Technical Details:**
    * **Constructor:** Automatically sorts the provided zone list ascending by radius: `zones.stream().sorted(Comparator.comparingDouble(ZoneConfig::radius)).toList()`.
    * **Zone Tracking:** `Map<UUID, ZoneConfig> entityZones`.
    * **Cleanup Timer (`serverTick`):** Every 20 ticks (`cleanupTimer >= 20`), calls `cleanupStaleEntities()`. Validates entities in `entityZones` via `serverLevel.getEntity(uuid)`. If entity died, was removed, or exited all zones — removes it from the map and posts `AnomalyZoneTransitionEvent` with `newZone = null`.
    * **Method `applyImpulse(anomaly, target, precalculatedZone)`:**
        1. Tracks active zone change relative to `entityZones.get(targetUuid)`.
        2. On zone transition, posts `AnomalyZoneTransitionEvent`. If canceled — halts physics calculation.
        3. Extracts `PhysicsConfig` (from `currentZone.physics()` or fallback `generalPhysics`).
        4. **Attraction Vector Calculation (`pullToCenter == true`):**
            * Anomaly center: (ax, ay + height * 0.5, az)
            * Vector to target: (dx, dy, dz)
            * Direction normalization using a single Math.sqrt(distSq) calculation.
            * **Tangential Spin (Spin Force):** Applied **only** if anomaly has more than 1 zone (`zones.size() > 1`) and target is in the innermost zone (`currentZone == zones.get(0)`).
                * Closeness formula: closeness = max(0.0, 1.0 - (dist / radius))
                * Spin force: aggressiveSpin = spinForce * (1.5 + closeness * 3.0)
                * Perpendicular spin vector: (-dirZ * aggressiveSpin, dirX * aggressiveSpin)
            * **Speed Cap:** If moveX² + moveZ² > 1.0, horizontal vector is normalized to exactly 1.0 block/tick.
        5. **Repulsion Vector Calculation (`pullToCenter == false`):** Adds fixed `impulseX`, `impulseY`, `impulseZ`.
        6. **Event:** Posts `AnomalyPhysicsEvent` with resulting `Vec3`. If not canceled — applies vector via `target.setDeltaMovement()` and sets `target.hurtMarked = true`.
* **Relationships:** `AnomalyPhysicsEvent`, `AnomalyZoneTransitionEvent`, `ZoneUtils`, `PhysicsConfig`, `ZoneConfig`, `AnomalyEntity`.
---

### 4.3. `ParticleComponent` (`components.ParticleComponent`)

* **Class:** `ParticleComponent` (implements `IAnomalyComponent`)
* **Purpose:** Client-side component for generating geometric particles with performance optimizations.
* **Technical Details:**
    * **Enum `Shape`:** `SPHERE`, `CYLINDER`, `DISC`.
    * **Render Distance Constant:** `RENDER_DISTANCE_SQ = 4900.0D` (70² blocks).
    * **Client LOD (Level of Detail):** In `clientTick()`, checks distance to `Minecraft.getInstance().player`. If player is further than 70 blocks, tick execution halts.
    * **Dynamic Timer:** Every `nextTriggerTick` ticks, generates a new interval value from `intervalRange.getInt()`, resets `clientTickCounter`, and requests a random particle count `countRange.getInt()`.
    * **Loop Unswitching Optimization:** `switch (shape)` check is moved outside the point generation loop, eliminating repeated branch evaluations.
    * **Rejection Sampling Optimization:** Points within volumes are generated using `do-while` loops on primitives without trigonometric functions (`Math.sin`, `Math.cos`):
        * **`SPHERE`:** Points rejected if rx² + ry² + rz² > 1.0. Spawned with base Y impulse = 0.02.
        * **`CYLINDER`:** Rejection condition rx² + rz² > 1.0, Y height chosen randomly within `random.nextDouble() * height`. Base Y impulse = 0.05.
        * **`DISC`:** Rejection condition rx² + rz² > 1.0, Y offset specified by micro-spread within ± 0.05. Base Y impulse = 0.01.
* **Relationships:** `ParticleOptions`, `MinMaxRange`, `Minecraft`, `AnomalyEntity`, `Level`.
---

### 4.4. `SoundComponent` (`components.SoundComponent`)

* **Class:** `SoundComponent` (implements `IAnomalyComponent`)
* **Purpose:** Client-side component for playing spatial sound effects with dynamic delay intervals.
* **Technical Details:**
    * **Fields:** `soundEvent`, `intervalRange`, `soundSource`, `volume`, `pitch`.
    * **Server Tick (`serverTick`):** Empty implementation.
    * **Client Tick (`clientTick`):**
        * Increments `clientTickCounter`.
        * Upon reaching `nextTriggerTick`, generates a new interval via `intervalRange.getInt()`.
        * Invokes `level.playLocalSound(pos.x, pos.y, pos.z, soundEvent, soundSource, volume, pitch, false)`. `distanceDelay = false` parameter guarantees immediate playback start.
* **Relationships:** `SoundEvent`, `SoundSource`, `MinMaxRange`, `AnomalyEntity`, `Level`.

---

### 4.5. `TriggerComponent` (`components.TriggerComponent`)

* **Class:** `TriggerComponent` (implements `IAnomalyComponent`)
* **Purpose:** Server-side spatial scanner detecting targets within the anomaly's area of effect.
* **Technical Details:**
    * **Sleep Radius Constant:** `ACTIVATION_DISTANCE = 48.0D` blocks.
    * **Server LOD Optimization (Sleep Mode):** In `serverTick()`, checks for nearest player via `level.getNearestPlayer(x, y, z, 48.0, false)`. If no players are within 48 blocks, AABB scanning is canceled, saving CPU cycles.
    * **Interval Scanning:** Executes target search after `currentIntervalTicks` elapsed (retrieved from `interval.getInt()`).
    * **AABB and Target Filtering:**
        * Expands anomaly bounding box in all directions by `expandRadius`: `getBoundingBox().inflate(expandRadius)`.
        * Retrieves target list via `getEntitiesOfClass()`, filtering entities:
            * Filters out players where `AnomalyIgnoreManager.isIgnored(player) == true`.
            * Passes only `LivingEntity` and `ItemEntity` instances.
    * **Handler Invocation:** Sequentially passes each found entity to `onTrigger.accept(anomaly, target)` (to orchestrator lambda `ZoneFactory.handleTriggerTarget`).
* **Relationships:** `AABB`, `LivingEntity`, `ItemEntity`, `Player`, `MinMaxRange`, `BiConsumer`, `AnomalyEntity`, `ZoneFactory`.

---

### 4.6. `SnapToGridComponent` (`components.SnapToGridComponent`)

* **Class:** `SnapToGridComponent` (implements `IAnomalyComponent`)
* **Purpose:** Automatic alignment of anomaly positioning to the block grid.
* **Technical Details:**
    * Contains single-execution flag `private boolean isSnapped = false`.
    * In `serverTick(AnomalyEntity anomaly)`, executes only when `!isSnapped`:
        * Calculates exact geometric block center:
            * x = floor(anomaly.getX()) + 0.5
            * y = floor(anomaly.getY()) (block bottom boundary)
            * z = floor(anomaly.getZ()) + 0.5
        * Updates entity position via `anomaly.setPos(x, y, z)`.
        * Sets `isSnapped = true`, preventing jittering and continuous position shifting on subsequent server ticks.
* **Relationships:** `AnomalyEntity`, `IAnomalyComponent`.

## 5. Control System and Multitool (`item`)

*Package:* `net.void_.anomalies.item`

Complete specification of multitool interaction architecture, event handling logic, and built-in mode processors for managing anomalies.

---

### 5.1. Event Handler and Interaction Bus

* **Class:** `AnomalyMultitoolHandler`
* **Purpose:** Forge game event interception bus (`@Mod.EventBusSubscriber`) routing player commands to appropriate mode processors.
* **Technical Details:**
    * **RMB Interception (`onEntityInteract`):**
        * Checks item held in `MAIN_HAND` via `isMultitool()`.
        * Cancels vanilla action (`setCanceled(true)`).
        * When switching current mode from `MODIFY` to any other mode, automatically calls `ModifyProcessor.clearSession()` to clear NBT state.
        * Routes execution call to `AnalyzeProcessor`, `ModifyProcessor`, `RelocateProcessor`, or `DeleteProcessor` based on `MultitoolMode`.
    * **LMB Interception on Anomaly (`onEntityAttacked`):** In `DELETE` mode, blocks vanilla damage and invokes `DeleteProcessor.process()`.
    * **LMB Interception on Block (`onLeftClickBlock`):** If anomaly capture is active in multitool NBT during `RELOCATE` mode, cancels block breaking and calls `RelocateProcessor.onLeftClickBlock()`.
    * **CHAT Interception (`onServerChat`):** Intercepts player server messages (`ServerChatEvent`). When `MODIFY` or `RELOCATE` modes are active, messages are consumed (`setCanceled(true)`), and text is passed to `onChat()` of the active processor.
* **Relationships:** `AnomalyMultitoolItem`, `MultitoolMode`, `AnalyzeProcessor`, `ModifyProcessor`, `RelocateProcessor`, `DeleteProcessor`, `ServerChatEvent`, `PlayerInteractEvent`.

---

### 5.2. Multitool Mode Processors (`item.processor`)

#### `AnalyzeProcessor`

* **Purpose:** Reads and prints comprehensive diagnostic anomaly data to player chat, taking local NBT overrides into account.
* **Technical Details:**
    * **Method:** `process(Player player, AnomalyEntity anomaly)`.
    * Compares current parameter values in `customOverrides` against base `AnomalyDefinition` from `AnomalyReloadListener`.
    * Formats and prints the following data blocks to system chat:
        1. **Identification:** Anomaly type and truncated UUID (first 8 characters).
        2. **Dimensions (`width` x `height`):** Prints active hitbox dimensions.
        3. **Zone Layers, Damage, and Physics:**
            * Reads layer count from `zones_count` in NBT (if overridden) or takes base count from `def.zones()`.
            * For each layer `#i`, checks NBT overrides with priority over JSON:
                * Radius: `zone_i_radius`
                * Damage: `zone_i_damage`
                * Physics: `zone_i_pullForce` (Pull), `zone_i_spinForce` (Spin), `zone_i_impulseY` (ImpulseY)
                * Burning: `fireSeconds` from JSON (if applicable).
        4. **Trigger:** Target detection radius (`expandRadius`).
        5. **Physics:** Attraction flag and vector (`pullToCenter`).
        6. **Sound and Particles:** Volume/pitch and particle spawn radius/height.
    * **Override Labeling (`formatVal`):** If a parameter is overridden locally in the entity's NBT, outputs with formatting `§e§l[Value] §6[Override]`, otherwise highlights base value as `§f[Value]`.
* **Relationships:** `AnomalyEntity`, `AnomalyDefinition`, `AnomalyReloadListener`, `CompoundTag`, `Component`.

#### `DeleteProcessor`

* **Purpose:** Safe removal of anomaly entities from the world with two-step confirmation.
* **Technical Details:**
    * **Confirmation Cache:** Static `CONFIRM_MAP` map of type `Map<UUID, ConfirmData>` with timeout `CONFIRM_TIMEOUT_MS = 5000` (5 seconds).
    * **Deletion Logic:**
        * **Shift + LMB / RMB:** Immediate `anomaly.discard()`, bypassing confirmation phase.
        * **Standard Click:** First click adds anomaly `UUID` and timestamp to cache and sends warning to chat. A second click on the same anomaly within 5 seconds executes `anomaly.discard()` and clears cache entry.
* **Relationships:** `AnomalyEntity`, `Player`, `ConfirmData`.

#### `RelocateProcessor`

* **Purpose:** Interactive spatial relocation of anomaly entities (via block clicks or coordinate offsets via chat).
* **Technical Details:**
    * **Capture (`onInteract`):** Saves `SelectedAnomaly` (UUID) in item NBT.
    * **Mode 1: Click Relocation (Standard RMB -> LMB on Block):**
        * `onLeftClickBlock`: When clicking a block, calculates target position `(x + 0.5, y + 1.0, z + 0.5)`, sets new entity coordinates via `anomaly.setPos()`, and removes `SelectedAnomaly` from item NBT.
    * **Mode 2: Offset Relocation (Shift + RMB -> Chat):**
        * Sets NBT flag `WaitingForOffset = true`.
        * `onChat`: Parses message in format `dx dy dz` (e.g., `0.5 0 -0.5`). Extracts `position()`, adds offset, updates entity position, and resets NBT flags.
* **Relationships:** `AnomalyEntity`, `ItemStack`, `CompoundTag`, `ServerLevel`, `BlockPos`.

---

### 5.3. Multitool Item & Mode Enumeration (`item`)

#### `MultitoolMode` (`item.MultitoolMode`)

* **Enum:** `MultitoolMode`
* **Purpose:** Set of supported multitool operation modes.
* **Values:**
    * `ANALYZE` ("Analysis", `ChatFormatting.AQUA`): RMB on anomaly to display characteristics in chat.
    * `MODIFY` ("Modification", `ChatFormatting.GOLD`): RMB starts interactive chat editing session | Shift+RMB resets overrides.
    * `RELOCATE` ("Relocation", `ChatFormatting.GREEN`): RMB captures anomaly | LMB on block relocates entity | Shift+RMB accepts coordinate offset via chat.
    * `DELETE` ("Deletion", `ChatFormatting.RED`): LMB on anomaly deletes entity with confirmation | Shift+LMB executes instant deletion.
* **Cyclic Rotation Method (`next()`):** `values()[(this.ordinal() + 1) % values().length]` — Provides continuous mode cycling.
* **Relationships:** `AnomalyMultitoolItem`, `ChatFormatting`.

---

## 6. Client Side (`client`)

*Package:* `net.void_.anomalies.client`

### 6.1. Invisible Entity Renderer

* **Class:** `AnomalyRenderer` (extends `EntityRenderer<AnomalyEntity>`)
* **Purpose:** Disables standard 3D mesh entity rendering while preserving client-side ticking execution.
* **Technical Details:**
    * **Dummy Texture:** `getTextureLocation()` returns path `anomalies:textures/entity/anomaly.png`.
    * **Visibility Management (`shouldRender`):** Method explicitly returns `true`. This prevents Minecraft engine rendering culling and ensures continuous execution of client-side components (`ParticleComponent`, `SoundComponent`), despite the absence of physical geometry or polygon meshes.
* **Relationships:** `EntityRenderer`, `AnomalyEntity`, `ParticleComponent`, `SoundComponent`.

---

### 6.2. Client-Side Setup Register (`client.ClientSetup`)

* **Class:** `ClientSetup`
* **Purpose:** Binds entity renderers on client initialization.
* **Technical Details:**
    * Annotated with `@Mod.EventBusSubscriber(modid = "anomalies", value = Dist.CLIENT, bus = Bus.MOD)`.
    * **Event Handling (`registerRenderers`):** Subscribed to `EntityRenderersEvent.RegisterRenderers` on mod bus.
    * Binds registered entity type `EntityInit.ANOMALY.get()` to dummy renderer factory `AnomalyRenderer::new`.

* **Relationships:** `EntityRenderersEvent.RegisterRenderers`, `EntityInit`, `AnomalyRenderer`, `Dist.CLIENT`.

---

## 7. Initialization, Commands, and Entry Point (`setup` / `root`)

*Packages:* `net.void_.anomalies.setup`, `net.void_.anomalies`

### 7.1. Administration Command Interface

* **Class:** `AnomalyCommands`
* **Purpose:** Registration and handling of anomaly administration console commands.
* **Technical Details:**
    * **Permission Level:** Requires `hasPermission(2)` (operator/administrator level).
    * **Command Structure:**
        * `/anomaly create <type>` — Spawns an anomaly of the specified type at the executing player's coordinates. Supports dynamic type autocompletion from `AnomalyReloadListener.getKeys()`.
        * `/anomaly ignore <player> <state>` — Sets player immunity/ignore status against anomalies (`true`/`false`). Persists state via `AnomalyIgnoreManager.setIgnored()`.
* **Relationships:** `CommandDispatcher`, `CommandSourceStack`, `ZoneFactory`, `AnomalyReloadListener`, `AnomalyIgnoreManager`, `AnomalyEntity`.

---

### 7.2. Entity Initialization (`setup.EntityInit`)

* **Class:** `EntityInit`
* **Purpose:** Registration of `AnomalyEntity` type within Minecraft Forge registries.
* **Technical Details:**
    * **Registry:** `DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "anomalies")`.
    * **Registry Entry `ANOMALY` (`RegistryObject<EntityType<AnomalyEntity>>`):**
        * Entity category: `MobCategory.MISC`.
        * Base hitbox: `sized(1.0F, 1.0F)`.
        * Client packet tracking range: `clientTrackingRange(64)` (64 blocks).
        * Network packet update interval: `updateInterval(20)` (every 20 ticks / 1 second).
    * **Method `register(IEventBus eventBus)`:** Binds `ENTITIES` registry to mod event bus.
* **Relationships:** `DeferredRegister`, `RegistryObject`, `EntityType`, `MobCategory`, `AnomalyEntity`, `Anomalies`.

