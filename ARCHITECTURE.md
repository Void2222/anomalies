# ⚙️ Anomalies Mod Architecture (HLD)

`Anomalies` is an event-driven, data-driven mod for **Minecraft Forge 1.20.1**, creating dynamic physical anomaly zones with state machines, in-world transmutation recipes, and its own domain-specific scripting language (`.anom`).

---

## 🏛️ Core Architectural Principles

1. **Decoupled from Vanilla Entity:**  
   Anomalies do not extend vanilla `Entity`. They run on top of the high-performance **[LightEntity Engine](file:///d:/VisualStudio/Java/anomalies/lightentity/README.md)**. This guarantees zero server-side tick lag, minimal RAM consumption (~200 bytes per instance), and chunk-based storage.
2. **Composition Over Inheritance (ECS Pattern):**  
   An anomaly instance (`LightAnomalyInstance`) is a lightweight runtime container. All domain logic (physics, damage, sound, particles, recipes) is encapsulated in standalone components (`IAnomalyComponent`).
3. **Data-Driven Architecture:**  
   All anomaly properties (zone radii, damage curves, impulse vectors, sounds, and particles) are loaded from external JSON datapacks.
4. **State Machines & DSL Scripting:**  
   Anomaly behaviors and phase transitions are defined using a declarative `.anom` script language, compiled via ANTLR4 into an AST execution model.

---

## 🗺️ High-Level Subsystem Flow

```mermaid
graph TD
    DP[JSON Datapacks] -->|Load| ARL[AnomalyReloadListener]
    SCR[.anom Scripts] -->|ANTLR4 / AST| ASR[AnomalyScriptRegistry]
    
    ARL --> ZF[ZoneFactory]
    ASR --> ZF
    
    ZF -->|Assemble Components| LAI[LightAnomalyInstance]
    LAI -->|Register| WLM[WorldLightManager (LightEntity)]
    
    subgraph "Anomaly Components"
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

## 📚 Documentation Navigation (`docs/`)

Detailed documentation is available in both Russian and English:

- **[Русская документация (`docs/ru/`)](file:///d:/VisualStudio/Java/anomalies/docs/ru/dev/ecs-components.md)**

### 🛠️ For Java & Addon Developers (`docs/en/dev/`)
- **[Engine ECS Components (`docs/en/dev/ecs-components.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/dev/ecs-components.md):** Complete component reference, culling algorithms (Frustum & VISUAL Occlusion), impulse physics, and recipe crafting.
- **[Lifecycle & Zone Factory (`docs/en/dev/lifecycle-and-zones.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/dev/lifecycle-and-zones.md):** `LightAnomalyInstance`, `ZoneFactory` assembly, zone layer hierarchy, and runtime overrides.
- **[Event API (`docs/en/dev/event-api.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/dev/event-api.md):** Forge Events (`AnomalyTriggerEvent`, `AnomalyDamageEvent`, `AnomalyPhysicsEvent`, etc.).
- **[DSL Script Engine (`docs/en/dev/dsl-engine.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/dev/dsl-engine.md):** ANTLR4 grammar pipeline, AST condition tree, execution context, and `TransientZoneCache`.
- **[LightEntity Subproject Engine (`lightentity/README.md`)](file:///d:/VisualStudio/Java/anomalies/lightentity/README.md):** Autonomous lightweight entity engine internals.

### 📜 For Datapack & Modpack Creators (`docs/en/datapacks/`)
- **[JSON Datapack Formats (`docs/en/datapacks/formats.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/datapacks/formats.md):** Structure of anomaly JSONs, trigger configs, sounds, particles, and zone layers.
- **[.anom Scripting Language Syntax (`docs/en/datapacks/dsl-syntax.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/datapacks/dsl-syntax.md):** Specification for writing state machines, timers, chances, and transitions.

### 🎮 For Server Admins & Testers (`docs/en/manual/`)
- **[Admin Multitool (`docs/en/manual/multitool.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/manual/multitool.md):** Analysis, GUI editor, relocation, and deletion of anomalies in real time.
- **[Console Commands (`docs/en/manual/commands.md`)](file:///d:/VisualStudio/Java/anomalies/docs/en/manual/commands.md):** Command reference for `/anomaly create`, `/anomaly state set`, and `/anomaly ignore`.
