package ddraig.net.rpgmounts.evolution.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountRegistry;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages evolution tree configuration files, deserialization, graph validation, and hot-reloading.
 */
public class EvolutionTreeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RPG-Mounts-Evolution-Trees");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static File treesFolder;

    // Active memory cache of all loaded evolution trees (keyed by base mount template ID)
    public static final Map<String, EvolutionTree> loadedTrees = new ConcurrentHashMap<>();

    public static void init() {
        File configDir = Platform.getConfigFolder().toFile();
        File baseDir = new File(configDir, "RPG Mounts");
        treesFolder = new File(baseDir, "Evolution Trees");

        if (!treesFolder.exists()) {
            treesFolder.mkdirs();
            createDefaultTreeTemplates();
        }
        reloadTrees();
    }

    public static File getTreesFolder() {
        return treesFolder;
    }

    /**
     * Reads all JSON files in the config folder, deserializes trees, and runs the DFS graph validator.
     */
    public static void reloadTrees() {
        loadedTrees.clear();
        if (treesFolder == null || !treesFolder.exists()) return;

        File[] files = treesFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                EvolutionTree tree = GSON.fromJson(reader, EvolutionTree.class);
                if (tree != null && tree.mountId != null && !tree.mountId.isEmpty()) {
                    loadedTrees.put(tree.mountId, tree);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse evolution tree config: " + file.getName(), e);
            }
        }
        LOGGER.info("Loaded {} evolution config trees.", loadedTrees.size());

        // Perform validation check
        runGraphValidationDiagnostics();
        executeOrphanMountRecovery();
    }

    /**
     * Saves a serialized tree configuration to disk.
     */
    public static boolean saveTree(String baseMountId, String jsonContent) {
        if (treesFolder == null) return false;
        try {
            // Validate JSON before saving
            EvolutionTree tree = GSON.fromJson(jsonContent, EvolutionTree.class);
            if (tree == null || tree.mountId == null || !tree.mountId.equals(baseMountId)) {
                return false;
            }

            File file = new File(treesFolder, baseMountId.replace(":", "_") + ".json");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(jsonContent);
                LOGGER.info("Saved evolution tree configuration for base mount: {}", baseMountId);
            }
            loadedTrees.put(baseMountId, tree);
            
            // Re-validate graph state
            runGraphValidationDiagnostics();
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save evolution tree JSON for " + baseMountId, e);
            return false;
        }
    }

    /**
     * Executes the Orphan Mount Recovery Policy.
     * Checks all unlocked mounts in cache and maps obsoleted/deleted configurations back
     * to the nearest active parent in their ancestry chain, or falls back to a base type.
     */
    public static void executeOrphanMountRecovery() {
        if (!DatabaseManager.isInitialized()) {
            return;
        }
        for (Map<String, DatabaseManager.UnlockedMountData> map : DatabaseManager.unlockedMountsCache.values()) {
            for (DatabaseManager.UnlockedMountData data : map.values()) {
                if (!MountRegistry.loadedTemplates.containsKey(data.mountId)) {
                    String recoveredId = findNearestActiveParent(data);
                    if (recoveredId != null) {
                        LOGGER.warn("Orphan Mount Recovery: Mapping orphan mount instance {} from obsolete type {} to active type {}", data.instanceId, data.mountId, recoveredId);
                        data.mountId = recoveredId;
                        data.dirty = true;
                    }
                }
            }
        }
        DatabaseManager.flushDirtyUnlockedMounts();
    }

    private static String findNearestActiveParent(DatabaseManager.UnlockedMountData data) {
        if (data.ancestryLog != null && !data.ancestryLog.isEmpty() && !data.ancestryLog.equals("[]")) {
            try {
                JsonArray arr = new JsonParser().parse(data.ancestryLog).getAsJsonArray();
                for (int i = arr.size() - 1; i >= 0; i--) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String fromId = obj.has("fromId") ? obj.get("fromId").getAsString() : "";
                    if (!fromId.isEmpty() && MountRegistry.loadedTemplates.containsKey(fromId)) {
                        return fromId;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        if (!MountRegistry.loadedTemplates.isEmpty()) {
            return MountRegistry.loadedTemplates.keySet().iterator().next();
        }
        return null;
    }

    /**
     * Builds adjacency list of all evolution paths and executes DFS validation check.
     */
    public static EvolutionGraphValidator.ValidationReport runGraphValidationDiagnostics() {
        Map<String, List<String>> adjList = new HashMap<>();

        // Populate adjacency list mapping parent template IDs to children template IDs
        for (Map.Entry<String, EvolutionTree> entry : loadedTrees.entrySet()) {
            String parent = entry.getKey();
            List<String> children = adjList.computeIfAbsent(parent, k -> new ArrayList<>());
            for (EvolutionBranch branch : entry.getValue().branches) {
                if (branch.targetId != null && !branch.targetId.isEmpty()) {
                    children.add(branch.targetId);
                }
            }
        }

        EvolutionGraphValidator.ValidationReport report = EvolutionGraphValidator.validate(adjList);
        if (!report.isValid) {
            LOGGER.error("------------------------------------------------------------");
            LOGGER.error("EVOLUTION TREE DIAGNOSTICS: CRITICAL CONFIGURATION FAULTS FOUND!");
            for (String error : report.errorMessages) {
                LOGGER.error(" -> [ERROR] {}", error);
            }
            LOGGER.error("------------------------------------------------------------");
        } else {
            LOGGER.info("Evolution Trees Graph Diagnostics: Passed DAG check successfully (No cycle loops detected).");
        }
        return report;
    }

    private static void createDefaultTreeTemplates() {
        // Creates a simple placeholder example JSON template
        EvolutionTree tree = new EvolutionTree();
        tree.mountId = "rpg_mounts:dire_wolf";
        
        EvolutionBranch hellhound = new EvolutionBranch();
        hellhound.targetId = "rpg_mounts:hellhound";
        hellhound.displayName = "Hellhound";
        hellhound.xCoord = -60;
        hellhound.yCoord = 50;
        hellhound.excludes = List.of("rpg_mounts:fenrir");
        hellhound.reroutePoints = List.of(new Vec2i(-20, 20), new Vec2i(-40, 20));
        hellhound.requiredBonding = 50;
        hellhound.requiredLevel = 10;
        
        hellhound.conditions.requiresChroma = true;
        hellhound.conditions.dimension = "minecraft:the_nether";
        hellhound.conditions.biome = "minecraft:soul_sand_valley";
        hellhound.conditions.damageDealt = 500.0;
        hellhound.conditions.items = List.of(new ItemCatalyst("minecraft:wither_skeleton_skull", 1));
        hellhound.conditions.statusEffects = List.of(new StatusEffectConfig("minecraft:fire_resistance", 0));
        hellhound.conditions.mobKills = List.of(new MobKillConfig("minecraft:wither", 1));
        hellhound.conditions.moonPhases = List.of(0);

        EvolutionBranch fenrir = new EvolutionBranch();
        fenrir.targetId = "rpg_mounts:fenrir";
        fenrir.displayName = "Fenrir";
        fenrir.xCoord = 60;
        fenrir.yCoord = 50;
        fenrir.excludes = List.of("rpg_mounts:hellhound");
        fenrir.requiredBonding = 60;
        fenrir.requiredLevel = 12;
        fenrir.conditions.dimension = "minecraft:overworld";
        fenrir.conditions.biome = "minecraft:frozen_peaks";
        fenrir.conditions.items = List.of(new ItemCatalyst("minecraft:nether_star", 1));

        tree.branches = List.of(hellhound, fenrir);

        File file = new File(treesFolder, "rpg_mounts_dire_wolf.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(tree, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default placeholder tree template", e);
        }
    }

    // --- Schema Definitions ---

    public static class EvolutionTree {
        public String mountId = "";
        public List<EvolutionBranch> branches = new ArrayList<>();
    }

    public static class EvolutionBranch {
        public String targetId = "";
        public String parentId = "";
        public String displayName = "";
        public int xCoord = 0;
        public int yCoord = 0;
        public List<String> excludes = new ArrayList<>();
        public List<Vec2i> reroutePoints = new ArrayList<>();
        public int requiredBonding = 0;
        public int requiredLevel = 0;
        public PrerequisiteConditions conditions = new PrerequisiteConditions();
    }

    public static class PrerequisiteConditions {
        public boolean requiresChroma = false;
        public String dimension = "";
        public String biome = "";
        public String weather = "";
        public double damageDealt = 0.0;
        public double damageTaken = 0.0;
        public int hpZeroCount = 0;
        public double distanceTravelled = 0.0;
        public List<ItemCatalyst> items = new ArrayList<>();
        public List<StatusEffectConfig> statusEffects = new ArrayList<>();
        public List<MobKillConfig> mobKills = new ArrayList<>();
        public List<Integer> moonPhases = new ArrayList<>();
        
        // Composite condition gates
        public List<PrerequisiteConditions> all_of = new ArrayList<>();
        public List<PrerequisiteConditions> any_of = new ArrayList<>();
        public List<PrerequisiteConditions> none_of = new ArrayList<>();
    }

    public static class ItemCatalyst {
        public String id = "";
        public int count = 0;

        public ItemCatalyst() {}
        public ItemCatalyst(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public static class StatusEffectConfig {
        public String id = "";
        public int amplifier = 0;

        public StatusEffectConfig() {}
        public StatusEffectConfig(String id, int amplifier) {
            this.id = id;
            this.amplifier = amplifier;
        }
    }

    public static class MobKillConfig {
        public String id = "";
        public int count = 0;

        public MobKillConfig() {}
        public MobKillConfig(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public static class Vec2i {
        public int x = 0;
        public int y = 0;

        public Vec2i() {}
        public Vec2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
