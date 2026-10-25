# ⏱️ Жизненный цикл и Фабрика зон (Lifecycle and Zones)

Документ описывает жизненный цикл сущностей аномалий, контракт `IAnomalyHolder`, фабрику компонентов `ZoneFactory` и алгоритмы расчёта слоёв зон.

---

## 🏗️ Контракт `IAnomalyHolder`

Каждая аномалия в моде реализует интерфейс `IAnomalyHolder`, объединяющий базовые пространственные свойства, NBT-оверрайды и жизненный цикл компонентов:

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

## ⚡ `LightAnomalyInstance` (Реализация ядра)

Класс `LightAnomalyInstance` является одновременно:
- `ILightEntity` для движка `LightEntity` (пространственный хэш, чанк-сторадж, сеть).
- `IAnomalyHolder` для прикладной логики мода `Anomalies`.

### Двухфазный тик и 0-Allocation
Внутри сущности хранится volatile-снимок активных компонентов:
```java
private volatile ILightComponent[] activeComponents = new ILightComponent[0];
```
В методе `tick()` сущность перебирает этот массив напрямую без создания итераторов или промежуточных объектов коллекций.

---

## 🏭 Сборка в `ZoneFactory` (Composition Root)

`ZoneFactory.applyComponents(IAnomalyHolder anomaly, String type, String state)` собирает аномалию:
1. Запрашивает конфигурацию `AnomalyDefinition` из `AnomalyReloadListener`.
2. Добавляет `SnapToGridComponent` для привязки к сетке.
3. На стороне сервера добавляет `StateMachineComponent` и, при наличии рецептов, `RecipeProcessorComponent`.
4. Считывает NBT-оверрайды `customOverrides` (установленные мультитулом).
5. Применяет размеры (`setupDimensions`).
6. Конфигурирует компоненты частиц (`setupParticles`) и звука (`setupSound`).
7. Настраивает триггер и физику (`setupTriggerAndPhysics`):
   - Если задан урон — создаётся `DamageComponent`.
   - Если заданы зоны тяги или общая физика — создаётся `ImpulseComponent`.
   - Создаётся `TriggerComponent`, вызывающий обработчик попадания целей в зону.

---

## 📐 Алгоритм сортировки зон (`ZoneUtils`)

В конфигурации аномалии может быть объявлено несколько вложенных зон с разными радиусами и эффектами:

```text
[   Внешняя зона (Зона 1: радиус 3.0, лёгкая тяга)   ]
   [   Ядро (Зона 0: радиус 1.0, сильный урон + вращение)   ]
```

### Принцип определения активной зоны:
1. Зоны всегда сортируются **по возрастанию радиуса** (`Comparator.comparingDouble(ZoneConfig::radius)`).
2. При проверке положения сущности проверяется расстояние от центра аномалии.
3. Выбирается **наименьшая зона**, в границы которой попала сущность. Если сущность находится в центре, на неё действует Зона 0. Если на периферии — Зона 1.
