# 🧩 ECS Components Reference

All domain logic of anomalies is encapsulated in independent components implementing `IAnomalyComponent` (which extends `ILightComponent` of the `LightEntity` engine).

The container (`LightAnomalyInstance`) executes components with 0-allocation overhead using a volatile snapshot array:
- Server side: `comp.serverTick(anomaly)`
- Client side: `comp.clientTick(anomaly)`

---

## 📋 Base Contract (`IAnomalyComponent`)

```java
public interface IAnomalyComponent extends ILightComponent {
    default void serverTick(IAnomalyHolder anomaly) {}
    default void clientTick(IAnomalyHolder anomaly) {}
    default void save(CompoundTag tag) {}
    default void load(CompoundTag tag) {}
}
```

---

## 1. ⚔️ `DamageComponent`
Handles zone-based entity damage calculations.

- **Damage Sources:** Supports `fire`, `lightning`, `magic`, and `generic`.
- **Friendly Fire & Ignore Protection:** Validates against `AnomalyIgnoreManager.isIgnored(player)`. If ignored, damage is completely bypassed.
- **Forge Events:** Fires `AnomalyDamageEvent` allowing addons to modify damage amounts or cancel the event (`@Cancelable`).
- **Ignition:** If configured with `fireSeconds > 0`, entities are set on fire.

---

## 2. 🧲 `ImpulseComponent`
Implements vector physics: center pull, vortex swirl, and vertical lift.

- **Centripetal Pull (`pullForce`):** Computes normalized direction vector towards anomaly center.
- **Orbital Spin (`spinForce`):** Cross product between direction vector and up vector (`Vec3(0, 1, 0)`), orbiting entities around the center.
- **Vertical Lift (`impulseY`):** Adds upward velocity.
- **Zone Tracking:** Tracks entity locations across zones and fires `AnomalyZoneTransitionEvent`.
- **Ignore Protection:** If `AnomalyIgnoreManager.isIgnored(player) == true`, no forces are applied.

---

## 3. ✨ `ParticleComponent`
Client-side particle rendering with multi-stage culling pipeline:

- **Geometry Shapes:**
  - `SPHERE`: 3D volume using rejection sampling.
  - `CYLINDER`: Vertical particle column.
  - `DISC`: Horizontal planar ring.
- **LOD Distance Culling:** Skips rendering if camera distance $> 70$ blocks ($4900$ blocks²).
- **Frustum Culling:** Checks anomaly AABB against camera view frustum cache.
- **Occlusion Culling:** Raycasts every 8 ticks using `ClipContext.Block.VISUAL`. Opaque blocks (stone, dirt) block particle emission, while transparent blocks (glass, ice) allow particles to remain visible.

---

## 4. 🔊 `SoundComponent`
Positional 3D audio playback with interval randomization:

- **Dynamic Timer:** Plays sound within a randomized interval `[min, max]` ticks.
- **Distance Attenuation:** Configurable `minDistance` and `maxDistance`.

---

## 5. 🎯 `TriggerComponent`
Proximity detector with Sleeping LOD:

- **Sleeping LOD:** Checks for players within 48 blocks every `checkInterval` ticks. If no player is nearby, broad-phase scanning is skipped, saving server ticks.
- **Target Filtering:** Detects living entities and items, filtering out ignored players.

---

## 6. 🧲 `SnapToGridComponent`
Snaps anomaly position to block center grid (`X.5`, `Y.0`, `Z.5`) once during initialization.

---

## 7. 🔄 `StateMachineComponent`
State machine orchestrator compiled from `.anom` files:

- Evaluates transition conditions every tick.
- Handles phase transitions: invokes `onExit`, flushes `TransientZoneCache`, rebuilds components via `ZoneFactory`, and notifies clients via `LightEntityTracker.syncEntity(...)`.

---

## 8. 🧪 `RecipeProcessorComponent`
Handles in-world crafting and transmutation for dropped items (`ItemEntity`):

- **Virtual Reservation:** Prevents duplicate consumption of items across competing recipes.
- **Craft Progress:** Increments timer and spawns crafted results upon completion while discarding input entities.
