# 📜 .anom Scripting Language Specification (DSL Syntax)

`.anom` files declare finite state machines: states, timers, chances, player triggers, and transmutation recipe bindings.

---

## 📁 File Location & Validation

- **Path:** `data/<mod_id>/anomalies/<folder_name>/<folder_name>.anom`
- The file name **must strictly match** its parent directory name (e.g., `data/anomalies/anomalies/zharka/zharka.anom`).
- Registry: `AnomalyScriptRegistry`, Loader: `AnomalyScriptLoader`.

---

## 🔤 Base Syntax

### 1. Initial State Declaration
```antlr
initial state idle;
```

### 2. Recipe Binding
```antlr
recipe cook_pork --> "recipes/cook_pork.json";
recipe make_glass --> "recipes/make_glass.json";
```

### 3. State Block (`state`)
```antlr
state <state_name> {
    recipe {
        cook_pork;
        make_glass;
    }

    when <condition> -> <next_state>;
}
```

---

## 🎯 Supported Conditions (`ICondition`)

| Condition | Syntax | Description |
|---|---|---|
| **Timer** | `timer(ticks)` | True when time in current state $\ge$ `ticks` (`timer(20)` = 1 sec). |
| **Probability** | `chance(val)` | Chance evaluation per tick (`chance(0.05)` = 5% per tick). |
| **Player in Zone** | `player.in_zone(idx)` | True while a player is inside zone index `idx`. |
| **Player Entered** | `player.entered_zone(idx)` | True on the single tick when a player enters zone `idx`. |
| **Player Exited** | `player.exited_zone(idx)` | True on the single tick when a player leaves zone `idx`. |

> ⚠️ **Zone Indices:** Zones are 0-indexed in ascending order of radius (Zone 0 = core).

---

## 🔀 Logical Operators

Supports parentheses `(...)` and standard precedence (`NOT` > `AND` > `OR`):

| Operation | Keyword | Symbolic |
|---|---|---|
| **AND** | `and` | `&&` |
| **OR** | `or` | `||` |
| **NOT** | `not` | `!` |

### Example:
```antlr
state active {
    when (player.entered_zone(0) || timer(100)) && !chance(0.2) -> cooldown;
}
```
