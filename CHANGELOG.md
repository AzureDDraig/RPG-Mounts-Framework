# RPG Mount Framework - Changelog

---

### [Build 135]
- **Mount Death Inventory Duplication Fix**:
  - Fixed an issue where items in a mount's backpack or equipped gear would duplicate when the mount died.
  - When a mount dies, its dropped equipment and backpack items are now properly cleared from storage so reviving or re-summoning the mount starts with an empty inventory.

---

### [Build 133/134]
- **Prevent Duplicate Mounts Fix**:
  - Fixed an issue where feeding or interacting with a mount could save duplicate copies to your mount collection.
  - Feeding a mount now properly updates and saves your existing mount.
  - Evolving a mount is now blocked if you already own that target mount type when duplicate prevention is turned on.
  - Added `/rpg_mounts admin deduplicate-mounts <player>` command to automatically clean up existing duplicate mounts for any player (keeps the highest-level mount).
  - Improved mount name recognition so commands understand names whether capitalized or lowercase.
- **Clean Release Packaging**:
  - Excluded development and source files from public release downloads.
  - Fixed CurseForge file names so they match the exact mod jar file.

---

### [Build 131/132]
- **Animation Menu Improvements**:
  - Fixed an issue where clicking on suggested animation names (like walk, run, idle) in the Creator menu failed to select them.
  - Added full keyboard support: use Up/Down arrow keys to browse suggestions, and press Enter or Tab to select.
  - Fixed mouse wheel scrolling inside the animation suggestions dropdown.
- **Automated Publishing**:
  - Set up automatic release builds for both Fabric and Forge on GitHub and CurseForge.

---

### [Build 130]
- **Mount Creator Menu Fixes**:
  - Fixed text boxes for Walk, Run, Idle, Swim, Fly, Hover, Attack, and Jump animations so they can be clicked and edited without issues.
  - Fixed suggestion boxes blocking clicks to sound settings below them.
  - Adjusting the 3D model zoom in the Creator now saves as the default view zoom in the Mount Manager menu.
- **Visuals and Effects**:
  - Fixed movement particle trails so other players on multiplayer servers can see them while you ride.
  - Summoning a mount now plays sound and particle effects for all nearby players on the server.
- **Duplicate Prevention Setting**:
  - Added a `prevent_duplicate_mounts` setting in the server config and in-game menu to stop players from getting duplicate mounts of the same type.

---

### [Build 128/129]
- **Mount Saving Improvements**:
  - Fixed a bug where creating more than 24 mounts could overwrite existing mount templates.
  - The game now automatically detects and registers all unpacked mount folders upon startup.

---

### [Build 127]
- **User Interface & Controls**:
  - Added scrollbar and mouse wheel scrolling to the sidebar list in the Mount Creator.
  - Added a Favorite Star button (`★`) in the Mount Manager HUD and a Favorite Mount keybind to instantly summon your favorite mount.
  - Mount lists in the Creator and Mount Manager are now sorted alphabetically.
  - Fixed text overflow in the Mount Ancestry tab.
  - Fixed custom hurt sounds not playing for custom mounts.

---

### [Build 126]
- **Compatibility & Stability**:
  - Fixed a crash during game loading when reloading resource packs or playing with Epic Fight.
  - Improved file lookup on Linux, macOS, and Windows to prevent missing sound file errors.

---

### [Build 125]
- **Aquatic Mount Performance**:
  - Fixed major server lag spikes caused by swimming mounts in water.
  - Improved water riding physics so players are not unexpectedly dismounted.
  - Optimized underwater breathing effects to reduce network lag.

---

### [Build 123]
- **Translations**:
  - Added translations for the Animations tab across all supported languages (English, Portuguese, Spanish, German, French, Japanese, Korean, Russian, Simplified Chinese).

---

### [Build 122]
- **Dedicated Animations Tab**:
  - Moved animation settings to their own tab in the Mount Creator so the 3D preview window is clear and unobstructed.
  - Fixed text from underlying menus showing through open dropdown boxes.

---

### [Build 121]
- **Command Suggestions**:
  - Fixed chat autocomplete suggestions for mount commands so they work for all players in multiplayer.

---

### [Build 120]
- **Aquatic Movement Trails**:
  - Added custom particle trail support for water mounts while swimming.
  - Improved visual layering in the Mount Creator menu.

---

### [Build 119]
- **Admin Commands & Tools**:
  - Improved `/rpg_mounts admin remove-mount` to support offline players, mount names, and template IDs.
  - Added an Animation Triggers HUD in the Creator menu with live suggestions.

---

### [Build 118]
- **Animation Auto-Detection**:
  - Added automatic scanning of model animation files to suggest animation names as you type.

---

### [Build 117]
- **Multiplayer Sound Sync**:
  - Fixed custom mount sounds so all players on multiplayer servers can hear them.
  - Increased character limit in text boxes to support long file paths.

---

### [Build 116]
- **Fall Damage & Sounds**:
  - Fixed mounts taking fall damage from their own normal jumps.
  - Added interactive Play buttons in the Mount Creator to preview sounds directly in-game.
  - Improved Step Assist ability detection.

---

### [Build 115]
- **Command Fixes**:
  - Fixed autocomplete suggestions in the remove-mount command for long mount IDs.

---

### [Build 114]
- **Water Mount Fixes**:
  - Fixed surface water mounts not appearing in the Mount Manager HUD.

---

### [Build 113]
- **New Features & Improvements**:
  - Added `/rpg_mounts admin view-mounts <player>` command to check owned mounts.
  - Added separate land and flight speeds for flying mounts.
  - Added Surface Water mount category for boat-like floating mounts.
  - Added a Copy Mount button in the Creator to easily duplicate existing templates.

---

### [Build 112]
- **Dynamic Animations**:
  - Added custom animation configuration support for walking, running, idling, swimming, flying, and attacking.
