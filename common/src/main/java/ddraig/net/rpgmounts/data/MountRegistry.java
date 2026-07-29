package ddraig.net.rpgmounts.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.config.ModConfig;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG Mount Registry
 * Handles scanning, parsing, registering, and default backup templates generation.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented directories scanning, default templates creation, and in-memory ConcurrentHashMap cache.
 * - 2026-06-19: [Config Guided Loading] - Filter loaded templates by loaded_mounts config array.
 */
public class MountRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Map<String, MountData> loadedTemplates = new ConcurrentHashMap<>();
    public static final Map<String, MountData.AbilityData> customAbilities = new ConcurrentHashMap<>();
    private static File mountsFolder;
    private static File packsFolder;
    private static File soundsFolder;
    private static File abilitiesFolder;

    public static void init() {
        File baseDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        mountsFolder = new File(baseDir, "Mounts/Unpacked");
        packsFolder = new File(baseDir, "Mounts/Packs");
        soundsFolder = new File(baseDir, "Mounts/Sounds");
        abilitiesFolder = new File(baseDir, "Abilities");

        if (!mountsFolder.exists()) mountsFolder.mkdirs();
        if (!packsFolder.exists()) packsFolder.mkdirs();
        if (!soundsFolder.exists()) soundsFolder.mkdirs();
        if (!abilitiesFolder.exists()) abilitiesFolder.mkdirs();

        reloadAbilities();
        reloadTemplates();
    }

    public static void reloadAbilities() {
        customAbilities.clear();
        File baseDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        abilitiesFolder = new File(baseDir, "Abilities");
        if (!abilitiesFolder.exists()) {
            abilitiesFolder.mkdirs();
        }
        
        // 1. Generate/Register default abilities first (saves them if not present on disk)
        generateDefaultAbilities();
        
        // 2. Load custom / overridden abilities from disk
        File[] files = abilitiesFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    try (FileReader reader = new FileReader(file)) {
                        MountData.AbilityData ability = GSON.fromJson(reader, MountData.AbilityData.class);
                        if (ability != null && !ability.name.isEmpty()) {
                            customAbilities.put(ability.name, ability);
                        }
                    } catch (IOException e) {
                        RPGMounts.LOGGER.error("Failed to parse custom ability at " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
    }

    public static void saveCustomAbility(MountData.AbilityData ability) {
        if (ability.name == null || ability.name.isEmpty()) return;
        File baseDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        File folder = new File(baseDir, "Abilities");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        String fileName = ability.name.toLowerCase().replace(" ", "_") + ".json";
        File file = new File(folder, fileName);
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(ability, writer);
            customAbilities.put(ability.name, ability);
        } catch (IOException e) {
            RPGMounts.LOGGER.error("Failed to save custom ability " + ability.name, e);
        }
    }

    private static void registerAbility(String name, String type, boolean isPassive, int cooldown, double stamina, double power, double damage, String damageType, double range, int duration, String description, String sound, String particle, String anim) {
        MountData.AbilityData ab = new MountData.AbilityData();
        ab.name = name;
        ab.type = type;
        ab.isPassive = isPassive;
        ab.cooldownTicks = cooldown;
        ab.staminaCost = stamina;
        ab.power = power;
        ab.damage = damage;
        ab.damageType = damageType;
        ab.range = range;
        ab.durationTicks = duration;
        ab.description = description;
        ab.sound = sound;
        ab.particle = particle;
        ab.vanillaAnimation = anim;
        
        File baseDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        File folder = new File(baseDir, "Abilities");
        String fileName = ab.name.toLowerCase().replace(" ", "_") + ".json";
        File file = new File(folder, fileName);
        if (!file.exists()) {
            saveCustomAbility(ab);
        } else {
            customAbilities.put(ab.name, ab);
        }
    }

    private static void generateDefaultAbilities() {
        // 1-12. Passive Abilities
        registerAbility("Fireproof Scales", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Mount and rider gain full immunity to lava and fire damage.", "", "minecraft:small_flame", "NONE");
        registerAbility("Gills of the Deep", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Rider gains infinite Water Breathing while riding the mount.", "", "", "NONE");
        registerAbility("Traction Tread", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Complete immunity to slow-down blocks (Soul Sand, Cobwebs, Sweet Berries).", "", "", "NONE");
        registerAbility("Feather Light", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Reduces mount fall damage by 90% and decreases landing speed.", "", "minecraft:cloud", "NONE");
        registerAbility("Rejuvenation Aura", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Mount regenerates 0.5 HP/s; rider gets Regen I if stamina > 50%.", "", "minecraft:happy_villager", "NONE");
        registerAbility("Deep Diver", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Swim speed increased by 40% and allows underwater sprinting.", "", "", "NONE");
        registerAbility("Step Assist", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Increases step height to 1.5 blocks; walk up full blocks seamlessly.", "", "", "NONE");
        registerAbility("Shadow Camouflage", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Reduces mob detection range by 50% in dark areas (light level < 5).", "", "minecraft:ash", "NONE");
        registerAbility("Bonding Boost", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Increases all bonding point gains from actions by 50%.", "", "", "NONE");
        registerAbility("Cargo Cushion", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Reduces stamina depletion from carrying heavy cargo by 50%.", "", "", "NONE");
        registerAbility("Thorn Guard", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Reflects 20% of incoming physical damage back to attacker.", "", "", "NONE");
        registerAbility("Photosynthesis", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Stamina recovery rate increased by 30% under direct sunlight.", "", "", "NONE");

        // 13-25. Active Combat & Utility
        registerAbility("Flame Breath", "PROJECTILE", false, 160, 30.0, 2.0, 6.0, "FIRE", 15.0, 0, "Shoots a flame stream dealing 6 Fire damage and igniting targets.", "minecraft:entity.blaze.shoot", "minecraft:flame", "DRAGON_BITE");
        registerAbility("Tail Sweep", "AOE", false, 80, 25.0, 4.0, 4.0, "PHYSICAL", 4.0, 0, "Deals 4 Physical damage and high knockback in a 360-degree radius.", "minecraft:entity.player.attack.knockback", "minecraft:sweep_attack", "NONE");
        registerAbility("Venomous Bite", "SINGLE_TARGET", false, 100, 20.0, 1.0, 5.0, "PHYSICAL", 3.0, 100, "Deals 5 Physical damage and applies Poison II for 5 seconds.", "minecraft:entity.wolf.bite", "minecraft:spore_blossom_air", "WOLF_BITE");
        registerAbility("Sonic Screech", "AOE", false, 200, 35.0, 2.0, 3.0, "MAGIC", 8.0, 120, "Deals 3 Magic damage, applying Slowness II and Weakness I in 8 blocks.", "minecraft:entity.warden.sonic_boom", "minecraft:sonic_boom", "DRAGON_FLAP");
        registerAbility("Thunder Stomp", "AOE", false, 240, 40.0, 0.0, 8.0, "EXPLOSIVE", 6.0, 30, "Deals 8 Explosive damage and stuns targets for 1.5 seconds.", "minecraft:entity.generic.explode", "minecraft:explosion", "HORSE_REAR");
        registerAbility("Frost Nova", "AOE", false, 180, 30.0, 0.0, 4.0, "MAGIC", 6.0, 60, "Freeze wave dealing 4 Magic damage and locking targets in ice (Slowness V).", "minecraft:entity.player.attack.weak", "minecraft:snowflake", "NONE");
        registerAbility("Blight Spit", "PROJECTILE", false, 120, 25.0, 1.0, 5.0, "MAGIC", 12.0, 80, "Launches acidic splash projectile dealing 5 Magic damage and Wither I.", "minecraft:entity.llama.spit", "minecraft:dragon_breath", "DRAGON_BITE");
        registerAbility("Healing Touch", "BUFF", false, 300, 50.0, 0.0, 0.0, "NONE", 0.0, 0, "Rejuvenates 10 HP to the mount and 4 HP to the rider.", "minecraft:entity.experience_orb.pickup", "minecraft:heart", "NONE");
        registerAbility("Sonic Dash", "DASH", false, 60, 15.0, 4.0, 3.0, "PHYSICAL", 3.0, 0, "Dashes forward instantly, dealing 3 Physical damage to mobs hit.", "minecraft:entity.horse.gallop", "minecraft:cloud", "HORSE_REAR");
        registerAbility("Abyssal Stealth", "STEALTH", false, 400, 40.0, 0.0, 0.0, "NONE", 0.0, 200, "Mount and rider gain Invisibility & silence for 10s. attacking breaks.", "minecraft:entity.phantom.extinguish", "minecraft:squid_ink", "NONE");
        registerAbility("High Jump", "BUFF", false, 80, 20.0, 5.0, 0.0, "NONE", 0.0, 0, "Propels mount 4 blocks straight up in the air.", "minecraft:entity.rabbit.jump", "minecraft:cloud", "HORSE_REAR");
        registerAbility("Wind Glide", "BUFF", false, 100, 15.0, 0.0, 0.0, "NONE", 0.0, 160, "Reduces gravity dramatically for 8s to glide across gaps.", "minecraft:entity.elytra.flying", "minecraft:end_rod", "NONE");
        registerAbility("Frightening Roar", "BUFF", false, 240, 30.0, 2.0, 0.0, "NONE", 8.0, 120, "Forces all nearby hostile mobs to flee in terror for 6 seconds.", "minecraft:entity.ender_dragon.growl", "minecraft:angry_villager", "DRAGON_FLAP");

        // 26-30. Passive Abilities
        registerAbility("Toxic Secretions", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Attackers have a 30% chance to be poisoned (Poison I, 4s) on hit.", "", "minecraft:effect", "NONE");
        registerAbility("Glacial Aura", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Chills nearby enemies (within 4 blocks), reducing their speed by 15%.", "", "minecraft:snowflake", "NONE");
        registerAbility("Magnetosphere", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Automatically draws dropped items within 5 blocks toward the rider.", "", "minecraft:crit", "NONE");
        registerAbility("Night Eyes", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Grants the rider Night Vision when riding in dark places or at night.", "", "minecraft:glow", "NONE");
        registerAbility("Reinforced Hide", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Grants static flat damage reduction (reduces all incoming damage by 1.5).", "", "", "NONE");

        // 31-35. Active Combat
        registerAbility("Lightning Strike", "PROJECTILE", false, 180, 35.0, 2.0, 7.0, "MAGIC", 15.0, 0, "Summons a bolt of lightning dealing 7 Magic damage and shocking targets.", "minecraft:entity.lightning_bolt.thunder", "minecraft:electric_spark", "NONE");
        registerAbility("Spore Blast", "AOE", false, 100, 20.0, 1.0, 3.0, "NONE", 5.0, 80, "Releases blind gas cloud dealing 3 Poison damage and blinding for 4s.", "minecraft:entity.creeper.primed", "minecraft:spore_blossom_air", "NONE");
        registerAbility("Iron Wall", "BUFF", false, 240, 30.0, 0.0, 0.0, "NONE", 0.0, 120, "Creates a temporary barrier absorbing 50% of all damage for 6 seconds.", "minecraft:item.armor.equip_iron", "minecraft:block_marker", "NONE");
        registerAbility("Infernal Charge", "DASH", false, 120, 25.0, 4.0, 5.0, "FIRE", 4.0, 0, "Dashes forward dealing 5 Fire damage and leaving trails of fire.", "minecraft:item.firecharge.use", "minecraft:flame", "NONE");
        registerAbility("Life Steal Bite", "SINGLE_TARGET", false, 140, 25.0, 1.0, 6.0, "PHYSICAL", 3.0, 0, "Deals 6 Physical damage, stealing 50% of damage dealt to heal mount.", "minecraft:entity.fox.bite", "minecraft:damage_indicator", "WOLF_BITE");

        // 36-40. Active Movement & Utility
        registerAbility("Teleport Dash", "DASH", false, 160, 30.0, 8.0, 0.0, "NONE", 8.0, 0, "Teleports the mount forward 8 blocks, bypassing solid obstacles.", "minecraft:entity.enderman.teleport", "minecraft:portal", "NONE");
        registerAbility("Glittering Dust", "BUFF", false, 80, 15.0, 0.0, 0.0, "NONE", 0.0, 200, "Highlights all chests and ore containers within 16 blocks for 10s.", "minecraft:block.amethyst_block.hit", "minecraft:wax_on", "NONE");
        registerAbility("Aqua Propulsion", "BUFF", false, 100, 20.0, 2.0, 0.0, "NONE", 0.0, 100, "Boosts swimming speed by 200% for 5 seconds (aquatic-only).", "minecraft:entity.dolphin.play", "minecraft:bubble_pop", "NONE");
        registerAbility("Trample", "DASH", false, 150, 30.0, 3.5, 4.0, "PHYSICAL", 4.0, 60, "Charge knock-down, dealing 4 Physical damage and applying Slowness.", "minecraft:entity.ravager.roar", "minecraft:crit", "HORSE_REAR");
        registerAbility("Feather Hover", "BUFF", false, 120, 15.0, 0.0, 0.0, "NONE", 0.0, 100, "Suspends mount Y-level in mid-air, allowing hovering for 5s.", "minecraft:entity.bat.takeoff", "minecraft:cloud", "NONE");

        // 41. Passive Spider Climb
        registerAbility("Spider Climb", "BUFF", true, 0, 0.0, 0.0, 0.0, "NONE", 0.0, 0, "Allows the mount to scale vertical walls and cliffs seamlessly on collision.", "", "", "NONE");
    }

    public static void reloadTemplates() {
        loadedTemplates.clear();
        
        // Scan packsFolder and unpack on load
        File[] packFiles = packsFolder.listFiles();
        if (packFiles != null) {
            for (File packFile : packFiles) {
                if (packFile.isFile() && packFile.getName().endsWith(".zip")) {
                    String name = packFile.getName().substring(0, packFile.getName().length() - 4);
                    unpackTemplate(name);
                }
            }
        }
        
        // Scan Unpacked folders
        File[] folders = mountsFolder.listFiles();
        boolean anyFolderChecked = false;
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    File mountJson = new File(folder, "mount.json");
                    if (mountJson.exists()) {
                        anyFolderChecked = true;
                        try (FileReader reader = new FileReader(mountJson)) {
                            MountData data = GSON.fromJson(reader, MountData.class);
                            if (data != null && !data.id.isEmpty()) {
                                if (ModConfig.get().general.loaded_mounts.contains(data.id)) {
                                    loadedTemplates.put(data.id, data);
                                }
                            }
                        } catch (IOException e) {
                            RPGMounts.LOGGER.error("Failed to parse mount config at " + mountJson.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }

        // Generate defaults if no files exist or loaded_mounts is empty and there are no files
        if (!anyFolderChecked && loadedTemplates.isEmpty()) {
            RPGMounts.LOGGER.info("No mount templates found. Generating default templates...");
            generateDefaults();
            boolean modified = false;
            for (String def : new String[]{"horse", "wolf", "dragon"}) {
                if (!ModConfig.get().general.loaded_mounts.contains(def)) {
                    ModConfig.get().general.loaded_mounts.add(def);
                    modified = true;
                }
            }
            if (modified) {
                ModConfig.get().save();
            }
            folders = mountsFolder.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory()) {
                        File mountJson = new File(folder, "mount.json");
                        if (mountJson.exists()) {
                            try (FileReader reader = new FileReader(mountJson)) {
                                MountData data = GSON.fromJson(reader, MountData.class);
                                if (data != null && !data.id.isEmpty()) {
                                    if (ModConfig.get().general.loaded_mounts.contains(data.id)) {
                                        loadedTemplates.put(data.id, data);
                                    }
                                }
                            } catch (IOException e) {
                                RPGMounts.LOGGER.error("Failed to parse mount config at " + mountJson.getAbsolutePath(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateDefaults() {
        // 1. Default Horse
        MountData horse = new MountData();
        horse.id = "horse";
        horse.name = "Stallion";
        horse.description = "A standard rideable horse.";
        horse.category = "GROUND";
        horse.modelType = "vanilla";
        horse.modelId = "minecraft:horse";
        horse.scale = 1.0f;
        horse.stats.maxHealth = 30.0;
        horse.stats.movementSpeed = 0.22;
        horse.stats.jumpHeight = 0.75;
        horse.allowed_cargo_map.put("minecraft:chest", 9);
        horse.allowed_cargo_map.put("minecraft:barrel", 18);
        horse.allowed_food_map.put("minecraft:wheat", 2);
        horse.allowed_food_map.put("minecraft:apple", 5);
        horse.allowed_food_map.put("minecraft:golden_carrot", 10);
        writeDefault(horse);

        // 2. Default Wolf
        MountData wolf = new MountData();
        wolf.id = "wolf";
        wolf.name = "Timber Wolf";
        wolf.description = "A loyal wolf companion.";
        wolf.category = "GROUND";
        wolf.modelType = "vanilla";
        wolf.modelId = "minecraft:wolf";
        wolf.scale = 1.2f;
        wolf.stats.maxHealth = 20.0;
        wolf.stats.movementSpeed = 0.28;
        wolf.stats.jumpHeight = 0.55;
        wolf.allowed_cargo_map.put("minecraft:chest", 9);
        wolf.allowed_food_map.put("minecraft:beef", 5);
        wolf.allowed_food_map.put("minecraft:chicken", 3);
        wolf.allowed_food_map.put("minecraft:porkchop", 5);
        writeDefault(wolf);

        // 3. Default Dragon
        MountData dragon = new MountData();
        dragon.id = "dragon";
        dragon.name = "Ender Drake";
        dragon.description = "A powerful flying dragon mount.";
        dragon.category = "FLYING";
        dragon.modelType = "vanilla";
        dragon.modelId = "minecraft:ender_dragon";
        dragon.scale = 0.3f;
        dragon.stats.maxHealth = 150.0;
        dragon.stats.movementSpeed = 0.2;
        dragon.stats.flySpeed = 0.35;
        dragon.stats.jumpHeight = 1.2;
        dragon.allowed_cargo_map.put("minecraft:shulker_box", 27);
        dragon.allowed_food_map.put("minecraft:chorus_fruit", 5);
        dragon.allowed_food_map.put("minecraft:golden_apple", 20);
        writeDefault(dragon);
    }

    private static void writeDefault(MountData data) {
        File folder = new File(mountsFolder, data.id.toLowerCase(java.util.Locale.ROOT));
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, "mount.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            RPGMounts.LOGGER.error("Failed to write default mount template for " + data.id, e);
        }
    }

    public static boolean packTemplate(String mountId) {
        File folder = new File(mountsFolder, mountId.toLowerCase(java.util.Locale.ROOT));
        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        File zipFile = new File(packsFolder, mountId + ".zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(file.getName()));
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        zos.write(bytes, 0, bytes.length);
                        zos.closeEntry();
                    }
                }
            }
            return true;
        } catch (IOException e) {
            RPGMounts.LOGGER.error("Failed to pack template " + mountId, e);
            return false;
        }
    }

    public static boolean unpackTemplate(String mountId) {
        File zipFile = new File(packsFolder, mountId + ".zip");
        if (!zipFile.exists() || !zipFile.isFile()) {
            return false;
        }

        File destFolder = new File(mountsFolder, mountId.toLowerCase(java.util.Locale.ROOT));
        File mountJson = new File(destFolder, "mount.json");
        if (destFolder.exists() && mountJson.exists() && zipFile.lastModified() <= mountJson.lastModified()) {
            return false;
        }

        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }

        try {
            String destCanonical = destFolder.getCanonicalPath();
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File file = new File(destFolder, entry.getName());
                    String fileCanonical = file.getCanonicalPath();
                    if (!fileCanonical.startsWith(destCanonical)) {
                        throw new SecurityException("Zip Slip directory traversal vulnerability detected in " + entry.getName());
                    }
                    
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        File parent = file.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }
            return true;
        } catch (IOException | SecurityException e) {
            RPGMounts.LOGGER.error("Failed to unpack template " + mountId, e);
            return false;
        }
    }

    public static File getMountsFolder() {
        return mountsFolder;
    }

    public static File getSoundsFolder() {
        return soundsFolder;
    }

    public static MountData getTemplate(String id) {
        return loadedTemplates.get(id);
    }

    private static final Map<String, java.util.List<String>> animationNamesCache = new ConcurrentHashMap<>();

    public static void clearAnimationCache() {
        animationNamesCache.clear();
    }

    public static java.util.List<String> getAnimationNamesForModel(String modelOrTemplateId) {
        if (modelOrTemplateId == null || modelOrTemplateId.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        String modelId = modelOrTemplateId;
        MountData template = loadedTemplates.get(modelOrTemplateId);
        if (template != null && template.modelId != null && !template.modelId.isEmpty()) {
            modelId = template.modelId;
        }
        
        if (animationNamesCache.containsKey(modelId)) {
            return animationNamesCache.get(modelId);
        }

        java.util.List<String> anims = new java.util.ArrayList<>();
        File configFolder = getMountsFolder();
        File unpackedFolder = new File(configFolder, modelId);
        if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
            File[] files = unpackedFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".animation.json")) {
                        try (FileReader reader = new FileReader(f)) {
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("animations") && json.get("animations").isJsonObject()) {
                                com.google.gson.JsonObject animationsObj = json.getAsJsonObject("animations");
                                for (String animName : animationsObj.keySet()) {
                                    if (!anims.contains(animName)) {
                                        anims.add(animName);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            RPGMounts.LOGGER.warn("Failed to parse animation file: " + f.getName(), e);
                        }
                    }
                }
            }
        }
        
        java.util.Collections.sort(anims);
        animationNamesCache.put(modelId, anims);
        return anims;
    }

    public static java.util.List<String> getAnimationSuggestions(String modelOrTemplateId, String query) {
        java.util.List<String> allAnims = getAnimationNamesForModel(modelOrTemplateId);
        if (allAnims.isEmpty()) return allAnims;
        if (query == null || query.trim().isEmpty()) {
            return allAnims;
        }

        String queryLower = query.trim().toLowerCase();
        java.util.List<String> result = new java.util.ArrayList<>();

        // Priority 1: Prefix match
        for (String anim : allAnims) {
            if (anim.toLowerCase().startsWith(queryLower)) {
                result.add(anim);
            }
        }

        // Priority 2: Substring match
        for (String anim : allAnims) {
            if (!result.contains(anim) && anim.toLowerCase().contains(queryLower)) {
                result.add(anim);
            }
        }

        // Priority 3: Initials match
        for (String anim : allAnims) {
            if (!result.contains(anim) && matchesInitials(queryLower, anim)) {
                result.add(anim);
            }
        }

        return result;
    }

    private static boolean matchesInitials(String typed, String name) {
        if (typed == null || typed.isEmpty()) return false;
        String[] words = name.toLowerCase().split("[\\s_\\-.:]+");
        if (words.length < typed.length()) return false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (words[i].isEmpty() || words[i].charAt(0) != c) {
                return false;
            }
        }
        return true;
    }
}
