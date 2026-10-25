# 🛠️ Administrator Multitool Manual

The **Anomaly Multitool** (`anomaly_multitool`) is an in-game tool for inspecting, modifying, relocating, and deleting anomalies in real time.

---

## 🔄 Mode Switching

Cycle through modes using:
**`Shift + Right-Click in the air`**

Available modes:
1. `ANALYZE`
2. `MODIFY`
3. `RELOCATE`
4. `DELETE`

---

## 1. 🔍 Mode: ANALYZE
- **Action:** **Right-Click** on an anomaly.
- **Output:** Detailed chat breakdown of UUID, type, current active state, bounding box dimensions, zone layers (radii, damage, impulse forces), sound, and particles. Values customized via overrides are tagged with `[NBT]`.

---

## 2. ✏️ Mode: MODIFY
- **Action (Right-Click):** Opens a client-side **GUI**:
  - Configure width and height.
  - Set trigger detection radius.
  - Toggle pull-to-center physics.
  - Clicking **Save** immediately syncs overrides to the server and broadcasts them to tracking players.
- **Quick Reset (Shift + Right-Click):** Clears all custom NBT overrides and restores the default JSON configurations.

---

## 3. 📍 Mode: RELOCATE
- **Direct Move:**
  1. **Right-Click** on an anomaly to select it.
  2. **Left-Click** on any block to instantly teleport the anomaly there.
- **Relative Offset (Shift + Right-Click):**
  1. Hold `Shift` and **Right-Click** on the anomaly.
  2. Type a 3-number offset into chat (e.g., `0.5 1.0 -0.5`).
  3. The anomaly will smoothly shift by the specified delta.

---

## 4. 🗑️ Mode: DELETE
- **Instant Deletion:** **`Shift + Left-Click`** on an anomaly.
- **Confirmation Mode:** Single **`Left-Click`** displays a prompt. Click again within 5 seconds to confirm deletion.
