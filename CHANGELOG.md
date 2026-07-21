# RPG Mount Framework - Rolling Build Changelog

This document tracks all builds and changes implemented.

---

### [Build 116 - 26-201-14-51] (Compilation Successful)
- **Jump Fall Damage**:
  - Implemented real-time tracking of jump altitudes (`jumpStartY`, `maxJumpY`) during a mount's active jump.
  - Subtracted the actual vertical jump height achieved (`maxJumpY - jumpStartY`) from the final `fallDistance` upon landing. This completely prevents self-fall damage from jumping when landing at the starting level or higher, while still retaining realistic damage if falling below the starting block.
- **Ability Naming Robustness**:
  - Overloaded `evaluatePassive()` in `RPGMountEntity.java` to support lists of alternate/alias names.
  - Configured `Step Assist` to check `"Step Assist"`, `"StepAssist"`, `"Assistant Step"`, `"Step Assistant"`, and `"Assistant Step Assist"` to ensure the passive works regardless of template naming variants.
- **Creator Sound Player**:
  - Shortened sound field widths in the "Sounds & FX" tab to leave room.
  - Added interactive green play buttons (`▶`) next to the Ambient, Step, Hurt, Death, and Spawn Sound fields in the Creator UI, enabling creators to play/test sounds directly.
- **Command Template Resolution**:
  - Updated admin template commands (`add-mount`, `unload-mount`, `edit-mount`, `delete-mount`, and `pack-mount`) to accept string types (supporting quotes and spaces for names like `"ekelboi lava"`).
  - Resolved input template IDs by checking if they match loaded template names case-insensitively.
  - Enhanced `LOADED_TEMPLATES_SUGGESTER` to autocomplete both template IDs and template names.

---

### [Build 115 - 26-201-14-33] (Compilation Successful)
- **Commands & Autocomplete**:
  - Changed `instance_id` argument type in `/rpg_mounts admin remove-mount` command from `string()` to `greedyString()`. This allows unquoted UUIDs (which contain hyphens) to be parsed correctly by Brigadier without syntax errors, restoring the autocomplete suggestions list.

---

### [Build 114 - 26-201-14-30] (Compilation Successful)
- **GUI & Summoning**:
  - Resolved an issue where `"SURFACE_WATER"` category mounts could not be summoned because they did not appear in the Mount Management HUD lists.
  - Added `"SURFACE_WATER"` category mounts to the client-side `aquaticMounts` category list in `MountHUDScreen`, making them visible and selectable.

---

### [Build 113 - 26-201-03-50] (Compilation Successful)
- **Commands**:
  - Changed `/rpg_mounts admin remove-mount` to use `StringArgumentType.string()` for `instance_id` to allow UUID hyphens.
  - Added active mount despawning and database cleanup when removing a mount.
  - Added `/rpg_mounts admin view-mounts <player>` command for listing owned mounts.
- **Separate Speed**:
  - Implemented ground/flying speed separation for flying mounts.
- **Localization & GUI**:
  - Resolved PT-BR/translation text overlaps in HUD tabs, stat rows, and ability slots.
  - Made Creator UI suggestions dropdown wider and dynamically truncated items using font width.
- **Jump Fall Damage**:
  - Subtracted the mount's jump height block equivalent from fall damage to prevent self-damage.
- **Surface Water Mounts**:
  - Added `"SURFACE_WATER"` category, enabling boat-like floating physics in water and slow land traversal.
- **Template Duplication**:
  - Added `Copy Mount` button to the Creator UI sidebar to easily clone template configurations.

---

### [Build 112 - 26-180-13-05]
- **Animation Config**:
  - Added `animation_mappings.json` config load and network synchronization.
  - Updated keyframe animations and GeckoLib models to dynamically retrieve custom walk, idle, run, swim, fly, and attack animations.
