package ddraig.net.rpgmounts.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ddraig.net.rpgmounts.RPGMounts;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * RPG Mounts Configuration Model
 * Manages load, save, and runtime configuration settings.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented ModConfig model, GSON serialization, default values, and culling/mortality configurations.
 * - 2026-06-19: [Phase 2 Config] - Added submerged rules, manual dismissal, bounds, stamina costs, and enhancer caps.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE = new ModConfig();
    private static File configFile;

    public General general = new General();
    public Stats stats = new Stats();
    public Stamina stamina = new Stamina();
    public BondingAndLeveling bondingAndLeveling = new BondingAndLeveling();
    public CombatAndEnhancers combatAndEnhancers = new CombatAndEnhancers();
    public MortalityAndSafety mortalityAndSafety = new MortalityAndSafety();
    public Evolution evolution = new Evolution();

    public static class General {
        public List<String> dimensionBlacklist = new ArrayList<>();
        public List<String> dimensionWhitelist = new ArrayList<>();
        public boolean useWhitelist = false;
        public boolean allowSummoningInWater = true;
        public boolean allowSummoningInLava = false;
        public boolean allowSummoningSubmerged = false;
        public boolean require_manual_dismissal = false;
        public boolean auto_dismiss_on_dismount = false;
        public int autoDespawnIdleSeconds = 600;
        public boolean enableMultiPassenger = true;
        public int culling_distance_blocks = 48;
        public boolean enableSpeedPulsing = true;
        public boolean enableRarity = true;
        public List<String> loaded_mounts = new ArrayList<>();
    }

    public static class Stats {
        public double min_health_allowed = 10.0;
        public double max_health_allowed = 1000.0;
        public double min_speed_allowed = 0.05;
        public double max_speed_allowed = 1.5;
    }

    public static class Stamina {
        public boolean enable_stamina_system = true;
        public double sprint_stamina_cost_per_second = 10.0;
        public double flight_stamina_cost_per_second = 8.0;
        public double flight_descent_stamina_regenerate_ratio = 0.4;
        public boolean allow_saddleless_jumping = true;
        public boolean allow_saddleless_sprinting = true;
        public double saddle_speed_boost_multiplier = 0.15;
    }

    public static class BondingAndLeveling {
        public boolean enable_bonding_buffs = true;
        public float bonding_speed_multiplier = 0.10f;
        public float bonding_health_multiplier = 0.15f;
        public boolean enableMountLevelling = true;
        public double baseXpRequirement = 100.0;
        public double xpExponent = 1.5;
        public double combatXpDealtRatio = 1.0;
        public double combatXpTakenRatio = 0.5;
        public double ridingXpPerSecond = 1.0;
    }

    public static class CombatAndEnhancers {
        public boolean enable_combat_abilities = true;
        public boolean enable_rider_reach_mixin = true;
        public float rider_reach_offset = 1.0f;
        public boolean enable_enhancers = true;
        public int max_enhancers_defense = 2;
        public int max_enhancers_movement = 2;
        public int max_enhancers_damage = 2;
        public int max_enhancers_ability = 2;
    }

    public static class MortalityAndSafety {
        public boolean enable_fall_protection = true;
        public int fall_protection_seconds = 3;
        public String mounts_mortality = "Timer"; // Timer, Permadeath, Item
        public String mounts_mortality_item = "minecraft:golden_carrot";
        public int mounts_mortality_cooldown_ticks = 6000;
    }

    public static class Evolution {
        public boolean enable_evolution_heritage = true;
        public String evolution_level_policy = "RETAIN"; // RESET, RETAIN, DEGRADE
        public double evolution_degrade_percentage = 0.25;
        public boolean enable_chroma_mutations = true;
        public double chroma_mutation_chance = 0.03;
        public boolean enable_environmental_triggers = true;
    }

    public static ModConfig get() {
        return INSTANCE;
    }

    public static void setInstance(ModConfig newConfig) {
        if (newConfig != null) {
            INSTANCE = newConfig;
        }
    }

    public static void load() {
        File configDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "server_config.json");

        if (!configFile.exists()) {
            INSTANCE = new ModConfig();
            // Default loaded templates list
            INSTANCE.general.loaded_mounts.add("horse");
            INSTANCE.general.loaded_mounts.add("wolf");
            INSTANCE.general.loaded_mounts.add("dragon");
            INSTANCE.save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            com.google.gson.stream.JsonReader jsonReader = new com.google.gson.stream.JsonReader(reader);
            jsonReader.setLenient(true);
            INSTANCE = GSON.fromJson(jsonReader, ModConfig.class);
            if (INSTANCE == null) {
                INSTANCE = new ModConfig();
            }
        } catch (IOException e) {
            RPGMounts.LOGGER.error("Failed to load mod config:", e);
        }
    }

    public void save() {
        if (configFile == null) {
            File configDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            configFile = new File(configDir, "server_config.json");
        }
        try {
            String json = GSON.toJson(this);
            String commentedJson = insertComments(json);
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(commentedJson);
            }
        } catch (IOException e) {
            RPGMounts.LOGGER.error("Failed to save mod config:", e);
        }
    }

    private static String insertComments(String json) {
        String[] lines = json.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String comment = getCommentForLine(line);
            if (comment != null) {
                int indent = 0;
                while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                    indent++;
                }
                String spaces = line.substring(0, indent);
                sb.append(spaces).append("// ").append(comment).append("\n");
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static String getCommentForLine(String line) {
        if (line.contains("\"dimensionBlacklist\"")) return "List of dimension IDs where mount summoning is prohibited.";
        if (line.contains("\"dimensionWhitelist\"")) return "List of dimension IDs where mount summoning is allowed (if useWhitelist is true).";
        if (line.contains("\"useWhitelist\"")) return "If true, mounts can only be summoned in whitelist dimensions. If false, blacklists apply.";
        if (line.contains("\"allowSummoningInWater\"")) return "If enabled, allows players to summon mounts while standing/swimming in water.";
        if (line.contains("\"allowSummoningInLava\"")) return "If enabled, allows players to summon mounts while in lava.";
        if (line.contains("\"allowSummoningSubmerged\"")) return "If enabled, mounts can be summoned or remain active while fully submerged underwater.";
        if (line.contains("\"require_manual_dismissal\"")) return "If enabled, mounts must be manually dismissed and will not automatically disappear when logging off.";
        if (line.contains("\"auto_dismiss_on_dismount\"")) return "If enabled, automatically dismisses the mount back to storage as soon as the rider dismounts.";
        if (line.contains("\"autoDespawnIdleSeconds\"")) return "Time in seconds an idle mount remains in the world before automatically dismissing itself. Set to 0 to disable.";
        if (line.contains("\"enableMultiPassenger\"")) return "If enabled, allows mounts with passenger seats to carry multiple players or entities.";
        if (line.contains("\"min_health_allowed\"")) return "The hard minimum health value allowed for any mount configuration.";
        if (line.contains("\"max_health_allowed\"")) return "The hard maximum health value allowed for any mount configuration.";
        if (line.contains("\"min_speed_allowed\"")) return "The hard minimum land speed value allowed for any mount configuration.";
        if (line.contains("\"max_speed_allowed\"")) return "The hard maximum land speed value allowed for any mount configuration.";
        if (line.contains("\"sprint_stamina_cost_per_second\"")) return "Stamina points consumed per second while the mount is sprinting.";
        if (line.contains("\"flight_stamina_cost_per_second\"")) return "Stamina points consumed per second when a flying mount is gaining altitude.";
        if (line.contains("\"flight_descent_stamina_regenerate_ratio\"")) return "Stamina regeneration multiplier rate when a flying mount is gliding/descending.";
        if (line.contains("\"enable_enhancers\"")) return "If enabled, allows mounts to equip statistical enhancer gems.";
        if (line.contains("\"max_enhancers_defense\"")) return "Maximum defense enhancer gems that can be equipped on a single mount.";
        if (line.contains("\"max_enhancers_movement\"")) return "Maximum speed enhancer gems that can be equipped on a single mount.";
        if (line.contains("\"max_enhancers_damage\"")) return "Maximum damage enhancer gems that can be equipped on a single mount.";
        if (line.contains("\"max_enhancers_ability\"")) return "Maximum active ability enhancer gems that can be equipped on a single mount.";
        if (line.contains("\"loaded_mounts\"")) return "List of currently loaded/configured mount templates.";
        if (line.contains("\"enable_bonding_buffs\"")) return "If enabled, taming and riding mounts increases bonding which grants stat boosts.";
        if (line.contains("\"bonding_speed_multiplier\"")) return "The movement speed multiplier boost granted when a mount reaches 100% bonding.";
        if (line.contains("\"bonding_health_multiplier\"")) return "The health multiplier boost granted when a mount reaches 100% bonding.";
        if (line.contains("\"enable_combat_abilities\"")) return "If enabled, mounts can execute active combat abilities if they have any mapped.";
        if (line.contains("\"enable_stamina_system\"")) return "If enabled, mount actions (like sprinting, flying, jumping) will consume stamina, requiring periodic recovery.";
        if (line.contains("\"enable_rider_reach_mixin\"")) return "If enabled, extends the player's interaction and attack reach while riding a mount.";
        if (line.contains("\"rider_reach_offset\"")) return "Additional attack and block reach distance in blocks given to players while riding.";
        if (line.contains("\"enable_fall_protection\"")) return "If enabled, protects the player from fall damage for a short period after dismounting.";
        if (line.contains("\"fall_protection_seconds\"")) return "The duration in seconds that dismount fall protection lasts.";
        if (line.contains("\"culling_distance_blocks\"")) return "The distance from players at which idle client-side mounts will suspend ticking to save CPU performance.";
        if (line.contains("\"enableSpeedPulsing\"")) return "If enabled, flying and aquatic mounts will periodically pulse their speed to simulate wing flaps or tail sweeps.";
        if (line.contains("\"mounts_mortality\"")) return "Mortality rules: Timer (summon cooldown on death), Permadeath, or Item (requires healing item on death).";
        if (line.contains("\"mounts_mortality_item\"")) return "The item required to revive a mount in Item mortality mode.";
        if (line.contains("\"mounts_mortality_cooldown_ticks\"")) return "The cooldown duration in ticks (20 ticks = 1 second) before a dead mount can be summoned in Timer mode.";
        if (line.contains("\"enableMountLevelling\"")) return "If enabled, mounts earn experience points from travelling and fighting to increase their stats.";
        if (line.contains("\"baseXpRequirement\"")) return "The amount of experience points required for a level 1 mount to level up to level 2.";
        if (line.contains("\"xpExponent\"")) return "Defines the scaling curve for subsequent mount level requirements.";
        if (line.contains("\"combatXpDealtRatio\"")) return "Experience points gained by the mount per point of damage dealt to enemies.";
        if (line.contains("\"combatXpTakenRatio\"")) return "Experience points gained by the mount per point of damage taken from enemies.";
        if (line.contains("\"ridingXpPerSecond\"")) return "Experience points gained by the mount per second of active riding.";
        if (line.contains("\"allow_saddleless_jumping\"")) return "If enabled, players can jump on mounts even if they do not have a saddle equipped.";
        if (line.contains("\"allow_saddleless_sprinting\"")) return "If enabled, players can sprint on mounts even if they do not have a saddle equipped.";
        if (line.contains("\"saddle_speed_boost_multiplier\"")) return "The movement speed multiplier boost granted to the mount when a saddle is equipped.";
        if (line.contains("\"enable_evolution_heritage\"")) return "If enabled, mounts can be evolved to their advanced branches using taming items or triggers.";
        if (line.contains("\"evolution_level_policy\"")) return "Determines whether mounts retain their level, reset to level 1, or degrade a percentage of levels on evolution.";
        if (line.contains("\"evolution_degrade_percentage\"")) return "The percentage of levels/experience lost upon evolution if the policy is set to DEGRADE.";
        if (line.contains("\"enable_chroma_mutations\"")) return "If enabled, mounts have a rare chance to evolve into a chroma mutation variant with inverted colors.";
        if (line.contains("\"chroma_mutation_chance\"")) return "The percentage probability (0.0 to 1.0) of a chroma mutation occurring during evolution.";
        if (line.contains("\"enable_environmental_triggers\"")) return "If enabled, allows environmental factors (like biome, block category, time) to trigger evolutions.";
        return null;
    }
}
