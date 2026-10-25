# Datapack Format Specification (JSON & .anom)

Complete reference for creating, configuring, and orchestrating anomaly zones using datapacks.

---

## 📁 1. Directory Structure

All anomaly definitions reside within:
`data/<mod_id>/anomalies/`

All type ids, state names, and recipe keys are automatically converted to **lowercase (`toLowerCase()`)**.

### Canonical Directory Layout:
```text
data/<mod_id>/anomalies/
└── smart_zharka/
    ├── smart_zharka.anom          # State machine logic script
    ├── recipes/                   # Transmutation recipes
    │   ├── cook_pork.json
    │   └── make_glass.json
    └── states/                    # Anomaly phase configurations
        ├── idle.json
        ├── warmup.json
        ├── burst.json
        └── cooldown.json
```

---

## 🎲 2. Number Ranges (`MinMaxRange`)

Numeric properties support flexible definitions:
- **Fixed Value:** `"interval": 12`
- **Random Range `[min, max]`:** `"interval": [40, 80]`

---

## 📄 3. Phase JSON Specification (`AnomalyDefinition`)

```json
{
  "size": {
    "width": 1.6,
    "height": 0.4
  },
  "particles": [
    {
      "type": "minecraft:flame",
      "shape": "SPHERE",
      "interval": 2,
      "radius": 1.2,
      "height": 1.0,
      "count": 5
    }
  ],
  "sound": {
    "event": "minecraft:block.fire.ambient",
    "interval": [40, 80],
    "volume": 1.0,
    "pitch": 1.0
  },
  "trigger": {
    "expandRadius": 2.5,
    "checkInterval": 2
  },
  "zones": [
    {
      "radius": 1.3,
      "damage": {
        "innerAmount": 14.0,
        "outerAmount": 2.0,
        "fireSeconds": 3,
        "damageType": "fire"
      },
      "physics": {
        "pullToCenter": true,
        "pullForce": 0.12,
        "spinForce": 0.3,
        "impulseY": 0.15
      }
    }
  ],
  "physics": {
    "pullToCenter": false,
    "pullForce": 0.05
  },
  "ignoreOtherAnomalies": true
}
```

### Key Elements:
- **`size`:** Bounding box dimensions for raycasts and clicks.
- **`particles`:** Client-side particle parameters (`shape`: `SPHERE`, `CYLINDER`, `DISC`).
- **`sound`:** Positional audio playback.
- **`trigger`:** Detection radius and interval.
- **`zones`:** Concentric physical and combat layers, sorted by radius (Zone 0 = core).
- **`ignoreOtherAnomalies`:** When true, does not pull or damage other anomalies.

---

## 🍳 4. Transmutation Recipes (`recipes/*.json`)

Path: `data/<mod_id>/anomalies/<type>/recipes/<recipe_id>.json`

```json
{
  "inputs": [
    {
      "item": "minecraft:porkchop",
      "count": 1
    }
  ],
  "output": {
    "item": "minecraft:cooked_porkchop",
    "count": 1
  },
  "time": 20
}
```

- When input items enter Zone 0, they are reserved into a `CraftingSession`.
- After `time` ticks, inputs are consumed and the result is spawned with smoke particles.

---

## 🛠 5. NBT Overrides (`customOverrides`)

Overrides can be assigned to an anomaly via the Multitool or commands. NBT overrides take precedence over JSON definitions.

Supported keys: `width`, `height`, `expandRadius`, `soundVolume`, `soundPitch`, `particleRadius`, `zones_count`, `zone_i_radius`, `zone_i_damage`, `zone_i_pullForce`, `zone_i_spinForce`, `zone_i_impulseY`, `pullToCenter`, `fireSeconds`.