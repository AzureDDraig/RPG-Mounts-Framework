# RPG Mount Framework - Rolling Build Changelog

This document tracks all builds and changes implemented.

---

### [Build 127 - 26-209-11-25] (Compilation Successful)
- **GitHub Issue #12 Fixes & Feature Additions**:
  - **Mount Creator UI Mount List Scrollbar & Mouse Wheel Scrolling (`MountCreatorScreen.java`)**: Added vertical scrollbar, scissor clipping box (`RenderSystem.enableScissor`), and mouse wheel scrolling to the sidebar template list.
  - **Animation Tab Walk & Run Field Saving (`MountCreatorScreen.java`)**: Fixed saving and network synchronization of `walk` and `run` animation mappings in `saveTextFieldsToActiveTemplate()` regardless of model string casing.
  - **Custom Hurt Sound Resolution & Playback (`RPGMountEntity.java`)**: Refactored `getSoundEvent` and `playMountSound` with `rpg_mounts:` namespace prefix fallback and safe `ResourceLocation.tryParse(...)` validation for custom hurt sound files.
  - **Alphabetical Mount List Sorting**: Mount lists across Creator UI sidebar and Mount Manager HUD tabs (Ground, Aquatic, Flying) are now sorted alphabetically (`a.compareToIgnoreCase(b)`).
  - **HUD Ancestry Tab Overflow Fix (`MountHUDScreen.java`)**: Dynamically calculated right-hand tab bounds (`maxAncRight`) and added text truncation (`plainSubstrByWidth`) to prevent the Ancestry tab label from overflowing past the HUD frame.
  - **Favorite Mount Hotkey & Star Toggle (`RPGMountsClient.java`, `MountHUDScreen.java`)**: Registered `favoriteKey` key mapping. Added `★ [Fav]` star toggle in `MountHUDScreen.java` next to mount titles to set favorite mounts, and pressing the hotkey summons the favorite mount instantly.
  - **Default Mount Preview Camera Settings (`MountData.java`, `MountHUDScreen.java`)**: Added `previewZoom` and `previewOffsetY` to `MountData` and 3D preview viewport calculation so models open pre-zoomed and centered according to template defaults.

---

### [Build 126 - 26-206-21-54] (Compilation Successful)
- **Epic Fight / Resource Reload Crash Fix (`DynamicMountPackResources.java`)**:
  - Resolved client crash during game loading / overlay render (`ForgeLoadingOverlay`) caused when Epic Fight or Minecraft reloads sound and pack resources.
  - Added strict path sanitization (`sanitizePath`) to lower-case, strip invalid special characters, and convert spaces to underscores for all dynamic `.ogg` sound resources and `.geo.json` / `.animation.json` pack entries.
  - Replaced raw `new ResourceLocation(...)` calls with `ResourceLocation.tryParse(...)` wrapped in safe exception handling to prevent any non-canonical user filename from crashing the game client.
  - Added case-insensitive cross-platform file lookup (`findFileCaseInsensitive`) to guarantee sound files match seamlessly on Windows, Linux, and macOS.

---

### [Build 125 - 26-205-00-53] (Compilation Successful)
- **Aquatic Mount Performance & Lag Spike Fixes**:
  - Identified and fixed 4 severe sources of server tick overhead and lag spikes from Spark profiling (`https://spark.lucko.me/kAvyAOpCZ0`).
  - **MountFloatGoal Bypassing**: Created `MountFloatGoal` to prevent Vanilla `FloatGoal` from executing `jumpControl.jump()` 20 times per second for `AQUATIC` mounts in water.
  - **Water-Avoiding Stroll Fix**: Created `MountStrollGoal` to stop `WaterAvoidingRandomStrollGoal` from executing failing land-path search loops every tick while aquatic mounts are in water.
  - **Water Riding & Fluid Push Overrides**: Overrode `canBeRiddenInWater(Entity rider)` to return `true` for `AQUATIC` and `SURFACE_WATER` mounts to prevent Vanilla dismount ticks, and overrode `isPushedByFluid()` to return `false` for `AQUATIC` mounts in fluids.
  - **Throttled Water Breathing Allocations**: Optimized water breathing effect application in `RPGMountEntity.java` to tick every 10 ticks and only apply when duration is under 30 ticks, eliminating 95%+ of effect allocations and network sync packets.

---

### [Build 123 - 26-202-00-51] (Compilation Successful)
- **Multi-Language Support for "Animations" Tab**:
  - Added `"gui.rpg_mounts.creator.tab.animations"` translation key to all 9 supported language files (`en_us`, `pt_br`, `es_es`, `de_de`, `fr_fr`, `ja_jp`, `ko_kr`, `ru_ru`, `zh_cn`).
  - `"Animations"` tab header and tab bar text now automatically adapts according to the player's client language settings.

---

### [Build 122 - 26-202-00-38] (Compilation Successful)
- **Dynamic "Animations" Tab UI**:
  - Moved the Animation Triggers panel out of the 3D preview viewport into a dedicated, dynamic `"Animations"` tab in `MountCreatorScreen.java`.
  - The `"Animations"` tab automatically appears in the top tab navigation bar whenever a mount template has a GeckoLib `.animation.json` file configured, and hides if no animation file is set.
  - The right side 3D Mount Preview viewport is now 100% clean and unobstructed.
  - The 8 animation fields (`Idle`, `Walk`, `Run`, `Swim`, `Fly`, `Hover`, `Attack`, `Jump`) now render cleanly stacked inside the left form panel box, complete with auto-detection dropdowns and `Tab` completion.
- **Universal Text Bleedthrough Fix for Dropdown Boxes**:
  - Elevated suggestion dropdown rendering to `Z=500.0f` with `graphics.pose().pushPose()` / `graphics.pose().translate(0, 0, 500.0f)` and solid opaque background fill (`0xFF161616`) in `MountCreatorScreen.java` and `AbilityCreatorScreen.java`.
  - Solved text bleedthrough where underlying field labels or inputs (`crabmount.png`, `Texture Location:`) bled through active dropdown boxes.

---

### [Build 121 - 26-202-00-28] (Compilation Successful)
- **Command Autocomplete Root-Cause Fix**:
  - Removed client-incompatible `if (context.getSource() instanceof CommandSourceStack)` checks from `OWNED_MOUNTS_SUGGESTER` and `TARGET_OWNED_MOUNTS_SUGGESTER` in `MountCommands.java`.
  - In Minecraft's client-side chat bar, Brigadier suggestion providers receive a `ClientSuggestionProvider` (which does NOT implement `CommandSourceStack`), causing suggestion providers with `instanceof CommandSourceStack` checks to produce zero suggestions on the client.
  - Both suggestion providers now work seamlessly on both client and server, immediately displaying autocompletes for Mount IDs (e.g. `new_mount_5`), custom names (e.g. `Spidersz`), display names, and template IDs.

---

### [Build 120 - 26-202-00-12] (Compilation Successful)
- **Animation Triggers HUD UI & Z-Level Fixes**:
  - Elevated rendering of the Animation Triggers HUD using Z-level translation (`Z=200`) in `MountCreatorScreen.java` to prevent 3D preview model and background text bleedthrough.
  - Added a solid dark grey backdrop box (`0xFE181818`) with gold/black borders (`UIHelper.drawOutline`) behind the 8 animation fields.
  - Implemented mouse click interception for the Animation HUD bounding box in `mouseClicked`, consuming click events to prevent misclicks behind the panel or dragging the 3D entity preview model.
- **Aquatic & Surface Water Movement Particles**:
  - Added customizable movement particle trail support (`groundParticleField`) for `AQUATIC` and `SURFACE_WATER` mounts when moving in water or submerged in `RPGMountEntity.java`.
  - Exposed `groundParticleField` in `MountCreatorScreen.java` for `AQUATIC` mounts so creators can customize water movement particles.
- **Remove Mount Autocomplete Search Fix**:
  - Changed `remove-mount` `instance_id` argument type from `greedyString()` to `StringArgumentType.string()` in `MountCommands.java`, enabling native client-side Brigadier Tab completion.
  - Updated `TARGET_OWNED_MOUNTS_SUGGESTER` and `suggestOwnedMounts` to unquote search queries and offer clean unquoted template IDs (e.g. `crimson_drake`), UUIDs, and quoted names.

---

### [Build 119 - 26-202-18-48] (Compilation Successful)
- **Remove Mount Command & Persistence Fixes**:
  - Updated `/rpg_mounts admin remove-mount` to support targeting both online players and offline player game profiles.
  - Implemented multi-matching resolution supporting UUIDs, template IDs (e.g. `crimson_drake`), custom display names, and space-separated strings.
  - Added `removeMatchingUnlockedMountsAsync` in `DatabaseManager.java` to purge matching mount instances from memory cache, `unlocked_mounts`, and `mount_gear` SQLite tables.
  - Added passenger ejection and entity despawning across all world levels, and cleared active mount records.
  - Updated Brigadier suggestions to wrap space-containing names in double quotes `"..."` and handle `greedyString()` arguments cleanly.
- **Animation Triggers HUD**:
  - Added a dedicated **Animation Triggers HUD** section on the "Model & Anims" tab in `MountCreatorScreen.java` exposing 8 animation state fields: `Idle`, `Walk`, `Run`, `Swim`, `Fly`, `Hover`, `Attack`, and `Jump`.
  - Integrated live auto-detection dropdown suggestions and `Tab`-key completion from `.animation.json` for all 8 animation states.
  - Added `C2S_SAVE_ANIMATION_MAPPINGS` network packet in `ModPackets.java` to serialize and synchronize `animation_mappings.json` across client and server.

---

### [Build 118 - 26-202-03-20] (Compilation Successful)
- **Animation Auto-Detection & Autocomplete**:
  - Implemented automatic parsing of GeckoLib `.animation.json` files in `MountRegistry.java` to extract all animation names defined for a mount model.
  - Added live search-by-typing, clickable suggestion dropdowns, and `Tab`-key completion to animation input fields in both `MountCreatorScreen` ("Model & Anims" & "Abilities" tabs) and `AbilityCreatorScreen` ("Custom Anim Name").
  - Registered `ANIMATION_SUGGESTER` in `MountCommands.java` for Brigadier command animation suggestions.

---

### [Build 117 - 26-202-03-13] (Compilation Successful)
- **UI Input Character Limit**:
  - Increased the maximum length of edit boxes to 1024 (from default 32) in both `AbilityCreatorScreen` and `MountCreatorScreen` to prevent truncation of long sound IDs and resource paths.
- **Multiplayer Mount Sounds**:
  - Fixed an issue where custom/unregistered mount sounds would not play on multiplayer servers due to packet serialization errors on unregistered `SoundEvent`s.
  - Implemented client-server sound synchronization using a custom `S2C_PLAY_SOUND` packet, which broadcasts sound playback events by name (string) and resolves them client-side.
- **Command Suggestions & Autocomplete**:
  - Restored autocomplete suggestions for the `/rpg_mounts admin remove-mount` command by changing the `instance_id` argument type from `greedyString()` to `string()`.
  - Added server-side initials-based matching (`cd` -> `crimson_drake`), substring, and prefix matching to suggestions.
  - Updated command suggestion formatting and template/instance resolution to seamlessly bypass client-side prefix matching filters.

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
