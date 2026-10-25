# 💻 Console Commands

All commands are registered under `/anomaly` and require permission level 2 (Operator).

---

## 1. `/anomaly create <type>`
Spawns a new anomaly of the given type at the player's position.

- **Parameters:**
  - `type` — String identifier from registered datapacks (`zharka`, `smart_zharka`, etc.). Supports tab completion.
- **Example:**
  ```text
  /anomaly create smart_zharka
  ```

---

## 2. `/anomaly state set <state>`
Forces a phase transition on the nearest anomaly within a 10-block radius.

- **Parameters:**
  - `state` — Target state name (`idle`, `warmup`, `burst`, `cooldown`). Supports tab completion for valid states.
- **Example:**
  ```text
  /anomaly state set burst
  ```

---

## 3. `/anomaly ignore <player> <true|false>`
Toggles immunity to all anomaly effects for a specific player (God Mode for mapmakers and developers).

- **Parameters:**
  - `player` — Target player selector or username.
  - `true` — Ignore player (anomalies will not pull, damage, or trigger on this player).
  - `false` — Remove immunity.
- **Example:**
  ```text
  /anomaly ignore Dev true
  ```
- **Persistence:** Ignored player UUIDs are stored in `config/anomalies_ignored_players.json` and persist across server reboots.
