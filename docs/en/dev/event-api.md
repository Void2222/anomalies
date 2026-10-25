# 📡 Event API for Addons (Forge Events)

The `Anomalies` mod posts custom events to `MinecraftForge.EVENT_BUS`, allowing third-party addons to hook into triggers, cancel damage, and alter physics forces.

Package: `net.void_.anomalies.api.event`

---

## 1. ⚔️ `AnomalyDamageEvent`
Fired before damage is dealt to an entity inside a damage zone.

- **Annotation:** `@Cancelable`
- **Methods:**
  - `IAnomalyHolder getAnomaly()` — The anomaly instance.
  - `Entity getTarget()` — The damaged entity.
  - `String getAnomalyType()` — Anomaly type id.
  - `ZoneConfig getZone()` — Active zone configuration.
  - `DamageSource getSource()` — Damage source.
  - `float getAmount()` / `void setAmount(float amount)` — Modify the final damage amount.

---

## 2. 🧲 `AnomalyPhysicsEvent`
Fired every tick when forces and motion impulses are calculated.

- **Annotation:** `@Cancelable`
- **Methods:**
  - `Vec3 getMotion()` / `void setMotion(Vec3 motion)` — The applied motion vector.

---

## 3. 🎯 `AnomalyTriggerEvent`
Fired when an entity enters the broad-phase detection radius.

- **Annotation:** `@Cancelable`

---

## 4. 🚪 `AnomalyZoneTransitionEvent`
Fired when an entity crosses boundaries between concentric zones.

- **Methods:**
  - `ZoneConfig getFromZone()` — Previous zone (`null` if entering from outside).
  - `ZoneConfig getToZone()` — Target zone (`null` if leaving the anomaly).

---

## 5. 📦 `AnomalyItemInteractEvent`
Fired when a dropped `ItemEntity` enters an anomaly zone.

---

## ☕ Java Custom State Behavior (`IAnomalyStateBehavior`)

Addons can register state behaviors directly in Java code:

```java
public interface IAnomalyStateBehavior {
    default void onEnter(IAnomalyHolder anomaly) {}
    default String onTick(IAnomalyHolder anomaly, int ticksInState) { return null; }
    default void onExit(IAnomalyHolder anomaly) {}
}
```

Registration via `AnomalyBehaviorRegistry`:
```java
AnomalyBehaviorRegistry.register("my_anomaly", "custom_state", new MyCustomBehavior());
```
