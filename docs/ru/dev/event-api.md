# 📡 Событийное API для аддонов (Forge Event API)

Мод `Anomalies` публикует события в шину `MinecraftForge.EVENT_BUS`, позволяя сторонним разработчикам вмешиваться в работу аномалий, переопределять урон, реагировать на триггеры и отменять эффекты.

Все события находятся в пакете `net.void_.anomalies.api.event`.

---

## 1. ⚔️ `AnomalyDamageEvent`
Вызывается перед нанесением урона сущности внутри зоны поражения.

- **Аннотация:** `@Cancelable`
- **Методы:**
  - `IAnomalyHolder getAnomaly()` — ссылка на инстанс аномалии.
  - `Entity getTarget()` — атакованная сущность.
  - `String getAnomalyType()` — тип аномалии (`zharka` и т.д.).
  - `ZoneConfig getZone()` — зона, в которой наносится урон.
  - `DamageSource getSource()` — источник урона.
  - `float getAmount()` / `void setAmount(float amount)` — позволяет изменить силу урона.

```java
@SubscribeEvent
public static void onAnomalyDamage(AnomalyDamageEvent event) {
    if (event.getTarget() instanceof Player player && player.isCreative()) {
        event.setCanceled(true); // Отменить урон
    }
}
```

---

## 2. 🧲 `AnomalyPhysicsEvent`
Вызывается каждый тик при расчёте и применении физических сил к сущности.

- **Аннотация:** `@Cancelable`
- **Методы:**
  - `Vec3 getMotion()` / `void setMotion(Vec3 motion)` — вектор применяемого импульса.
  - `IAnomalyHolder getAnomaly()`, `Entity getTarget()`.

---

## 3. 🎯 `AnomalyTriggerEvent`
Вызывается при первичном обнаружении сущности триггером аномалии.

- **Аннотация:** `@Cancelable`
- Позволяет отменить активацию аномалии на конкретную цель.

---

## 4. 🚪 `AnomalyZoneTransitionEvent`
Вызывается при перемещении сущности между вложенными зонами аномалии (например, переход из внешней зоны в ядро или выход из аномалии).

- **Методы:**
  - `ZoneConfig getFromZone()` — предыдущая зона (может быть `null`, если вошёл снаружи).
  - `ZoneConfig getToZone()` — новая зона (может быть `null`, если покинул аномалию).

---

## 5. 📦 `AnomalyItemInteractEvent`
Срабатывает, когда в зону аномалии попадает брошенный предмет (`ItemEntity`).

- Используется для реализации кастомных эффектов или специфических реакций на предметы аддонов.

---

## ☕ Java API кастомного поведения (`IAnomalyStateBehavior`)

Помимо DSL `.anom`, разработчики могут регистрировать поведение состояний напрямую через Java:

```java
public interface IAnomalyStateBehavior {
    default void onEnter(IAnomalyHolder anomaly) {}
    default String onTick(IAnomalyHolder anomaly, int ticksInState) { return null; }
    default void onExit(IAnomalyHolder anomaly) {}
}
```

Регистрация в `AnomalyBehaviorRegistry`:
```java
AnomalyBehaviorRegistry.register("my_anomaly", "custom_state", new MyCustomBehavior());
```
