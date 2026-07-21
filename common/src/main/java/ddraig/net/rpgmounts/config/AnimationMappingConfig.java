package ddraig.net.rpgmounts.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

public class AnimationMappingConfig {
    private static AnimationMappingConfig INSTANCE = new AnimationMappingConfig();

    public Map<String, AnimationNames> mappings = new HashMap<>();

    public static class AnimationNames {
        public String idle = "idle";
        public String walk = "walk";
        public String run = "run";
        public String swim = "swim";
        public String fly = "fly";
        public String hover = "hover";
        public String attack = "attack";
        public String jump = "jump";
    }

    public static AnimationMappingConfig get() {
        return INSTANCE;
    }

    public static void setInstance(AnimationMappingConfig newConfig) {
        if (newConfig != null) {
            INSTANCE = newConfig;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    public static void load() {
        File configDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "animation_mappings.json");
        if (!configFile.exists()) {
            INSTANCE = new AnimationMappingConfig();
            AnimationNames example = new AnimationNames();
            example.idle = "idle_pose";
            example.walk = "walk_forward";
            example.run = "run_fast";
            example.swim = "swim_glide";
            example.fly = "fly_wings";
            example.hover = "hover_air";
            example.attack = "melee_bite";
            example.jump = "hop_up";
            INSTANCE.mappings.put("example_mount_id", example);
            save();
        } else {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = GSON.fromJson(reader, AnimationMappingConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new AnimationMappingConfig();
                }
            } catch (Exception e) {
                INSTANCE = new AnimationMappingConfig();
            }
        }
    }

    public static void save() {
        if (configFile == null) {
            File configDir = new File(Platform.getConfigFolder().toFile(), "RPG Mounts");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            configFile = new File(configDir, "animation_mappings.json");
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(INSTANCE, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AnimationNames getMappingFor(String templateId) {
        if (templateId == null) return new AnimationNames();
        AnimationNames mapping = mappings.get(templateId);
        if (mapping == null) {
            for (Map.Entry<String, AnimationNames> entry : mappings.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(templateId)) {
                    return entry.getValue();
                }
            }
            return new AnimationNames();
        }
        
        // Handle partial overrides where the server owner defined only some fields
        if (mapping.idle == null) mapping.idle = "idle";
        if (mapping.walk == null) mapping.walk = "walk";
        if (mapping.run == null) mapping.run = "run";
        if (mapping.swim == null) mapping.swim = "swim";
        if (mapping.fly == null) mapping.fly = "fly";
        if (mapping.hover == null) mapping.hover = "hover";
        if (mapping.attack == null) mapping.attack = "attack";
        if (mapping.jump == null) mapping.jump = "jump";
        
        return mapping;
    }
}
