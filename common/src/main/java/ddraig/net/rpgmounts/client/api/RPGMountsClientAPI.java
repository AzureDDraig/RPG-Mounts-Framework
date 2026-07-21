package ddraig.net.rpgmounts.client.api;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG Mounts Client API class
 * Exposes registration hooks for compiled Java class models from external mods.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented registerJavaModel and model cache maps.
 */
public class RPGMountsClientAPI {
    public static final ConcurrentHashMap<ResourceLocation, JavaModelData> registeredModels = new ConcurrentHashMap<>();

    public static void registerJavaModel(ResourceLocation modelId, EntityModel<RPGMountEntity> modelInstance, ResourceLocation textureLocation) {
        registeredModels.put(modelId, new JavaModelData(modelInstance, textureLocation));
    }

    public static class JavaModelData {
        public final EntityModel<RPGMountEntity> modelInstance;
        public final ResourceLocation textureLocation;

        public JavaModelData(EntityModel<RPGMountEntity> modelInstance, ResourceLocation textureLocation) {
            this.modelInstance = modelInstance;
            this.textureLocation = textureLocation;
        }
    }
}
