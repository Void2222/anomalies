# ⚙️ Архитектура Мода Anomalies (HLD)

`Anomalies` — это событийно-ориентированный, дата-дривен мод для **Minecraft Forge 1.20.1**, воссоздающий физические и динамические аномальные зоны со сложными конечными автоматами, поддержкой крафта предметов в мире и собственным языком описания сценариев (`.anom`).

---

## 🏛️ Ключевые архитектурные принципы

1. **Декаплинг от ванильного Entity:**  
   Аномалии больше не являются ванильными `Entity`. Они работают на базе высокопроизводительного автономного движка **[LightEntity](file:///d:/VisualStudio/Java/anomalies/lightentity/README.md)**. Это гарантирует отсутствие серверных лагов, минимальное потребление RAM (~200 байт/аномалия) и независимое хранение внутри чанков.
2. **Composition Over Inheritance (ECS-подход):**  
   Инстанс аномалии (`LightAnomalyInstance`) — это лёгкий runtime-контейнер. Вся прикладная логика (гравитация, урон, звук, частицы, крафты) разбита на изолированные компоненты (`IAnomalyComponent`).
3. **Data-Driven архитектура:**  
   Все параметры аномалий (радиусы зон, формулы урона, кривые импульсов, звуки и частицы) задаются через внешние JSON-датапаки.
4. **Конечные автоматы и скриптовый DSL:**  
   Поведение аномалий описывается декларативным скриптовым языком `.anom` (компилируется через ANTLR4 в AST-модель выполнения).

---

## 🗺️ Общая схема подсистем

```mermaid
graph TD
    DP[JSON Датапаки] -->|Загрузка| ARL[AnomalyReloadListener]
    SCR[Скрипты .anom] -->|ANTLR4 / AST| ASR[AnomalyScriptRegistry]
    
    ARL --> ZF[ZoneFactory]
    ASR --> ZF
    
    ZF -->|Сборка компонентов| LAI[LightAnomalyInstance]
    LAI -->|Регистрация| WLM[WorldLightManager (LightEntity)]
    
    subgraph "Компоненты Аномалии"
        SMC[StateMachineComponent]
        TC[TriggerComponent]
        IC[ImpulseComponent]
        DC[DamageComponent]
        PC[ParticleComponent]
        SC[SoundComponent]
        RPC[RecipeProcessorComponent]
    end
    
    LAI --> SMC
    LAI --> TC
    LAI --> IC
    LAI --> DC
    LAI --> PC
    LAI --> SC
    LAI --> RPC
```

---

## 📚 Навигация по детальной документации (`docs/`)

Вся подробная техническая документация и руководства разделены по специализированным разделам:

### 🛠️ Для разработчиков ядра и аддонов (`docs/dev/`)
- **[Компоненты движка (`docs/dev/ecs-components.md`)](file:///d:/VisualStudio/Java/anomalies/docs/dev/ecs-components.md):** Полный справочник встроенных компонентов, алгоритмы отсечения рендеринга частиц (Frustum & Occlusion Culling), физика импульсов и крафт.
- **[Жизненный цикл и фабрика зон (`docs/dev/lifecycle-and-zones.md`)](file:///d:/VisualStudio/Java/anomalies/docs/dev/lifecycle-and-zones.md):** `LightAnomalyInstance`, сборка в `ZoneFactory`, структура слоёв зон и кэш оверрайдов.
- **[Событийное API (`docs/dev/event-api.md`)](file:///d:/VisualStudio/Java/anomalies/docs/dev/event-api.md):** События Forge (`AnomalyTriggerEvent`, `AnomalyDamageEvent`, `AnomalyPhysicsEvent` и др.), интеграция аддонов.
- **[Движок DSL-скриптов (`docs/dev/dsl-engine.md`)](file:///d:/VisualStudio/Java/anomalies/docs/dev/dsl-engine.md):** Грамматика ANTLR4, компиляция AST, контекст выполнения и кэш `TransientZoneCache`.
- **[Движок LightEntity (`lightentity/README.md`)](file:///d:/VisualStudio/Java/anomalies/lightentity/README.md):** Внутреннее устройство автономного движка легковесных сущностей.

### 📜 Для создателей датапаков (`docs/datapacks/`)
- **[Спецификация JSON-форматов (`docs/datapacks/formats.md`)](file:///d:/VisualStudio/Java/anomalies/docs/datapacks/formats.md):** Структура файлов аномалий, настройки триггеров, звуков, частиц и слоёв зон.
- **[Синтаксис языка сценариев .anom (`docs/datapacks/dsl-syntax.md`)](file:///d:/VisualStudio/Java/anomalies/docs/datapacks/dsl-syntax.md):** Руководство по написанию логики состояний, таймеров, вероятностей и переходов.

### 🎮 Для администраторов и тестеров (`docs/manual/`)
- **[Мультитул администратора (`docs/manual/multitool.md`)](file:///d:/VisualStudio/Java/anomalies/docs/manual/multitool.md):** Анализ, GUI-редактор оверрайдов, перенос и удаление аномалий в реальном времени.
- **[Консольные команды (`docs/manual/commands.md`)](file:///d:/VisualStudio/Java/anomalies/docs/manual/commands.md):** Синтаксис `/anomaly create`, `/anomaly state set` и система белого списка `/anomaly ignore`.
