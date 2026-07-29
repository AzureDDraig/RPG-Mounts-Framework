package ddraig.net.rpgmounts.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RPG Mount Data Model
 * Represents the configuration template for a custom mount.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented MountData configuration mapping model with allowed_cargo_map.
 * - 2026-06-19: [Phase 2 Data] - Added enhancerSlots, availableAbilities, and dynamic ability properties.
 */
public class MountData {
    public String id = "";
    public String name = "";
    public String description = "";
    public String category = "GROUND"; // GROUND, AQUATIC, FLYING
    public String rarity = "COMMON";
    public String modelType = "vanilla"; // vanilla, geckolib, mcmodel, java
    public String modelId = "";
    public String texturePath = "";
    public String animationPath = "";
    public float scale = 1.0f;
    public float previewZoom = 1.0f;
    public float previewOffsetY = 0.0f;
    public int enhancerSlots = 4;
    public int staminaIconType = 0; // 0 to 4
    public String flightParticle = "minecraft:cloud";
    public String groundParticle = "minecraft:crit";
    
    public StatsData stats = new StatsData();
    public SoundsData sounds = new SoundsData();
    public CombatData combat = new CombatData();
    
    public Map<String, Integer> allowed_cargo_map = new HashMap<>();
    public Map<String, Integer> allowed_food_map = new HashMap<>();
    public List<SeatOffset> seats = new ArrayList<>();
    public List<AbilityData> availableAbilities = new ArrayList<>();
    public EvolutionData evolution = new EvolutionData();
    public SpawnEffectsData spawnEffects = new SpawnEffectsData();

    public static class StatsData {
        public double maxHealth = 20.0;
        public double movementSpeed = 0.25;
        public double swimSpeed = 0.25;
        public double flySpeed = 0.25;
        public double jumpHeight = 0.6;
        public double maxStamina = 100.0;
        public double staminaRecoveryRate = 5.0;
    }

    public static class SoundsData {
        public String ambient = "";
        public String step = "";
        public String hurt = "";
        public String death = "";
    }

    public static class CombatData {
        public boolean enableCombat = false;
        public double strength = 2.0;
        public double attackSpeed = 1.0;
        public String combatAi = "ASSIST_RIDER";
        public AbilityData ability1 = new AbilityData();
        public AbilityData ability2 = new AbilityData();
    }

    public static class AbilityData {
        public String name = "";
        public String type = "DASH"; // DASH, PROJECTILE, AOE, BUFF, STEALTH
        public boolean isPassive = false;
        public int cooldownTicks = 100;
        public double staminaCost = 20.0;
        public double power = 5.0;
        public int durationTicks = 0;
        public String description = "";
        
        // Phase 2 Fields
        public double damage = 0.0;
        public String damageType = "PHYSICAL"; // PHYSICAL, FIRE, MAGIC, EXPLOSIVE
        public double range = 5.0;
        public String sound = "";
        public String particle = "";
        public int particleCount = 10;
        public String animationName = "";
        public String vanillaAnimation = "NONE"; // NONE, WOLF_BITE, HORSE_REAR, DRAGON_BITE, DRAGON_FLAP
        public List<String> allowedCategories = new ArrayList<>();
        public List<String> allowedMountIds = new ArrayList<>();
    }

    public static class SeatOffset {
        public double x = 0.0;
        public double y = 0.0;
        public double z = 0.0;
        public String boneName = "";

        public SeatOffset() {}
        public SeatOffset(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.boneName = "";
        }
        public SeatOffset(double x, double y, double z, String boneName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.boneName = boneName;
        }
    }

    public static class EvolutionData {
        public String targetId = "";
        public int requiredBonding = 0;
        public Map<String, Integer> requiredItems = new HashMap<>();
    }

    public static class SpawnEffectsData {
        public String particle = "minecraft:happy_villager";
        public int count = 15;
        public String sound = "minecraft:entity.experience_orb.pickup";
    }

    // Transient fields for overall model dimensions
    private transient float calculatedWidth = -1.0f;
    private transient float calculatedHeight = -1.0f;
    private transient boolean dimsCalculated = false;

    public float getModelWidth() {
        if (!dimsCalculated) {
            calculateDimensions();
        }
        return calculatedWidth;
    }

    public float getModelHeight() {
        if (!dimsCalculated) {
            calculateDimensions();
        }
        return calculatedHeight;
    }

    public void resetDimensions() {
        this.dimsCalculated = false;
    }

    private void calculateDimensions() {
        try {
            if (this.modelType != null && this.modelType.equalsIgnoreCase("vanilla")) {
                try {
                    net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(this.modelId);
                    net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (type != null) {
                        net.minecraft.world.entity.EntityDimensions d = type.getDimensions();
                        this.calculatedWidth = d.width;
                        this.calculatedHeight = d.height;
                        this.dimsCalculated = true;
                        return;
                    }
                } catch (Exception ignored) {}
            } else if (this.id != null && !this.id.isEmpty()) {
                java.nio.file.Path baseDir = dev.architectury.platform.Platform.getConfigFolder().resolve("RPG Mounts/Mounts/Unpacked/" + this.id.toLowerCase(java.util.Locale.ROOT));
                File folder = baseDir.toFile();
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles();
                    if (files != null) {
                        if (this.modelType != null && this.modelType.equalsIgnoreCase("geckolib")) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().endsWith(".geo.json")) {
                                    parseGeckoLibDimensions(f);
                                    this.dimsCalculated = true;
                                    return;
                                }
                            }
                        } else if (this.modelType != null && this.modelType.equalsIgnoreCase("java")) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().endsWith(".java")) {
                                    parseJavaDimensions(f);
                                    this.dimsCalculated = true;
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback done below
        }
        
        // Fallback default bounds
        this.calculatedWidth = 1.2f;
        this.calculatedHeight = 1.5f;
        this.dimsCalculated = true;
    }

    private void parseGeckoLibDimensions(File file) {
        try {
            String content = Files.readString(file.toPath());
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has("minecraft:geometry")) {
                JsonArray geometries = json.getAsJsonArray("minecraft:geometry");
                if (geometries.size() > 0) {
                    JsonObject geom = geometries.get(0).getAsJsonObject();
                    if (geom.has("description")) {
                        JsonObject desc = geom.getAsJsonObject("description");
                        float width = desc.has("visible_bounds_width") ? desc.get("visible_bounds_width").getAsFloat() : 0.0f;
                        float height = desc.has("visible_bounds_height") ? desc.get("visible_bounds_height").getAsFloat() : 0.0f;
                        if (width > 0.0f && height > 0.0f) {
                            this.calculatedWidth = width;
                            this.calculatedHeight = height;
                            return;
                        }
                    }
                    
                    // Fallback parse cubes in bones
                    if (geom.has("bones")) {
                        JsonArray bones = geom.getAsJsonArray("bones");
                        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
                        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                        boolean hasCubes = false;
                        for (int i = 0; i < bones.size(); i++) {
                            JsonObject bone = bones.get(i).getAsJsonObject();
                            if (bone.has("cubes")) {
                                JsonArray cubes = bone.getAsJsonArray("cubes");
                                for (int j = 0; j < cubes.size(); j++) {
                                    JsonObject cube = cubes.get(j).getAsJsonObject();
                                    if (cube.has("origin") && cube.has("size")) {
                                        JsonArray origin = cube.getAsJsonArray("origin");
                                        JsonArray size = cube.getAsJsonArray("size");
                                        if (origin.size() >= 3 && size.size() >= 3) {
                                            float ox = origin.get(0).getAsFloat();
                                            float oy = origin.get(1).getAsFloat();
                                            float oz = origin.get(2).getAsFloat();
                                            float sx = size.get(0).getAsFloat();
                                            float sy = size.get(1).getAsFloat();
                                            float sz = size.get(2).getAsFloat();
                                            minX = Math.min(minX, ox);
                                            maxX = Math.max(maxX, ox + sx);
                                            minY = Math.min(minY, oy);
                                            maxY = Math.max(maxY, oy + sy);
                                            minZ = Math.min(minZ, oz);
                                            maxZ = Math.max(maxZ, oz + sz);
                                            hasCubes = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (hasCubes) {
                            this.calculatedWidth = Math.max(maxX - minX, maxZ - minZ) / 16.0f;
                            this.calculatedHeight = (maxY - minY) / 16.0f;
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        this.calculatedWidth = 1.2f;
        this.calculatedHeight = 1.5f;
    }

    private void parseJavaDimensions(File file) {
        try {
            String content = Files.readString(file.toPath());
            content = cleanCommentsAndWhitespace(content);
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            boolean hasCubes = false;
            String[] statements = content.split(";");
            for (String stmt : statements) {
                stmt = stmt.trim();
                if (stmt.contains("addBox")) {
                    int open = stmt.indexOf('(');
                    int close = stmt.lastIndexOf(')');
                    if (open != -1 && close != -1 && close > open) {
                        String inner = stmt.substring(open + 1, close).trim();
                        if (inner.contains("CubeDeformation")) {
                            int lastComma = inner.lastIndexOf(',');
                            if (lastComma != -1) {
                                inner = inner.substring(0, lastComma).trim();
                            }
                        }
                        String[] parts = inner.split(",");
                        if (parts.length >= 6) {
                            float x = parseFloatOrZero(parts[0]);
                            float y = parseFloatOrZero(parts[1]);
                            float z = parseFloatOrZero(parts[2]);
                            float w = parseFloatOrZero(parts[3]);
                            float h = parseFloatOrZero(parts[4]);
                            float d = parseFloatOrZero(parts[5]);
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x + w);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y + h);
                            minZ = Math.min(minZ, z);
                            maxZ = Math.max(maxZ, z + d);
                            hasCubes = true;
                        }
                    }
                }
            }
            if (hasCubes) {
                this.calculatedWidth = Math.max(maxX - minX, maxZ - minZ) / 16.0f;
                this.calculatedHeight = (maxY - minY) / 16.0f;
                return;
            }
        } catch (Exception e) {
            // fallback
        }
        this.calculatedWidth = 1.2f;
        this.calculatedHeight = 1.5f;
    }

    private String cleanCommentsAndWhitespace(String content) {
        content = content.replaceAll("//.*", "");
        content = content.replaceAll("/\\*(?s:.*?)\\*/", "");
        content = content.replaceAll("\\s+", " ");
        return content;
    }

    private float parseFloatOrZero(String s) {
        try {
            s = s.trim().replaceAll("[Ff]", "");
            return Float.parseFloat(s);
        } catch (Exception e) {
            return 0.0f;
        }
    }
}
