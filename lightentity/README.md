# LightEntity Engine

Высокопроизводительный автономный ECS-движок для сущностей в Minecraft Forge 1.20.1.

`LightEntity` полностью отвязан от ванильного класса `net.minecraft.world.entity.Entity`, что позволяет спавнить тысячи интерактивных объектов без лагов серверного тика, переполнения сущностей и раздутия памяти.

---

## ⚡ Ключевые преимущества

- **~200 байт на сущность в памяти:** Против ~2–4 КБ у ванильной `Entity`.
- **0 аллокаций в тике:** Использование паттерна Snapshot Array Swap (`volatile ILightComponent[]`).
- **Сетка пространственного хеширования ($O(1)$ SpatialGrid):** Моментальный поиск сущностей в радиусе вокруг игрока или блока по чанкам.
- **Встроенное сохранение в чанки:** Данные сущностей сериализуются прямо в NBT чанка (`ChunkDataEvent.Save` / `Load`) под тегом `LightEntities`.
- **AOI Network Tracker (64 блока):** Динамическая подгрузка и выгрузка сущностей клиентам в радиусе видимости (пакеты спавна с NBT-данными и деспавна).
- **Собственные хитбоксы и рейкаст:** Поддержка визуализации хитбоксов в `F3 + B` и перехват кликов ПКМ/ЛКМ через `LightEntityEvent.Interact` и `LightEntityEvent.Attack`.

---

## 📦 Архитектура подсистем

```text
lightentity/
├── api/
│   ├── ILightEntity.java       # Базовый интерфейс легковесной сущности
│   ├── ILightComponent.java    # Контракт компонента (serverTick, clientTick, NBT)
│   ├── LightEntityType.java    # Реестр фабрик типов сущностей
│   └── event/
│       └── LightEntityEvent    # События взаимодействия (Interact, Attack)
├── core/
│   └── LightEntityInstance     # Базовая потокобезопасная реализация сущности
├── storage/
│   ├── SpatialGrid.java        # Пространственная сетка чанков
│   ├── ChunkLightStorage.java  # Хранилище сущностей отдельного чанка
│   └── WorldLightManager.java  # Менеджер мира, тикающий сущности и сохраняющий чанки
├── network/
│   ├── LightNetwork.java       # Сетевой канал Forge
│   ├── LightEntityTracker.java # Трекер расстояния до игроков (AOI)
│   └── packet/                 # Сетевые пакеты спавна, синхронизации и кликов
└── client/
    ├── ClientLightManager.java # Зеркало сущностей на клиенте
    ├── ClientHitboxAndInputHandler.java # Отрисовка F3+B и рейкаст кликов
    └── LightRaycast.java       # Математика пересечения луча взгляда с AABB
```

---

## 🚀 Как использовать в своём моде

### 1. Определение сущности
Создайте класс, реализующий `ILightEntity` (или отнаследуйтесь от `LightEntityInstance`):

```java
public class MyCustomLightEntity extends LightEntityInstance {

    public static final LightEntityType<MyCustomLightEntity> TYPE = LightEntityType.register(
            new ResourceLocation("mymod", "custom_unit"),
            MyCustomLightEntity::new
    );

    public MyCustomLightEntity(LightEntityType<?> type, UUID id, Level level, Vec3 pos) {
        super(type, id, level, pos);
    }
}
```

### 2. Создание компонента
Компоненты реализуют логику тика и сохранение:

```java
public class PulseEffectComponent implements ILightComponent {
    private int counter = 0;

    @Override
    public void serverTick(ILightEntity entity) {
        counter++;
        if (counter >= 20) {
            counter = 0;
            // логика раз в секунду
        }
    }
}
```

### 3. Спавн в мире
```java
MyCustomLightEntity entity = MyCustomLightEntity.TYPE.create(UUID.randomUUID(), level, position);
entity.addComponent(new PulseEffectComponent());
WorldLightManager.get(level).spawn(entity);
```

### 4. Взаимодействие (Клики)
Слушайте события через стандартную шину Forge:
```java
@SubscribeEvent
public static void onInteract(LightEntityEvent.Interact event) {
    if (event.getEntity() instanceof MyCustomLightEntity unit) {
        event.getPlayer().sendSystemMessage(Component.literal("Клик по юниту!"));
    }
}
```
