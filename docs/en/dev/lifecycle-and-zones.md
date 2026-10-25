# ⏱️ Lifecycle and Zone Factory

Describes anomaly lifecycle management, the `IAnomalyHolder` contract, the `ZoneFactory` composition root, and zone sorting algorithms.

---

## 🏗️ `IAnomalyHolder` Contract

Every anomaly in the mod implements `IAnomalyHolder`:

```java
public interface IAnomalyHolder {
    UUID getUUID();
    Level getLevel();
    Vec3 getPosition();
    AABB getBoundingBox();

    float getWidth();
    float getHeight();
    void setDimensions(float width, float height);

    String getAnomalyType();
    void setAnomalyType(String type);

    String getCurrentState();
    void setCurrentState(String state);

    CompoundTag getCustomOverrides();
    void setCustomOverrides(CompoundTag tag);

    void rebuildComponents();
    void discard();
    boolean isDiscarded();

    <C extends ILightComponent> void addComponent(C component);
    <C extends ILightComponent> Optional<C> getLightComponent(Class<C> componentClass);
}
```

---

## ⚡ `LightAnomalyInstance`

`LightAnomalyInstance` implements both:
- `ILightEntity` for the `LightEntity` engine (spatial hash, chunk serialization, networking).
- `IAnomalyHolder` for `Anomalies` mod domain logic.

Ticks execute with 0 allocations by iterating over a volatile snapshot array:
```java
private volatile ILightComponent[] activeComponents = new ILightComponent[0];
```

---

## 🏭 `ZoneFactory` (Composition Root)

`ZoneFactory.applyComponents(IAnomalyHolder anomaly, String type, String state)`:
1. Fetches `AnomalyDefinition` from `AnomalyReloadListener`.
2. Adds `SnapToGridComponent`.
3. Adds `StateMachineComponent` and optional `RecipeProcessorComponent` on the server.
4. Applies NBT overrides (`customOverrides`).
5. Configures dimensions, particles, and positional sounds.
6. Configures trigger detection, physics impulses, and damage zones.

---

## 📐 Zone Layer Sorting (`ZoneUtils`)

When multiple concentric zones are configured:
1. Zones are sorted in **ascending order of radius** (`Comparator.comparingDouble(ZoneConfig::radius)`).
2. The entity's distance to the anomaly center determines the active zone.
3. The smallest enclosing zone takes priority (Zone 0 = core, Zone 1 = outer layer).
