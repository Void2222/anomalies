[Russian Version / Русская версия](README_RU.md)

# Anomalies Mod

A Minecraft Forge mod introducing a flexible, data-driven anomaly system fully managed via JSON datapacks and NBT tags. Each anomaly acts as a spatial entity container with configurable effect layers, physics, visual effects, and damage.

---

## 🛠 Features

* **JSON Templates & NBT Overrides:** Base configurations load from datapacks (`data/<mod_id>/anomalies/*.json`). Individual entity parameters (radii, damage, particles, physics) can be dynamically overridden in-game via the `customOverrides` NBT tag.
* **Multi-Layered Physics & Zones:** Supports multiple spatial effect layers per anomaly. Each layer features independent radii, center-pull forces, tangential spin forces, Y-axis impulses, and fire duration.
* **Level of Detail (LOD) Optimization:**
* **Server:** Scanning triggers enter sleep mode when no players are within 48 blocks.
* **Client:** Particle generation and visual renders cull at distances exceeding 70 blocks.


* **Developer API:** Includes a set of cancellable Forge events to hook custom logic and interactions.

---

## 🔧 Admin Multitool

The `AnomalyMultitoolItem` allows real-time configuration and management of anomalies in the world. Cycle modes using **Shift + Right-Click in air**:

* **ANALYZE**
    * **Controls:** Right-Click anomaly
    * **Purpose:** Prints anomaly properties (dimensions, layers, physics, damage) to chat and highlights active `[Override]` NBT tags.
* **MODIFY**
    * **Controls:** Right-Click (session) / Shift+Right-Click (reset)
    * **Purpose:** Starts an interactive chat editing session or resets entity NBT overrides back to JSON definitions.
* **RELOCATE**
    * **Controls:** Right-Click (select) / Left-Click block / Shift+Right-Click
    * **Purpose:** Relocates the anomaly to a targeted block or applies coordinate offsets via chat (`dx dy dz`).
* **DELETE**
    * **Controls:** Left-Click / Shift+Left-Click
    * **Purpose:** Safe deletion with a 5-second confirmation window to prevent accidental removal, or instant deletion with Shift.

---

## 📜 Commands

* `/anomaly create <type>` — Spawns an anomaly of the specified type at the player's position (with auto-complete for registered types).
* `/anomaly ignore <player> <true|false>` — Toggles player protection status (protected players are ignored by triggers, physics, and damage).

---

## 💻 Developer API

All events are published on `MinecraftForge.EVENT_BUS` and are `@Cancelable`:

* `AnomalyTriggerEvent` — Fired when an entity enters an anomaly detection area.
* `AnomalyDamageEvent` — Fired during damage calculation and application.
* `AnomalyPhysicsEvent` — Fired before applying pull/spin vectors.
* `AnomalyZoneTransitionEvent` — Fired when an entity crosses boundaries between internal layers.
* `AnomalyItemInteractEvent` — Fired when dropped items (`ItemEntity`) enter an anomaly zone.

---

## 📚 Architecture Documentation

Detailed specification of class structures, ECS components, and JSON schema format:

* [ARCHITECTURE_EN.md](ARCHITECTURE_EN.md) — English documentation.
* [ARCHITECTURE_RU.md](ARCHITECTURE_RU.md) — Документация на русском языке.